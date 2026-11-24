"""Generates the red strain's slender body (the red form of Andy's own Symbiote 1.20.1 mod, worn by our red symbiote).

Provenance: the geometry and the art are Andy's own (his mod, made with Claude/GPT; he confirmed it 2026-11-24 and
authorised reusing it here). tools/recover_symbiote_slender.py read them out of that jar into tools/data/slender_*: this
script needs only those files, not the jar. The original's six back tendrils and two arm blades were left out there: our
own tendrils and weapon arm are drawn on this body instead.

Writes:
  src/client/java/com/jaymospowers/client/power/web/WebSlenderMesh.java   the body cut at the player model's joints: a
      holder part ("slender") under each of the player model's head, body, arms and legs, holding the original's parts
      for it (the head with its jaw and tongue; the hips and torso; each arm with its forearm and fingers; each leg with
      its shin), every cube the original's, its own texOffs and mirror, in the original's units. WebSlender scales the
      holders and moves the player model's joints to the original's (WebSuitGrowth.shift), so everything drawn on the
      body lines up with it.
  textures/entity/web_symbiote_slender.png        the original's art (only the kept cubes' UV rectangles); its reds and
                                                  near-blacks are already the red strain's own (gen_web_strains R_BASE, VEIN)
  textures/entity/web_symbiote_slender_glow.png   the eyes' glow, drawn fullbright

Run from the project root:  python3 -I tools/gen_web_slender.py
"""
import json
from pathlib import Path

from PIL import Image

TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
DATA = TOOLS / "data"
ENTITY = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
JAVA = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web" / "WebSlenderMesh.java"

# which of the player model's parts holds each of the original's subtrees (cut off its parent there)
CUTS = {"head": "head", "right_arm": "right_arm", "left_arm": "left_arm", "right_leg": "right_leg", "left_leg": "left_leg", "hips": "body"}
# the original's ground (its feet stand on y 24, as a player model's do)
GROUND = 24.0


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


def pose(pos, rot):
    if not any(rot):
        return "PartPose.offset(%s, %s, %s)" % tuple(f(n) for n in pos)
    return "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(f(n) for n in list(pos) + list(rot))


def absolute(by, name):
    x = y = z = 0.0
    p = by[name]
    while p:
        x, y, z = x + p["pos"][0], y + p["pos"][1], z + p["pos"][2]
        p = by.get(p["parent"])
    return x, y, z


def java(data):
    parts = data["parts"]
    by = {p["name"]: p for p in parts}
    for name in CUTS:
        assert name in by, name
    joints = {name: absolute(by, name) for name in CUTS}
    lines = []
    holders = {}
    for name, player_part in CUTS.items():
        var = "slender_" + player_part
        holders[name] = var
        lines.append('\t\tPartDefinition %s = root.getChild("%s").addOrReplaceChild("slender", CubeListBuilder.create(), PartPose.ZERO.withScale(WebSlender.SCALE));'
                     % (var, player_part))
    for p in parts:
        name = p["name"]
        if name in CUTS:
            # the subtree's top sits on its holder: at the joint itself (the hips: the body's holder is at the neck)
            pos = (0.0, 0.0, 0.0) if name != "hips" else (0.0, joints["hips"][1] - neck(joints), 0.0)
            if name == "head":
                pos = (0.0, 0.0, joints["head"][2])
            lines.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", %s, %s);' % (name, holders[name], name, cube_chain(p["cubes"]), pose(pos, p["rot"])))
        else:
            lines.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", %s, %s);' % (name, p["parent"], name, cube_chain(p["cubes"]), pose(p["pos"], p["rot"])))
    mouth, eyes = data["anchors"]["mouth"]["at"], data["anchors"]["eyes"]["at"]
    return """package com.jaymospowers.client.power.web;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;

/**
 * GENERATED by {@code tools/gen_web_slender.py}: don't edit by hand. The red strain's slender body (the red form of Andy's
 * own Symbiote mod), cut at the player model's joints: under each of the player model's head, body, arms and legs a
 * holder part ({@code slender}) holds the original's parts for it, in the original's units, UV-mapped onto
 * {@code web_symbiote_slender.png}. the holders are drawn {@link WebSlender#SCALE} of their size, and WebSlender moves the joints to the original's.
 */
final class WebSlenderMesh {
	static final int TEX_W = %d, TEX_H = %d;
	/**
	 * The original's joints (its units, y down, its feet on {@code GROUND}): the neck (the head's pivot, and the top of the
	 * body's holder), the shoulders, the hips; and how far forward the head sits on the neck.
	 */
	static final float GROUND = %s, NECK_Y = %s, HEAD_Z = %s, SHOULDER_X = %s, SHOULDER_Y = %s, HIP_X = %s, HIP_Y = %s;
	/** The mouth and the eyes in the head's space. */
	static final float MOUTH_X = %s, MOUTH_Y = %s, MOUTH_Z = %s;
	static final float EYES_X = %s, EYES_Y = %s, EYES_Z = %s;

	private WebSlenderMesh() {
	}

	static LayerDefinition create() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		PartDefinition root = mesh.getRoot();
		// (the player model's own cubes are dropped: only the holders' parts are drawn)
		for (String part : new String[] {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
			root.clearChild(part).clearRecursively();
		}
%s
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}
}
""" % (data["texture"][0], data["texture"][1], f(GROUND), f(neck(joints)), f(joints["head"][2]), f(-joints["right_arm"][0]), f(joints["right_arm"][1]),
       f(-joints["right_leg"][0]), f(joints["right_leg"][1]), f(mouth[0]), f(mouth[1]), f(mouth[2]), f(eyes[0]), f(eyes[1]), f(eyes[2]), "\n".join(lines))


def neck(joints):
    return joints["head"][1]


def main():
    data = json.loads((DATA / "slender_geometry.json").read_text(encoding="utf-8"))
    JAVA.write_text(java(data), encoding="utf-8", newline="\n")
    Image.open(DATA / "slender_source.png").convert("RGBA").save(ENTITY / "web_symbiote_slender.png", optimize=True)
    Image.open(DATA / "slender_source_glow.png").convert("RGBA").save(ENTITY / "web_symbiote_slender_glow.png", optimize=True)
    print("slender body written:", len(data["parts"]), "parts")


if __name__ == "__main__":
    main()
