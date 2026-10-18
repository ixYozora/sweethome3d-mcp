package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.sh3d.mcp.command.handler.TestFixtures.createAccessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * render_photo's long-render handling and output file naming: {@link RenderJobRunner},
 * {@link RenderPhotoHandler#resolveImagePath}, the fallback files and the early response.
 */
class RenderLongRunTest {

    /** A render thread of this test's own, so nothing here queues behind or ahead of other tests. */
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = RenderJobRunner.newRenderExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // --- RenderJobRunner ---

    @Test
    void testFastJobReturnsItsResponse() {
        RenderJobRunner runner = new RenderJobRunner(5_000, executor);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("done", true);

        Response resp = runner.run(() -> Response.ok(data),
                () -> Response.error("pending"), r -> fail("no late result for a fast job"));

        assertTrue(resp.isOk());
        assertEquals(true, resp.getData().get("done"));
    }

    @Test
    void testSlowJobAnswersPendingAndDeliversLateResult() throws Exception {
        RenderJobRunner runner = new RenderJobRunner(50, executor);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Response> late = new AtomicReference<>();

        Response resp = runner.run(() -> {
            sleep(400);
            return Response.ok(Collections.singletonMap("late", "yes"));
        }, () -> Response.ok(Collections.singletonMap("status", "rendering")), r -> {
            late.set(r);
            delivered.countDown();
        });

        assertEquals("rendering", resp.getData().get("status"));
        assertTrue(delivered.await(5, TimeUnit.SECONDS), "late result never arrived");
        assertEquals("yes", late.get().getData().get("late"));
    }

    @Test
    void testJobExceptionBecomesError() {
        RenderJobRunner runner = new RenderJobRunner(5_000, executor);

        Response resp = runner.run(() -> {
            throw new IllegalStateException("boom");
        }, () -> Response.error("pending"), r -> { });

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("boom"));
    }

    // --- resolveImagePath ---

    @Test
    void testPngReplacesJpgInsteadOfDoubling() {
        assertEquals(abs("/r/ov_1.png"), RenderPhotoHandler.resolveImagePath("/r/ov_1.jpg", "png"));
        assertEquals(abs("/r/ov.png"), RenderPhotoHandler.resolveImagePath("/r/ov.JPEG", "png"));
        assertEquals(abs("/r/ov.png"), RenderPhotoHandler.resolveImagePath("/r/ov.png", "png"));
        assertEquals(abs("/r/ov.png"), RenderPhotoHandler.resolveImagePath("/r/ov", "png"));
    }

    @Test
    void testJpegReplacesPngAndKeepsJpegSpelling() {
        assertEquals(abs("/r/ov.jpg"), RenderPhotoHandler.resolveImagePath("/r/ov.png", "jpeg"));
        assertEquals(abs("/r/ov.jpeg"), RenderPhotoHandler.resolveImagePath("/r/ov.jpeg", "jpeg"));
        assertEquals(abs("/r/ov.jpg"), RenderPhotoHandler.resolveImagePath("/r/ov.jpg", "jpeg"));
        assertEquals(abs("/r/ov.jpg"), RenderPhotoHandler.resolveImagePath("/r/ov", "jpeg"));
    }

    @Test
    void testOtherDotsInTheNameAreKept() {
        assertEquals(abs("/r/plan.v2.png"), RenderPhotoHandler.resolveImagePath("/r/plan.v2", "png"));
    }

    // --- fallback files for inline renders ---

    @Test
    void testFallbackPathsAreIndexedInTempDir() {
        List<Path> one = RenderPhotoHandler.fallbackPaths(1, "jpeg");
        List<Path> four = RenderPhotoHandler.fallbackPaths(4, "png");

        assertEquals(1, one.size());
        assertTrue(one.get(0).getFileName().toString().matches("render-[0-9-]+\\.jpg"));
        assertEquals(4, four.size());
        assertTrue(four.get(3).getFileName().toString().matches("render-[0-9-]+_4\\.png"));
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath();
        assertTrue(four.get(0).startsWith(tmp.resolve("sh3d-mcp-renders")));
    }

    @Test
    void testSaveInlineImagesWritesSingleAndOverheadImages(@TempDir Path dir) throws Exception {
        byte[] a = {1, 2, 3};
        byte[] b = {4, 5};
        Map<String, Object> single = new LinkedHashMap<>();
        single.put("_image", Base64.getEncoder().encodeToString(a));
        Path singleTarget = dir.resolve("sub/one.jpg");

        RenderPhotoHandler.saveInlineImages(Response.ok(single), Collections.singletonList(singleTarget));

        assertArrayEquals(a, Files.readAllBytes(singleTarget));

        Map<String, Object> overhead = new LinkedHashMap<>();
        overhead.put("_images", Arrays.asList(
                Collections.singletonMap("data", Base64.getEncoder().encodeToString(a)),
                Collections.singletonMap("data", Base64.getEncoder().encodeToString(b))));
        List<Path> targets = Arrays.asList(dir.resolve("o_1.png"), dir.resolve("o_2.png"));

        RenderPhotoHandler.saveInlineImages(Response.ok(overhead), targets);

        assertArrayEquals(a, Files.readAllBytes(targets.get(0)));
        assertArrayEquals(b, Files.readAllBytes(targets.get(1)));
    }

    // --- handler integration ---

    @Test
    @SuppressWarnings("unchecked")
    void testRenderPastBudgetReturnsTargetPaths(@TempDir Path dir) {
        Home home = new Home();
        RenderPhotoHandler handler = new RenderPhotoHandler(new RenderJobRunner(0, executor));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("filePath", dir.resolve("shot.jpg").toString());
        params.put("width", 2);
        params.put("height", 2);

        CountDownLatch release = blockRenderThread();
        Response resp;
        try {
            resp = handler.execute(new Request("render_photo", params), createAccessor(home));
        } finally {
            release.countDown();
        }

        // The render waits behind the blocking job, so the response goes out first
        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("rendering", resp.getData().get("status"));
        assertEquals(Collections.singletonList(dir.resolve("shot.png").toAbsolutePath().toString()),
                resp.getData().get("filePaths"));
        assertEquals("png", resp.getData().get("format"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testOverheadPastBudgetReturnsIndexedTargets(@TempDir Path dir) {
        Home home = new Home();
        home.addWall(new Wall(0, 0, 500, 0, 10, 250));
        RenderPhotoHandler handler = new RenderPhotoHandler(new RenderJobRunner(0, executor));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("view", "overhead");
        params.put("angles", 4);
        params.put("format", "jpeg");
        params.put("filePath", dir.resolve("ov.png").toString());
        params.put("width", 2);
        params.put("height", 2);

        CountDownLatch release = blockRenderThread();
        Response resp;
        try {
            resp = handler.execute(new Request("render_photo", params), createAccessor(home));
        } finally {
            release.countDown();
        }

        assertTrue(resp.isOk(), resp.getMessage());
        List<String> paths = (List<String>) resp.getData().get("filePaths");
        assertEquals(4, paths.size());
        assertEquals(dir.resolve("ov_1.jpg").toAbsolutePath().toString(), paths.get(0));
        assertEquals(dir.resolve("ov_4.jpg").toAbsolutePath().toString(), paths.get(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInlineRenderPastBudgetIsRedirectedToFiles() {
        RenderPhotoHandler handler = new RenderPhotoHandler(new RenderJobRunner(0, executor));

        CountDownLatch release = blockRenderThread();
        Response resp;
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("width", 2);
            params.put("height", 2);
            resp = handler.execute(new Request("render_photo", params), createAccessor(new Home()));
        } finally {
            release.countDown();
        }

        assertTrue(resp.isOk(), resp.getMessage());
        List<String> paths = (List<String>) resp.getData().get("filePaths");
        assertEquals(1, paths.size());
        assertTrue(paths.get(0).endsWith(".jpg"));
        assertTrue(resp.getData().get("message").toString().contains("instead of returned inline"));
    }

    /**
     * Occupies the shared render thread until the returned latch is released, so a render
     * submitted meanwhile is guaranteed to outlive a zero budget.
     */
    private CountDownLatch blockRenderThread() {
        CountDownLatch release = new CountDownLatch(1);
        new RenderJobRunner(0, executor).run(() -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Response.ok(new LinkedHashMap<>());
        }, () -> Response.ok(new LinkedHashMap<>()), r -> { });
        return release;
    }

    private static Path abs(String path) {
        return Paths.get(path).toAbsolutePath().normalize();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
