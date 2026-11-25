"""Ports four cars of the Cars mod (Pokato, MIT: code and art) into Jaymo's Powers as the future's cars.

Andy's Patch 8: exactly four cars, found only in the future's ruins. The Cars mod keeps each body as a binary mesh
("CSMH": a list of named groups of quads, 4 vertices of x, y, z, u, v, nx, ny, nz each, positions in sixteenths of a block,
y down) and builds the rest (wheels, doors that swing, lights) in code. We keep the body meshes and the paint, and flatten
each mesh to one list of quads in blocks, y up, nose to +z, ready to draw with one render call:
  * the door and door-zone groups are written relative to their hinges in the mesh, so each is moved to the side of the
    car it belongs to (closed): the zone strips just inside the body's outer face, the doors flush with it;
  * the whole body is lifted to sit {@link RIDE} blocks above the ground the wheels stand on;
  * the wheels are not in the mesh: the game draws its own (FutureCarRenderer).
Our names: hatchback = runabout, pickup = hauler, muscle = bruiser, roadster = dasher.

Usage (from the repository root), with the Cars 1.16.2 Fabric jar unpacked in <cars>:
    python3 -I tools/car_port.py <cars-unpacked-folder>

Writes src/main/resources/assets/jaymospowers/car_meshes/<name>.quads and <name>.json (size, axles), and copies the paint to
textures/entity/future/car_<name>.png.
"""
import json
import shutil
import struct
import sys
from pathlib import Path

CARS = {"hatchback": "runabout", "pickup": "hauler", "muscle": "bruiser", "roadster": "dasher"}
# how far above the ground the body's lowest point rides (blocks); the wheels' radius is 0.34
RIDE = 0.22
# the strip beside each door sits this much (sixteenths) inside the body's outer face, so the door covers it
INSET = 0.15
DOOR_GROUPS = ("/d_door_r", "/d_door_l")


def load(path: Path):
    data = path.read_bytes()
    magic, version, count = struct.unpack_from(">iii", data, 0)
    assert magic == 0x43534D48 and version == 1, path
    o = 12
    entries = []
    for _ in range(count):
        (n,) = struct.unpack_from(">H", data, o)
        o += 2
        name = data[o:o + n].decode()
        o += n
        (quads,) = struct.unpack_from(">i", data, o)
        o += 4
        floats = struct.unpack_from(">%df" % (quads * 32), data, o)
        o += quads * 128
        entries.append((name, quads, list(floats)))
    assert o == len(data), path
    return entries


def bounds(entries, axis):
    values = [f[i] for _, _, f in entries for i in range(axis, len(f), 8)]
    return min(values), max(values)


def classify(name: str) -> str:
    if any(g in name for g in DOOR_GROUPS):
        return "doorR" if "_door_r" in name else "doorL"
    if "_zR" in name:
        return "zoneR"
    if "_zl" in name:
        return "zoneL"
    return "body"


def shift(entries, dx, dy, dz):
    for _, _, f in entries:
        for i in range(0, len(f), 8):
            f[i] += dx
            f[i + 1] += dy
            f[i + 2] += dz


def port(src: Path, ours: str, out: Path, cars: Path) -> None:
    entries = load(src)
    groups = {"body": [], "zoneR": [], "zoneL": [], "doorR": [], "doorL": []}
    for e in entries:
        groups[classify(e[0])].append(e)
    body = groups["body"]
    # the glass is written in frames we don't have (panes land well beyond the body), and these cars are a lifetime past the
    # end: their windows are gone, so the glass is left out

    # and the roof panel and its trim (the two top-level groups with no zone in their path) float above their pillars in
    # the mesh's own frame: with the windows gone they are the roofs the ruins lost
    kept = [e for e in body if not e[0].startswith("g_") and e[0] not in ("b_body", "t_trim")]
    groups["body"] = body = kept
    outer = max(abs(v) for v in bounds(body, 0))
    sill = max(f[i + 1] for name, _, f in body if name.startswith("b_body") for i in range(0, len(f), 8))
    for side, sign in (("R", 1), ("L", -1)):
        zone, door = groups["zone" + side], groups["door" + side]
        if zone:
            lo, hi = bounds(zone, 0)
            shift(zone, (outer - INSET) - hi if sign > 0 else -(outer - INSET) - lo, 0.0, 0.0)
        if door:
            xl, xh = bounds(door, 0)
            yl, yh = bounds(door, 1)
            zl, zh = bounds(door, 2)
            zone_z = bounds(zone, 2) if zone else (-12.0, 12.0)
            shift(door, outer - xh if sign > 0 else -outer - xl, sill - yh, (zone_z[0] + zone_z[1]) / 2.0 - (zl + zh) / 2.0)
    everything = [e for g in groups.values() for e in g]
    y_low = max(f[i + 1] for _, _, f in everything for i in range(0, len(f), 8))  # y is down: the largest is the lowest point
    x_lo, x_hi = bounds(everything, 0)
    z_lo, z_hi = bounds(everything, 2)
    y_top = min(f[i + 1] for _, _, f in everything for i in range(0, len(f), 8))
    quads = []
    for _, n, f in everything:
        for q in range(n):
            quad = []
            for v in range(4):
                x, y, z, u, w, nx, ny, nz = f[(q * 4 + v) * 8:(q * 4 + v) * 8 + 8]
                # sixteenths, y down -> blocks, y up, turned about the vertical so the nose points to +z
                quad.append((-x / 16.0, (y_low - y) / 16.0 + RIDE, z / 16.0, u, w, -nx, -ny, nz))
            quads.append(quad)
    (out / "car_meshes").mkdir(parents=True, exist_ok=True)
    with open(out / "car_meshes" / f"{ours}.quads", "wb") as fh:
        fh.write(struct.pack(">i", len(quads)))
        for quad in quads:
            for vertex in quad:
                fh.write(struct.pack(">8f", *vertex))
    length = (z_hi - z_lo) / 16.0
    meta = {"halfWidth": round((x_hi - x_lo) / 32.0, 3), "front": round(z_hi / 16.0, 3), "rear": round(z_lo / 16.0, 3),
            "height": round((y_low - y_top) / 16.0 + RIDE, 3), "length": round(length, 3), "quads": len(quads)}
    (out / "car_meshes" / f"{ours}.json").write_text(json.dumps(meta, indent=1))
    tex = out / "textures/entity/future"
    tex.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(cars / f"assets/cars/textures/entity/{src.stem}.png", tex / f"car_{ours}.png")
    print(ours, meta)


def main() -> None:
    cars = Path(sys.argv[1])
    out = Path("src/main/resources/assets/jaymospowers")
    for src, ours in CARS.items():
        port(cars / f"assets/cars/meshes/{src}.bin", ours, out, cars)


if __name__ == "__main__":
    main()
