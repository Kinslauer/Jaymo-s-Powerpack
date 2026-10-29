"""(0.7.322) jsellier23's Mystical Deer (Blockbench Workshop, CC BY 4.0: credit in tools/horse_models/source/CREDITS.md)
made into a horse, the moonlit horse (tools/moonlit_horse.py): its antlers and wisteria taken off, its body, neck, head
and legs bulked, the body lengthened, the neck shortened and leaned forward, the cannons shortened, the snout longer,
the ears stood up, and a mane, forelock and long tail of silver hair added. Works on the Blockbench project itself
(free format, y up), so the result can be opened in Blockbench: horseify.py deer.bbmodel out.bbmodel [variant]"""
import base64, copy, io, json, random, sys, uuid

def index(data):
    gr = {g["uuid"]: g for g in data["groups"]}; el = {e["uuid"]: e for e in data["elements"]}
    nodes = {}
    def walk(n, parent):
        g = gr[n["uuid"]]; nodes[g["name"]] = (g, n, parent)
        for c in n.get("children", []):
            if isinstance(c, dict): walk(c, n)
    for n in data["outliner"]: walk(n, None)
    return gr, el, nodes

class Model:
    def __init__(self, data):
        self.d = data; self.gr, self.el, self.nodes = index(data)
    def elems(self, name, deep=False):
        g, n, _ = self.nodes[name]; out = []
        for c in n["children"]:
            if isinstance(c, str): out.append(self.el[c])
            elif deep: out += self.elems(self.gr[c["uuid"]]["name"], True)
        return out
    def groups(self, name):
        g, n, _ = self.nodes[name]; out = [g]
        for c in n["children"]:
            if isinstance(c, dict): out += self.groups(self.gr[c["uuid"]]["name"])
        return out
    def drop(self, name):
        g, n, parent = self.nodes[name]
        for e in self.elems(name, True): self.d["elements"].remove(e)
        for sub in self.groups(name): self.d["groups"].remove(sub)
        (parent["children"] if parent else self.d["outliner"]).remove(n)
        self.gr, self.el, self.nodes = index(self.d)
    def move(self, name, dx, dy, dz):
        """The group and all under it, moved."""
        d = (dx, dy, dz)
        for g in self.groups(name): g["origin"] = [g["origin"][k] + d[k] for k in range(3)]
        for e in self.elems(name, True):
            for key in ("from", "to", "origin"): e[key] = [e[key][k] + d[k] for k in range(3)]
    def thicken(self, name, sx, sy, sz, about=None, deep=False):
        """The group's cubes made bigger: about `about` (positions spread too) or each about its own middle."""
        s = (sx, sy, sz)
        for e in self.elems(name, deep):
            c = about or [(e["from"][k] + e["to"][k]) / 2 for k in range(3)]
            for key in ("from", "to"): e[key] = [round(c[k] + (e[key][k] - c[k]) * s[k], 4) for k in range(3)]
            if about: e["origin"] = [round(c[k] + (e["origin"][k] - c[k]) * s[k], 4) for k in range(3)]
    def scale(self, name, about, sx, sy, sz):
        """The group and all under it scaled about `about` (cubes and pivots)."""
        sc = (sx, sy, sz)
        f = lambda v: [round(about[k] + (v[k] - about[k]) * sc[k], 4) for k in range(3)]
        for g in self.groups(name): g["origin"] = f(g["origin"])
        for e in self.elems(name, True):
            for key in ("from", "to", "origin"): e[key] = f(e[key])
    def turn(self, name, rx=0, ry=0, rz=0):
        g = self.nodes[name][0]; r = g.get("rotation", [0, 0, 0]); g["rotation"] = [r[0] + rx, r[1] + ry, r[2] + rz]

def bounds(elems):
    lo = [min(e["from"][k] for e in elems) for k in range(3)]; hi = [max(e["to"][k] for e in elems) for k in range(3)]
    return lo, hi

def hair_texture(seed=7, base=(196, 198, 210)):
    """A 256x256 hair texture: streaks running down v."""
    from PIL import Image
    rnd = random.Random(seed); im = Image.new("RGBA", (256, 256)); px = im.load()
    for u in range(256):
        shade = rnd.uniform(0.7, 1.25); drift = rnd.uniform(-8, 8)
        for v in range(256):
            f = shade * (1 + 0.08 * ((v + drift) % 23 < 2)) * rnd.uniform(0.94, 1.06)
            px[u, v] = tuple(min(255, int(c * f)) for c in base) + (255,)
    buf = io.BytesIO(); im.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

def add_cube(m, group, name, frm, to, origin=None, rotation=None, tex=1):
    w, h, d = (abs(to[k] - frm[k]) for k in range(3)); S = 16
    side = lambda a, b: [0, 0, min(256, max(1, a * S)), min(256, max(1, b * S))]
    e = {"name": name, "box_uv": False, "render_order": "default", "locked": False, "export": True, "from": [round(v, 4) for v in frm],
         "to": [round(v, 4) for v in to], "autouv": 0, "color": 0, "origin": origin or [round((frm[k] + to[k]) / 2, 4) for k in range(3)],
         "faces": {"north": {"uv": side(w, h), "texture": tex}, "south": {"uv": side(w, h), "texture": tex},
                   "east": {"uv": side(d, h), "texture": tex}, "west": {"uv": side(d, h), "texture": tex},
                   "up": {"uv": side(w, d), "texture": tex}, "down": {"uv": side(w, d), "texture": tex}},
         "type": "cube", "uuid": str(uuid.uuid4())}
    if rotation: e["rotation"] = rotation
    m.d["elements"].append(e); m.nodes[group][1]["children"].append(e["uuid"]); m.el[e["uuid"]] = e
    return e

VARIANTS = {
    # name: body x/y/z, neck x/z, leg thickness, neck lean, head scale
    "riding": dict(body=(1.45, 1.3, 1.4), neck_len=0.72, neck=(1.6, 1.45), legs=1.45, lean=-22, head=(1.3, 1.2, 1.15), out=0.85),
    "draft": dict(body=(1.75, 1.5, 1.42), neck_len=0.68, neck=(2.0, 1.7), legs=1.8, lean=-30, head=(1.45, 1.35, 1.2), out=1.5),
}

def horse(data, v):
    m = Model(copy.deepcopy(data)); p = VARIANTS[v]
    for g in ("bois_g", "bois_d", "glycine_g", "glycine_d"): m.drop(g)
    bx, by, bz = p["body"]
    centre = [0, 21.5, 0]
    m.thicken("corps", bx, by, bz, about=centre)
    top = centre[1] + (25.4 - centre[1]) * by; front = -8.4 * bz
    m.move("cou", 0, top - 25.4 - 0.6, front + 8.4 + 0.6)
    m.move("queue", 0, (23.9 - centre[1]) * (by - 1), 8.2 * (bz - 1))
    nx, nz = p["neck"]
    for g in ("cou", "cou_haut"): m.thicken(g, nx, 1.0, nz)
    nl = p["neck_len"]; co = m.nodes["cou"][0]["origin"]; ho = m.nodes["cou_haut"][0]["origin"]; to = m.nodes["tete"][0]["origin"]
    m.thicken("cou", 1, nl, 1, about=co)
    m.move("cou_haut", 0, (co[1] - ho[1]) * (1 - nl), 0)  # starts where the shorter lower neck ends
    ho = m.nodes["cou_haut"][0]["origin"]
    for e in m.elems("cou_haut"):
        for key in ("from", "to"): e[key][1] = round(ho[1] + (e[key][1] - ho[1]) * nl, 4)
    m.move("tete", 0, (ho[1] - m.nodes["tete"][0]["origin"][1]) * (1 - nl), 0)
    m.turn("cou", rx=p["lean"]); m.turn("tete", rx=-p["lean"] * 0.6)
    hx, hy, hz = p["head"]; m.thicken("tete", hx, hy, hz, about=[0, 35.4, -5.3])
    m.thicken("tete", 1, 1, p.get("snout", 1.35), about=[0, 35.4, -6.0])
    for ear in ("oreille_g", "oreille_d"):
        s = -1 if ear.endswith("g") else 1; m.move(ear, s * (hx - 1) * 1.4, (hy - 1) * 2.2, 0)
        m.turn(ear, rz=s * 50, ry=s * 10)
        o = m.nodes[ear][0]["origin"]; m.scale(ear, o, 0.75, 0.9, 0.9)  # shorter: a deer's ears are long  # deer ears out sideways; a horse's stand up
    t = p["legs"]; ox = p["out"]; k = p.get("short", 0.65)
    drops = []
    for leg in ("patte_avg", "patte_avd", "patte_arg", "patte_ard"):
        s = -1 if leg.endswith("g") else 1
        m.move(leg, s * ox, 0, 0)
        for g in m.groups(leg): m.thicken(g["name"], t, 1.0, t)
        bas = leg + "_bas"; o = m.nodes[bas][0]["origin"]
        d = o[1] * (1 - k); drops.append(d)
        m.move(leg, 0, -d, 0); m.move(bas, 0, d, 0)
        m.scale(bas, [o[0], 0, o[2]], 1, k, 1)  # a shorter cannon; the rest of the leg comes down to it
    m.move("corps", 0, -sum(drops) / len(drops), 0)
    # the hind legs go back with the bigger rump
    for leg in ("patte_arg", "patte_ard"): m.move(leg, 0, 0, 8.2 * (bz - 1) * 0.85)
    for leg in ("patte_avg", "patte_avd"): m.move(leg, 0, 0, -8.4 * (bz - 1) * 0.85)
    hair(m, p)
    m.d["name"] = f"deer_horse_{v}"
    return m.d

def hair(m, p):
    """A mane down the back of the neck, a forelock, and a long tail in place of the deer's scut."""
    m.d["textures"].append({"name": "hair.png", "id": "1", "render_mode": "default", "uuid": str(uuid.uuid4()), "source": hair_texture(),
                            "width": 256, "height": 256, "uv_width": 256, "uv_height": 256})
    mw = 0.7 if p["neck"][0] < 1.8 else 0.9
    for g, lift in (("cou", 0.0), ("cou_haut", 0.6)):
        lo, hi = bounds(m.elems(g))
        y0 = lo[1] + (0.6 if g == "cou" else 0)
        add_cube(m, g, "mane|hair", [-mw, y0, hi[2] - 0.7], [mw, hi[1] + lift, hi[2] + 1.1])
    lo, hi = bounds([e for e in m.elems("tete") if e["name"] == "crane"])
    add_cube(m, "tete", "forelock|hair", [-0.8, hi[1] - 0.4, hi[2] - 2.6], [0.8, hi[1] + 0.5, hi[2] - 0.4], rotation=[-13, 0, 0],
             origin=[0, hi[1], hi[2]])
    q = m.nodes["queue"][0]; o = q["origin"]
    for e in list(m.elems("queue")):
        if e["name"] == "queue_bout":
            m.d["elements"].remove(e); m.nodes["queue"][1]["children"].remove(e["uuid"])
    lo, hi = bounds(m.elems("queue")); top = hi[1] + 0.2; back = hi[2]
    add_cube(m, "queue", "tail|hair", [-1.1, top - 1.6, back - 1.2], [1.1, top, back + 1.6])
    add_cube(m, "queue", "tail|hair", [-1.3, top - 13.5, back - 0.4], [1.3, top - 0.4, back + 1.9], rotation=[-14, 0, 0],
             origin=[0, top - 0.4, back])

if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    json.dump(horse(data, sys.argv[3] if len(sys.argv) > 3 else "riding"), open(sys.argv[2], "w"), indent=1)
