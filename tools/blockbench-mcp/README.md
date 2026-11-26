# Headless Blockbench MCP

Runs Blockbench desktop (5.2.x, from Flathub) under a virtual display with the
[Blockbench MCP plugin](https://jasonjgardner.github.io/blockbench-mcp-plugin/) and GeckoLib's own Blockbench plugin
(4.2.x) loaded, so Claude can build, animate and export models (Java block/item JSON, Java entity, `.bbmodel`, glTF,
GeckoLib `.geo.json` + `.animation.json`) with no screen.
Tested in a Claude cloud session (Ubuntu 24.04): first run ~40 s, later starts load the plugin by themselves.

```bash
tools/blockbench-mcp/setup.sh            # install on first run, then start; prints the MCP URL
claude mcp add --transport http blockbench http://127.0.0.1:3000/bb-mcp
tools/blockbench-mcp/setup.sh status     # or: stop
```

Check it without Claude (`npm i` in this folder first):

```bash
node bbmcp_call.mjs list
node bbmcp_call.mjs create_project '{"name":"stool","format":"java_block"}' \
  place_cube '{"elements":[{"name":"seat","from":[2,8,2],"to":[14,10,14]}]}' \
  export_model '{"path":"/tmp/stool.json"}'
BB_IMG=/tmp/stool.png node bbmcp_call.mjs capture_screenshot '{"max_size":600}'
```

- GeckoLib: make the project with format `geckolib_model` (or convert one). The MCP plugin's 10 `geckolib_*` tools
  (settings, validate, easings, `geckolib_export_model`, `geckolib_export_animations`) appear only in such a project.
- Most tools appear only once a project is open (`create_project` first); 17 are there before that.
- The plugin gets its process/net/fs permissions up front (`plugin_permissions.json`), the same as clicking
  "Always allow" in Blockbench, so exports to a path don't stop on a dialog.
- Files: `setup.sh` (install/start/stop), `flathub_fetch.py` (pulls the Flathub app over plain HTTPS, because
  cloud sessions can't download GitHub releases and the proxy refuses libostree's own pull),
  `install_plugin.cjs` (first-run plugin install over the DevTools port, localhost only), `bbmcp_call.mjs`
  (tiny MCP client for tests).
- Settings by env var: `BB_HOME` (default `~/.blockbench-mcp`), `BB_DISPLAY` (`:99`), `BB_BIN` (use an existing
  Blockbench, e.g. a Flatpak install on the Steam Deck), `BB_MCP_URL`.
- Models only: per the project's art rule, Claude doesn't paint textures with this.
