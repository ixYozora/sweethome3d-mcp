package com.sh3d.mcp.command.util;

import com.eteks.sweethome3d.model.CatalogDoorOrWindow;
import com.eteks.sweethome3d.model.CatalogLight;
import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.CatalogShelfUnit;
import com.eteks.sweethome3d.model.HomeDoorOrWindow;
import com.eteks.sweethome3d.model.HomeLight;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeShelfUnit;
import com.eteks.sweethome3d.model.LightSource;
import com.eteks.sweethome3d.model.Sash;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HomePieceFactoryTest {

    @Test
    void testCatalogLightBecomesHomeLightWithItsLightSources() {
        CatalogLight lamp = new CatalogLight("test#lamp", "Lamp", null, null, null,
                30f, 30f, 60f, 0f, true,
                new LightSource[] {new LightSource(0.5f, 0.5f, 0.9f, 0xFFFFE0)},
                null, null, true, null, null);

        HomePieceOfFurniture piece = HomePieceFactory.create(lamp);

        assertTrue(piece instanceof HomeLight);
        HomeLight light = (HomeLight) piece;
        assertEquals(1, light.getLightSources().length);
        assertEquals(0.5f, light.getPower(), 0.001f);
    }

    @Test
    void testCatalogDoorBecomesHomeDoorOrWindow() {
        CatalogDoorOrWindow door = new CatalogDoorOrWindow("test#door", "Door", null, null, null,
                90f, 12f, 210f, 0f, false, 1f, 0f, new Sash[0], null, null, true, null, null);

        assertTrue(HomePieceFactory.create(door) instanceof HomeDoorOrWindow);
    }

    @Test
    void testCatalogShelfUnitKeepsShelvesAndModelPresetProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("modelPresetTransformationsName_0", "Open");
        properties.put("unrelated", "not copied");
        CatalogShelfUnit shelf = new CatalogShelfUnit("test#shelf", "Shelf",
                null, null, null, null, null, null,
                null, null, null,
                80f, 30f, 180f, 0f, 180f,
                new float[] {40f, 80f}, null,
                true, null, null, 0, null, null,
                true, true, true, true,
                null, null, null,
                properties, null);

        HomePieceOfFurniture piece = HomePieceFactory.create(shelf);

        assertTrue(piece instanceof HomeShelfUnit);
        assertEquals(2, ((HomeShelfUnit) piece).getShelfElevations().length);
        assertEquals("Open", piece.getProperty("modelPresetTransformationsName_0"));
        assertNull(piece.getProperty("unrelated"));
    }

    @Test
    void testPlainCatalogPieceStaysPlain() {
        CatalogPieceOfFurniture table = new CatalogPieceOfFurniture(
                "Table", null, null, 120f, 80f, 75f, true, false);

        assertEquals(HomePieceOfFurniture.class, HomePieceFactory.create(table).getClass());
    }
}
