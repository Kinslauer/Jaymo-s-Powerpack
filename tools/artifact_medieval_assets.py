"""Medieval stand-ins for the Artifacts baubles that looked modern (Andy, 2026-11-04).

Each icon is a 16x16 pixel map in vanilla item style: a dark outline, flat shades lit from the top left.
Run from the repo root: python3 tools/artifact_medieval_assets.py [preview.png]
"""
import os
import sys
from PIL import Image

OUT = 'src/main/resources/assets/jaymospowers/textures/item/bauble/artifacts/'

O = (40, 26, 18)  # outline, a dark brown as vanilla leather uses

SPRITES = {}


def sprite(name, palette, rows):
    assert len(rows) == 16, (name, len(rows))
    for r in rows:
        assert len(r) == 16, (name, r, len(r))
    SPRITES[name] = (palette, rows)


# Alewife's Cap: a tall brown felt hat with a band of green hops
sprite('alewife_cap', {'o': O, 'B': (120, 82, 52), 'b': (92, 60, 38), 'L': (150, 106, 70), 'h': (98, 140, 52), 'H': (140, 182, 74)}, [
    '................',
    '.......oo.......',
    '......oLBo......',
    '......oLBo......',
    '.....oLBBbo.....',
    '.....oLBBbo.....',
    '....oLBBBBbo....',
    '....oLBBBBbo....',
    '...ohHhHhHhho...',
    '...ohhhhhhhho...',
    '..oLBBBBBBBBbo..',
    '.oLLBBBBBBBBBbo.',
    '.obbbbbbbbbbbbo.',
    '..oooooooooooo..',
    '................',
    '................',
])

# Jester's Cap: two drooping points, red and green, with brass bells
sprite('jester_cap', {'o': O, 'R': (186, 46, 40), 'r': (130, 30, 28), 'G': (60, 140, 60), 'g': (38, 96, 40), 'y': (240, 200, 70), 'Y': (180, 136, 30), 'w': (230, 220, 190)}, [
    '................',
    '.yo..........oy.',
    'oYRo........oGgo',
    '.oRRo......oGGo.',
    '..oRRo....oGGo..',
    '..oRRRo..oGGGo..',
    '...oRRRooGGGo...',
    '...oRRRRGGGgo...',
    '..oRRRRrGGGGgo..',
    '..oRRRRrGGGGgo..',
    '.oRRRRRrGGGGGgo.',
    '.owwwwwwwwwwwwo.',
    '.owywwywwywwywo.',
    '..oooooooooooo..',
    '................',
    '................',
])

# Reed Breather: a hollow reed bound to a leather mouth strap
sprite('reed_breather', {'o': O, 'R': (176, 170, 96), 'r': (132, 124, 64), 'L': (122, 84, 52), 'l': (90, 60, 36), 'c': (70, 50, 30)}, [
    '..........oo....',
    '.........oRro...',
    '.........oRro...',
    '.........oRro...',
    '.........ocro...',
    '.........oRro...',
    '.........oRro...',
    '.........ocro...',
    '.........oRro...',
    '..oooooooORro...',
    '.oLLLLLLLLRLlo..',
    'oLLlllllllllLLo.',
    'oLl.......ooLlo.',
    '.ol........olo..',
    '..o.........o...',
    '................',
])

# Owl Mask: a feathered half mask with round amber eyes
sprite('owl_mask', {'o': O, 'F': (156, 120, 84), 'f': (112, 82, 54), 'W': (220, 200, 160), 'a': (240, 170, 40), 'k': (20, 14, 10), 'b': (200, 160, 60)}, [
    '................',
    '...oo......oo...',
    '..oFfo....ofFo..',
    '..oFFfooooofFo..',
    '.oFFFFFFFFFFFFo.',
    '.oFWWWFFFFWWWFo.',
    'oFWaaaWFFWaaaWFo',
    'oFWakaWffWakaWFo',
    'oFWaaaWFFWaaaWFo',
    '.oFWWWFbbFWWWFo.',
    '.ofFFFFbbFFFFfo.',
    '..ofFFFooFFFfo..',
    '...offo..offo...',
    '....oo....oo....',
    '................',
    '................',
])

# Soothsayer's Hood: a deep purple hood with a stitched gold eye
sprite('soothsayer_hood', {'o': O, 'P': (110, 60, 140), 'p': (76, 38, 100), 'L': (146, 92, 176), 'k': (24, 14, 30), 'y': (236, 196, 70), 'Y': (170, 130, 30)}, [
    '................',
    '......oooo......',
    '.....oLPPPo.....',
    '....oLPPPPpo....',
    '...oLPPyyPPpo...',
    '...oLPyYYyPpo...',
    '..oLPPPyyPPPpo..',
    '..oLPPkkkkPPpo..',
    '..oPPkkkkkkPpo..',
    '.oLPkkkkkkkkPpo.',
    '.oPPkkkkkkkkPpo.',
    '.oPPkkkkkkkkPpo.',
    'oLPPpkkkkkkpPPpo',
    'oPPpppoooopppPpo',
    '.oooo......oooo.',
    '................',
])

# Rider's Hat: a wide-brimmed leather riding hat with a pheasant feather
sprite('riders_hat', {'o': O, 'B': (128, 88, 54), 'b': (96, 64, 38), 'L': (158, 114, 72), 'f': (170, 70, 40), 'F': (220, 150, 70), 'n': (60, 40, 24)}, [
    '................',
    '..........oo....',
    '.........oFo....',
    '........oFfo....',
    '.....ooooffo....',
    '....oLBBBfBo....',
    '....oLBBBBbo....',
    '....onnnnnnno...',
    '..ooLBBBBBBbboo.',
    '.oLLBBBBBBBBBbbo',
    'oLBBBBBBBBBBBBbo',
    '.obbbbbbbbbbbbo.',
    '..oooooooooooo..',
    '................',
    '................',
    '................',
])

# Lodestone: a dark magnetic stone bound in iron, iron filings stuck to it
sprite('lodestone_charm', {'o': (24, 24, 30), 'S': (78, 78, 92), 's': (54, 54, 66), 'L': (110, 110, 126), 'i': (190, 190, 196), 'I': (140, 140, 150), 'c': (90, 60, 36)}, [
    '.......oo.......',
    '......occo......',
    '.......oo.......',
    '.....oIIIIo.....',
    '....oLSSSSso....',
    '...oLSiSSSSso...',
    '..oLSSSSSiSSso..',
    '..oISSSSSSSSIo..',
    '..oLSiSSSSSSso..',
    '..oLSSSSiSSSso..',
    '...oISSSSSSIo...',
    '...osSSSSSsso...',
    '....oIIIIIIo....',
    '..i..oooooo..i..',
    '.....i....i.....',
    '................',
])

# Wind Bladder: a pig's bladder blown up and tied off with twine
sprite('wind_bladder', {'o': O, 'P': (226, 172, 150), 'p': (190, 132, 112), 'L': (246, 210, 190), 't': (176, 150, 96), 'T': (130, 108, 64)}, [
    '................',
    '.....oooooo.....',
    '...ooLLPPPPoo...',
    '..oLLPPPPPPPpo..',
    '.oLLPPPPPPPPPpo.',
    '.oLPPPPPPPPPPpo.',
    'oLPPPPPPPPPPPPpo',
    'oLPPPPPPPPPPPppo',
    'oPPPPPPPPPPPPppo',
    '.oPPPPPPPPPPppo.',
    '.opPPPPPPPPpppo.',
    '..oppPPPPPpppo..',
    '...ooppppppoo...',
    '.....otTTto.....',
    '.....oTooTo.....',
    '......o..o......',
])

# Pearl Pouch: a dark leather pouch with ender pearls showing at the mouth
sprite('ender_pouch', {'o': O, 'L': (70, 52, 70), 'l': (48, 34, 48), 'H': (100, 76, 100), 'e': (40, 120, 110), 'E': (90, 200, 170), 'g': (210, 180, 90)}, [
    '................',
    '.....oo..oo.....',
    '....oEeooEeo....',
    '....oeEooeEo....',
    '....ooooooooo...',
    '...ogggggggggo..',
    '...oHLLLLLLLlo..',
    '..oHLLLLLLLLLlo.',
    '..oHLLLLLLLLLlo.',
    '.oHLLLLgLLLLLLlo',
    '.oHLLLLLLLLLLLlo',
    '.oHLLLLLLLLLLllo',
    '.oLLLLLLLLLLLllo',
    '..olllllllllllo.',
    '...ooooooooooo..',
    '................',
])

# Iron Knuckles: a riveted iron gauntlet, fist closed
sprite('iron_knuckles', {'o': (30, 30, 34), 'I': (190, 190, 196), 'i': (140, 140, 150), 'W': (226, 226, 232), 'd': (96, 96, 106), 'L': (110, 76, 46), 'l': (80, 54, 32)}, [
    '................',
    '....oooooooo....',
    '...oWIoWIoWIo...',
    '...oIioIioIio...',
    '..oWIIIIIIIIio..',
    '..oIdIdIdIdIio..',
    '..oWIIIIIIIIIo..',
    '..oIIIIIIIIIio..',
    '..oiIIIIIIIIdo..',
    '...oiiiiiiiido..',
    '...oLLLLLLLLlo..',
    '...oLlLlLlLLlo..',
    '...oLLLLLLLLlo..',
    '...oooooooooo...',
    '................',
    '................',
])

# Ram's-Head Bracer: a leather bracer with a brass ram's head boss
sprite('ram_bracer', {'o': O, 'L': (128, 86, 50), 'l': (94, 62, 36), 'H': (156, 112, 70), 'y': (226, 184, 70), 'Y': (170, 126, 34), 'k': (60, 40, 20)}, [
    '................',
    '................',
    '...oooooooooo...',
    '..oHLLLLLLLLlo..',
    '..oHLLyyyyLLlo..',
    '..oHLyYYYYyLlo..',
    '..oHyYykkYYylo..',
    '..oHyYYYYYYylo..',
    '..oHLyYYYYyLlo..',
    '..oHLLyYYyLLlo..',
    '..oHLLLyyLLLlo..',
    '..oHLLLLLLLLlo..',
    '..ollllllllllo..',
    '...oooooooooo...',
    '................',
    '................',
])

# Smelter's Bracer: an iron-banded bracer holding a glowing coal
sprite('smelters_bracer', {'o': O, 'L': (110, 74, 44), 'l': (80, 52, 30), 'I': (150, 150, 160), 'i': (100, 100, 112), 'f': (250, 160, 40), 'F': (255, 230, 120), 'r': (200, 70, 30)}, [
    '................',
    '................',
    '...oooooooooo...',
    '..oIIIIIIIIIio..',
    '..oLLLLLLLLLlo..',
    '..oLLoooooLLlo..',
    '..oLorfFfroLlo..',
    '..oLofFFFfoLlo..',
    '..oLorfFfroLlo..',
    '..oLLoooooLLlo..',
    '..oLLLLLLLLLlo..',
    '..oIIIIIIIIIio..',
    '..oiiiiiiiiiio..',
    '...oooooooooo...',
    '................',
    '................',
])

# Glutton's Ring: a heavy bronze ring set with a carved boar's head
sprite('gluttons_ring', {'o': (50, 30, 16), 'B': (196, 132, 64), 'b': (146, 92, 40), 'L': (232, 180, 110), 'p': (210, 140, 140), 'k': (40, 24, 16)}, [
    '................',
    '................',
    '......oooo......',
    '.....oppppo.....',
    '....oppkpkpo....',
    '....opppppp o...'.replace(' ', 'o'),
    '.....oBBBBo.....',
    '....oLBooBbo....',
    '...oLBo..oBbo...',
    '...oBo....obo...',
    '...oBo....obo...',
    '...oBbo..obbo...',
    '....oBboobbo....',
    '.....obbbbo.....',
    '......oooo......',
    '................',
])

# Courier's Boots: tall soft leather boots, laced, for a runner
sprite('couriers_boots', {'o': O, 'L': (150, 100, 58), 'l': (110, 72, 40), 'H': (182, 132, 82), 's': (60, 40, 24), 'w': (220, 200, 160)}, [
    '................',
    '.oooo...oooo....',
    '.oHLo...oHLo....',
    '.oHwo...oHwo....',
    '.oHLo...oHLo....',
    '.oHwo...oHwo....',
    '.oHLo...oHLo....',
    '.oHLo...oHLo....',
    '.oHLlo..oHLlo...',
    '.oHLLlo.oHLLlo..',
    '.oHLLLLooHLLLLo.',
    '.osssssooosssssso'[:16],
    '..oooooo.oooooo.',
    '................',
    '................',
    '................',
])

# Waterstrider Boots: oiled sealskin boots with blue webbing at the toes
sprite('waterstrider_boots', {'o': (20, 30, 40), 'S': (70, 90, 100), 's': (48, 64, 74), 'H': (100, 124, 136), 'w': (90, 160, 220), 'W': (150, 200, 240)}, [
    '................',
    '.oooo...oooo....',
    '.oHSo...oHSo....',
    '.oHSo...oHSo....',
    '.oHSo...oHSo....',
    '.oHSo...oHSo....',
    '.oHSo...oHSo....',
    '.oHSso..oHSso...',
    '.oHSSso.oHSSso..',
    '.oHSSSWooHSSSWo.',
    '.oHSSWwWoHSSWwWo',
    '.oswWwWwowWwWwwo',
    '..oooooooooooooo',
    '................',
    '................',
    '................',
])

# Frogskin Flippers: green frog-leather flippers
sprite('frogskin_flippers', {'o': (20, 36, 20), 'G': (90, 150, 60), 'g': (62, 108, 40), 'H': (130, 186, 90), 'y': (200, 210, 90)}, [
    '................',
    '..oooo....oooo..',
    '..oHGo....oHGo..',
    '..oHGo....oHGo..',
    '.oHGGgo..oHGGgo.',
    '.oHGyGo..oHGyGo.',
    'oHGGGGgooHGGGGgo',
    'oHGGGGgooHGGGGgo',
    'oHGyGGgooHGyGGgo',
    'oHGGGGgooHGGGGgo',
    'oHGGGGGgoHGGGGGg'[:15] + 'o',
    'ogGgGgGgogGgGgGo',
    '.o.o.o.o.o.o.o.o',
    '................',
    '................',
    '................',
])


def draw(name):
    palette, rows = SPRITES[name]
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                img.putpixel((x, y), palette[ch] + (255,) if ch in palette else O + (255,))
    return img


if __name__ == '__main__':
    for name in SPRITES:
        draw(name).save(os.path.join(OUT, name + '.png'))
    if len(sys.argv) > 1:
        sheet = Image.new('RGBA', (len(SPRITES) * 80 + 8, 88), (198, 198, 198, 255))
        for i, name in enumerate(SPRITES):
            big = draw(name).resize((64, 64), Image.NEAREST)
            sheet.paste(big, (8 + i * 80, 12), big)
        sheet.save(sys.argv[1])
    print(len(SPRITES), 'icons')
