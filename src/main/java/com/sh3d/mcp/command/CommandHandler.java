package com.sh3d.mcp.command;

import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

/**
 * Интерфейс обработчика команды.
 * <p>
 * Метод вызывается в рабочем потоке (не в EDT).
 * Для взаимодействия с Home используйте accessor.runOnEDT().
 */
public interface CommandHandler {

    /**
     * Выполняет команду.
     *
     * @param request  распарсенный запрос (action + params)
     * @param accessor потокобезопасный мост к Sweet Home 3D
     * @return ответ (ok + data, либо error + message)
     */
    Response execute(Request request, HomeAccessor accessor);

    /**
     * Whether this command can change the home model.
     * <p>
     * Sweet Home 3D only marks a home as modified when a change goes through its undo
     * support, and handlers change the model directly. {@link CommandRegistry} therefore
     * marks the home modified after every command that returns {@code true}, so that
     * closing Sweet Home 3D asks to save. Read-only commands override this with {@code false}.
     */
    default boolean modifiesHome() {
        return true;
    }
}
