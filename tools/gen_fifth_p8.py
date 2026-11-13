"""Draws the textures, models and item definitions of the magic items (patch 8).

Run from the repository root: python3 tools/gen_fifth_p8.py
Kingdoms: the gadgets, the six wands and staffs, the Deck of Fates and the keep deed, and the blocks the rod, the rope's knot
and the floating light put in the world. Powers: the boots of long strides and the cloak of the depths.
"""
import json
import os

from PIL import Image, ImageDraw

K = "kingdoms/src/main/resources/assets/jaymoskingdoms"
P = "src/main/resources/assets/jaymospowers"


def canvas():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def rgba(c):
    return tuple(c) + (255,)


def shade(c, d):
    return tuple(max(0, min(255, v + d)) for v in c)


def wand(colour, staff):
    im, d = canvas()
    wood = (112, 78, 48)
    if staff:
        d.line([(3, 14), (11, 6)], fill=rgba(wood))
        d.line([(4, 14), (12, 6)], fill=rgba(shade(wood, -30)))
        d.ellipse([9, 1, 14, 6], outline=rgba((200, 170, 90)))
        d.rectangle([11, 3, 12, 4], fill=rgba(colour))
        im.putpixel((11, 3), rgba(shade(colour, 60)))
    else:
        d.line([(3, 13), (11, 5)], fill=rgba(wood))
        d.line([(4, 13), (12, 5)], fill=rgba(shade(wood, -30)))
        d.rectangle([12, 2, 13, 3], fill=rgba(colour))
        im.putpixel((12, 2), rgba(shade(colour, 70)))
        for p in [(14, 1), (11, 1), (14, 4)]:
            im.putpixel(p, rgba(shade(colour, 30)))
    return im


def rod():
    im, d = canvas()
    d.rectangle([7, 1, 8, 14], fill=rgba((150, 156, 164)))
    d.line([(7, 1), (7, 14)], fill=rgba((196, 202, 210)))
    d.line([(8, 1), (8, 14)], fill=rgba((104, 110, 120)))
    for y in (3, 7, 11):
        d.rectangle([6, y, 9, y + 1], fill=rgba((84, 88, 98)))
    im.putpixel((7, 5), rgba((220, 60, 50)))
    im.putpixel((8, 5), rgba((220, 60, 50)))
    return im


def rope_coil():
    im, d = canvas()
    for r, c in ((6, (196, 160, 100)), (4, (176, 140, 84)), (2, (150, 116, 68))):
        d.ellipse([8 - r, 8 - r, 7 + r, 7 + r], outline=rgba(c))
    d.line([(10, 10), (14, 14)], fill=rgba((196, 160, 100)))
    d.line([(11, 10), (14, 13)], fill=rgba((150, 116, 68)))
    return im


def jug():
    im, d = canvas()
    d.ellipse([3, 4, 12, 14], fill=rgba((92, 150, 210)))
    d.rectangle([6, 1, 9, 5], fill=rgba((92, 150, 210)))
    d.rectangle([5, 1, 10, 2], fill=rgba((120, 84, 52)))
    d.ellipse([4, 6, 7, 10], fill=rgba((140, 190, 235)))
    d.arc([10, 5, 15, 11], 270, 90, fill=rgba((70, 120, 170)))
    for p in [(8, 9), (9, 11)]:
        im.putpixel(p, rgba((200, 230, 250)))
    return im


def lantern():
    im, d = canvas()
    d.rectangle([5, 3, 10, 13], outline=rgba((120, 120, 130)))
    d.rectangle([6, 5, 9, 11], fill=rgba((240, 230, 150)))
    d.rectangle([7, 7, 8, 9], fill=rgba((255, 255, 230)))
    d.rectangle([6, 1, 9, 2], fill=rgba((90, 90, 100)))
    d.rectangle([5, 13, 10, 14], fill=rgba((90, 90, 100)))
    for p in [(3, 6), (12, 8), (4, 11), (11, 4)]:
        im.putpixel(p, rgba((255, 245, 170)))
    return im


def hound():
    im, d = canvas()
    g = (150, 150, 158)
    d.rectangle([4, 7, 11, 11], fill=rgba(g))
    d.rectangle([10, 4, 14, 8], fill=rgba(shade(g, 14)))
    d.polygon([(10, 4), (11, 1), (12, 4)], fill=rgba(shade(g, -20)))
    d.polygon([(13, 4), (14, 1), (14, 4)], fill=rgba(shade(g, -20)))
    d.rectangle([4, 11, 5, 14], fill=rgba(shade(g, -30)))
    d.rectangle([10, 11, 11, 14], fill=rgba(shade(g, -30)))
    d.line([(4, 7), (2, 4)], fill=rgba(shade(g, -20)))
    im.putpixel((13, 5), rgba((40, 40, 50)))
    im.putpixel((14, 7), rgba((50, 50, 60)))
    for p in [(6, 8), (8, 9), (9, 8)]:
        im.putpixel(p, rgba(shade(g, -22)))
    return im


def horn():
    im, d = canvas()
    d.arc([2, 2, 14, 14], 20, 200, fill=rgba((218, 200, 160)), width=3)
    d.rectangle([2, 7, 4, 10], fill=rgba((170, 120, 60)))
    d.ellipse([11, 2, 15, 6], fill=rgba((230, 214, 178)))
    d.line([(4, 10), (11, 13)], fill=rgba((170, 120, 60)))
    im.putpixel((13, 3), rgba((90, 70, 40)))
    return im


def smoke():
    im, d = canvas()
    d.rectangle([6, 7, 10, 14], fill=rgba((90, 100, 110)))
    d.rectangle([7, 4, 9, 7], fill=rgba((90, 100, 110)))
    d.rectangle([7, 3, 9, 4], fill=rgba((130, 90, 60)))
    d.rectangle([7, 9, 9, 13], fill=rgba((196, 204, 212)))
    for p, c in [((8, 1), (210, 214, 220)), ((9, 0), (190, 196, 204)), ((7, 2), (170, 176, 184)), ((10, 1), (210, 214, 220)), ((6, 0), (180, 186, 194))]:
        im.putpixel(p, rgba(c))
    return im


def deck():
    im, d = canvas()
    d.rectangle([2, 3, 13, 13], fill=rgba((38, 30, 36)), outline=rgba((90, 70, 40)))
    d.rectangle([4, 5, 11, 11], fill=rgba((226, 214, 188)))
    d.rectangle([5, 6, 10, 10], outline=rgba((140, 70, 170)))
    d.polygon([(7, 7), (8, 6), (9, 7), (8, 9)], fill=rgba((200, 60, 70)))
    im.putpixel((7, 4), rgba((176, 150, 80)))
    im.putpixel((8, 4), rgba((176, 150, 80)))
    return im


def deed():
    im, d = canvas()
    d.rectangle([3, 2, 12, 13], fill=rgba((228, 212, 176)), outline=rgba((160, 132, 90)))
    for y in (4, 6, 8):
        d.line([(5, y), (10, y)], fill=rgba((120, 96, 64)))
    d.ellipse([8, 9, 12, 13], fill=rgba((170, 40, 40)))
    im.putpixel((9, 10), rgba((220, 90, 80)))
    return im


def boots():
    im, d = canvas()
    leather = (126, 84, 52)
    d.rectangle([4, 3, 8, 11], fill=rgba(leather))
    d.rectangle([4, 10, 13, 13], fill=rgba(shade(leather, -10)))
    d.rectangle([4, 3, 8, 4], fill=rgba((214, 190, 130)))
    d.rectangle([3, 13, 13, 14], fill=rgba((60, 40, 28)))
    for p in [(5, 6), (6, 8), (11, 11)]:
        im.putpixel(p, rgba(shade(leather, 30)))
    d.line([(1, 7), (3, 7)], fill=rgba((232, 232, 240)))
    d.line([(0, 9), (3, 9)], fill=rgba((200, 200, 215)))
    return im


def cloak():
    im, d = canvas()
    blue = (36, 98, 130)
    d.polygon([(5, 2), (10, 2), (14, 14), (1, 14)], fill=rgba(blue))
    d.polygon([(5, 2), (10, 2), (9, 5), (6, 5)], fill=rgba(shade(blue, 26)))
    d.line([(5, 14), (6, 7)], fill=rgba(shade(blue, -24)))
    d.line([(10, 14), (9, 7)], fill=rgba(shade(blue, -24)))
    for p in [(3, 12), (12, 11), (7, 10)]:
        im.putpixel(p, rgba((150, 220, 235)))
    im.putpixel((7, 2), rgba((214, 190, 90)))
    im.putpixel((8, 2), rgba((214, 190, 90)))
    return im


def rod_block():
    im, d = canvas()
    d.rectangle([0, 0, 15, 15], fill=rgba((110, 116, 126)))
    for y in (0, 8):
        d.rectangle([0, y, 15, y + 1], fill=rgba((74, 78, 88)))
    for x in range(0, 16, 4):
        d.line([(x, 0), (x, 15)], fill=rgba((140, 146, 156)))
    for p in [(2, 4), (10, 12), (6, 5), (13, 3)]:
        im.putpixel(p, rgba((60, 62, 70)))
    return im


def knot_block():
    im, d = canvas()
    rope = (178, 140, 84)
    d.line([(3, 0), (3, 15)], fill=rgba(rope))
    d.line([(12, 0), (12, 15)], fill=rgba(rope))
    d.line([(4, 0), (4, 15)], fill=rgba(shade(rope, -34)))
    d.line([(13, 0), (13, 15)], fill=rgba(shade(rope, -34)))
    for y in (2, 7, 12):
        d.rectangle([3, y, 12, y + 1], fill=rgba(shade(rope, 10)))
        d.line([(3, y + 1), (12, y + 1)], fill=rgba(shade(rope, -34)))
    return im


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item(root, mod, name, im, texture):
    os.makedirs(f"{root}/textures/{os.path.dirname(texture)}", exist_ok=True)
    im.save(f"{root}/textures/{texture}.png")
    write(f"{root}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{mod}:item/{name}"}})
    write(f"{root}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{mod}:{texture}"}})


def block_state(name, data):
    write(f"{K}/blockstates/{name}.json", data)


if __name__ == "__main__":
    wands = {
        "wand_of_embers": ((255, 138, 42), False), "wand_of_frost": ((159, 224, 255), False), "wand_of_mending": ((138, 240, 160), False),
        "wand_of_binding": ((200, 160, 255), False), "staff_of_gales": ((221, 238, 255), True), "staff_of_warding": ((255, 233, 160), True),
    }
    for n, (c, staff) in wands.items():
        item(K, "jaymoskingdoms", n, wand(c, staff), f"item/{n}")
    for n, im in {"immovable_rod": rod(), "climbing_rope": rope_coil(), "endless_jug": jug(), "floating_light": lantern(), "stone_hound": hound(),
                  "breaking_horn": horn(), "smoke_bottle": smoke(), "deck_of_fates": deck(), "keep_deed": deed()}.items():
        item(K, "jaymoskingdoms", n, im, f"item/{n}")
    item(P, "jaymospowers", "boots_of_long_strides", boots(), "item/bauble/artifacts/boots_of_long_strides")
    item(P, "jaymospowers", "cloak_of_the_depths", cloak(), "item/bauble/artifacts/cloak_of_the_depths")

    # blocks
    os.makedirs(f"{K}/textures/block", exist_ok=True)
    rod_block().save(f"{K}/textures/block/immovable_rod.png")
    knot_block().save(f"{K}/textures/block/climbing_knot.png")
    write(f"{K}/models/block/immovable_rod.json", {"parent": "minecraft:block/cube_column", "textures": {"end": "jaymoskingdoms:block/immovable_rod", "side": "jaymoskingdoms:block/immovable_rod"}})
    block_state("immovable_rod", {"variants": {
        "axis=y": {"model": "jaymoskingdoms:block/immovable_rod"},
        "axis=x": {"model": "jaymoskingdoms:block/immovable_rod", "x": 90, "y": 90},
        "axis=z": {"model": "jaymoskingdoms:block/immovable_rod", "x": 90},
    }})
    write(f"{K}/models/block/climbing_knot.json", {"parent": "minecraft:block/ladder", "textures": {"particle": "jaymoskingdoms:block/climbing_knot", "texture": "jaymoskingdoms:block/climbing_knot"}})
    block_state("climbing_knot", {"variants": {
        "facing=north": {"model": "jaymoskingdoms:block/climbing_knot"},
        "facing=east": {"model": "jaymoskingdoms:block/climbing_knot", "y": 90},
        "facing=south": {"model": "jaymoskingdoms:block/climbing_knot", "y": 180},
        "facing=west": {"model": "jaymoskingdoms:block/climbing_knot", "y": 270},
    }})
    write(f"{K}/models/block/floating_light.json", {"textures": {"particle": "minecraft:block/glass"}})
    block_state("floating_light", {"variants": {"": {"model": "jaymoskingdoms:block/floating_light"}}})
    print("drew the patch 8 items and blocks")
