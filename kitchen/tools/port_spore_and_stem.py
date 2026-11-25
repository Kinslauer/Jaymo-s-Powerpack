"""Ports Spore & Stem's small-mushroom art into the Kitchen (Apache-2.0, code and art; see
src/main/resources/licenses/spore-and-stem/).

    python3 -I kitchen/tools/port_spore_and_stem.py <path to a Spore & Stem checkout>

Reads its 26.3 workspace's generated assets, renames every id into jaymoskitchen and puts the files under the
`forage` folders (block/forage, item/forage) so they never meet the Kitchen's own art. What it copies: the six small
mushrooms' 3D models, blockstates and textures (and their flower pots), Truffle Soil, Mycelial Soil, Mycelial Thread,
the Truffle, the Foraging Knife, the Cultivation Log's models and sounds, and the eight dishes' icons. Loot tables,
recipes, tags and worldgen are our own (gen_forage.py).
"""
import json
import shutil
import sys
from pathlib import Path

SRC_NS = "spore_and_stem"
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/jaymoskitchen"

SMALL = ["morel", "chanterelle", "deathcap", "frostcap", "emberstool", "puffball"]
LOG_FUNGI = ["glowcap", "bouncecap", "inkcap", "morel", "chanterelle", "frostcap", "emberstool"]
ITEMS = SMALL + ["truffle", "mycelial_thread", "foraging_knife", "cultivation_log", "mycelial_soil", "truffle_soil"]
DISHES = ["roasted_morel", "chanterelle_toast", "glowcap_soup", "bouncecap_jelly", "frostcap_tea", "ember_skewer",
          "truffle_stew", "foragers_stew"]


def rename(text):
    """spore_and_stem:block/fungus/x -> jaymoskitchen:block/forage/x; block/ and item/ get the forage folder."""
    text = text.replace(SRC_NS + ":block/fungus/", "jaymoskitchen:block/forage/")
    text = text.replace(SRC_NS + ":block/", "jaymoskitchen:block/forage/")
    text = text.replace(SRC_NS + ":item/", "jaymoskitchen:item/forage/")
    assert SRC_NS not in text, text
    return text


def copy_json(src, dst, plain=False):
    text = src.read_text(encoding="utf-8")
    if not plain:
        text = rename(text)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text, encoding="utf-8")


def main():
    root = Path(sys.argv[1]) / "26.3/common/src/generated/resources/assets" / SRC_NS
    models_block = []
    for name in SMALL:
        models_block += [name, "potted_" + name]
    models_block += ["cultivation_log", "cultivation_log_colonized", "mycelial_soil", "truffle_soil"]
    for fungus in LOG_FUNGI:
        for stage in (1, 2, 3):
            models_block.append(f"cultivation_log_{fungus}_{stage}")
    for name in models_block:
        copy_json(root / "models/block" / f"{name}.json", OUT / "models/block/forage" / f"{name}.json")
    for name in ITEMS + DISHES:
        copy_json(root / "models/item" / f"{name}.json", OUT / "models/item/forage" / f"{name}.json")
        copy_json(root / "items" / f"{name}.json", OUT / "items" / f"{name}.json")
    for name in SMALL + ["cultivation_log", "mycelial_soil", "truffle_soil"]:
        copy_json(root / "blockstates" / f"{name}.json", OUT / "blockstates" / f"{name}.json")
    for name in SMALL:
        copy_json(root / "blockstates" / f"potted_{name}.json", OUT / "blockstates" / f"potted_{name}.json")
    # textures: the fungus folder is flattened into block/forage
    for png in (root / "textures/block/fungus").glob("*.png"):
        (OUT / "textures/block/forage").mkdir(parents=True, exist_ok=True)
        shutil.copy(png, OUT / "textures/block/forage" / png.name)
    for stem in ["cultivation_log", "cultivation_log_end", "cultivation_log_mycelium", "mycelial_soil", "mycelial_soil_side",
                 "mycelial_soil_top", "truffle_soil"]:
        shutil.copy(root / "textures/block" / f"{stem}.png", OUT / "textures/block/forage" / f"{stem}.png")
    (OUT / "textures/item/forage").mkdir(parents=True, exist_ok=True)
    for name in ITEMS + DISHES:
        if (root / "textures/item" / f"{name}.png").exists():
            shutil.copy(root / "textures/item" / f"{name}.png", OUT / "textures/item/forage" / f"{name}.png")
    # the log's two sounds (each with two takes)
    (OUT / "sounds/block/cultivation_log").mkdir(parents=True, exist_ok=True)
    for ogg in (root / "sounds/block/cultivation_log").glob("*.ogg"):
        shutil.copy(ogg, OUT / "sounds/block/cultivation_log" / ogg.name)
    print("ported")


if __name__ == "__main__":
    main()
