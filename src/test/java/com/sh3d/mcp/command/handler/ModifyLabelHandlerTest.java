package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ModifyLabelHandlerTest {

    private ModifyLabelHandler handler;
    private HomeAccessor accessor;
    private Home home;
    private Label label;

    @BeforeEach
    void setUp() {
        handler = new ModifyLabelHandler();
        home = new Home();
        accessor = createAccessor(home);
        label = new Label("Kitchen", 100, 200);
        home.addLabel(label);
    }

    private Response modify(Object... keyValues) {
        Object[] all = new Object[keyValues.length + 2];
        all[0] = "id";
        all[1] = label.getId();
        System.arraycopy(keyValues, 0, all, 2, keyValues.length);
        return handler.execute(makeRequest("modify_label", all), accessor);
    }

    @Test
    void testMovesLabel() {
        Response resp = modify("x", 350.0, "y", -40.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(350f, label.getX(), 0f);
        assertEquals(-40f, label.getY(), 0f);
        assertEquals("Kitchen", label.getText());
    }

    @Test
    void testChangesTextAngleAndColors() {
        Response resp = modify("text", "S1 Herren-WC\n10,91 m²", "angle", 90.0,
                "color", "#3535C8", "outlineColor", "#FFFFFF");

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals("S1 Herren-WC\n10,91 m²", label.getText());
        assertEquals(Math.PI / 2, label.getAngle(), 1e-5);
        assertEquals(Integer.valueOf(0x3535C8), label.getColor());
        assertEquals(Integer.valueOf(0xFFFFFF), label.getOutlineColor());
        assertEquals("#3535C8", resp.getData().get("color"));
        assertEquals(90.0, ((Number) resp.getData().get("angle")).doubleValue(), 0.01);
    }

    @Test
    void testNullColorResetsIt() {
        label.setColor(0xFF0000);

        Response resp = modify("color", null);

        assertTrue(resp.isOk(), resp.getMessage());
        assertNull(label.getColor());
    }

    @Test
    void testStyleChangesOnlyGivenProperties() {
        label.setStyle(new TextStyle(null, 24f, true, false, TextStyle.Alignment.LEFT));

        Response resp = modify("fontSize", 14.0);

        assertTrue(resp.isOk(), resp.getMessage());
        TextStyle style = label.getStyle();
        assertEquals(14f, style.getFontSize(), 0f);
        assertTrue(style.isBold());
        assertFalse(style.isItalic());
        assertEquals(TextStyle.Alignment.LEFT, style.getAlignment());
    }

    @Test
    void testStyleOnUnstyledLabelStartsFromDefaultSize() {
        Response resp = modify("bold", true, "alignment", "right");

        assertTrue(resp.isOk(), resp.getMessage());
        TextStyle style = label.getStyle();
        assertEquals(ModifyLabelHandler.DEFAULT_FONT_SIZE, style.getFontSize(), 0f);
        assertTrue(style.isBold());
        assertEquals(TextStyle.Alignment.RIGHT, style.getAlignment());
        @SuppressWarnings("unchecked")
        Map<String, Object> styleMap = (Map<String, Object>) resp.getData().get("style");
        assertEquals("RIGHT", styleMap.get("alignment"));
    }

    @Test
    void testElevationAndPitch() {
        Response resp = modify("elevation", 150.0, "pitch", 90.0);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(150f, label.getElevation(), 0f);
        assertEquals(Math.PI / 2, label.getPitch(), 1e-5);

        Response flat = modify("pitch", null);
        assertTrue(flat.isOk(), flat.getMessage());
        assertNull(label.getPitch());
        assertNull(flat.getData().get("pitch"));
    }

    @Test
    void testOmittedPropertiesUnchanged() {
        label.setColor(0x123456);
        label.setAngle(1f);

        modify("x", 5.0);

        assertEquals(Integer.valueOf(0x123456), label.getColor());
        assertEquals(1f, label.getAngle(), 0f);
        assertEquals(200f, label.getY(), 0f);
        assertNull(label.getStyle());
    }

    @Test
    void testMissingId() {
        Response resp = handler.execute(makeRequest("modify_label", "x", 1.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testUnknownId() {
        Response resp = handler.execute(makeRequest("modify_label", "id", "label-nope", "x", 1.0), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
    }

    @Test
    void testNoModifiableProperties() {
        Response resp = modify();

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("No modifiable properties"));
    }

    @Test
    void testInvalidValuesChangeNothing() {
        assertTrue(modify("x", "far", "y", 1.0).isError());
        assertTrue(modify("x", Double.POSITIVE_INFINITY).isError());
        assertTrue(modify("text", "").isError());
        assertTrue(modify("fontSize", 0.0).isError());
        assertTrue(modify("alignment", "JUSTIFY").isError());
        assertTrue(modify("color", "blue").isError());
        assertTrue(modify("pitch", "up").isError());

        assertEquals(100f, label.getX(), 0f);
        assertEquals(200f, label.getY(), 0f);
        assertEquals("Kitchen", label.getText());
        assertNull(label.getStyle());
        assertNull(label.getColor());
    }

    @Test
    void testDescriptorAndSchema() {
        assertFalse(handler.getDescription().isEmpty());
        Map<String, Object> schema = handler.getSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("id"));
        assertTrue(props.containsKey("text"));
        assertTrue(props.containsKey("fontSize"));
        assertEquals(java.util.Collections.singletonList("id"), schema.get("required"));
    }
}
