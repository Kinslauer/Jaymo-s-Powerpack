"""Draws a default player model in one of our freerun poses, for checking a pose before it goes in game.

    python3 -I tools/pose_preview.py OUT_DIR

A small painter's-algorithm box renderer that mirrors vanilla's HumanoidModel (the parts' pivots and boxes, xRot/yRot/zRot
applied Z-Y-X as ModelPart does) and our KneeBend (a limb cut in two at its joint, the lower half swung about the cut),
under the transform LivingEntityRenderer gives a model (feet at the origin, +X the body's right, +Y up, +Z behind it,
then model pixels: x and y flipped, /16, 1.501 up). The poses here are kept in step with the Java poses by hand
(client/power/web/WebParkourPose's climb, WebWallRunPose, client/freerun/UpRunPose): same knobs, same formulas.
It writes contact sheets of each move through a cycle, seen from the side and from behind, with the wall drawn in.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

SKIN, SHIRT, PANTS, HAIR = (214, 170, 130), (60, 150, 160), (60, 60, 140), (70, 45, 30)
WALL = (150, 150, 150)


def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Part:
    def __init__(self, pivot, box, colour, joint=None):
        self.pivot = np.array(pivot, float)
        self.box = box  # (x0, y0, z0, w, h, d)
        self.colour = colour
        self.x = self.y = self.z = 0.0
        self.bend = 0.0
        self.joint = joint
        self.sideways = False
        self.pre = np.eye(3)

    def set(self, x, y=0.0, z=0.0):
        self.x, self.y, self.z = x, y, z


def model():
    return {
        "head": Part((0, 0, 0), (-4, -8, -4, 8, 8, 8), SKIN),
        "body": Part((0, 0, 0), (-4, 0, -2, 8, 12, 4), SHIRT),
        "rightArm": Part((-5, 2, 0), (-3, -2, -2, 4, 12, 4), SKIN, 4.0),
        "leftArm": Part((5, 2, 0), (-1, -2, -2, 4, 12, 4), SKIN, 4.0),
        "rightLeg": Part((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), PANTS, 6.0),
        "leftLeg": Part((1.9, 12, 0), (-2, 0, -2, 4, 12, 4), PANTS, 6.0),
    }


def hip_bend(parts, pitch, hip=12.0):
    """PoseBlend.bend: the upper body (body, head, arms) bent forward by pitch about the hips; the legs stay planted."""
    q = rx(pitch)
    for name in ("body", "head", "rightArm", "leftArm"):
        p = parts[name]
        p.pivot = q @ (p.pivot - np.array([0, hip, 0])) + np.array([0, hip, 0])
        p.pre = q @ p.pre


def cube(x0, y0, z0, w, h, d):
    xs, ys, zs = (x0, x0 + w), (y0, y0 + h), (z0, z0 + d)
    v = np.array([[xs[i & 1], ys[(i >> 1) & 1], zs[(i >> 2) & 1]] for i in range(8)], float)
    faces = [(0, 2, 6, 4), (1, 3, 7, 5), (0, 1, 5, 4), (2, 3, 7, 6), (0, 1, 3, 2), (4, 5, 7, 6)]
    return v, faces


def part_quads(p):
    """Model-space quads (4x3 arrays, colour) of one part, posed, bent at its joint if it has a bend."""
    rot = p.pre @ rz(p.z) @ ry(p.y) @ rx(p.x)
    x0, y0, z0, w, h, d = p.box
    out = []
    pieces = []
    if p.joint is not None and abs(p.bend) > 1e-4:
        j = p.joint
        pieces.append((cube(x0, y0, z0, w, j - y0, d), None))
        pieces.append((cube(x0, j, z0, w, y0 + h - j, d), j))
    else:
        pieces.append((cube(x0, y0, z0, w, h, d), None))
    for (v, faces), j in pieces:
        if j is not None:
            # the lower half swung about the cut: + swings it back (+z), as KneeBend does; sideways about z
            local = v - np.array([0, j, 0])
            # + swings the lower half back (+z) about the cut, as KneeBend does; sideways, toward -x
            r = rz(p.bend) if p.sideways else rx(p.bend)
            v = local @ r.T + np.array([0, j, 0])
        v = v @ rot.T + p.pivot
        for f in faces:
            out.append((v[list(f)], p.colour))
    return out


def to_frame(q):
    """Model pixels to the render frame (blocks; +X right, +Y up, +Z behind)."""
    return np.stack([-q[:, 0] / 16.0, -q[:, 1] / 16.0 + 1.501, q[:, 2] / 16.0], axis=1)


def render(parts, root, walls, yaw, pitch=12.0, size=360, scale=150.0, centre=(0.0, 1.0, 0.0), label=""):
    """root: 4x4 matrix (frame → world) for the body; walls: list of (point, normal) planes drawn as grey panels."""
    quads = []
    for p in parts.values():
        for q, c in part_quads(p):
            f = to_frame(q)
            w = (np.c_[f, np.ones(4)] @ root.T)[:, :3]
            quads.append((w, c))
    for (pt, n, span) in walls:
        pt, n = np.array(pt, float), np.array(n, float)
        a = np.array([0, 1.0, 0]) if abs(n[1]) < 0.9 else np.array([1.0, 0, 0])
        u = np.cross(n, a)
        u /= np.linalg.norm(u)
        vv = np.cross(n, u)
        corners = [pt + u * s1 * span + vv * s2 * span for s1, s2 in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        quads.append((np.array(corners), WALL))
    cam = rx(math.radians(pitch)) @ ry(math.radians(yaw))
    img = Image.new("RGB", (size, size), (235, 238, 232))
    dr = ImageDraw.Draw(img, "RGBA")
    light = np.array([0.4, 0.8, 0.5])
    light /= np.linalg.norm(light)
    items = []
    for w, c in quads:
        v = (w - np.array(centre)) @ cam.T
        n = np.cross(w[1] - w[0], w[2] - w[0])
        nn = np.linalg.norm(n)
        shade = 0.6 + 0.4 * abs(np.dot(n / nn, light)) if nn > 1e-9 else 0.8
        col = tuple(int(ch * shade) for ch in c)
        alpha = 120 if c == WALL else 255
        items.append((v[:, 2].mean(), v, col + (alpha,)))
    items.sort(key=lambda t: t[0])
    for _, v, col in items:
        pts = [(size / 2 + p[0] * scale, size / 2 - p[1] * scale) for p in v]
        dr.polygon(pts, fill=col, outline=(30, 30, 30, 160))
    dr.text((6, 6), label, fill=(0, 0, 0))
    return img


def mat(*ops):
    m = np.eye(4)
    for op in ops:
        m = m @ op
    return m


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def R(axis, deg):
    m = np.eye(4)
    r = {"x": rx, "y": ry, "z": rz}[axis](math.radians(deg))
    m[:3, :3] = r
    return m


def sheet(frames, cols):
    w, h = frames[0].size
    rows = (len(frames) + cols - 1) // cols
    out = Image.new("RGB", (w * cols, h * rows), (255, 255, 255))
    for i, f in enumerate(frames):
        out.paste(f, ((i % cols) * w, (i // cols) * h))
    return out
