# Sweet Home 3D MCP (local fork)

Fork of [grimashevich/sweethome3d-mcp-server](https://github.com/grimashevich/sweethome3d-mcp-server)
(remote `upstream`), published as [ixYozora/sweethome3d-mcp](https://github.com/ixYozora/sweethome3d-mcp)
(remote `origin`, public). Our changes: `git log upstream/main..main`; list them in CHANGELOG.md
under "Fork changes".
It is a Sweet Home 3D plugin that runs an MCP server inside the app. Installed for the
Debian/Ubuntu `sweethome3d` 7.5 package on this laptop, which runs it on Java 25.

## Using it

- Sweet Home 3D must be running: app menu, or `setsid -f sweethome3d >/dev/null 2>&1`.
  The server is `http://127.0.0.1:9877/mcp` (`/health` for a quick check), registered in
  `.mcp.json`, so start Claude Code in this directory. Started SH3D after Claude? Run `/mcp`.
- The tools act on the home in the most recently opened Sweet Home 3D window.

## Conventions when designing

- Units are cm. Plan coordinates: x to the right, **y downwards**. Angles in degrees.
  At angle 0 a piece's front faces +y (south); use 180 to back it onto the bottom wall.
- `create_walls` builds a clockwise rectangle, so each wall's **right** side faces inside.
- Call `get_state` first; object IDs are stable strings. A room's `area` is in cm²
  (45600 = 4.56 m²). Wall surface for tiles/paint = length x height minus doors/windows,
  computed from `get_state`.
- Ctrl+Z in Sweet Home 3D does **not** undo MCP edits. Use `checkpoint` before risky
  changes and `restore_checkpoint` to undo.
- `get_state` reports `modified` and `homeFile`. Save designs into `designs/`.
  `load_home` refuses while there are unsaved changes; ask the user before passing
  `discardChanges=true`.
- Ambiguous catalog names ("door" matches 3 items) need a `catalogId` from `list_furniture_catalog`.
- Lights come from the Lights category; brightness is `modify_furniture` `power` (0-1, default 0.5).
- `render_photo` and `export_plan_image` return images inline. Low quality, 800x600: ~5 s.
  A render over 45 s answers `status: "rendering"` with `filePaths` and finishes in the background;
  read the files once they exist. Medium quality or 4 angles of a full plan typically take that long.
- Labels: `modify_label` / `delete_label` by ID. A multi-line label grows upward from its `y`.
- Catalog: 920 pieces (100 built-in + 820 from `sweethome3d-furniture`), only 26 textures.
- The GUI shows imperial units (en_US locale) unless changed in Preferences; model data is always cm.
- `photos/` holds the user's reference photos, `designs/` their `.sh3d` files (both gitignored).

## Building and deploying

- `mvn -B -ntp verify`: full test suite plus the JaCoCo coverage gate. The `system-sh3d`
  profile activates by itself and compiles against `/usr/share/sweethome3d/sweethome3d.jar`
  and the system Java3D **1.5.2** (JNI), not the Java3D 1.6 jars in `lib/`.
- Run the **whole** suite before committing: `ResponseShapeTest` pins exact response keys.
- Deploy: close Sweet Home 3D first (it holds the plugin jar open), then
  `mvn -B -ntp package -Pdeploy-local -DskipTests` copies the `.sh3p` to
  `~/.eteks/sweethome3d/plugins/`, and restart Sweet Home 3D. Plugin log: `sh3d-mcp.log.0` there.
- A handler that does not change the home must override `modifiesHome()` to return false;
  otherwise every call marks the home modified.
