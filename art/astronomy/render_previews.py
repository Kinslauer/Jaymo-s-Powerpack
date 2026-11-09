"""Render exact front/right/back/left checks with the repository's bbrender.py."""
from __future__ import annotations

import copy
import json
from pathlib import Path
import sys

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "tools/mesh_to_cubes"))
import bbrender  # noqa: E402

VIEWS = (0, 90, 180, 270)


def main():
    with (HERE / "telescope.bbmodel").open(encoding="utf-8") as f:
        block = json.load(f)
    bbrender.render(block, str(HERE / "telescope_preview.png"), yaws=VIEWS)

    # bbrender deliberately draws both sides. Split a double-sided item sprite into
    # single-sided temporary renders so its front and back do not overprint each other.
    with (HERE / "telescope_item.bbmodel").open(encoding="utf-8") as f:
        item = json.load(f)
    front = copy.deepcopy(item)
    back = copy.deepcopy(item)
    front["elements"][0]["faces"]["south"]["texture"] = None
    back["elements"][0]["faces"]["north"]["texture"] = None
    front_path = HERE / ".telescope_item_front.png"
    back_path = HERE / ".telescope_item_back.png"
    bbrender.render(front, str(front_path), yaws=VIEWS, px=18, centre_y=8)
    bbrender.render(back, str(back_path), yaws=VIEWS, px=18, centre_y=8)
    front_sheet = Image.open(front_path)
    back_sheet = Image.open(back_path)
    sheet = Image.new("RGB", front_sheet.size)
    for i in range(4):
        source = front_sheet if i < 2 else back_sheet
        sheet.paste(source.crop((i * 360, 0, (i + 1) * 360, 360)), (i * 360, 0))
    sheet.save(HERE / "telescope_item_preview.png")
    front_sheet.close()
    back_sheet.close()
    front_path.unlink()
    back_path.unlink()
    print("Rendered front, right side, back and left side for both editable models")


if __name__ == "__main__":
    main()
