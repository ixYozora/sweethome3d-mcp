package com.sh3d.mcp.command.handler;

import com.sh3d.mcp.bridge.CheckpointManager;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which commands mark the home modified after they run (see {@link CommandHandler#modifiesHome()}).
 */
class ModifiesHomeTest {

    private final CheckpointManager checkpoints = new CheckpointManager();

    @Test
    void testReadOnlyCommandsDoNotMarkHomeModified() {
        List<CommandHandler> readOnly = Arrays.asList(
                new GetStateHandler(),
                new ListCategoriesHandler(),
                new ListFurnitureCatalogHandler(),
                new ListTexturesCatalogHandler(),
                new ListLevelsHandler(),
                new ListCheckpointsHandler(checkpoints),
                new GetCamerasHandler(),
                new ExportPlanImageHandler(null),
                new ExportSvgHandler(null),
                new ExportToObjHandler(),
                new RenderPhotoHandler(),
                new CheckpointHandler(checkpoints),
                new SaveHomeHandler(),
                new LoadHomeHandler(),
                new SetSelectedLevelHandler(),
                new BatchCommandsHandler(new CommandRegistry(), checkpoints));
        for (CommandHandler handler : readOnly) {
            assertFalse(handler.modifiesHome(), handler.getClass().getSimpleName());
        }
    }

    @Test
    void testEditingCommandsMarkHomeModified() {
        List<CommandHandler> editing = Arrays.asList(
                new CreateWallHandler(),
                new CreateWallsHandler(),
                new ModifyWallHandler(),
                new DeleteWallHandler(),
                new ConnectWallsHandler(),
                new CreateRoomPolygonHandler(),
                new ModifyRoomHandler(),
                new DeleteRoomHandler(),
                new PlaceFurnitureHandler(),
                new ModifyFurnitureHandler(),
                new DeleteFurnitureHandler(),
                new DuplicateObjectsHandler(),
                new GroupFurnitureHandler(),
                new UngroupFurnitureHandler(),
                new PlaceDoorOrWindowHandler(),
                new ApplyTextureHandler(),
                new SetEnvironmentHandler(),
                new GenerateShapeHandler(),
                new AddLabelHandler(),
                new AddDimensionLineHandler(),
                new ModifyDimensionLineHandler(),
                new DeleteDimensionLineHandler(),
                new SetCameraHandler(),
                new StoreCameraHandler(),
                new AddLevelHandler(),
                new DeleteLevelHandler(),
                new ClearSceneHandler(checkpoints),
                new RestoreCheckpointHandler(checkpoints));
        for (CommandHandler handler : editing) {
            assertTrue(handler.modifiesHome(), handler.getClass().getSimpleName());
        }
    }
}
