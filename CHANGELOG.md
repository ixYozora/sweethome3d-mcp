# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Fork changes - 2026-10-19

Changes made in this fork ([ixYozora/sweethome3d-mcp](https://github.com/ixYozora/sweethome3d-mcp))
on top of upstream `main` at `c76bbc6`. Everything under "Unreleased" and below is upstream's.

### Fixed
- Edits made through MCP now mark the home as modified, so closing Sweet Home 3D asks to save it. Sweet Home 3D only sets that flag for changes made through its undo support, so the plugin's direct changes left the home looking unmodified and the work could be lost. Read-only commands leave the flag alone.
- Catalog lights placed with `place_furniture` are real lights (`HomeLight`) with their light sources, so they light rendered photos; shelf units keep their shelves. (#13)
- `load_home` and `restore_checkpoint` keep every object on its level. In multi-level homes, objects used to end up on the selected level or on none. (#10)
- `restore_checkpoint` restores a copy of the checkpoint. It used to put the checkpoint's own objects into the scene, so later edits changed the checkpoint too.
- `list_levels` reports the stable level IDs that `set_selected_level` and `delete_level` accept, not list positions. (#9)
- `render_photo` truncates an existing JPEG file before writing; a smaller new image used to keep the previous image's tail. (#11)
- `render_photo` no longer runs past the client's tool-call timeout. A render that takes longer than 45 s answers with `status: "rendering"` and the `filePaths` it will write, and keeps rendering in the background; an inline render is then saved to `<tmp>/sh3d-mcp-renders/` instead. Medium-quality and 4-angle renders used to time out on the client while the plugin kept rendering, and the inline result was lost. Renders run one at a time on a background thread.
- `render_photo` and `export_plan_image` replace an image extension that does not match the format instead of appending a second one: `ov.jpg` saved as PNG is `ov.png` (as `ov_1.png` … for 4 angles), not `ov.jpg.png`; `shot.jpeg` saved as JPEG stays `shot.jpeg` instead of becoming `shot.jpeg.jpg`.
- `add_level` on a home that has content but no levels yet puts that content on a ground level, as Sweet Home 3D's own Add level does: the new level if its elevation is 0, otherwise a new "Level 0". The content used to be left without a level, drawn on every floor.

### Added
- `get_state` reports `homeFile` (null until saved) and `modified`.
- `load_home` refuses to replace a home with unsaved changes unless `discardChanges` is true.
- `modify_furniture` accepts `power` (0 to 1) for lights; furniture results report `power` for lights.
- `modify_label` — change a label's text, position, rotation, colors, font style (size, bold, italic, alignment; only what is given) or 3D elevation and pitch by ID. Invalid values are rejected before anything changes.
- `delete_label` — remove a label by ID. Labels could only be added before, so fixing one meant restoring a checkpoint.

### Build
- Maven profile `system-sh3d`, activated by the Debian/Ubuntu `sweethome3d` package: compiles against the installed Sweet Home 3D jar and the system Java3D 1.5.2 it runs with.
- JaCoCo 0.8.14, which supports JDK 25.
- `-Pdeploy-local` copies the plugin to the plugins folder of the current OS (it was the Windows folder everywhere).

### Documentation
- The Linux plugins folder is `~/.eteks/sweethome3d/plugins/`, not `~/.sweethome3d/plugins/`.

## [Unreleased]

### Added
- `sashPreset` (`single_left`, `single_right`, `double`, `none`) and `sashes` (explicit list of `{xAxis, yAxis, width, startAngle, endAngle}`, angles in degrees) parameters on `place_door_or_window` and `modify_furniture`, so a swing arc can be drawn in the 2D plan for catalog doors and windows that ship without sash data. Presets follow the convention of Sweet Home 3D's own doors: leaves open toward the piece's front and are hinged on the front face of the frame; `mirrored` swaps the hinge side. An invalid preset or sash list, or sashes on a piece that is not a door or window, is rejected before anything changes. `get_state` and furniture results include a `sashes` count for doors and windows, so pieces without a swing arc are easy to find.
- `modify_dimension_line` — change an existing dimension line's endpoints or offset by ID. Length is always recalculated from the endpoints. A value that is not a finite number, or one that makes the line's length or drawn position overflow, is rejected before anything changes.
- `delete_dimension_line` — remove a dimension line from the 2D plan by ID.

  Dimension lines could previously only be added.

### Changed
- `add_dimension_line` responses gain a `level` field, the same one `get_state` reports for the line.
- `add_label` reports `angle` rounded the same way as `get_state` (the stored angle to two decimals). The two used to disagree by 0.01° for some angles given with a third decimal of 5, e.g. `5.275` was echoed as `5.28` by `add_label` and reported as `5.27` by `get_state`; both now say `5.27`. Angles with up to two decimals are unaffected.
- Internal: the response maps for levels, labels, the 3D environment and dimension lines are built in one place (`FormatUtil`) and shared by `get_state`, `list_levels`, `add_level`, `set_selected_level`, `add_label`, `set_environment` and `add_dimension_line`. Apart from the two changes above, responses keep their exact fields, key order and values.

### Fixed
- `place_door_or_window` and `place_furniture` now create a `HomeDoorOrWindow` when the catalog item is a door or window, instead of a generic `HomePieceOfFurniture` flagged as one. The generic piece lost the catalog's sash definitions (no swing arc in the plan, hinge side not switchable with `mirrored`), the wall cut-out shape and the frame-to-wall metadata, so doors rendered as a narrow leaf floating in an oversized opening. Doors placed with `place_door_or_window` in a straight wall are also bound to it, as the app does on drop.
- `place_door_or_window` fits the door or window frame into the wall the way Sweet Home 3D does on drop: a stretchable model gets the depth at which its frame is exactly as thick as the wall, and the piece is offset so the frame sits within the wall. The reported `depth` and `x`/`y` change accordingly (e.g. 38.7 cm instead of 20 cm for the default `Door` in a 20 cm wall). The plan footprint now uses the same depth as the 3D model, and a catalog item that cannot be resized no longer fails with "Piece isn't resizable" (asking to mirror such an item now returns a clear error instead of placing nothing).
- `place_door_or_window` on a round (arc) wall places the piece on the arc itself, with `position` measured along the arc and the piece turned along the tangent; it used to sit on the chord, away from the wall.
- The `offset` parameter of `add_dimension_line` was documented as "positive = above/left". Sweet Home 3D puts a positive offset on the right-hand side of the line's direction (start → end): below a left-to-right line, left of a top-to-bottom one, since the plan's Y axis points down. The description now says so, and `modify_dimension_line` uses the same wording; behaviour is unchanged.
- `add_dimension_line` rejects coordinates and offsets that are not finite numbers (e.g. `1e40`, which overflows to infinity), or that are so large that the line's length or drawn position overflows, instead of storing them in the plan.
- Command responses report finite values beyond about 9.2e16 as they are; they used to come back as `9.223372036854776E16`.

### Documentation
- Installation: the README now describes installing by double-clicking the `.sh3p` file (Sweet Home 3D imports the plug-in itself and asks for a restart), next to copying it into the plugins folder.
- Fixed the Claude Desktop configuration in the README: Claude Desktop does not load `"type": "http"` entries, so the documented entry now uses the `mcp-remote` bridge, as the "Auto-configure Claude Desktop" button does. (#7)
- Added a Troubleshooting section documenting the macOS Mac App Store sandbox limitation: that build lacks the `com.apple.security.network.server` entitlement, so the MCP server cannot open its listening port. Use a non-sandboxed Sweet Home 3D build instead. (#2)

## [1.1.0] - 2026-03-13

### Added
- **Multilingual UI** — plugin settings dialog and menu items are now localized for 18 languages: French, German, Spanish, Italian, Russian, Simplified Chinese, Traditional Chinese, Japanese, Portuguese, Brazilian Portuguese, Dutch, Swedish, Czech, Polish, Hungarian, Greek, Bulgarian, Vietnamese
- Plugin name and description in SH3D Plugin Manager are also localized

### Changed
- All UI strings externalized to Java ResourceBundle (`McpPlugin.properties`)
- `McpSettingsDialog` accepts `ResourceBundle` via constructor (dependency injection)
- Dynamic strings use `java.text.MessageFormat` for proper placeholder handling
- Version bumped to 1.1.0

## [1.0.0] - 2026-02-27

### Initial public release

Full-featured MCP server embedded directly into Sweet Home 3D as a plugin.
Exposes 42 tools over Streamable HTTP (JSON-RPC 2.0) on `http://localhost:9877/mcp`,
compatible with the MCP protocol version `2025-03-26`.

### Added

**Scene state**
- `get_state` — full scene snapshot: walls, furniture, rooms, levels, camera
- `clear_scene` — remove all objects from the scene
- `save_home` / `load_home` — persist and restore `.sh3d` files

**Walls**
- `create_wall` — single wall by two endpoints
- `create_walls` — four-wall rectangular room in one call
- `modify_wall` — update height, thickness, color, shininess, arc
- `delete_wall` — remove wall by ID
- `connect_walls` — join two walls for correct corner rendering

**Rooms**
- `create_room_polygon` — room from an arbitrary point polygon
- `modify_room` — update name, floor/ceiling color, shininess, visibility
- `delete_room` — remove room by ID

**Furniture**
- `list_furniture_catalog` — browse the built-in catalog with filtering
- `list_categories` — catalog categories with item counts
- `place_furniture` — place a catalog item at given coordinates
- `modify_furniture` — update position, rotation, size, color, visibility
- `delete_furniture` — remove furniture by ID

**Doors and windows**
- `place_door_or_window` — insert a catalog door/window into a wall by wall ID and position

**Textures**
- `list_textures_catalog` — browse texture catalog with filtering
- `apply_texture` — apply a catalog texture to a wall side, floor, or ceiling

**Levels (floors)**
- `add_level` — create a new floor with elevation and slab thickness
- `list_levels` — list all levels with the currently selected one
- `set_selected_level` — switch the active level
- `delete_level` — remove a level and all its objects

**Cameras and rendering**
- `set_camera` — switch between top-view and observer camera; set position and angles
- `store_camera` — save a named viewpoint
- `get_cameras` — list all saved viewpoints
- `render_photo` — 3D photo-realistic render (Sunflow); optionally save to file
- `set_environment` — configure sky, ground, light intensity, wall transparency, drawing mode

**Export**
- `export_plan_image` — fast 2D floor plan export to PNG
- `export_svg` — 2D floor plan export to SVG
- `export_to_obj` — 3D scene export to Wavefront OBJ (ZIP with OBJ + MTL + textures)

**Annotations**
- `add_label` — text label on the 2D plan
- `add_dimension_line` — measurement annotation on the 2D plan

**Checkpoints (undo timeline)**
- `checkpoint` — take an in-memory snapshot of the scene
- `restore_checkpoint` — restore a snapshot (undo/redo by index or ID)
- `list_checkpoints` — list all snapshots with the current cursor position

**3D shape generation**
- `generate_shape` — create arbitrary 3D geometry: extrude (polygon + height) or mesh (vertices + triangles)

**Utility**
- `batch_commands` — execute multiple commands in a single call

[Unreleased]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/grimashevich/sweethome3d-mcp-server/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/grimashevich/sweethome3d-mcp-server/releases/tag/v1.0.0
