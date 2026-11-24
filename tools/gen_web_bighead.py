"""Generates the big body's head (the symbiote's head from Andy's own Symbiote 1.20.1 mod, adapted to our big body).

Provenance: the geometry and the art are Andy's own (his mod, made with Claude/GPT; he confirmed it 2026-11-24 and
authorised reusing it here, so the "nothing copied" line of the older symbiote tools doesn't apply to this head).
tools/recover_symbiote_head.py read them out of that jar's bytecode and texture into tools/data/bighead_*: this script
needs only those files, not the jar.

Writes:
  src/client/java/com/jaymospowers/client/power/web/WebBigHeadMesh.java   the head, the jaw, their fang parts and the tongue (every cube
                                                                          the original's, its own texOffs, its own mirror)
  textures/entity/web_symbiote_bighead.png         the black strain (the original's art, only the head's UV rectangles, its dark
                                                   and red colours matched to the big body's own texture, see match_palette)
  textures/entity/web_symbiote_bighead_white.png   the white and red strains' twins, recoloured the way
  textures/entity/web_symbiote_bighead_red.png     gen_web_strains.py recolours the rest of the suit
  textures/entity/web_symbiote_bighead_glow.png    the eyes and the teeth's glow, drawn fullbright over the head

The texture is the original's 6x density (1536x1152 for a 256x192 UV space): the head is sharper than the rest of the
suit, and the mesh keeps the 256x192 UV space of the original.

Run from the project root:  python3 -I tools/gen_web_bighead.py
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
sys.path.insert(0, str(TOOLS))
import gen_web_strains  # noqa: E402  (recolour, the strains' colours)

DATA = TOOLS / "data"
ENTITY = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
JAVA = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web" / "WebBigHeadMesh.java"


BINS = 256


def kinds(rgba):
    """gen_web_strains.classify for every opaque pixel of an RGBA array: 0 clear, 1 eye, 2 bone, 3 red, 4 black."""
    out = np.zeros(rgba.shape[:2], np.uint8)
    for y, x in zip(*np.nonzero(rgba[..., 3] > 0)):
        out[y, x] = ("clear", "eye", "bone", "red", "black").index(gen_web_strains.classify(tuple(int(v) for v in rgba[y, x])))
    return out


def lum(rgb):
    return 0.3 * rgb[..., 0] + 0.59 * rgb[..., 1] + 0.11 * rgb[..., 2]


def match_palette(head, body):
    """The head's dark and red colours moved onto the big body's own (the original's head is blue-black and crimson, the body
    a lighter slate blue with violet veins). Per kind (black, red): each head pixel takes the body's colour at the same
    brightness rank (ties share a rank, so a flat area stays flat), so the head's shading and detail stay and the body's
    colours come with them. Eyes, teeth and the clear background are untouched."""
    h = np.asarray(head.convert("RGBA")).copy()
    b = np.asarray(body.convert("RGBA"))
    hk, bk = kinds(h), kinds(b)
    for kind in (4, 3):
        hm, bm = hk == kind, bk == kind
        if not hm.any() or not bm.any():
            continue
        bl, bc = lum(b[bm][:, :3].astype(float)), b[bm][:, :3].astype(float)
        order = np.argsort(bl, kind="stable")
        bins = np.array_split(order, BINS)
        palette = np.array([bc[i].mean(axis=0) for i in bins if len(i)])
        hl = lum(h[hm][:, :3].astype(float))
        values, inverse, counts = np.unique(hl, return_inverse=True, return_counts=True)
        rank = (np.cumsum(counts) - counts / 2.0) / counts.sum()
        pick = np.minimum((rank[inverse] * len(palette)).astype(int), len(palette) - 1)
        h[hm, :3] = np.clip(palette[pick], 0, 255).astype(np.uint8)
    return Image.fromarray(h)


def f(x):
    """A Java float literal."""
    text = ("%.4f" % x).rstrip("0").rstrip(".")
    return (text if text not in ("", "-0") else "0") + "F"


def cube_chain(cubes):
    out = "CubeListBuilder.create()"
    mirrored = False
    for c in cubes:
        if c["mirror"] != mirrored:
            out += ".mirror()" if c["mirror"] else ".mirror(false)"
            mirrored = c["mirror"]
        box = ", ".join(f(n) for n in c["box"])
        out += ".texOffs(%d, %d).addBox(%s, new CubeDeformation(%s))" % (c["uv"][0], c["uv"][1], box, f(c["grow"]))
    if mirrored:
        out += ".mirror(false)"
    return out


def pose(part):
    if part["name"] == "head":
        return "PartPose.ZERO"
    if not any(part["rot"]):
        return "PartPose.offset(%s, %s, %s)" % tuple(f(n) for n in part["pos"])
    return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(f(n) for n in list(part["pos"]) + list(part["rot"]))


def java(data):
    lines = []
    for part in data["parts"]:
        parent = part["parent"] or "root"
        lines.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", %s, %s);' %
                     (part["name"], parent, part["name"], cube_chain(part["cubes"]), pose(part)))
    mouth, eyes = data["anchors"]["mouth"]["at"], data["anchors"]["eyes"]["at"]
    jaw = next(p for p in data["parts"] if p["name"] == "jaw")
    return """package com.jaymospowers.client.power.web;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by {@code tools/gen_web_bighead.py}: don't edit by hand. The big body's head and jaw, with their fang parts,
 * as the original's cubes (the jaw holds the tongue's four segments, WebBigHead stretches them), UV-mapped onto
 * {@code web_symbiote_bighead.png}. The units are the original's: {@link WebBigHead} scales and places the head.
 */
final class WebBigHeadMesh {
	static final int TEX_W = 256, TEX_H = 192;
	/** The jaw's hinge in the head's space, and where the mouth and the eyes are (the original's anchors). */
	static final float JAW_Y = %s, JAW_Z = %s;
	static final float MOUTH_X = %s, MOUTH_Y = %s, MOUTH_Z = %s;
	static final float EYES_X = %s, EYES_Y = %s, EYES_Z = %s;

	private WebBigHeadMesh() {
	}

	static LayerDefinition create() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
%s
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}
}
""" % (f(jaw["pos"][1]), f(jaw["pos"][2]), f(mouth[0]), f(mouth[1]), f(mouth[2]), f(eyes[0]), f(eyes[1]), f(eyes[2]), "\n".join(lines))


def main():
    data = json.loads((DATA / "bighead_geometry.json").read_text(encoding="utf-8"))
    JAVA.write_text(java(data), encoding="utf-8", newline="\n")
    black = match_palette(Image.open(DATA / "bighead_source.png"), Image.open(ENTITY / "web_symbiote_hulk.png"))
    black.save(ENTITY / "web_symbiote_bighead.png", optimize=True)
    Image.open(DATA / "bighead_source_glow.png").convert("RGBA").save(ENTITY / "web_symbiote_bighead_glow.png", optimize=True)
    for j, strain in enumerate(("white", "red")):
        gen_web_strains.recolour(black, strain, 980 + j, True).save(ENTITY / ("web_symbiote_bighead_%s.png" % strain), optimize=True)
    print("big head written:", len(data["parts"]), "parts")


if __name__ == "__main__":
    main()
