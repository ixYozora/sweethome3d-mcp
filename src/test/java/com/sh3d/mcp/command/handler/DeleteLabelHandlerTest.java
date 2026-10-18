package com.sh3d.mcp.command.handler;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.protocol.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.sh3d.mcp.command.handler.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DeleteLabelHandlerTest {

    private DeleteLabelHandler handler;
    private HomeAccessor accessor;
    private Home home;

    @BeforeEach
    void setUp() {
        handler = new DeleteLabelHandler();
        home = new Home();
        accessor = createAccessor(home);
    }

    @Test
    void testDeletesOnlyTarget() {
        Label keep = new Label("keep", 0, 0);
        Label target = new Label("target", 50, 60);
        home.addLabel(keep);
        home.addLabel(target);

        Response resp = handler.execute(makeIdRequest("delete_label", target.getId()), accessor);

        assertTrue(resp.isOk(), resp.getMessage());
        assertEquals(1, home.getLabels().size());
        assertSame(keep, home.getLabels().iterator().next());
        Map<String, Object> data = resp.getData();
        assertEquals(target.getId(), data.get("id"));
        assertEquals("target", data.get("text"));
        assertTrue(((String) data.get("message")).contains("deleted"));
    }

    @Test
    void testUnknownId() {
        home.addLabel(new Label("keep", 0, 0));

        Response resp = handler.execute(makeIdRequest("delete_label", "label-nope"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("not found"));
        assertEquals(1, home.getLabels().size());
    }

    @Test
    void testMissingId() {
        Response resp = handler.execute(makeRequest("delete_label"), accessor);

        assertTrue(resp.isError());
        assertTrue(resp.getMessage().contains("id"));
    }

    @Test
    void testDescriptorAndSchema() {
        assertFalse(handler.getDescription().isEmpty());
        assertEquals(java.util.Collections.singletonList("id"), handler.getSchema().get("required"));
    }
}
