"""Draws the scrolls of the nine spells a Sorcerer can keep: four of volcanic glass, three of fire, the black flame and the storm cloak.

Run from the repository root: python3 tools/gen_element_scrolls.py
The paper, seal and glyph are drawn the way the magic rules' scrolls are (gen_fifth_p6.py), so they sit beside them.
"""
from gen_fifth_p6 import item_files, scroll

# name: (seal colour, glyph rows)
SCROLLS = {
    "glass_shards": ((96, 78, 140), ["1001", "0101", "0011", "1111"]),
    "glass_spires": ((96, 78, 140), ["0100", "0101", "1101", "1111"]),
    "glass_skin": ((96, 78, 140), ["0110", "1001", "1001", "0110"]),
    "molten_glass": ((255, 154, 60), ["0000", "0110", "1111", "1111"]),
    "fire_ring": ((240, 110, 40), ["0110", "1001", "1001", "0110"]),
    "fire_pillars": ((240, 110, 40), ["1010", "1010", "1010", "1111"]),
    "fire_lance": ((240, 110, 40), ["0001", "0010", "0100", "1000"]),
    "black_flame": ((42, 18, 56), ["0100", "0110", "1111", "0110"]),
    "storm_cloak": ((170, 200, 255), ["0010", "0110", "0100", "1000"]),
}

if __name__ == "__main__":
    for n, (seal, glyph) in SCROLLS.items():
        scroll(n, seal, glyph)
        item_files(f"scroll_of_{n}")
    print("drew", len(SCROLLS), "scrolls")
