package com.sh3d.mcp.command.handler;
import com.sh3d.mcp.command.CommandHandler;
import com.sh3d.mcp.command.CommandDescriptor;
import com.sh3d.mcp.command.util.ColorParser;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.TextStyle;
import com.sh3d.mcp.bridge.HomeAccessor;
import com.sh3d.mcp.bridge.ObjectResolver;
import com.sh3d.mcp.protocol.Request;
import com.sh3d.mcp.protocol.Response;

import com.sh3d.mcp.command.util.SchemaBuilder;
import com.sh3d.mcp.command.util.ValidationUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Handler for "modify_label".
 * Changes an existing label's text, position, rotation, colors, style or 3D placement by stable ID.
 * Style keys (fontSize, bold, italic, alignment) change only what is given; a label without
 * a style starts from Sweet Home 3D's default label size.
 */
public class ModifyLabelHandler implements CommandHandler, CommandDescriptor {

    /** Sweet Home 3D's default label font size, used when a label has no style yet. */
    static final float DEFAULT_FONT_SIZE = 18f;

    private static final List<String> MODIFIABLE_KEYS = Arrays.asList(
            "text", "x", "y", "angle", "color", "outlineColor",
            "fontSize", "bold", "italic", "alignment", "elevation", "pitch"
    );

    private static final String[] NUMERIC_KEYS = {"x", "y", "angle", "fontSize", "elevation"};

    @Override
    public Response execute(Request request, HomeAccessor accessor) {
        String id = request.getString("id");
        if (id == null) {
            return Response.error("Missing required parameter 'id'");
        }

        Map<String, Object> params = request.getParams();
        boolean hasModifiable = MODIFIABLE_KEYS.stream().anyMatch(params::containsKey);
        if (!hasModifiable) {
            return Response.error("No modifiable properties provided. Supported: "
                    + String.join(", ", MODIFIABLE_KEYS));
        }

        // Validate everything before EDT so a bad value changes nothing
        if (params.containsKey("text")) {
            String text = request.getString("text");
            if (text == null || text.isEmpty()) {
                return Response.error("Parameter 'text' must not be empty");
            }
        }
        String numberError = ValidationUtil.validateFiniteNumbers(params, NUMERIC_KEYS);
        if (numberError != null) {
            return Response.error(numberError);
        }
        if (params.containsKey("fontSize") && request.getFloat("fontSize") <= 0) {
            return Response.error("Parameter 'fontSize' must be greater than 0");
        }
        Object pitchVal = params.get("pitch");
        if (pitchVal != null && !(pitchVal instanceof Number)) {
            return Response.error("Parameter 'pitch' must be a number or null");
        }

        ColorParser.ColorResult colorResult = ColorParser.parseNullable(params, "color");
        if (colorResult != null && colorResult.hasError()) {
            return Response.error(colorResult.error);
        }
        ColorParser.ColorResult outlineColorResult = ColorParser.parseNullable(params, "outlineColor");
        if (outlineColorResult != null && outlineColorResult.hasError()) {
            return Response.error(outlineColorResult.error);
        }

        TextStyle.Alignment alignment = null;
        String alignStr = request.getString("alignment");
        if (alignStr != null) {
            try {
                alignment = TextStyle.Alignment.valueOf(alignStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Response.error("Invalid alignment: '" + alignStr
                        + "'. Expected LEFT, CENTER, or RIGHT");
            }
        }

        final TextStyle.Alignment finalAlignment = alignment;
        Map<String, Object> data = accessor.runOnEDT(() -> {
            Home home = accessor.getHome();
            Label label = ObjectResolver.findLabel(home, id);
            if (label == null) {
                return null;
            }

            if (params.containsKey("text")) {
                label.setText(request.getString("text"));
            }
            if (params.containsKey("x")) {
                label.setX(request.getFloat("x"));
            }
            if (params.containsKey("y")) {
                label.setY(request.getFloat("y"));
            }
            if (params.containsKey("angle")) {
                label.setAngle((float) Math.toRadians(request.getFloat("angle")));
            }
            if (colorResult != null) {
                label.setColor(colorResult.clear ? null : colorResult.value);
            }
            if (outlineColorResult != null) {
                label.setOutlineColor(outlineColorResult.clear ? null : outlineColorResult.value);
            }
            if (params.containsKey("elevation")) {
                label.setElevation(request.getFloat("elevation"));
            }
            if (params.containsKey("pitch")) {
                label.setPitch(pitchVal != null
                        ? (float) Math.toRadians(((Number) pitchVal).floatValue()) : null);
            }

            boolean styleChange = params.containsKey("fontSize") || params.containsKey("bold")
                    || params.containsKey("italic") || finalAlignment != null;
            if (styleChange) {
                TextStyle style = label.getStyle() != null
                        ? label.getStyle() : new TextStyle(DEFAULT_FONT_SIZE);
                if (params.containsKey("fontSize")) {
                    style = style.deriveStyle(request.getFloat("fontSize"));
                }
                Boolean bold = request.getBoolean("bold");
                if (bold != null) {
                    style = style.deriveBoldStyle(bold);
                }
                Boolean italic = request.getBoolean("italic");
                if (italic != null) {
                    style = style.deriveItalicStyle(italic);
                }
                if (finalAlignment != null) {
                    style = style.deriveStyle(finalAlignment);
                }
                label.setStyle(style);
            }

            return AddLabelHandler.buildFullLabelInfo(label);
        });

        if (data == null) {
            return Response.error("Label not found: id '" + id + "'");
        }
        return Response.ok(data);
    }

    @Override
    public String getDescription() {
        return "Modifies an existing text label by ID: text, position, rotation, colors, "
                + "font style or 3D placement. Use get_state to find label IDs. "
                + "Only provided properties are changed; omitted ones remain unchanged. "
                + "Style properties (fontSize, bold, italic, alignment) change only what is given. "
                + "Multi-line labels grow upward from y: y is the baseline of the last line.";
    }

    @Override
    public Map<String, Object> getSchema() {
        return SchemaBuilder.create()
                .requiredString("id", "Label ID from get_state")
                .string("text", "New label text; '\\n' starts a new line")
                .number("x", "New X position in centimeters")
                .number("y", "New Y position in centimeters")
                .number("angle", "Rotation angle in degrees (clockwise)")
                .nullableString("color", "Text color as hex '#RRGGBB', or null for default")
                .nullableString("outlineColor", "Outline color as hex '#RRGGBB', or null for no outline")
                .number("fontSize", "Font size in points, greater than 0")
                .bool("bold", "Bold text")
                .bool("italic", "Italic text")
                .enumProp("alignment", "Text alignment", "LEFT", "CENTER", "RIGHT")
                .number("elevation", "Elevation in 3D view (cm). 0 = on the floor")
                .nullableNumber("pitch",
                        "Pitch angle in degrees for 3D view. null = flat on plan. "
                                + "90 = vertical (like a wall sign)")
                .build();
    }

}
