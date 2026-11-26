#!/usr/bin/env bash
# Headless Blockbench + the Blockbench MCP plugin (jasonjgardner/blockbench-mcp-plugin) on Linux,
# for cloud sessions and the Steam Deck. Blockbench desktop runs under Xvfb, the plugin's MCP server
# listens on http://127.0.0.1:3000/bb-mcp, and Claude Code talks to it like any HTTP MCP server.
#
#   tools/blockbench-mcp/setup.sh          install (first time) and start
#   tools/blockbench-mcp/setup.sh stop     stop Blockbench and Xvfb
#   tools/blockbench-mcp/setup.sh status   is the MCP server answering?
#
# Then:  claude mcp add --transport http blockbench http://127.0.0.1:3000/bb-mcp
# Or test without Claude:  node tools/blockbench-mcp/bbmcp_call.mjs list
#
# Why Flathub: cloud sessions can't download GitHub release files, so Blockbench comes from the
# Flathub OSTree repo (static files over plain HTTPS, fetched by flathub_fetch.py; libostree's own
# pull is refused by the cloud proxy). The Flatpak itself isn't used, only the Electron app inside it.
# On the Steam Deck you can instead `flatpak install flathub net.blockbench.Blockbench` and point
# BB_BIN at it; the rest is the same.
#
# The plugin is given its three permissions (process, net, fs) up front in plugin_permissions.json,
# which is what clicking "Always allow" in Blockbench's prompts writes. fs lets export_model write files.
set -euo pipefail

BB_HOME="${BB_HOME:-$HOME/.blockbench-mcp}"
BB_DISPLAY="${BB_DISPLAY:-:99}"
BB_CDP_PORT="${BB_CDP_PORT:-9222}"
BB_MCP_URL="${BB_MCP_URL:-http://127.0.0.1:3000/bb-mcp}"
PLUGIN_URL="${PLUGIN_URL:-https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js}"
# GeckoLib's own Blockbench plugin (GeckoLib model format + .geo.json/.animation.json export; MIT), from the
# official plugin list (jsDelivr serves the GitHub repo; github.com itself is blocked in cloud sessions).
GECKOLIB_URL="${GECKOLIB_URL:-https://cdn.jsdelivr.net/gh/JannisX11/blockbench-plugins@master/plugins/geckolib/geckolib.js}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$BB_HOME/app"
BB_BIN="${BB_BIN:-$APP/files/bin/Blockbench/blockbench}"
USERDATA="$BB_HOME/userdata"
LOG="$BB_HOME/blockbench.log"

mcp_up() { # the server answers 400 to a bare GET (no MCP session), which means it is listening
  local code; code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "$BB_MCP_URL" || true)
  [[ "$code" != "000" ]]
}

case "${1:-start}" in
  stop)
    pkill -f "$BB_BIN" || true
    pkill -f "Xvfb $BB_DISPLAY" || true
    echo "stopped"; exit 0 ;;
  status)
    if mcp_up; then echo "MCP server up at $BB_MCP_URL"; else echo "MCP server not answering"; exit 1; fi
    exit 0 ;;
  start) ;;
  *) echo "usage: $0 [start|stop|status]"; exit 2 ;;
esac

if mcp_up; then echo "already running: $BB_MCP_URL"; exit 0; fi
mkdir -p "$BB_HOME"

# 1. System packages (Ubuntu/Debian cloud image). Skipped when everything is already there.
if ! command -v Xvfb >/dev/null || { [[ ! -x "$BB_BIN" ]] && ! command -v ostree >/dev/null; }; then
  sudo_=""; [[ $(id -u) -ne 0 ]] && sudo_="sudo"
  $sudo_ apt-get update -qq
  DEBIAN_FRONTEND=noninteractive $sudo_ apt-get install -y -qq xvfb ostree gir1.2-ostree-1.0 python3-gi >/dev/null
fi

# 2. Blockbench desktop (Electron) from Flathub.
if [[ ! -x "$BB_BIN" ]]; then
  PY=""
  for p in /usr/bin/python3 /usr/bin/python3.12 /usr/bin/python3.13 /usr/bin/python3.11; do
    if [[ -x $p ]] && $p -c "import gi; gi.require_version('OSTree','1.0')" 2>/dev/null; then PY=$p; break; fi
  done
  [[ -n "$PY" ]] || { echo "no python with gi/OSTree bindings found"; exit 1; }
  rm -rf "$APP"
  "$PY" -I "$HERE/flathub_fetch.py" app/net.blockbench.Blockbench/x86_64/stable "$BB_HOME/repo" "$APP"
  chmod +x "$BB_BIN"
  rm -rf "$BB_HOME/repo"   # 300+ MB of compressed objects, not needed after checkout
fi

# 3. The MCP plugin (stable build), the GeckoLib plugin, and the MCP plugin's permissions.
[[ -f "$BB_HOME/mcp.js" ]] || curl -fsSL -o "$BB_HOME/mcp.js" "$PLUGIN_URL"
[[ -f "$BB_HOME/geckolib.js" ]] || curl -fsSL -o "$BB_HOME/geckolib.js" "$GECKOLIB_URL"
mkdir -p "$USERDATA"
[[ -f "$USERDATA/plugin_permissions.json" ]] || \
  echo '{"mcp":{"allowed":{"process":true,"net":true,"fs":true}}}' > "$USERDATA/plugin_permissions.json"

# 4. Virtual display + Blockbench. The DevTools port (localhost only) is how step 5 installs the plugin.
pgrep -f "Xvfb $BB_DISPLAY" >/dev/null || { Xvfb "$BB_DISPLAY" -screen 0 1600x1000x24 >"$BB_HOME/xvfb.log" 2>&1 & sleep 1; }
DISPLAY="$BB_DISPLAY" nohup "$BB_BIN" --no-sandbox --user-data-dir="$USERDATA" \
  --remote-debugging-address=127.0.0.1 --remote-debugging-port="$BB_CDP_PORT" >"$LOG" 2>&1 &
for _ in $(seq 60); do curl -s -m 2 "http://127.0.0.1:$BB_CDP_PORT/json" | grep -q Blockbench && break; sleep 1; done
sleep 3   # let Blockbench finish loading installed plugins

# 5. Install the plugins from the local files (the "false" skips the confirm dialog); ones already
#    installed are skipped. Blockbench remembers them, so later starts load them by themselves.
BB_PLUGINS="geckolib=$BB_HOME/geckolib.js,mcp=$BB_HOME/mcp.js" BB_CDP="http://127.0.0.1:$BB_CDP_PORT" node "$HERE/install_plugin.cjs"
for _ in $(seq 30); do mcp_up && break; sleep 1; done
if mcp_up; then
  echo "Blockbench MCP server up at $BB_MCP_URL"
  echo "Add it to Claude Code:  claude mcp add --transport http blockbench $BB_MCP_URL"
else
  echo "MCP server did not come up; see $LOG"; exit 1
fi
