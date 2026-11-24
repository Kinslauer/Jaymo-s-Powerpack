#!/usr/bin/env python3
"""Ports the boxes of a Mo' Creatures 1.10.2 model (GPL-3, see licenses/mocreatures) to 26.3 Java.

    python3 -I port_model.py <MoCModelX.java> [--only A,B,C] [--skip A,B] [--animation]

Reads the model's constructor (ModelRenderer boxes, rotation points, rotations, mirrors, children) and prints, to stdout,
the body of a 26.3 `createBodyLayer()` (the part tree) and a `lookup` block (each part found by its MoC name), so the
hand-written model class keeps the model's boxes exactly as they were. With --animation it also prints the model's
setRotationAngles body translated line by line (rotateAngleN -> nRot, rotationPointN -> x/y/z, MathHelper -> Mth), to be
tidied by hand.

The tool is ours; what it reads (and so what it prints) is DrZhark's. Not part of the build.
"""
import re
import sys


def jf(expr, radian=57.29578):
    """A Java float expression from the model's source: numbers, 'F' suffixes, '/ this.radianF' and such, as Python."""
    e = expr.strip().replace("this.radianF", "RAD").replace("radianF", "RAD")
    e = re.sub(r"(\d+\.?\d*)[Ff]\b", r"\1", e)
    e = re.sub(r"\(float\)\s*", "", e)
    e = e.replace("Math.PI", "3.141592653589793")
    return eval(e, {"RAD": radian, "__builtins__": {}})


def fmt(v):
    s = repr(round(float(v), 7))
    if s.endswith(".0"):
        s = s[:-2]
    return s + "F"


def args(text):
    out, depth, cur = [], 0, ""
    for c in text:
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        if c == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += c
    if cur.strip():
        out.append(cur.strip())
    return out


class Part:
    def __init__(self, name, u=0, v=0):
        self.name, self.u, self.v = name, u, v
        self.boxes = []  # (u, v, mirror, x, y, z, w, h, d, scale)
        self.pos = [0.0, 0.0, 0.0]
        self.rot = [0.0, 0.0, 0.0]
        self.mirror = False
        self.parent = None
        self.children = []


def parse(path, radian=57.29578):
    src = open(path, encoding="utf-8").read()
    ctor = re.search(r"public\s+\w+\(\)\s*\{", src)
    start = ctor.end()
    depth, i = 1, start
    while depth:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
        i += 1
    body = src[start:i - 1]
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r"//[^\n]*", "", body)
    tex = [64, 32]
    parts = {}
    order = []
    for stmt in [s.strip() for s in body.split(";")]:
        if not stmt:
            continue
        m = re.match(r"this\.textureWidth\s*=\s*(\d+)", stmt)
        if m:
            tex[0] = int(m.group(1))
            continue
        m = re.match(r"this\.textureHeight\s*=\s*(\d+)", stmt)
        if m:
            tex[1] = int(m.group(1))
            continue
        m = re.match(r"this\.(\w+)\s*=\s*new ModelRenderer\(this(?:,\s*([^)]*))?\)$", stmt)
        if m:
            a = [int(x) for x in m.group(2).split(",")] if m.group(2) else [0, 0]
            p = Part(m.group(1), a[0], a[1])
            parts[p.name] = p
            order.append(p.name)
            continue
        m = re.match(r"this\.(\w+)\.addBox\((.*)\)$", stmt, re.S)
        if m and m.group(1) in parts:
            a = args(m.group(2))
            p = parts[m.group(1)]
            vals = [jf(x, radian) for x in a]
            if len(vals) == 7:
                p.boxes.append((p.u, p.v, p.mirror, *vals[:6], vals[6]))
            else:
                p.boxes.append((p.u, p.v, p.mirror, *vals[:6], 0.0))
            continue
        m = re.match(r"this\.(\w+)\.setRotationPoint\((.*)\)$", stmt)
        if m and m.group(1) in parts:
            parts[m.group(1)].pos = [jf(x, radian) for x in args(m.group(2))]
            continue
        m = re.match(r"setRotation(G?)\(this\.(\w+),(.*)\)$", stmt)
        if m and m.group(2) in parts:
            r = [jf(x, radian) for x in args(m.group(3))]
            if m.group(1):
                r = [x / radian for x in r]
            parts[m.group(2)].rot = r
            continue
        m = re.match(r"this\.(\w+)\.rotateAngle([XYZ])\s*=\s*(.*)$", stmt)
        if m and m.group(1) in parts:
            parts[m.group(1)].rot["XYZ".index(m.group(2))] = jf(m.group(3), radian)
            continue
        m = re.match(r"this\.(\w+)\.mirror\s*=\s*(true|false)$", stmt)
        if m and m.group(1) in parts:
            parts[m.group(1)].mirror = m.group(2) == "true"
            continue
        m = re.match(r"this\.(\w+)\.setTextureOffset\((.*)\)$", stmt)
        if m and m.group(1) in parts:
            a = [int(x) for x in args(m.group(2))]
            parts[m.group(1)].u, parts[m.group(1)].v = a
            continue
        m = re.match(r"this\.(\w+)\.addChild\(this\.(\w+)\)$", stmt)
        if m and m.group(1) in parts and m.group(2) in parts:
            parts[m.group(2)].parent = m.group(1)
            parts[m.group(1)].children.append(m.group(2))
            continue
        if stmt.startswith("this.") and ".setTextureSize" in stmt:
            continue
    return tex, parts, order


def java_name(n):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", n).lower().replace("__", "_")


def emit(tex, parts, order, only=None, skip=None):
    skip = skip or set()

    def keep(n):
        return (only is None or n in only) and n not in skip

    lines = ["\t\tMeshDefinition mesh = new MeshDefinition();", "\t\tPartDefinition root = mesh.getRoot();"]

    def part(n, parent_var, depth):
        p = parts[n]
        v = "p_" + n
        cube = "CubeListBuilder.create()"
        mir = None
        for (u, vv, mirror, x, y, z, w, h, d, s) in p.boxes:
            cube += ".texOffs(%d, %d)" % (u, vv)
            if mirror != mir:
                cube += ".mirror(%s)" % ("true" if mirror else "false")
                mir = mirror
            if s:
                cube += ".addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d), fmt(s))
            else:
                cube += ".addBox(%s, %s, %s, %s, %s, %s)" % (fmt(x), fmt(y), fmt(z), fmt(w), fmt(h), fmt(d))
        pose = ("PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(fmt(t) for t in p.pos + p.rot)
                if any(p.rot) else "PartPose.offset(%s, %s, %s)" % tuple(fmt(t) for t in p.pos))
        lines.append("\t\tPartDefinition %s = %s.addOrReplaceChild(\"%s\", %s, %s);" % (v, parent_var, n, cube, pose))
        for c in p.children:
            if keep(c):
                part(c, v, depth + 1)

    for n in order:
        if keep(n) and parts[n].parent is None:
            part(n, "root", 0)
    lines.append("\t\treturn LayerDefinition.create(mesh, %d, %d);" % tuple(tex))

    def path(n):
        chain = []
        while n is not None:
            chain.append(n)
            n = parts[n].parent
        return list(reversed(chain))

    look = []
    for n in order:
        if keep(n):
            expr = "root"
            for q in path(n):
                expr += ".getChild(\"%s\")" % q
            look.append("\t\tthis.%s = %s;" % (n, expr))
    return lines, look


def animation(path):
    src = open(path, encoding="utf-8").read()
    m = re.search(r"public void setRotationAngles\([^)]*\)\s*\{", src)
    if not m:
        return ""
    i, depth = m.end(), 1
    while depth:
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
        i += 1
    body = src[m.end():i - 1]
    body = re.sub(r"(\d+\.?\d*)F\b", r"\1F", body)
    body = body.replace("this.radianF", "RAD").replace("radianF", "RAD").replace("MathHelper.", "Mth.")
    body = re.sub(r"this\.(\w+)\.rotateAngleX", r"this.\1.xRot", body)
    body = re.sub(r"this\.(\w+)\.rotateAngleY", r"this.\1.yRot", body)
    body = re.sub(r"this\.(\w+)\.rotateAngleZ", r"this.\1.zRot", body)
    body = re.sub(r"this\.(\w+)\.rotationPointX", r"this.\1.x", body)
    body = re.sub(r"this\.(\w+)\.rotationPointY", r"this.\1.y", body)
    body = re.sub(r"this\.(\w+)\.rotationPointZ", r"this.\1.z", body)
    body = re.sub(r"this\.(\w+)\.isHidden\s*=\s*(\w+)", r"this.\1.visible = !\2", body)
    return body


def java_class(path, name, state, only=None, skip=None, flags=""):
    """A whole 26.3 model class: fields, lookups, the part tree, and the animation to tidy by hand."""
    tex, parts, order = parse(path)
    lines, look = emit(tex, parts, order, only, skip)
    names = [n for n in order if (only is None or n in only) and (skip is None or n not in skip)]
    out = []
    out.append("package com.jaymoskingdoms.client.monsters;\n")
    out.append("import net.minecraft.client.model.EntityModel;\nimport net.minecraft.client.model.geom.ModelPart;\n"
               "import net.minecraft.client.model.geom.PartPose;\nimport net.minecraft.client.model.geom.builders.CubeDeformation;\n"
               "import net.minecraft.client.model.geom.builders.CubeListBuilder;\nimport net.minecraft.client.model.geom.builders.LayerDefinition;\n"
               "import net.minecraft.client.model.geom.builders.MeshDefinition;\nimport net.minecraft.client.model.geom.builders.PartDefinition;\n"
               "import net.minecraft.util.Mth;\n")
    out.append("/**\n * TODO doc: from %s of DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}), ported to 26.3.\n */" % path.split("/")[-1][:-5])
    out.append("final class %s extends EntityModel<%s> {" % (name, state))
    out.append("\tprivate static final float RAD = 57.29578F;")
    out.append("\tprivate final ModelPart " + ", ".join(names) + ";")
    out.append(flags)
    out.append("\n\t%s(ModelPart root) {\n\t\tsuper(root);\n%s\n\t}\n" % (name, "\n".join(look)))
    out.append("\tstatic LayerDefinition createBodyLayer() {\n%s\n\t}\n" % "\n".join(lines))
    out.append("\t@Override\n\tpublic void setupAnim(%s state) {\n\t\tsuper.setupAnim(state);\n\t\t// TODO set the flags from the state\n"
               "\t\trig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);\n\t}\n" % state)
    out.append("\tprivate void rig(float f, float f1, float f2, float f3, float f4) {\n%s\n\t}\n}" % animation(path))
    return "\n".join(out)


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__)
        return
    only = skip = None
    anim = "--animation" in a
    for i, x in enumerate(a):
        if x == "--only":
            only = set(a[i + 1].split(","))
        if x == "--skip":
            skip = set(a[i + 1].split(","))
    if "--java" in a:
        i = a.index("--java")
        print(java_class(a[0], a[i + 1], a[i + 2], only, skip))
        return
    tex, parts, order = parse(a[0])
    lines, look = emit(tex, parts, order, only, skip)
    print("// ---- createBodyLayer (texture %dx%d, %d parts)" % (tex[0], tex[1], len([n for n in order if (only is None or n in only) and (skip is None or n not in skip)])))
    print("\n".join(lines))
    print("// ---- lookup")
    print("\n".join(look))
    if anim:
        print("// ---- setRotationAngles")
        print(animation(a[0]))


if __name__ == "__main__":
    main()
