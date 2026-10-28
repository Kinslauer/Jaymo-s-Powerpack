# The spirit figures, one spec for the Java mesh and the renders.
# Boxes: (cx, y, cz, w, h, d, comment) in model pixels, figure-local: y down from the shoulder line, -z ahead.
# Groups: torso (on the figure root), skull (pivot at the neck, 0,0,0), tail (pivot at the torso's foot, 0, TAIL_Y, 0).

GRIMSBY, VARRO, AMBROSE, THESSALY, QUIET = range(5)
NAMES = ["Grimsby", "Varro", "Ambrose", "Thessaly", "The Quiet One"]

FIG = {
    GRIMSBY: dict(
        scale=1.6, half=3.5, front=3.3, tail_y=11.0,
        torso=[
            (0, 0, 0, 7, 11, 3.5, "the gaunt chest"),
            (0, 0.2, 0, 8, 1, 3, "the collarbones"),
            (0, 3, -1.9, 6, 0.7, 0.6, "ribs"),
            (0, 5, -1.9, 5.6, 0.7, 0.6, None),
            (0, 7, -1.9, 5, 0.7, 0.6, None),
            (0, 2, 2.0, 1.2, 1.2, 1.0, "the spine's knobs"),
            (0, 5, 2.0, 1.2, 1.2, 1.0, None),
            (0, 8, 2.0, 1.2, 1.2, 1.0, None),
        ],
        skull=[
            (0, -1.5, 0.3, 2.4, 1.6, 2.4, "the neck"),
            (0, -8.5, 0, 6.6, 7, 6.6, "the skull"),
            (0, -2.2, -0.6, 5, 1.4, 5, "the jaw"),
            (0, -9.4, 0.6, 8.2, 1.2, 7.8, "the cowl: its crown"),
            (-3.7, -9.0, 0.6, 0.9, 7.4, 7.8, "its sides"),
            (3.7, -9.0, 0.6, 0.9, 7.4, 7.8, None),
            (0, -9.0, 4.0, 8.2, 8, 1.0, "its back"),
            (0, -8.0, 4.8, 4, 6, 1.2, "and its tail, hanging"),
        ],
        tail=[
            (0, 0, 0.3, 6, 4, 3, "the ghost's tail"),
            (0, 4, 0.6, 4.4, 4, 2.4, None),
            (-1.6, 8, 0.9, 1.2, 5, 1.2, "ragged strands"),
            (0.3, 8, 0.9, 1.2, 7, 1.2, None),
            (1.8, 8, 0.9, 1.0, 4, 1.0, None),
        ]),
    VARRO: dict(
        scale=1.9, half=6.0, front=5.0, tail_y=12.0,
        torso=[
            (0, 0, 0, 12, 12, 6, "the plated chest"),
            (0, 1, -3.2, 10, 7, 1.0, "the breastplate, standing proud"),
            (0, -1.2, 0, 7, 1.6, 5, "the gorget"),
            (0, 10, 0, 12.6, 2, 6.6, "the belt"),
        ],
        skull=[
            (0, -1.2, 0, 4, 1.4, 4, "the neck"),
            (0, -10, 0, 9, 9, 9, "the helm"),
            (0, -5.6, -4.7, 6.6, 3.6, 0.6, "its faceplate"),
            (0, -7.2, -4.9, 8, 1.0, 0.6, "the visor's brow"),
            (0, -12, 0.5, 1.4, 2.4, 10, "the crest"),
        ],
        tail=[
            (-3, 0, -0.5, 5.4, 5, 6.4, "the tassets"),
            (3, 0, -0.5, 5.4, 5, 6.4, None),
            (0, 5, 0.5, 9, 4, 4.5, "then the ghost's tail"),
            (0, 9, 1.0, 6, 4, 3, None),
            (0, 13, 1.4, 3.4, 4, 2, None),
        ]),
    AMBROSE: dict(
        scale=1.8, half=5.0, front=4.0, tail_y=12.0,
        torso=[
            (0, 0, 0, 10, 12, 5, "the robed chest"),
            (0, -0.6, 0, 11.4, 3, 6.2, "the mantle"),
            (-2.4, 2, -2.8, 1.6, 10, 0.6, "the stole"),
            (2.4, 2, -2.8, 1.6, 10, 0.6, None),
        ],
        skull=[
            (0, -8.6, 0, 7, 7.6, 7, "the head"),
            (0, -2.5, -3.3, 4.4, 8, 1.4, "the long beard"),
            (0, -9.8, 0.5, 9.4, 1.4, 9, "the hood: its crown"),
            (-4.2, -9.0, 0.5, 1.0, 8.6, 9, "its sides"),
            (4.2, -9.0, 0.5, 1.0, 8.6, 9, None),
            (0, -9.4, 4.5, 9.4, 9.4, 1.2, "its back"),
            (0, -11.4, 1.5, 4, 1.8, 5, "its point"),
        ],
        tail=[
            (0, 0, 0.3, 12, 6, 7, "the robe flaring"),
            (0, 6, 0.8, 10, 5, 6, None),
            (0, 11, 1.3, 7, 5, 4.4, "then thinning to a wisp"),
            (0, 16, 1.8, 4, 4, 2.6, None),
        ]),
    THESSALY: dict(
        scale=1.2, half=3.2, front=3.6, tail_y=11.0,
        torso=[
            (0, 0, 0, 6.4, 6, 3.4, "the slim chest"),
            (0, 6, 0, 5, 5, 3, "the waist"),
            (0, -0.4, 0.4, 7.6, 2, 4, "a shawl"),
            (0, 9.6, 0, 5.6, 1, 3.4, "the sash"),
        ],
        skull=[
            (0, -1.4, 0, 1.8, 1.6, 1.8, "the neck"),
            (0, -7.6, 0, 6, 6.2, 6, "the head"),
            (0, -8.2, 0.5, 6.6, 1.2, 6.6, "the hair"),
            (0, -7.8, 2.6, 6.6, 9, 1.6, "falling down the back"),
            (0, -6.6, 0, 6.6, 0.6, 6.6, "a circlet"),
            (0, -6.9, -3.4, 1, 1, 0.4, "its stone"),
        ],
        tail=[
            (0, 0, 0.2, 7, 5, 4.4, "the skirt"),
            (0, 5, 0.6, 6, 5, 3.6, None),
            (0, 10, 1.0, 4, 5, 2.6, None),
            (-1.2, 15, 1.2, 0.8, 5, 0.8, "ribbons trailing"),
            (1.2, 15, 1.2, 0.8, 6, 0.8, None),
        ]),
    QUIET: dict(
        scale=1.7, half=4.0, front=3.45, tail_y=12.0,
        torso=[
            (0, 0, 0, 8, 8, 4, "the smooth chest"),
            (0, 8, 0, 6.6, 4, 3.4, "the waist"),
        ],
        skull=[
            (0, -1.6, 0, 2, 1.8, 2, "the neck"),
            (0, -10, 0, 6.4, 8.6, 6.4, "the long faceless head"),
            (0, -12.6, -3.2, 6, 0.5, 0.5, "a ring over it"),
            (0, -12.6, 3.2, 6, 0.5, 0.5, None),
            (-3.2, -12.6, 0, 0.5, 0.5, 6, None),
            (3.2, -12.6, 0, 0.5, 0.5, 6, None),
        ],
        tail=[
            (0, 0, 0.4, 6, 5, 3.2, "the tail"),
            (0, 5, 0.8, 4.4, 5, 2.6, None),
            (0, 10, 1.2, 3, 5, 2, None),
            (0, 15, 1.5, 1.6, 5, 1.4, None),
        ]),
}

# the arms' inner reach from their pivot toward the body, per unit of arm size (upper arm width / 2 - 1)
ARM_INNER = {GRIMSBY: 0.5, VARRO: 1.5, AMBROSE: 1.0, THESSALY: 0.5, QUIET: 1.0}
ARM_SIZE = {GRIMSBY: 1.0, VARRO: 2.4, AMBROSE: 2.0, THESSALY: 0.6, QUIET: 1.0}


def fmt(v):
    s = ("%.2f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s + "F"


def java(kind, group):
    out = []
    for (cx, y, cz, w, h, d, c) in FIG[kind][group]:
        line = "box(c, %s, %s, %s, %s, %s, %s, g);" % tuple(fmt(v) for v in (cx, y, cz, w, h, d))
        if c:
            line += " // " + c
        out.append(line)
    return out


if __name__ == "__main__":
    for k in range(5):
        for grp in ("torso", "skull", "tail"):
            print("//", NAMES[k], grp)
            print("\n".join(java(k, grp)))
