import sys, os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from canvas import Canvas
from vstyle import hx, MAT
PLANKS = {  # vanilla 26.3 planks, darkest to lightest
    'oak': '67502c 7e6237 967441 9f844d af8f55 b8945f c29d62', 'spruce': '553a1f 5a4424 614b2e 70522e 7a5a34 82613a 886539',
    'birch': '9e8b61 a59467 ae9f76 b8a875 c8b77a d7c185 d7cb8d', 'jungle': '68462f 785437 976a44 9f714a aa7954 b88764 bf8e6b',
    'acacia': '884728 8f4c2a 99502b a05630 ad5d32 ba6337 c26d3f', 'dark_oak': '291a0c 301e0e 3a2411 3e2912 492f17 4f3218 53381a',
    'mangrove': '5d1c1e 642423 6f2a2d 753136 773934 7f4234 8b4d3a', 'cherry': 'cd8580 dd9d97 e1a8a1 e6b3ad e7bab4 e7c2bb e7cac5',
    'pale_oak': 'c0b2b2 c7b8b8 d2c6c7 ddcecd e7e3e1 faefee fffbf8', 'bamboo': '907e3a 998741 bba84c c8b14d d3bb50 e3cc6a efd97e',
    'crimson': '3f1e2d 442131 4b2737 5c3042 6a344b 7e3a56 863e5a', 'warped': '113835 1e4340 1f5752 2e5f51 287067 398382 3a8e8c'}
def wood_ramp(w):
    cs = [hx(c) for c in PLANKS[w].split()]
    lo = cs[0]; outline = tuple(int(v * 0.42) for v in lo); shade = tuple(int(v * 0.7) for v in lo)
    return [outline, shade, cs[0], cs[2], cs[4], cs[5], cs[6]]
CLOTH = [hx(c) for c in ('5d574f', '8f887c', 'bdb5a6', 'd9d2c3', 'ece6d8', 'f7f2e6', 'ffffff')]
SACK = [hx(c) for c in ('4a3a20', '7a6440', '9e8558', 'b89e6c', 'cdb47f', 'dcc58f', 'e8d6a4')]
IRON = MAT['iron']

def bed(c, top=4):
    """open cart bed in 3/4 view: rim, dark inside, front planks, shaded end"""
    c.new_part()
    c.poly([(3, top + 3), (6, top), (15, top), (12, top + 3)], 'w', 1)          # inside
    c.line(6, top, 15, top, 'w', 4); c.line(3, top + 3, 12, top + 3, 'w', 6)    # rims
    c.line(3, top + 3, 6, top, 'w', 5)
    c.rect(3, top + 4, 12, top + 6, 'w', 4); c.line(3, top + 5, 12, top + 5, 'w', 3)   # front planks
    c.poly([(13, top + 3), (15, top + 1), (15, top + 4), (13, top + 6)], 'w', 2)  # end face
    c.line(15, top, 15, top + 1, 'w', 3); c.line(12, top + 3, 13, top + 3, 'w', 5)
def wheel(c, cx, cy):
    c.new_part()
    c.grid(cx - 3, cy - 3, ['..RRR..', '.RgSgR.', 'RgSSSgR', 'RSSoSSR', 'RgSSSgR', '.RgSgR.', '..RRR..'],
           {'R': ('w', 3), 'g': ('w', 1), 'S': ('w', 5), 'o': ('i', 2)})
def shafts(c, y):
    c.line(3, y, 0, y + 3, 'w', 5); c.line(4, y + 1, 1, y + 4, 'w', 3)

def supply_cart(w):
    c = Canvas(); shafts(c, 9); c.new_part()
    c.grid(7, 1, ['.####.', '#++++#', '#+--+#', '#++++#'], {'#': ('w', 3), '+': ('w', 5), '-': ('w', 3)})  # crate
    c.new_part(); c.grid(4, 2, ['.ss.', 'ssss', 'ssss'], {'s': ('s', 4)})                                                   # sack
    c.px(5, 2, 's', 6); c.px(4, 3, 's', 5)
    bed(c, 4); wheel(c, 8, 12)
    return c.finish({'w': wood_ramp(w), 'i': IRON, 's': SACK})
def animal_cart(w):
    c = Canvas(); shafts(c, 9); c.new_part()
    for x in (4, 7, 10, 13): c.line(x + (1 if x == 13 else 0), 2, x, 6, 'w', 3)   # rail posts
    c.line(5, 2, 14, 2, 'w', 5); c.line(4, 4, 13, 4, 'w', 4)
    bed(c, 5); wheel(c, 8, 12)
    return c.finish({'w': wood_ramp(w), 'i': IRON})
def wagon(w):
    c = Canvas(); shafts(c, 10); c.new_part()
    c.poly([(3, 7), (3, 3), (5, 1), (12, 1), (15, 3), (15, 7)], 'c', 4)     # canvas cover
    c.line(4, 2, 12, 2, 'c', 6); c.line(5, 1, 11, 1, 'c', 5)
    for x in (6, 10): c.line(x, 2, x, 7, 'c', 2)                             # hoops
    c.poly([(13, 3), (15, 3), (15, 7), (13, 7)], 'c', 3)
    bed(c, 6)
    wheel(c, 5, 12); wheel(c, 12, 12)
    return c.finish({'w': wood_ramp(w), 'i': IRON, 'c': CLOTH})
def plow(w):
    c = Canvas()
    c.line(9, 8, 13, 1, 'w', 5); c.line(10, 8, 14, 1, 'w', 4); c.line(11, 8, 15, 2, 'w', 2)                 # handles
    c.line(13, 1, 14, 0, 'w', 4)
    c.new_part()
    c.grid(8, 9, ['..##', '.###', '+##.', '+#..'], {'#': ('i', 2), '+': ('i', 5)})   # share, bright cutting edge
    c.px(9, 10, 'i', 6)
    c.new_part(); c.rect(1, 7, 12, 8, 'w', 4); c.line(1, 7, 12, 7, 'w', 6)       # beam
    c.px(0, 7, 'i', 3); c.px(0, 8, 'i', 2)                                      # hitch ring
    wheel(c, 3, 11)
    return c.finish({'w': wood_ramp(w), 'i': IRON})
