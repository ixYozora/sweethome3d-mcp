package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;

import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;

import java.util.List;
import java.util.Map;
import com.sh3d.mcp.command.util.FormatUtil;

/**
 * Обработчик команды "add_level".
 * Создаёт новый уровень (этаж) и делает его активным.
 */
public class AddLevelHandler implements CommandHandler, CommandDescriptor {

    static final String GROUND_LEVEL_NAME = "Level 0";

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String name = request.getString("name");
        if (name == null || name.trim().isEmpty()) {
            return Response.error("Parameter 'name' is required and must not be empty");
        }

        float elevation = request.getFloat("elevation");
        float height = request.getFloat("height", 250f);
        float floorThickness = request.getFloat("floorThickness", 12f);

        if (height <= 0) {
            return Response.error("Parameter 'height' must be positive, got " + height);
        }
        if (floorThickness < 0) {
            return Response.error("Parameter 'floorThickness' must be non-negative, got " + floorThickness);
        }

        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Level level = new Level(name.trim(), elevation, floorThickness, height);

            // Like Sweet Home 3D's own "Add level": content drawn before the home had levels goes
            // onto a ground level, instead of staying without a level (drawn on every floor).
            // The new level is that ground level if it is at elevation 0.
            Level groundLevel = null;
            int movedItems = 0;
            if (home.getLevels().isEmpty() && countItems(home) > 0) {
                groundLevel = elevation == 0 ? level : createGroundLevel(accessor.getUserPreferences());
                home.addLevel(groundLevel);
                movedItems = moveAllItemsToLevel(home, groundLevel);
                groundLevel.setBackgroundImage(home.getBackgroundImage());
                home.setBackgroundImage(null);
            }
            if (level != groundLevel) {
                home.addLevel(level);
            }
            home.setSelectedLevel(level);

            Map<String, Object> result = FormatUtil.buildLevelInfo(level.getId(), level);
            result.put("levelCount", home.getLevels().size());
            if (groundLevel != null) {
                Map<String, Object> ground = FormatUtil.buildLevelInfo(groundLevel.getId(), groundLevel);
                ground.put("movedItemCount", movedItems);
                result.put("groundLevel", ground);
            }
            return result;
        });

        return Response.ok(data);
    }

    /** Level 0 with the default new wall height and floor thickness, as PlanController.addLevel creates it. */
    private static Level createGroundLevel(UserPreferences preferences) {
        float height = preferences != null ? preferences.getNewWallHeight() : 250f;
        float floorThickness = preferences != null ? preferences.getNewFloorThickness() : 12f;
        return new Level(GROUND_LEVEL_NAME, 0, floorThickness, height);
    }

    private static int countItems(Home home) {
        return home.getFurniture().size() + home.getWalls().size() + home.getRooms().size()
                + home.getPolylines().size() + home.getDimensionLines().size() + home.getLabels().size();
    }

    /** Puts every item of the home on the given level, like PlanController.moveHomeItemsToLevel. */
    private static int moveAllItemsToLevel(Home home, Level level) {
        for (HomePieceOfFurniture piece : home.getFurniture()) {
            piece.setLevel(level);
        }
        for (Wall wall : home.getWalls()) {
            wall.setLevel(level);
        }
        for (Room room : home.getRooms()) {
            room.setLevel(level);
        }
        for (Polyline polyline : home.getPolylines()) {
            polyline.setLevel(level);
        }
        for (DimensionLine dimensionLine : home.getDimensionLines()) {
            dimensionLine.setLevel(level);
        }
        for (Label label : home.getLabels()) {
            label.setLevel(level);
        }
        return countItems(home);
    }

    @Override
    public String getDescription() {
        return "Creates a new level (floor/storey) in the home. The new level becomes the active (selected) level. "
                + "All subsequent create commands (walls, rooms, furniture) will be placed on the selected level. "
                + "Elevation is the bottom height of the level in cm (e.g., 0 for ground floor, 250 for second floor). "
                + "Height is the wall height on this level (default 250 cm). "
                + "Use list_levels to see all levels, set_selected_level to switch between them."
                + "\n\nIf the home has no levels yet but already has content, that content is first put on "
                + "a ground level, as Sweet Home 3D's own Add level does: the new level itself if its "
                + "elevation is 0, otherwise a new '" + GROUND_LEVEL_NAME + "' at elevation 0 "
                + "(reported as 'groundLevel' with 'movedItemCount').";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("name", "Name of the level (e.g., 'Ground Floor', 'Second Floor', 'Attic')")
                .requiredNumber("elevation", "Bottom elevation of the level in cm. 0 for ground floor, typically previous level's elevation + height for upper floors")
                .numberWithDefault("height", "Wall height on this level in cm", 250)
                .numberWithDefault("floorThickness", "Floor/ceiling slab thickness in cm", 12)
                .build();
    }

}
