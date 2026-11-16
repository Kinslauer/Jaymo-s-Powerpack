"""Longsword trial, the Blender route: the same three Still Water moves on the same blocky player, keyed in Blender.

    blender -b --python tools/combat_trial/blender_route.py
    blender -b --python tools/combat_trial/blender_route.py -- --preview   # also render the comparison sheet

The rig is the player's arms as Blender bones (upper arm, forearm folding only at the elbow, a fist out to the grip),
built in the game's model space turned to stand up in Blender (MC (x, y, z) -> Blender (x, z, -y)). The right arm is keyed
with the same poses and timing as the Blockbench route (blockbench_route.POSES / MOVES), so the two differ only in what
each tool does with them:
- the left hand is held on the hilt by inverse kinematics at every frame, not set by hand at the keys;
- the keys ease through Blender's Bezier curves, and the cut speeds up into the hit (quadratic ease-in) instead of
  arriving at an even speed.
Each tick is then read back off the posed bones and written as the game's player_animations, into the built-in resource
pack "longsword_blender" (switch it on in Resource Packs to see these instead of the Blockbench ones). The rig and its
keys are saved to art/combat_trial/longsword_blender.blend for editing in Blender.

--preview renders each move's key frames from both routes side by side (art/combat_trial/longsword_trial.png).
"""
import math
import os
import sys

import bpy
from mathutils import Euler, Matrix, Vector

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import body  # noqa: E402
import emote  # noqa: E402
from blockbench_route import MOVES, POSES  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
BLEND = os.path.join(ROOT, "art", "combat_trial", "longsword_blender.blend")
PACK = os.path.join(ROOT, "src", "main", "resources", "resourcepacks", "longsword_blender")
OUT = os.path.join(PACK, "assets", "jaymospowers", "player_animations")
BB_OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "player_animations")
SHEET = os.path.join(ROOT, "art", "combat_trial", "longsword_trial.png")

C = Matrix(((1, 0, 0), (0, 0, 1), (0, -1, 0)))  # game model space -> Blender
CT = C.transposed()
S = 0.1  # Blender units per pixel


def bv(p):
    """A game model-space point (pixels) in Blender."""
    return (C @ Vector(p)) * S


# --- the rig --------------------------------------------------------------------------------------------------------

def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.fps = 20
    return scene


def build_rig():
    arm_data = bpy.data.armatures.new("player")
    rig = bpy.data.objects.new("player", arm_data)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones

    def bone(name, head, tail, parent=None, connect=False):
        b = eb.new(name)
        b.head, b.tail = bv(head), bv(tail)
        b.align_roll(Vector((0, 1, 0)))  # local X along the game's X (the elbow's hinge)
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
        return b

    for side, sx in (("right", -1), ("left", 1)):
        px, py, pz = body.PIVOT[side]
        gx, gy, gz = body.GRIP[side]
        bone(side + "_arm", (px, py, pz), (px, py + body.ELBOW, pz))
        bone(side + "_forearm", (px, py + body.ELBOW, pz), (px, py + 10, pz), side + "_arm", True)
        bone(side + "_fist", (px, py + 10, pz), (px + gx, py + gy, pz + gz), side + "_forearm", True)
    # the hilt, where the left fist's grip belongs: up the handle from the right fist's grip
    grip = Vector(body.PIVOT["right"]) + Vector(body.GRIP["right"])
    hilt = grip - Vector(body.BLADE) * body.HILT
    bone("hilt", tuple(hilt), tuple(hilt - Vector(body.BLADE)), "right_fist")
    bpy.ops.object.mode_set(mode="POSE")

    pb = rig.pose.bones
    for b in pb:
        b.rotation_mode = "XYZ"
    for side in ("right", "left"):
        fore = pb[side + "_forearm"]
        fore.lock_ik_y = fore.lock_ik_z = True
        fore.use_ik_limit_x = True
        fore.ik_min_x, fore.ik_max_x = -2.4, 0.0  # folds forward only
        fist = pb[side + "_fist"]
        fist.lock_ik_x = fist.lock_ik_y = fist.lock_ik_z = True
    ik = pb["left_fist"].constraints.new("IK")
    ik.target, ik.subtarget = rig, "hilt"
    ik.chain_count = 3
    ik.iterations = 500
    bpy.ops.object.mode_set(mode="OBJECT")
    return rig


def rest_rot(rig, name):
    return rig.data.bones[name].matrix_local.to_3x3()


def set_fk(rig, side, pitch, yaw, roll, bend):
    """Poses an arm from the game's angles (radians)."""
    pb = rig.pose.bones
    rest = rest_rot(rig, side + "_arm")
    world = C @ Euler((pitch, yaw, roll), "XYZ").to_matrix() @ CT
    local = (rest.inverted() @ world @ rest).to_euler("XYZ", pb[side + "_arm"].rotation_euler)
    pb[side + "_arm"].rotation_euler = local
    pb[side + "_forearm"].rotation_euler = Euler((-bend, 0, 0), "XYZ")


def read_arm(rig, side):
    """The game's angles of an arm as posed (after IK), and its elbow's fold."""
    pb = rig.pose.bones
    up = pb[side + "_arm"].matrix.to_3x3() @ rest_rot(rig, side + "_arm").inverted()
    fore = pb[side + "_forearm"].matrix.to_3x3() @ rest_rot(rig, side + "_forearm").inverted()
    m = CT @ up @ C
    e = m.to_euler("XYZ")
    rel = CT @ (up.inverted() @ fore) @ C
    bend = -math.atan2(rel[2][1], rel[1][1])
    return {"pitch": e.x, "yaw": e.y, "roll": e.z, "bend": bend}


# --- the keys -------------------------------------------------------------------------------------------------------

def key_moves(rig):
    """One action per move: the right arm keyed at each of the move's poses, Blender's way."""
    actions = {}
    for name, (desc, loop, begin, end, frames) in MOVES.items():
        action = bpy.data.actions.new(name)
        action.use_fake_user = True
        rig.animation_data_create().action = action
        for tick, pose, interp in frames:
            (rp, ry, rr, rb), _ = POSES[pose]
            set_fk(rig, "right", math.radians(rp), math.radians(ry), math.radians(rr), math.radians(rb))
            for b in ("right_arm", "right_forearm"):
                rig.pose.bones[b].keyframe_insert("rotation_euler", frame=tick)
        # the stretch into each strike speeds up into the hit
        strikes = {frames[i - 1][0] for i, f in enumerate(frames) if f[2] == "linear"}
        for fc in fcurves(action):
            for kp in fc.keyframe_points:
                if round(kp.co.x) in strikes:
                    kp.interpolation = "QUAD"
                    kp.easing = "EASE_IN"
        actions[name] = action
    return actions


def fcurves(action):
    """An action's curves (Blender 4.4+ keeps them in layers and slots)."""
    if hasattr(action, "fcurves") and len(action.fcurves):
        return list(action.fcurves)
    out = []
    for layer in getattr(action, "layers", []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                out.extend(bag.fcurves)
    return out


def bake(rig, actions):
    scene = bpy.context.scene
    for name, action in actions.items():
        desc, loop, begin, end, frames = MOVES[name]
        rig.animation_data.action = action
        if hasattr(rig.animation_data, "action_slot") and action.slots:
            rig.animation_data.action_slot = action.slots[0]
        samples = []
        for tick in range(frames[-1][0] + 1):
            scene.frame_set(tick)
            bpy.context.view_layer.update()
            samples.append({"rightArm": read_arm(rig, "right"), "leftArm": read_arm(rig, "left")})
        emote.write(os.path.join(OUT, name + ".json"), name, desc + " (Blender route)", samples, begin, end, loop=loop)
        report(name, samples)


def report(name, samples):
    """How far the left fist strays from the hilt, worst tick, for this route and the Blockbench one."""
    import json
    worst = max(miss(s) for s in samples)
    with open(os.path.join(BB_OUT, name + ".json")) as f:
        bb = json.load(f)
    bb_worst = max(miss(s) for s in emote_samples(bb))
    print(f"{name}: left fist off the hilt at worst {worst:.1f} px (Blender), {bb_worst:.1f} px (Blockbench)")


def miss(s):
    want = body.second_grip(s["rightArm"])
    got, _ = body.hand("left", s["leftArm"])
    return math.dist(want, got)


def emote_samples(anim):
    """Every tick of an Emotecraft file we wrote (straight pieces between its moves) as samples."""
    e = anim["emote"]
    tracks = {}
    for m in e["moves"]:
        for part, chans in m.items():
            if part in ("rightArm", "leftArm"):
                for ch, v in chans.items():
                    tracks.setdefault((part, ch), []).append((m["tick"], v))
    out = []
    for t in range(e["stopTick"] + 1):
        s = {"rightArm": {}, "leftArm": {}}
        for (part, ch), keys in tracks.items():
            keys.sort()
            v = keys[-1][1] if t >= keys[-1][0] else keys[0][1]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    v = v0 + (v1 - v0) * ((t - t0) / (t1 - t0) if t1 > t0 else 1)
                    break
            s[part][ch] = v
        out.append(s)
    return out


def write_pack():
    os.makedirs(PACK, exist_ok=True)
    with open(os.path.join(PACK, "pack.mcmeta"), "w", newline="\n") as f:
        f.write('{\n  "pack": {\n    "description": "Jaymo\'s Powers: the longsword trial\'s Blender moves",\n'
                '    "min_format": 97,\n    "max_format": 97\n  }\n}\n')


# --- the comparison sheet -------------------------------------------------------------------------------------------

def preview(rig, actions):
    import json
    scene = bpy.context.scene
    add_body(rig)
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = 6.2
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    cam.location = (-4.2, -5.2, 1.4)
    cam.rotation_euler = Euler((math.radians(80), 0, math.radians(-38)), "XYZ")
    scene.camera = cam
    # Cycles on the CPU (there may be no GPU); each box coloured by its object colour
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 16
    scene.render.resolution_x, scene.render.resolution_y = 240, 270
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.9, 0.9, 0.88, 1)
    scene.world = world
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3
    sun.rotation_euler = Euler((math.radians(50), 0, math.radians(-30)), "XYZ")
    scene.collection.objects.link(sun)
    mat = bpy.data.materials.new("box")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    info = nodes.new("ShaderNodeObjectInfo")
    mat.node_tree.links.new(info.outputs["Color"], nodes["Principled BSDF"].inputs["Base Color"])
    for ob in scene.objects:
        if ob.type == "MESH":
            ob.data.materials.append(mat)
    tiles = []
    ik = rig.pose.bones["left_fist"].constraints[0]
    for name, (desc, loop, begin, end, frames) in MOVES.items():
        ticks = [t for t, _, _ in frames if t <= end] + ([] if loop else [round(end / 2)])
        ticks = sorted(set(ticks))
        with open(os.path.join(BB_OUT, name + ".json")) as f:
            bb = emote_samples(json.load(f))
        with open(os.path.join(OUT, name + ".json")) as f:
            bl = emote_samples(json.load(f))
        rig.animation_data.action = None
        ik.mute = True
        for route, samples in (("blockbench", bb), ("blender", bl)):
            for t in ticks:
                s = samples[t]
                for side, part in (("right", "rightArm"), ("left", "leftArm")):
                    a = s[part]
                    set_fk(rig, side, a.get("pitch", 0), a.get("yaw", 0), a.get("roll", 0), a.get("bend", 0))
                bpy.context.view_layer.update()
                path = os.path.join(bpy.app.tempdir, f"{name}_{route}_{t}.png")
                scene.render.filepath = path
                bpy.ops.render.render(write_still=True)
                tiles.append((name, route, t, path))
    ik.mute = False
    sheet(tiles)


def add_body(rig):
    def box(name, a, b, bone, color, turn=None, origin=(0, 0, 0)):
        """A box between game model-space corners a and b (turned by `turn` about `origin`), following `bone`."""
        corners = [Vector((x, y, z)) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
        if turn is not None:
            corners = [Vector(origin) + turn @ c for c in corners]
        mesh = bpy.data.meshes.new(name)
        f = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
        mesh.from_pydata([bv(tuple(c)) for c in corners], [], f)
        ob = bpy.data.objects.new(name, mesh)
        ob.color = color
        bpy.context.scene.collection.objects.link(ob)
        if bone:
            rest = rig.data.bones[bone]
            ob.parent, ob.parent_type, ob.parent_bone = rig, "BONE", bone
            ob.matrix_parent_inverse = (rig.matrix_world @ rest.matrix_local @ Matrix.Translation((0, rest.length, 0))).inverted()
        return ob

    skin, shirt, trousers, steel = (0.85, 0.66, 0.52, 1), (0.2, 0.55, 0.6, 1), (0.25, 0.25, 0.55, 1), (0.8, 0.8, 0.85, 1)
    box("head", (-4, -8, -4), (4, 0, 4), None, skin)
    box("torso", (-4, 0, -2), (4, 12, 2), None, shirt)
    box("right_leg", (-3.9, 12, -2), (0.1, 24, 2), None, trousers)
    box("left_leg", (-0.1, 12, -2), (3.9, 24, 2), None, trousers)
    for side, x0 in (("right", -8), ("left", 4)):
        box(side + "_upper", (x0, 0, -2), (x0 + 4, 6, 2), side + "_arm", shirt)
        box(side + "_lower", (x0, 6, -2), (x0 + 4, 12, 2), side + "_forearm", skin)
    # the sword in the right fist: blade and handle along BLADE from the grip (26 pixels ahead, 6 behind), and a crossguard
    grip = Vector(body.PIVOT["right"]) + Vector(body.GRIP["right"])
    turn = Matrix.Rotation(math.atan2(body.BLADE[1], -body.BLADE[2]), 3, "X")
    box("blade", (-0.5, -0.5, -26), (0.5, 0.5, 6), "right_fist", steel, turn, grip)
    box("guard", (-2.5, -0.5, -1), (2.5, 0.5, 0), "right_fist", steel, turn, grip)


def sheet(tiles):
    """Lays the renders out: a row per move and route, labelled."""
    import numpy as np
    rows = []
    for name in MOVES:
        for route in ("blockbench", "blender"):
            rows.append([t for t in tiles if t[0] == name and t[1] == route])
    w, h = 240, 270
    cols = max(len(r) for r in rows)
    out = np.ones((h * len(rows), w * cols, 4), dtype=np.float32)
    for ri, row in enumerate(rows):
        for ci, (_, _, _, path) in enumerate(row):
            img = bpy.data.images.load(path)
            px = np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)
            out[(len(rows) - 1 - ri) * h:(len(rows) - ri) * h, ci * w:(ci + 1) * w] = px
            bpy.data.images.remove(img)
    im = bpy.data.images.new("sheet", w * cols, h * len(rows), alpha=True)
    im.pixels = out.ravel()
    im.filepath_raw = SHEET
    im.file_format = "PNG"
    im.save()
    print("sheet rows:", [f"{r[0][0]} / {r[0][1]} ticks {[t[2] for t in r]}" for r in rows])


def main():
    fresh_scene()
    bpy.context.preferences.filepaths.save_version = 0
    rig = build_rig()
    actions = key_moves(rig)
    write_pack()
    bake(rig, actions)
    os.makedirs(os.path.dirname(BLEND), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=BLEND, compress=True)
    if "--preview" in sys.argv:
        preview(rig, actions)


main()
