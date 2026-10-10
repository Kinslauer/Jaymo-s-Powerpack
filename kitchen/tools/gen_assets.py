"""Generates Jaymo's Kitchen item textures, item models and the mod icon.

Hand-drawn 16x16 pixel grids (one char per pixel), so no vanilla art is copied.
Run from the project root:  python tools/gen_assets.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"

# shared palette: '.' is transparent
PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (40, 24, 12, 255),     # outline
    "b": (139, 90, 43, 255),    # bowl wood
    "B": (101, 63, 30, 255),    # bowl shadow
    "h": (176, 122, 68, 255),   # bowl highlight
    "s": (110, 54, 28, 255),    # stew broth
    "S": (84, 38, 18, 255),     # broth shadow
    "m": (150, 60, 50, 255),    # beef chunk
    "o": (235, 140, 40, 255),   # carrot
    "y": (226, 205, 120, 255),  # potato
    "c": (238, 228, 196, 255),  # risotto cream
    "C": (210, 196, 158, 255),  # risotto shadow
    "n": (150, 110, 80, 255),   # brown mushroom
    "r": (200, 40, 40, 255),    # red mushroom / berry
    "R": (140, 20, 30, 255),    # berry shadow
    "w": (255, 255, 255, 255),  # white fleck
    "g": (226, 170, 80, 255),   # crust
    "G": (186, 124, 50, 255),   # crust shadow
    "l": (250, 210, 120, 255),  # crust highlight
    "p": (250, 140, 110, 255),  # salmon
    "P": (215, 100, 80, 255),   # salmon shadow
    "q": (255, 190, 160, 255),  # salmon highlight
    "x": (60, 30, 20, 255),     # grill mark
    "e": (60, 110, 50, 255),    # kelp
    "E": (40, 80, 35, 255),     # kelp shadow
    "a": (214, 140, 40, 255),   # honey glaze
    "A": (170, 96, 24, 255),    # glaze shadow
    "u": (255, 200, 90, 255),   # glaze shine
    "i": (240, 236, 220, 255),  # bone
    "I": (200, 194, 176, 255),  # bone shadow
    "v": (200, 225, 235, 255),  # glass
    "V": (150, 180, 195, 255),  # glass shadow
    "f": (232, 196, 70, 255),   # oil
    "F": (196, 150, 40, 255),   # oil shadow
    "j": (120, 84, 50, 255),    # cork
    "d": (238, 222, 180, 255),  # dough
    "D": (210, 190, 150, 255),  # dough shadow
    "t": (250, 240, 215, 255),  # dough highlight
    "z": (172, 42, 26, 255),    # baked beetroot sauce
    "Z": (130, 90, 60, 255),    # baked mushroom
    "1": (66, 110, 160, 255),   # fish-stew broth
    "2": (210, 190, 170, 255),  # cod flakes
    "3": (228, 130, 36, 255),   # pumpkin soup
    "4": (250, 175, 70, 255),   # pumpkin soup highlight
    "5": (140, 80, 50, 255),    # roasted meat
    "6": (100, 56, 34, 255),    # roasted meat shadow
    "7": (180, 140, 80, 255),   # skewer
    "8": (196, 130, 80, 255),   # cooked pork
    "9": (160, 100, 58, 255),   # cooked pork shadow
    "0": (140, 20, 40, 255),    # berry glaze
    "#": (196, 130, 60, 255),   # bread crust
    "$": (226, 170, 90, 255),   # bread highlight
    "%": (150, 96, 40, 255),    # bread shadow
    "&": (200, 150, 60, 255),   # tart filling
    "*": (150, 40, 30, 255),    # apple skin
    "+": (168, 68, 44, 255),    # smoked meat
    "=": (126, 48, 30, 255),    # smoked shadow
    "-": (230, 180, 130, 255),  # smoked fat
    "^": (110, 40, 20, 255),    # jerky
    "~": (82, 28, 14, 255),     # jerky shadow
    "!": (154, 70, 50, 255),    # seared steak
    "@": (110, 46, 30, 255),    # steak shadow
    "(": (42, 20, 10, 255),     # sear stripe
    ")": (255, 160, 40, 255),   # blaze / magma glow
    "[": (255, 220, 110, 255),  # glow highlight
    "]": (180, 120, 90, 255),   # rabbit meat
    "{": (140, 88, 64, 255),    # rabbit shadow
    "}": (170, 30, 40, 255),    # crimson fungus
    "<": (120, 20, 30, 255),    # crimson shadow
    ">": (40, 150, 130, 255),   # warped fungus
    "?": (120, 30, 30, 255),    # nether wart
    "/": (100, 40, 40, 255),    # nether stew broth
    "L": (140, 88, 48, 255),    # quiver leather
    "M": (104, 62, 32, 255),    # quiver leather shadow
    "N": (178, 122, 70, 255),   # quiver leather highlight
    "T": (70, 44, 24, 255),     # quiver strap
    "U": (150, 110, 60, 255),   # arrow shaft
    "W": (236, 236, 236, 255),  # fletching
    "X": (190, 190, 190, 255),  # fletching shadow
    "H": (150, 104, 70, 255),   # hide
    "J": (116, 78, 50, 255),    # hide shadow
    "K": (184, 136, 96, 255),   # hide highlight
    "O": (200, 170, 120, 255),  # rope
    ",": (96, 150, 60, 255),    # herb
    ";": (64, 110, 40, 255),    # herb shadow
    ":": (140, 190, 90, 255),   # herb highlight
    "|": (120, 90, 50, 255),    # herb stem
    "_": (240, 232, 214, 255),  # rolling paper
    "`": (206, 194, 168, 255),  # paper shadow
    "'": (255, 140, 40, 255),   # ember
}

# item textures not generated here: their PNGs are kept as-is
HAND_MADE_SPRITES = ["quiver"]  # (NiftyCarts' cart sprites are written by write_cart_items)

SPRITES = {
    "hearty_stew": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kkssmsosyskk..",
        ".kssomSsmsSoyssk",
        ".kSsysmossmsSsSk",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "mushroom_risotto": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kkccncwcrckk..",
        ".kccrcCcnccCwcck",
        ".kCcwcnccrcCcnCk",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "sweet_berry_pie": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kklgrgRgrgkk..",
        ".klgggrgggRgggGk",
        ".kRrglgRrglgRrGk",
        ".kgggrgggrgggGGk",
        ".kRrglgRrglgRrGk",
        ".kGgggggggggGGGk",
        "..kGGGGGGGGGGGk.",
        "...kkkkkkkkkkk..",
        "................",
        "................",
        "................",
    ],
    "grilled_salmon": [
        "................",
        "................",
        "................",
        "................",
        "......kkkkkk....",
        "....kkqqpppPkk..",
        "...kqqpxppxppPk.",
        "..kqpxppxppxpPk.",
        "..kppppxppxpPPk.",
        "..kPpxppxppPPk..",
        "...kkPPPPPPkk...",
        "..keeEeeEeeEek..",
        "...kkkkkkkkkk...",
        "................",
        "................",
        "................",
    ],
    "honey_glazed_chicken": [
        "................",
        "................",
        "................",
        ".....kkkk.......",
        "....kuuaak......",
        "...kuaaaaAk.....",
        "...kaaaaaaAk....",
        "...kaaaaaaAk....",
        "....kAaaaAAk....",
        ".....kAAAAkk....",
        "......kkkIik....",
        "........kIik....",
        ".......kiiIik...",
        ".......kIkkIk...",
        "........k..k....",
        "................",
    ],
    "dough": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......kkkkk.....",
        "....kkttdddkk...",
        "...kttdddddddk..",
        "..ktdddddddddDk.",
        "..kddddddddddDk.",
        "..kdddddddddDDk.",
        "...kDDdddddDDk..",
        "....kkDDDDDkk...",
        "......kkkkk.....",
        "................",
        "................",
    ],
    "pizza": [
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkggggggkk...",
        "..kgglzzzzZggk..",
        "..kgzzZzzzzzgk..",
        ".kgzzzzzzZzzzGk.",
        ".kgzZzzzzzzzzGk.",
        ".kgzzzzZzzzZzGk.",
        ".kgzzzzzzzzzzGk.",
        "..kgzzZzzzZzGk..",
        "..kGgzzzzzzGGk..",
        "...kkGGGGGGkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "fish_stew": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kk11211e11kk..",
        ".k1122111211e11k",
        ".k1e1112y11121yk",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "pumpkin_soup": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kk33434333kk..",
        ".k33343333e3433k",
        ".k3343333334333k",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "mutton_kebab": [
        "................",
        "..............k7",
        "............kk7k",
        "..........kkok..",
        ".........k5ook..",
        "........k556kk..",
        ".......k5566k...",
        "......kko6kk....",
        ".....k5ookk.....",
        "....k5566k......",
        "...k5566k.......",
        "...kk66kk.......",
        "..k7kkk.........",
        ".k7k............",
        "k7k.............",
        "kk..............",
    ],
    "glazed_pork_chop": [
        "................",
        "................",
        "................",
        "....kkkkkk......",
        "...k-888890k....",
        "..k-8808889k....",
        "..k-8888089k....",
        "..k-8088889k....",
        "...k-888099k....",
        "....k-8899kkkk..",
        ".....kk999kiiik.",
        "......kkkkkkkk..",
        "................",
        "................",
        "................",
        "................",
    ],
    "rustic_bread": [
        "................",
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "...kk$$$##%kk...",
        "..k$$#k$#k##%k..",
        ".k$###k##k###%k.",
        ".k$##k##k###%%k.",
        ".k##########%%k.",
        "..k%%%%%%%%%%k..",
        "...kkkkkkkkkk...",
        "................",
        "................",
        "................",
        "................",
    ],
    "apple_tart": [
        "................",
        "................",
        ".....kkkkkk.....",
        "...kkggggggkk...",
        "..kgg&*&g&*ggk..",
        "..kg&&&g&&&&gk..",
        ".kg*&g&*&g&*&Gk.",
        ".kgggggggggggGk.",
        ".kg&*&g&&*g&&Gk.",
        ".kg&&&&g&&&g&Gk.",
        "..kG&*g&&*&GGk..",
        "..kGgggggggGGk..",
        "...kkGGGGGGkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "smoked_ham": [
        "................",
        "................",
        "......kkkk......",
        ".....k---=k.....",
        "....k++++++k....",
        "...k+++-++==k...",
        "...k++++++==k...",
        "...k+-++++==k...",
        "...k++++++==k...",
        "....k++++==k....",
        ".....k+===k.....",
        "......kkkk......",
        ".......kk.......",
        "......k--k......",
        ".......kk.......",
        "................",
    ],
    "beef_jerky": [
        "................",
        "................",
        "..kk............",
        ".k^^k...kk......",
        ".k^~^k.k^^k.....",
        "..k^~^kk^~^k....",
        "...k^~^k^~^k.kk.",
        "....k^~^k^~^k^^k",
        ".....k^~^k^~^~^k",
        "......k^~^k^~^k.",
        ".......k^~^k^k..",
        "........k^~^k...",
        ".........k^k....",
        "..........k.....",
        "................",
        "................",
    ],
    "blazing_steak": [
        "................",
        "................",
        "................",
        "....kkkkkkk.....",
        "..kk!(!!(!!kk...",
        ".k!!(!!(!!(!)k..",
        ".k!(!!(!!(!!!)k.",
        "k!(!!(!!(!!@@@k.",
        "k!!!(!!(!!@@@k..",
        ".k)!!!(!!@@@k...",
        "..kk@@@@@@kk....",
        "....kkkkkk......",
        "................",
        "................",
        "................",
        "................",
    ],
    "magma_rabbit": [
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "...kk]])]]]kk...",
        "..k]]][)]]]]{k..",
        ".k]])]]]]])]{{k.",
        ".k]]]]])]]]{{{k.",
        ".k)]]]]]]){{{k..",
        "..k]]])]]{{{k...",
        "...kk{{{{{kk....",
        ".....kkkkk......",
        "................",
        "................",
        "................",
        "................",
    ],
    "crimson_skewer": [
        "................",
        "..............k7",
        "............kk7k",
        "..........kk}k..",
        ".........k5}<k..",
        "........k556kk..",
        ".......k5566k...",
        "......kk}<kk....",
        ".....k5}<kk.....",
        "....k5566k......",
        "...k5566k.......",
        "...kk66kk.......",
        "..k7kkk.........",
        ".k7k............",
        "k7k.............",
        "kk..............",
    ],
    "nether_wart_stew": [
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kk//?//}//kk..",
        ".k//>///?//>//?k",
        ".k/?//}///?///}k",
        ".khbbbbbbbbbbbBk",
        "..khbbbbbbbbbBk.",
        "..khbbbbbbbbbBk.",
        "...khbbbbbbbBk..",
        "....kBBbbbbBk...",
        ".....kkkkkkk....",
        "................",
        "................",
    ],
    "corpse": [
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "...kKKHHHHHJk...",
        "..kKHHHHHHHHJk..",
        ".kKHHkOOkHHHHJk.",
        ".kHHHkOOkHHHHJk.",
        ".kHHHHOOHHHHHJk.",
        ".kHHHkOOkHHHJJk.",
        ".kJHHkOOkHHJJJk.",
        "..kJJHHHHHJJJk..",
        "...kkJJJJJJkk...",
        ".....kkkkkk.....",
        "................",
        "................",
    ],
    "pmeh": [
        "................",
        "........k.......",
        ".......k:k......",
        "..k....k,k....k.",
        ".k:k..k:,;k..k:k",
        "..k,k.k,,;k.k,k.",
        "...k,kk,,;kk,k..",
        "....k,k,,;k,k...",
        "..kkkk,,,,;kkkk.",
        ".k::,,,,,;;;;k..",
        "..kkk,,k|k;kkk..",
        "....k,k.|.k;k...",
        "....kk..|..kk...",
        "........|.......",
        ".......k|k......",
        "........k.......",
    ],
    "joint": [
        "................",
        "................",
        "................",
        "...........kkk..",
        "..........k''k..",
        ".........k`_'k..",
        "........k`__k...",
        ".......k`__k....",
        "......k`__k.....",
        ".....k`__k......",
        "....k`__k.......",
        "...k`__k........",
        "..k,__k.........",
        "..k;,k..........",
        "...kk...........",
        "................",
    ],
    "saddlebag": [
        "................",
        "................",
        "..kkkkkkkkkkkk..",
        "..kTTTTTTTTTTk..",
        ".kkkkkk..kkkkkk.",
        "kNNNNMk..kNNNNMk",
        "kNLLLMk..kNLLLMk",
        "kMMuMMk..kMMuMMk",
        "kNLaLMk..kNLaLMk",
        "kNLLLMk..kNLLLMk",
        "kNLLLMk..kNLLLMk",
        "kLLLLMk..kLLLLMk",
        "kMLLMMk..kMLLMMk",
        ".kMMMk....kMMMk.",
        "..kkk......kkk..",
        "................",
    ],
}


def saddlebag_entity_texture():
    """64x32 texture for the pouches on a horse (UV regions match SaddlebagModel's boxes)."""
    img = Image.new("RGBA", (64, 32))
    px = img.load()
    leather, shadow, light, strap = PALETTE["L"], PALETTE["M"], PALETTE["N"], PALETTE["T"]
    brass = (214, 170, 60, 255)

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color

    # pouch body (0,0): leather with a stitched seam and darker lower edge
    rect(0, 0, 32, 16, leather)
    for x in range(0, 32):
        px[x, 13] = shadow
        px[x, 14] = shadow
        if x % 2 == 0:
            px[x, 7] = light
    # flap (0,16): darker, stitched border
    rect(0, 16, 32, 32, shadow)
    for x in range(0, 32, 2):
        px[x, 17] = light
        px[x, 24] = light
    # strap (32,0) and brass buckle (32,8)
    rect(32, 0, 64, 8, strap)
    for x in range(32, 64, 3):
        px[x, 2] = shadow
    rect(32, 8, 40, 12, brass)
    return img


# every wood gets a butcher block: (wood, the log texture its sides use, the log tag it's made from, top colours as
# (bark, bark shadow, wood, ring, heart)). Oak keeps the original id, butcher_block.
BUTCHER_WOODS = [
    ("oak", "oak_log", "oak_logs", (92, 64, 38), (66, 44, 26), (188, 148, 96), (150, 112, 66), (212, 176, 122)),
    ("spruce", "spruce_log", "spruce_logs", (58, 38, 22), (40, 26, 14), (130, 94, 56), (100, 70, 40), (152, 112, 68)),
    ("birch", "birch_log", "birch_logs", (216, 214, 204), (60, 56, 50), (204, 184, 128), (172, 150, 98), (222, 206, 152)),
    ("jungle", "jungle_log", "jungle_logs", (86, 68, 30), (60, 46, 20), (170, 120, 80), (138, 94, 58), (190, 140, 96)),
    ("acacia", "acacia_log", "acacia_logs", (104, 96, 88), (76, 70, 64), (174, 92, 50), (140, 70, 36), (196, 112, 64)),
    ("dark_oak", "dark_oak_log", "dark_oak_logs", (60, 46, 26), (40, 30, 16), (96, 64, 34), (72, 48, 24), (114, 80, 44)),
    ("mangrove", "mangrove_log", "mangrove_logs", (84, 66, 42), (58, 44, 28), (122, 50, 46), (96, 36, 34), (142, 64, 58)),
    ("cherry", "cherry_log", "cherry_logs", (58, 30, 40), (40, 20, 28), (226, 180, 170), (196, 146, 138), (238, 200, 190)),
    ("pale_oak", "pale_oak_log", "pale_oak_logs", (88, 82, 78), (64, 60, 56), (230, 222, 216), (204, 194, 186), (240, 234, 228)),
    ("crimson", "crimson_stem", "crimson_stems", (92, 26, 30), (64, 16, 20), (124, 58, 82), (96, 40, 62), (148, 74, 100)),
    ("warped", "warped_stem", "warped_stems", (52, 40, 60), (36, 26, 42), (58, 142, 140), (40, 108, 108), (74, 166, 162)),
]


def butcher_block_id(wood):
    return "butcher_block" if wood == "oak" else f"{wood}_butcher_block"


def butcher_block_top(bark, bark_dark, wood, ring, light):
    """The top: the end grain of two logs side by side, in this wood's colours, with knife marks."""
    bark, bark_dark, wood, ring, light = (c + (255,) for c in (bark, bark_dark, wood, ring, light))
    cut = tuple(int(v * 0.7) for v in ring[:3]) + (255,)

    def end_grain(img, cx, cy):
        # one log's end: bark rim, then growth rings
        for y in range(16):
            for x in range(16):
                dx, dy = (x + 0.5 - cx) / 4.0, (y + 0.5 - cy) / 8.0
                d = (dx * dx + dy * dy) ** 0.5
                if d <= 1.0:
                    if d > 0.86:
                        color = bark
                    elif int(d * 5) % 2:
                        color = ring
                    else:
                        color = wood if d > 0.2 else light
                    img.putpixel((x, y), color)

    top = Image.new("RGBA", (16, 16), bark_dark)
    end_grain(top, 4.0, 8.0)
    end_grain(top, 12.0, 8.0)
    for x, y in [(3, 4), (4, 5), (5, 6), (10, 9), (11, 10), (12, 11), (6, 11), (7, 10)]:
        top.putpixel((x, y), cut)
    return top


def write_butcher_blocks():
    """Per wood: the top texture, blockstate, block and item models, loot table and recipe; plus the tags."""
    data = ROOT.parent.parent / "data"
    ids = []
    for wood, log, logs_tag, *colours in BUTCHER_WOODS:
        block = butcher_block_id(wood)
        ids.append(f"jaymoskitchen:{block}")
        butcher_block_top(*colours).save(ROOT / "textures" / "block" / f"{block}_top.png")
        write_json(ROOT / "blockstates" / f"{block}.json", {"variants": {"": {"model": f"jaymoskitchen:block/{block}"}}})
        # the sides and bottom are the log's own (vanilla) textures, so they always match it
        write_json(ROOT / "models" / "block" / f"{block}.json", {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"jaymoskitchen:block/{block}_top", "bottom": f"minecraft:block/{log}_top",
                         "side": f"minecraft:block/{log}"}})
        write_json(ROOT / "items" / f"{block}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:block/{block}"}})
        write_json(data / "jaymoskitchen" / "loot_table" / "blocks" / f"{block}.json", {
            "type": "minecraft:block",
            "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": f"jaymoskitchen:{block}"}], "rolls": 1}],
            "random_sequence": f"jaymoskitchen:blocks/{block}"})
        write_json(data / "jaymoskitchen" / "recipe" / f"{block}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["L", "L"],
            "key": {"L": f"#minecraft:{logs_tag}"}, "result": {"id": f"jaymoskitchen:{block}"}})
    write_json(data / "jaymoskitchen" / "tags" / "block" / "butcher_blocks.json", {"values": ids})
    # (plus the wooden mill machines)
    axe = ids + [f"jaymoskitchen:{b}" for b in ("chopping_block", "chopper", "press", "horse_press", "machine_filler")]
    write_json(data / "minecraft" / "tags" / "block" / "mineable" / "axe.json", {"replace": False, "values": axe})


def zombie_tatters_texture():
    """64x64 overlay in the player skin layout: rips in the clothes with grey-blue dead flesh showing through, and
    grime. Drawn over a zombie player's (tinted) skin, so their clothes look torn."""
    import random
    rng = random.Random(1311)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    flesh, flesh_dark, rip, grime = (112, 138, 140, 255), (86, 108, 112, 255), (38, 44, 46, 255), (52, 50, 40, 150)
    # (x0, y0, x1, y1) of the base layer's clothed parts: body, arms, legs (both arms and legs, both layouts)
    regions = [(16, 20, 40, 32), (40, 20, 56, 32), (32, 52, 48, 64), (0, 20, 16, 32), (16, 52, 32, 64)]
    for x0, y0, x1, y1 in regions:
        area = (x1 - x0) * (y1 - y0)
        for _ in range(area // 40 + 1):
            # a ragged tear: a blob of flesh with a dark torn edge
            cx, cy = rng.randrange(x0, x1), rng.randrange(y0, y1)
            r = rng.uniform(1.0, 2.6)
            for y in range(max(y0, int(cy - r - 1)), min(y1, int(cy + r + 2))):
                for x in range(max(x0, int(cx - r - 1)), min(x1, int(cx + r + 2))):
                    d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 + rng.uniform(-0.6, 0.6)
                    if d <= r - 0.6:
                        px[x, y] = flesh if rng.random() < 0.7 else flesh_dark
                    elif d <= r + 0.4 and px[x, y][3] == 0:
                        px[x, y] = rip
        for _ in range(area // 25):
            x, y = rng.randrange(x0, x1), rng.randrange(y0, y1)
            if px[x, y][3] == 0:
                px[x, y] = grime
    # the face: a little grime under the eyes
    for x, y in [(9, 12), (14, 12)]:
        px[x, y] = grime
    return img


def quiver_entity_texture():
    """32x32 texture for the 3D quiver worn on the back (UV regions match QuiverModel's boxes)."""
    img = Image.new("RGBA", (32, 32))
    px = img.load()

    def rect(x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                px[x, y] = color

    leather, shadow, light = PALETTE["L"], PALETTE["M"], PALETTE["N"]
    strap, wood, white = PALETTE["T"], PALETTE["U"], PALETTE["W"]
    # tube (4x12x3 box at 0,0): sides with vertical shading, stitched seams and a strap band
    rect(0, 3, 14, 15, leather)
    for y in range(3, 15):
        px[0, y] = shadow
        px[3, y] = light
        px[13, y] = shadow
    for x in range(0, 14):
        px[x, 6] = shadow if x % 2 else leather
        px[x, 12] = shadow if x % 2 else leather
        px[x, 8] = strap
        px[x, 9] = strap
    rect(3, 0, 7, 3, (40, 28, 18, 255))   # mouth: dark inside
    rect(7, 0, 11, 3, shadow)             # bottom
    # rim (5x1.5x4 at 0,15) and end cap (4.5x1.5x3.5 at 0,22)
    rect(0, 15, 18, 21, shadow)
    rect(0, 22, 16, 27, shadow)
    for x in range(0, 18, 2):
        px[x, 17] = light
    # strap (1x16x1 at 20,0)
    rect(20, 0, 24, 17, strap)
    for y in range(1, 17, 3):
        px[21, y] = shadow
    # arrow shaft (28,0) and fletching (24,20): fletching is white so it can be tinted per arrow
    rect(28, 0, 32, 12, wood)
    rect(24, 20, 32, 26, white)
    return img


def render(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "sprites are 16x16"
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    for name, rows in SPRITES.items():
        tex = ROOT / "textures" / "item" / f"{name}.png"
        tex.parent.mkdir(parents=True, exist_ok=True)
        render(rows).save(tex)
        write_json(ROOT / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
        write_json(ROOT / "items" / f"{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})

    # cooking oil: vanilla's potion bottle (so it follows resource packs) with its liquid tinted golden
    write_json(ROOT / "items" / "cooking_oil.json", {"model": {"type": "minecraft:model", "model": "minecraft:item/potion",
                                                               "tints": [{"type": "minecraft:constant", "value": 0xFFE0B82C - (1 << 32)}]}})

    # sprites drawn elsewhere (the quiver uses vanilla's unused quiver sprite, supplied by the user): models only
    for name in HAND_MADE_SPRITES:
        write_json(ROOT / "models" / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
        write_json(ROOT / "items" / f"{name}.json",
                   {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})

    # mod icon: the pie on a warm tile, scaled up crisply
    icon = Image.new("RGBA", (128, 128), (60, 36, 20, 255))
    pie = render(SPRITES["sweet_berry_pie"]).resize((112, 112), Image.NEAREST)
    icon.alpha_composite(pie, (8, 4))
    icon.save(ROOT / "icon.png")

    block_dir = ROOT / "textures" / "block"
    block_dir.mkdir(parents=True, exist_ok=True)
    write_butcher_blocks()

    entity = ROOT / "textures" / "entity" / "quiver.png"
    entity.parent.mkdir(parents=True, exist_ok=True)
    quiver_entity_texture().save(entity)
    zombie_tatters_texture().save(entity.parent / "zombie_player_tatters.png")
    saddlebag_entity_texture().save(entity.parent / "saddlebag.png")
    print(f"wrote {len(SPRITES)} items + icon to {ROOT}")


if __name__ == "__main__":
    main()
