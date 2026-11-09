"""Generate the journal's original tab icons and their editable Blockbench inspection model."""
from __future__ import annotations

import base64
import io
import json
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TEXTURE = ROOT / "src/main/resources/assets/jaymoskingdoms/textures/gui/journal/tabs.png"

GOLD = "#ffc21a"
GOLD_DARK = "#8f5d20"
PAPER = "#ead9ad"
PAPER_SHADE = "#b89b6a"
INK = "#44352d"
RED = "#b84a3c"
TEAL = "#62a8a0"
GREEN = "#78a85c"
WHITE = "#fff4d6"


def quests(draw: ImageDraw.ImageDraw, ox: int) -> None:
    # A tied travel scroll: dark leather silhouette, parchment, writing and a wax seal.
    draw.rectangle((ox + 3, 2, ox + 12, 13), fill=INK)
    draw.rectangle((ox + 4, 2, ox + 11, 12), fill=PAPER_SHADE)
    draw.rectangle((ox + 5, 3, ox + 11, 11), fill=PAPER)
    draw.rectangle((ox + 3, 3, ox + 5, 5), fill=PAPER)
    draw.point((ox + 3, 3), fill=WHITE)
    draw.rectangle((ox + 10, 1, ox + 12, 4), fill=PAPER)
    draw.point((ox + 12, 1), fill=WHITE)
    for y, end in ((6, 10), (8, 9)):
        draw.line((ox + 6, y, ox + end, y), fill=INK)
    draw.rectangle((ox + 8, 10, ox + 10, 12), fill=RED)
    draw.point((ox + 9, 10), fill="#e58a65")
    draw.line((ox + 9, 12, ox + 8, 14), fill=GOLD_DARK)
    draw.line((ox + 10, 12, ox + 11, 14), fill=GOLD_DARK)


def map_icon(draw: ImageDraw.ImageDraw, ox: int) -> None:
    # Three folded parchment leaves, with a river and a dotted route.
    draw.polygon([(ox + 1, 4), (ox + 5, 2), (ox + 10, 4), (ox + 14, 2),
                  (ox + 14, 12), (ox + 10, 14), (ox + 5, 12), (ox + 1, 14)], fill=INK)
    draw.polygon([(ox + 2, 4), (ox + 5, 3), (ox + 5, 11), (ox + 2, 13)], fill=PAPER_SHADE)
    draw.polygon([(ox + 6, 4), (ox + 9, 5), (ox + 9, 12), (ox + 6, 11)], fill=PAPER)
    draw.polygon([(ox + 10, 4), (ox + 13, 3), (ox + 13, 11), (ox + 10, 13)], fill=PAPER_SHADE)
    draw.line((ox + 11, 4, ox + 10, 6, ox + 12, 8, ox + 11, 11), fill=TEAL)
    draw.line((ox + 3, 10, ox + 5, 8, ox + 7, 9), fill=GREEN)
    for x, y in ((5, 5), (7, 6), (8, 8)):
        draw.point((ox + x, y), fill=GOLD_DARK)
    draw.rectangle((ox + 7, 7, ox + 8, 8), fill=RED)
    draw.point((ox + 7, 7), fill=WHITE)


def reputation(draw: ImageDraw.ImageDraw, ox: int) -> None:
    # A settlement's banner: gilded pole and a green shield with a pale tower.
    draw.rectangle((ox + 2, 1, ox + 3, 14), fill=INK)
    draw.point((ox + 2, 1), fill=GOLD)
    draw.rectangle((ox + 1, 14, ox + 5, 14), fill=GOLD_DARK)
    draw.polygon([(ox + 4, 3), (ox + 13, 3), (ox + 13, 10), (ox + 9, 14), (ox + 4, 10)], fill=GOLD_DARK)
    draw.polygon([(ox + 5, 4), (ox + 12, 4), (ox + 12, 9), (ox + 9, 12), (ox + 5, 9)], fill=GREEN)
    draw.rectangle((ox + 7, 6, ox + 10, 9), fill=PAPER)
    draw.point((ox + 7, 5), fill=PAPER)
    draw.point((ox + 10, 5), fill=PAPER)
    draw.point((ox + 8, 9), fill=INK)
    draw.line((ox + 5, 4, ox + 12, 4), fill=GOLD)


def faces(u: int) -> dict:
    # The broad faces carry the icon; the thin edges sample its border pixels.
    return {
        "north": {"uv": [u, 0, u + 16, 16], "texture": 0},
        "south": {"uv": [u + 16, 0, u, 16], "texture": 0},
        "east": {"uv": [u + 15, 0, u + 16, 16], "texture": 0},
        "west": {"uv": [u, 0, u + 1, 16], "texture": 0},
        "up": {"uv": [u, 0, u + 16, 1], "texture": 0},
        "down": {"uv": [u, 15, u + 16, 16], "texture": 0},
    }


def main() -> None:
    atlas = Image.new("RGBA", (48, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)
    quests(draw, 0)
    map_icon(draw, 16)
    reputation(draw, 32)
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(TEXTURE)

    raw = io.BytesIO()
    atlas.save(raw, "PNG")
    texture_id = "6b506d2d-6f79-4726-956c-6c6f6e616c00"
    model = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": "journal_tabs",
        "model_identifier": "journal_tabs",
        "visible_box": [3, 1, 1],
        "resolution": {"width": 48, "height": 16},
        "elements": [],
        "outliner": [],
        "textures": [{
            "name": "tabs.png",
            "folder": "journal",
            "namespace": "jaymoskingdoms",
            "id": "0",
            "uuid": texture_id,
            "relative_path": "../../src/main/resources/assets/jaymoskingdoms/textures/gui/journal/tabs.png",
            "source": "data:image/png;base64," + base64.b64encode(raw.getvalue()).decode("ascii"),
            "width": 48,
            "height": 16,
        }],
    }
    for i, name in enumerate(("quests", "map", "reputation")):
        uuid = f"journal-tab-{i}"
        model["elements"].append({
            "name": name,
            "box_uv": False,
            "from": [i * 18, 0, -0.5],
            "to": [i * 18 + 16, 16, 0.5],
            "origin": [i * 18 + 8, 8, 0],
            "rotation": [0, 0, 0],
            "inflate": 0,
            "faces": faces(i * 16),
            "type": "cube",
            "uuid": uuid,
        })
        model["outliner"].append({"name": name, "origin": [i * 18 + 8, 8, 0], "uuid": f"journal-group-{i}", "children": [uuid]})
    (HERE / "journal-tabs.bbmodel").write_text(json.dumps(model, indent="\t") + "\n", encoding="utf-8")
    print(TEXTURE)
    print(HERE / "journal-tabs.bbmodel")


if __name__ == "__main__":
    main()
