# Writes each spirit figure as a .bbmodel (Blockbench 4.x, bedrock entity, y up; x mirrored as Blockbench shows ModelParts).
import json, uuid, os, sys
from figures import FIG, NAMES
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
for k in range(5):
    slug = NAMES[k].lower().replace("the ", "").replace(" ", "_")
    elements = []
    groups = {}
    def group(name, origin):
        g = dict(name=name, origin=origin, rotation=[0, 0, 0], color=0, uuid=str(uuid.uuid4()), export=True, isOpen=True, visibility=True, children=[])
        groups[name] = g
        return g
    fig = group("figure", [0, 24, 0])
    skull = group("skull", [0, 24, 0])
    tail = group("tail", [0, 24 - FIG[k]["tail_y"], 0])
    for grp, off, g in (("torso", 0.0, fig), ("skull", 0.0, skull), ("tail", FIG[k]["tail_y"], tail)):
        for n, (cx, y, cz, w, h, d, c) in enumerate(FIG[k][grp]):
            yy = y + off
            u = str(uuid.uuid4())
            faces = {f: {"uv": [0, 0, 1, 1], "texture": None} for f in ("north", "east", "south", "west", "up", "down")}
            elements.append(dict(name=(c or "%s %d" % (grp, n)), type="cube", uuid=u, box_uv=False, rescale=False, locked=False,
                                 from_=None, color=k, origin=[0, 24 - off, 0],
                                 **{"from": [round(-(cx + w / 2), 3), round(24 - (yy + h), 3), round(cz - d / 2, 3)],
                                    "to": [round(-(cx - w / 2), 3), round(24 - yy, 3), round(cz + d / 2, 3)]}, faces=faces))
            elements[-1].pop("from_")
            g["children"].append(u)
    fig["children"] += [skull, tail]
    model = dict(meta=dict(format_version="4.10", model_format="bedrock", box_uv=False),
                 name="spirit_figure_" + slug, model_identifier="spirit_figure_" + slug,
                 visible_box=[2, 3, 0], resolution=dict(width=16, height=16),
                 elements=elements, outliner=[fig], textures=[])
    with open(os.path.join(out, "spirit_figure_%s.bbmodel" % slug), "w") as fh:
        json.dump(model, fh, indent=1)
    print(slug, len(elements))
