"""Reads a Blockbench-exported Java model (createBodyLayer style): parts, boxes, UVs, poses."""
import re
NUM = r'(-?[\d.]+)F?'
def parse(path, method=None):
    src = open(path, encoding="utf-8").read()
    # pick the layer method body
    if method:
        i = src.index(method); src = src[i:]
    j = src.find("LayerDefinition.create(")
    tex = re.search(r'LayerDefinition\.create\(\w+,\s*(\d+),\s*(\d+)\)', src)
    body = src[:j] if j > 0 else src
    parts = {}; order = []
    stmts = re.split(r';\s*\n', body)
    for st in stmts:
        m = re.search(r'(?:PartDefinition\s+(\w+)\s*=\s*)?(\w+)\.addOrReplaceChild\("(\w+)",\s*(CubeListBuilder\.create\(\).*),\s*PartPose\.(\w+)\(([^()]*)\)\s*\)\s*$', st.strip(), re.S)
        if not m:
            continue
        var, parentvar, name, cubes, posekind, poseargs = m.groups()
        pose = [float(x.replace("F", "")) for x in poseargs.split(",")] if poseargs.strip() else []
        if posekind == "offset": pos, rot = pose[:3], [0, 0, 0]
        elif posekind == "offsetAndRotation": pos, rot = pose[:3], pose[3:6]
        elif posekind == "rotation": pos, rot = [0, 0, 0], pose[:3]
        else: pos, rot = [0, 0, 0], [0, 0, 0]
        boxes = []; u = v = 0; mirror = False
        for t in re.finditer(r'\.texOffs\((\d+),\s*(\d+)\)|\.mirror\((false)?\)|\.addBox\(' + r',\s*'.join([NUM] * 6) + r'(?:,\s*new CubeDeformation\(' + NUM + r'\))?\)', cubes):
            s = t.group(0)
            if s.startswith(".texOffs"): u, v = int(t.group(1)), int(t.group(2))
            elif s.startswith(".mirror"): mirror = t.group(3) is None
            else:
                g = [float(x) for x in t.groups()[3:9]]
                boxes.append(dict(u=u, v=v, x=g[0], y=g[1], z=g[2], w=g[3], h=g[4], d=g[5], grow=float(t.group(10) or 0), mirror=mirror))
        parts[name] = dict(name=name, var=var or name, parentvar=parentvar, pos=pos, rot=rot, boxes=boxes)
        order.append(name)
    byvar = {p["var"]: p["name"] for p in parts.values()}
    for p in parts.values():
        p["parent"] = byvar.get(p["parentvar"])
    return dict(parts=parts, order=order, tw=int(tex.group(1)), th=int(tex.group(2)))

def faces(b):
    """{face: (u, v, w, h)} texture rects of a box, vanilla layout."""
    u, v, w, h, d = b["u"], b["v"], int(round(b["w"])), int(round(b["h"])), int(round(b["d"]))
    return dict(top=(u + d, v, w, d), bottom=(u + d + w, v, w, d), west=(u, v + d, d, h), north=(u + d, v + d, w, h),
                east=(u + d + w, v + d, d, h), south=(u + d + w + d, v + d, w, h))
if __name__ == "__main__":
    import sys
    m = parse(sys.argv[1])
    print(m["tw"], m["th"], len(m["order"]), "parts", sum(len(p["boxes"]) for p in m["parts"].values()), "boxes")
    for n in m["order"]:
        p = m["parts"][n]; print(n, "<-", p["parent"], p["pos"], p["rot"], [(b["u"], b["v"], b["w"], b["h"], b["d"], b["mirror"]) for b in p["boxes"]])
