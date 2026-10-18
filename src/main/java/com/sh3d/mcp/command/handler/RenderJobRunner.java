package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.protocol.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs render jobs on one background thread and answers within a time budget.
 *
 * <p>Ray-traced renders can take longer than an MCP client waits for a tool call
 * (often 60 s). When a job outlives the budget, the caller gets the {@code pending}
 * response instead and the job keeps running; its result goes to {@code lateResult}
 * when it finishes, so the images still reach disk. Jobs run one at a time, which
 * also keeps two Sunflow renders from competing for memory.
 */
final class RenderJobRunner {

    private static final Logger LOG = Logger.getLogger(RenderJobRunner.class.getName());

    /** One daemon thread shared by all handlers: renders queue instead of running in parallel. */
    private static final ExecutorService SHARED_EXECUTOR = newRenderExecutor();

    private final long budgetMs;
    private final ExecutorService executor;

    RenderJobRunner(long budgetMs) {
        this(budgetMs, SHARED_EXECUTOR);
    }

    /** For tests: run jobs on {@code executor} instead of the shared render thread. */
    RenderJobRunner(long budgetMs, ExecutorService executor) {
        this.budgetMs = budgetMs;
        this.executor = executor;
    }

    /** A single daemon thread named "sh3d-mcp-render". */
    static ExecutorService newRenderExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sh3d-mcp-render");
            t.setDaemon(true);
            return t;
        });
    }

    long getBudgetMs() {
        return budgetMs;
    }

    /**
     * Runs {@code job}; returns its response if it finishes within the budget, otherwise
     * {@code pending.get()}, and hands the job's eventual response to {@code lateResult}.
     */
    Response run(Supplier<Response> job, Supplier<Response> pending, Consumer<Response> lateResult) {
        CompletableFuture<Response> future = CompletableFuture.supplyAsync(job, executor);
        try {
            return future.get(budgetMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Registered after the timeout: if the job finished in between, this runs right away
            future.whenComplete((response, error) -> {
                if (error != null) {
                    LOG.log(Level.WARNING, "Background render failed", error);
                    return;
                }
                try {
                    lateResult.accept(response);
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Could not finish background render", ex);
                }
            });
            return pending.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOG.log(Level.WARNING, "Render failed", cause);
            return Response.error("Rendering failed: " + cause.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.error("Rendering interrupted");
        }
    }
}
