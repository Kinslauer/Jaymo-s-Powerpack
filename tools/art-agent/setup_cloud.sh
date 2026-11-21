#!/bin/sh
# Gets a fresh cloud container ready to run the art agent: the agents CLI, the agent's Python packages,
# headless Blender (with Mesa's software OpenGL, since there is no GPU) and the .env with the Gemini key.
# Run from tools/art-agent. Needs GEMINI_API_KEY as an environment variable (never a network secret).
set -e
export PATH="$HOME/.local/bin:$PATH"
uv tool install "google-agents-cli~=1.15.0"
agents-cli install
if [ ! -x "$HOME/blender/blender-5.3.0-linux-x64/blender" ]; then
  mkdir -p "$HOME/blender"
  curl -sSL https://download.blender.org/release/Blender5.3/blender-5.3.0-linux-x64.tar.xz | tar -xJ -C "$HOME/blender"
fi
apt-get install -y -q libegl1 libgl1-mesa-dri libegl-mesa0 libxkbcommon0 libsm6 libxi6 libxrender1 libxxf86vm1 libxfixes3 >/dev/null 2>&1 \
  || (apt-get update -q >/dev/null && apt-get install -y -q libegl1 libgl1-mesa-dri libegl-mesa0 libxkbcommon0 libsm6 libxi6 libxrender1 libxxf86vm1 libxfixes3 >/dev/null)
if [ -n "$GEMINI_API_KEY" ]; then
  printf 'GEMINI_API_KEY=%s\nBLENDER_BIN=%s\n' "$GEMINI_API_KEY" "$HOME/blender/blender-5.3.0-linux-x64/blender" > .env
else
  echo "GEMINI_API_KEY is not set: the tools work, but the agent can't think until it is."
fi
echo 'Ready. Try: uv run python -m app.ask "Paint a 16x16 texture of a copper key"'
