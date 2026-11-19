"""The art kit's tools from the command line, for Prometheus or anyone else, without the Gemini agent.

Run from tools/art-agent:
  python -m app.cli <tool> <args.json>      call a tool with its arguments from a JSON file (or a JSON string)
  python -m app.cli install <name> <mod>    copy a model's exported GeckoLib files into that mod's resources
  python -m app.cli tools                   list the tools and their arguments

Tools: paint_texture, look_at, build_model, add_animation, preview_model, export_geckolib, run_blender.
Images the tools would show the agent are left out; their saved paths are printed instead.
"""

import inspect
import json
import os
import shutil
import sys

from app import artkit

TOOLS = {
    f.__name__: f
    for f in (
        artkit.paint_texture,
        artkit.look_at,
        artkit.build_model,
        artkit.add_animation,
        artkit.preview_model,
        artkit.export_geckolib,
        artkit.run_blender,
    )
}
REPO = os.path.dirname(artkit.TOOLS)
MOD_ASSETS = {
    "jaymospowers": "src/main/resources/assets/jaymospowers",
    "jaymoskingdoms": "kingdoms/src/main/resources/assets/jaymoskingdoms",
    "jaymoskitchen": "kitchen/src/main/resources/assets/jaymoskitchen",
}


def install(name, mod):
    """Copies out/assets/<mod>/... for this model into the mod's resources, refusing to overwrite."""
    if mod not in MOD_ASSETS:
        raise SystemExit(f"mod must be one of {sorted(MOD_ASSETS)}")
    rel = [
        f"geckolib/models/entity/{name}.geo.json",
        f"geckolib/animations/entity/{name}.animation.json",
        f"textures/entity/{name}.png",
    ]
    src_root = os.path.join(artkit.OUT, "assets", mod)
    dst_root = os.path.join(REPO, MOD_ASSETS[mod])
    missing = [r for r in rel if not os.path.isfile(os.path.join(src_root, r))]
    if missing:
        raise SystemExit(f"not exported yet (run export_geckolib): {missing}")
    taken = [r for r in rel if os.path.exists(os.path.join(dst_root, r))]
    if taken and "--overwrite" not in sys.argv:
        raise SystemExit(f"already in the mod (add --overwrite to replace): {taken}")
    for r in rel:
        dst = os.path.join(dst_root, r)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(src_root, r), dst)
        print("copied", os.path.relpath(dst, REPO))


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print(__doc__)
        return
    if args[0] == "tools":
        for n, f in TOOLS.items():
            print(f"{n}{inspect.signature(f)}")
        return
    if args[0] == "install" and len(args) >= 3:
        install(args[1], args[2])
        return
    if args[0] not in TOOLS or len(args) < 2:
        raise SystemExit(__doc__)
    raw = args[1]
    if os.path.isfile(raw):
        with open(raw, encoding="utf-8") as f:
            raw = f.read()
    res = TOOLS[args[0]](**json.loads(raw))
    res.pop("image", None)
    print(json.dumps(res, indent=2))
    if "error" in res:
        sys.exit(1)


if __name__ == "__main__":
    main()
