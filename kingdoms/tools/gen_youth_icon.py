"""
Youth's face for the voice chat icon row (YouthWispRenderer#drawHearing): the wisp's own face and hair, cut from its
entity texture and scaled up to 8x8 like a player head. Writes textures/gui/youth_icon.png.

Run from the project root: py -3 -I kingdoms/tools/gen_youth_icon.py   (needs Pillow)
"""
from PIL import Image

ASSETS = "kingdoms/src/main/resources/assets/jaymoskingdoms"
skin = Image.open(f"{ASSETS}/textures/entity/youth_wisp.png").convert("RGBA")
face = skin.crop((12, 10, 16, 14)).resize((8, 8), Image.NEAREST)
hair = skin.crop((28, 10, 32, 12)).resize((8, 3), Image.NEAREST)
icon = Image.new("RGBA", (8, 8))
icon.alpha_composite(face)
icon.alpha_composite(hair, (0, 0))
# the head is see-through in the world; an icon is solid
icon.putdata([(r, g, b, 255 if a else 0) for r, g, b, a in icon.getdata()])
icon.save(f"{ASSETS}/textures/gui/youth_icon.png")
print("wrote textures/gui/youth_icon.png")
