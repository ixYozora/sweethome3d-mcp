package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.DoorOrWindow;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeShelfUnit;
import com.eteks.sweethome3d.model.Light;
import com.eteks.sweethome3d.model.PieceOfFurniture;
import com.eteks.sweethome3d.model.ShelfUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates the home piece for a catalog piece with the matching type, like Sweet Home 3D's
 * {@code FurnitureController.createHomePieceOfFurniture}: a light keeps its light sources
 * and power, a door or window its wall cut-out and sashes, a shelf unit its shelves.
 * A plain {@link HomePieceOfFurniture} silently drops all of these (lights then render dark).
 */
public final class HomePieceFactory {

    /** Catalog properties copied to the home piece, as Sweet Home 3D does. */
    private static final String[] COPIED_PROPERTY_PREFIXES = {
            "modelPresetTransformationsName_", "modelPresetTransformations_"
    };

    private HomePieceFactory() {
    }

    public static HomePieceOfFurniture create(PieceOfFurniture piece) {
        String[] copiedProperties = copiedProperties(piece);
        if (piece instanceof DoorOrWindow) {
            return new HomeDoorOrWindow((DoorOrWindow) piece, copiedProperties);
        } else if (piece instanceof Light) {
            return new HomeLight((Light) piece, copiedProperties);
        } else if (piece instanceof ShelfUnit) {
            return new HomeShelfUnit((ShelfUnit) piece, copiedProperties);
        } else {
            return new HomePieceOfFurniture(piece, copiedProperties);
        }
    }

    private static String[] copiedProperties(PieceOfFurniture piece) {
        List<String> names = new ArrayList<>();
        for (String name : piece.getPropertyNames()) {
            for (String prefix : COPIED_PROPERTY_PREFIXES) {
                if (name.startsWith(prefix)) {
                    names.add(name);
                    break;
                }
            }
        }
        return names.toArray(new String[0]);
    }
}
