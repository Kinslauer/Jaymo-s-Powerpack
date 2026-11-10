# The spirit figures, one spec for the Java mesh, the .bbmodels and the renders.
# Boxes: (cx, y, cz, w, h, d, comment) in model pixels, figure-local: y down from the shoulder line, -z ahead.
# Groups:
#   torso  on the figure root
#   skull  pivot at the neck (0, 0, 0)
#   tail   the lower body (legs, robe or skirt), pivot at the waist (0, TAIL_Y, 0)
#   arm    the right arm, in its own frame, pivot at ARM_AT (x, y) on the figure; the left arm mirrors it (x negated).
#          Only the manifested figure shows its own arms: over your back the spirit's real arms (SpiritRig) hang from there.
# foot:  the sole, model pixels below the shoulder line (where the manifested figure stands on the ground).
# stage: the manifested figure's scale (a player is 1): near your own size, so it reads as a person standing with you.

GRIMSBY, VARRO, AMBROSE, THESSALY, QUIET = range(5)
NAMES = ["Grimsby", "Varro", "Ambrose", "Thessaly", "The Quiet One"]

FIG = {
    # a dead traveller: a gaunt swordsman's bones under a tattered cowl and cloak, a scabbard at his hip
    GRIMSBY: dict(
        scale=1.6, half=3.5, front=3.3, tail_y=11.0, foot=23.6, stage=1.1, arm_at=(-4.4, 0.8),
        torso=[
            (0, 0, 0, 7, 11, 3.5, "the gaunt chest"),
            (0, 0.2, 0, 8, 1, 3, "the collarbones"),
            (0, 2.6, -1.9, 6, 0.7, 0.6, "ribs"),
            (0, 4.4, -1.9, 5.6, 0.7, 0.6, None),
            (0, 6.2, -1.9, 5, 0.7, 0.6, None),
            (0, 8.0, -1.9, 4.2, 0.7, 0.6, None),
            (0, 2, 2.0, 1.2, 1.2, 1.0, "the spine's knobs"),
            (0, 5, 2.0, 1.2, 1.2, 1.0, None),
            (0, 8, 2.0, 1.2, 1.2, 1.0, None),
            (0, -0.4, 2.4, 9, 14, 0.7, "the cloak, hanging down the back"),
            (-3.4, -0.6, 0.4, 2.6, 1.6, 4.6, "rags over the shoulders"),
            (3.4, -0.6, 0.4, 2.6, 1.6, 4.6, None),
            (0, 9.6, 0, 7.6, 1.0, 4.0, "a rope belt"),
            (2.9, 9.0, 1.4, 1.2, 1.2, 1.2, "its knot"),
            (-4.0, 9.4, 0.6, 1.2, 9, 1.6, "the scabbard at the hip"),
        ],
        skull=[
            (0, -1.5, 0.3, 2.4, 1.6, 2.4, "the neck"),
            (0, -8.5, 0, 6.6, 7, 6.6, "the skull"),
            (0, -6.4, -3.5, 6.2, 0.8, 0.6, "the brow over the sockets"),
            (-1.6, -4.4, -3.4, 1.4, 0.8, 0.5, "cheekbones"),
            (1.6, -4.4, -3.4, 1.4, 0.8, 0.5, None),
            (0, -4.8, -3.6, 0.8, 1.2, 0.5, "the nose's hollow ridge"),
            (0, -2.2, -0.6, 5, 1.4, 5, "the jaw"),
            (0, -2.9, -3.2, 4.2, 0.5, 0.4, "teeth"),
            (0, -9.4, 0.6, 8.2, 1.2, 7.8, "the cowl: its crown"),
            (-3.7, -9.0, 0.6, 0.9, 7.4, 7.8, "its sides"),
            (3.7, -9.0, 0.6, 0.9, 7.4, 7.8, None),
            (0, -9.0, 4.0, 8.2, 8, 1.0, "its back"),
            (0, -9.9, -3.0, 6.4, 0.8, 1.6, "its brim, shading the face"),
        ],
        tail=[
            (0, 0, 0, 6, 2.4, 3, "the hips"),
            (0, 1.0, -1.9, 5.6, 6, 0.6, "tatters in front"),
            (-2.0, 7.0, -1.9, 1.2, 3, 0.6, None),
            (1.4, 7.0, -1.9, 1.0, 4, 0.6, None),
            (-1.6, 2.2, 0, 1.4, 10, 1.4, "the leg bones"),
            (1.6, 2.2, 0, 1.4, 10, 1.4, None),
            (-1.6, 6.4, -0.3, 2.0, 1.2, 2.0, "knees"),
            (1.6, 6.4, -0.3, 2.0, 1.2, 2.0, None),
            (-1.6, 12.0, -0.8, 2.0, 0.8, 3.0, "the feet"),
            (1.6, 12.0, -0.8, 2.0, 0.8, 3.0, None),
        ],
        arm=[
            (0, -1.0, 0, 3.2, 3.4, 3.4, "the ragged sleeve"),
            (0, 0, 0, 1.6, 6, 1.6, "the upper bone"),
            (0, 5.6, 0, 2.0, 1.0, 2.0, "the elbow"),
            (0, 6.4, 0, 1.4, 5, 1.4, "the forearm"),
            (0, 11.2, -0.2, 2.0, 2.2, 1.2, "the hand"),
            (0.4, 13.2, -0.2, 0.5, 1.6, 0.5, "finger bones"),
            (-0.4, 13.2, -0.2, 0.5, 1.8, 0.5, None),
        ]),
    # a warlord of the old wars in plate: great pauldrons, a crested helm with a visor slit, tassets and sabatons
    VARRO: dict(
        scale=1.9, half=6.0, front=5.0, tail_y=12.0, foot=28.0, stage=1.05, arm_at=(-8.0, 0.6),
        torso=[
            (0, 0, 0, 12, 12, 6, "the plated chest"),
            (0, 1, -3.2, 10, 7, 1.0, "the breastplate, standing proud"),
            (0, 2.4, -3.8, 2, 4.6, 0.6, "its ridge"),
            (0, 8.4, -3.2, 9, 1.2, 0.6, "the fauld's plates"),
            (0, 9.8, -3.2, 9.6, 1.2, 0.6, None),
            (0, -1.2, 0, 7, 1.6, 5, "the gorget"),
            (0, 10, 0, 12.6, 2, 6.6, "the belt"),
            (0, 10.4, -3.5, 2.4, 1.4, 0.6, "its buckle"),
            (0, 0.4, 3.4, 11, 16, 0.8, "the war cloak"),
        ],
        skull=[
            (0, -1.2, 0, 4, 1.4, 4, "the neck"),
            (0, -10, 0, 9, 9, 9, "the helm"),
            (0, -5.6, -4.7, 6.6, 3.6, 0.6, "its faceplate"),
            (0, -7.2, -4.9, 8, 1.0, 0.6, "the visor's brow"),
            (0, -6.0, -5.0, 6.0, 0.4, 0.4, "the eye slit's lower lip"),
            (-4.6, -6.0, -1.0, 0.6, 5.0, 5.0, "cheek guards"),
            (4.6, -6.0, -1.0, 0.6, 5.0, 5.0, None),
            (0, -12, 0.5, 1.4, 2.4, 10, "the crest"),
            (0, -11.0, 5.2, 1.2, 6.0, 1.2, "its plume, falling behind"),
        ],
        tail=[
            (0, 0, 0, 11, 4, 6.2, "the mail skirt"),
            (-3, 0, -0.5, 5.4, 5, 6.4, "the tassets"),
            (3, 0, -0.5, 5.4, 5, 6.4, None),
            (-2.6, 4, 0, 4, 9, 4.4, "the cuisses"),
            (2.6, 4, 0, 4, 9, 4.4, None),
            (-2.6, 7.2, -2.4, 3.4, 2.2, 0.8, "the knee cops"),
            (2.6, 7.2, -2.4, 3.4, 2.2, 0.8, None),
            (-2.6, 13, -0.8, 4.6, 3, 6, "the sabatons"),
            (2.6, 13, -0.8, 4.6, 3, 6, None),
        ],
        arm=[
            (0, -2.0, 0, 6.4, 3.6, 7, "the pauldron"),
            (0, 1.0, 0, 5.6, 1.4, 6.4, "its lower lame"),
            (0, 0, 0, 4, 5.4, 4, "the rerebrace"),
            (0, 4.8, -0.2, 4.8, 1.8, 4.8, "the couter"),
            (0, 6.4, 0, 4, 5, 4, "the vambrace"),
            (0, 11.0, 0, 4.8, 3.4, 4.8, "the gauntlet"),
            (0, 14.0, -1.8, 4.2, 0.9, 1.2, "its knuckle studs"),
        ]),
    # an elder in a hooded robe: a long beard, bushy brows, mantle and stole, wide sleeves, a satchel of scrolls
    AMBROSE: dict(
        scale=1.8, half=5.0, front=4.0, tail_y=12.0, foot=24.4, stage=1.1, arm_at=(-6.4, 0.8),
        torso=[
            (0, 0, 0, 10, 12, 5, "the robed chest"),
            (0, -0.6, 0, 11.4, 3, 6.2, "the mantle"),
            (-2.4, 2, -2.8, 1.6, 10, 0.6, "the stole"),
            (2.4, 2, -2.8, 1.6, 10, 0.6, None),
            (0, 9.4, 0, 10.6, 1.0, 5.6, "the cord belt"),
            (-1.6, 10.2, -2.9, 0.6, 3.0, 0.6, "its tassel"),
            (5.2, 6.4, 0.6, 1.6, 4, 3.6, "a satchel of scrolls"),
            (5.2, 5.4, 1.0, 1.0, 1.4, 1.0, "a scroll's end"),
        ],
        skull=[
            (0, -8.6, 0, 7, 7.6, 7, "the head"),
            (0, -6.6, -3.7, 5.6, 0.9, 0.7, "bushy brows"),
            (0, -5.4, -3.8, 1.2, 1.8, 0.8, "the nose"),
            (0, -4.4, -3.7, 5.0, 0.9, 0.7, "the moustache"),
            (0, -3.6, -3.4, 4.6, 8.4, 1.4, "the long beard"),
            (0, 4.6, -3.2, 2.6, 2.4, 1.0, "its point"),
            (0, -9.8, 0.5, 9.4, 1.4, 9, "the hood: its crown"),
            (-4.2, -9.0, 0.5, 1.0, 8.6, 9, "its sides"),
            (4.2, -9.0, 0.5, 1.0, 8.6, 9, None),
            (0, -9.4, 4.5, 9.4, 9.4, 1.2, "its back"),
            (0, -11.4, 1.5, 4, 1.8, 5, "its point"),
        ],
        tail=[
            (0, 0, 0.3, 11, 6, 6.4, "the robe"),
            (0, 6, 0.3, 12, 5.6, 7, "flaring"),
            (0, 11.4, 0.4, 12.8, 1.0, 7.6, "the hem"),
            (-2.2, 11.6, -3.8, 2.4, 0.8, 1.6, "the toes of his boots"),
            (2.2, 11.6, -3.8, 2.4, 0.8, 1.6, None),
            (-2.4, 2, -3.0, 1.6, 9, 0.6, "the stole hanging on"),
            (2.4, 2, -3.0, 1.6, 9, 0.6, None),
        ],
        arm=[
            (0, -1.0, 0, 4.4, 3, 4.4, "the shoulder of the robe"),
            (0, 0, 0, 4, 6.4, 4, "the sleeve"),
            (0, 6.0, 0, 5.6, 4.6, 5.6, "its wide cuff"),
            (0, 9.8, -0.2, 2.6, 2.6, 2.6, "the hand"),
            (0.8, 12.2, -0.6, 0.7, 1.4, 0.7, "fingers"),
            (-0.4, 12.2, -0.6, 0.7, 1.6, 0.7, None),
        ]),
    # a scholar: slim, a collar and shawl, hair long down her back and in side locks under a circlet, bell sleeves, a long skirt
    THESSALY: dict(
        scale=1.2, half=3.2, front=3.6, tail_y=11.0, foot=24.0, stage=1.0, arm_at=(-4.2, 0.8),
        torso=[
            (0, 0, 0, 6.4, 6, 3.4, "the slim chest"),
            (0, 6, 0, 5, 5, 3, "the waist"),
            (0, -0.4, 0.4, 7.6, 2, 4, "a shawl"),
            (0, -1.4, 0, 4.6, 1.2, 3.8, "the high collar"),
            (0, 1.6, -1.9, 1.2, 1.2, 0.5, "a brooch"),
            (0, 9.6, 0, 5.6, 1, 3.4, "the sash"),
            (1.8, 10.4, -1.7, 1.0, 4.0, 0.4, "its trailing end"),
        ],
        skull=[
            (0, -1.4, 0, 1.8, 1.6, 1.8, "the neck"),
            (0, -7.6, 0, 6, 6.2, 6, "the head"),
            (0, -8.2, 0.5, 6.6, 1.2, 6.6, "the hair"),
            (0, -7.8, 2.6, 6.6, 12, 1.6, "falling down the back"),
            (-3.1, -7.0, -0.8, 0.8, 7.0, 2.4, "side locks"),
            (3.1, -7.0, -0.8, 0.8, 7.0, 2.4, None),
            (0, -6.6, 0, 6.6, 0.6, 6.6, "a circlet"),
            (0, -6.9, -3.4, 1, 1, 0.4, "its stone"),
            (0, -4.4, -3.1, 2.4, 0.4, 0.3, "spectacles"),
        ],
        tail=[
            (0, 0, 0.2, 7, 5, 4.4, "the skirt"),
            (0, 5, 0.2, 8, 5, 5.2, None),
            (0, 10, 0.3, 9, 2.4, 6, "its hem"),
            (-1.2, 2, 3.0, 0.8, 9, 0.8, "ribbons trailing"),
            (1.2, 2, 3.0, 0.8, 10.5, 0.8, None),
            (-1.4, 12.2, -2.6, 1.6, 0.8, 1.6, "slippers"),
            (1.4, 12.2, -2.6, 1.6, 0.8, 1.6, None),
        ],
        arm=[
            (0, -0.6, 0, 2.6, 2.4, 2.6, "the shoulder"),
            (0, 0, 0, 2.0, 5.6, 2.0, "the slim sleeve"),
            (0, 5.2, 0, 3.4, 3.0, 3.4, "the bell cuff"),
            (0, 8.0, 0, 1.6, 3.2, 1.6, "the forearm"),
            (0, 11.0, -0.2, 1.8, 1.8, 1.2, "the hand"),
            (0, 11.6, -0.2, 2.0, 0.4, 1.4, "a ring"),
        ]),
    # never manifests a body (Andy: "only animating the large arms for the quiet one"): kept for its arms' frame
    QUIET: dict(
        scale=1.7, half=4.0, front=3.45, tail_y=12.0, foot=24.0, stage=1.0, arm_at=(-5.0, 0.8),
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
        ],
        arm=[
            (0, 0, 0, 3, 12, 3, "the smooth arm"),
        ]),
}

# the arms' inner reach from their pivot toward the body, per unit of arm size (upper arm width / 2 - 1)
ARM_INNER = {GRIMSBY: 0.5, VARRO: 1.5, AMBROSE: 1.0, THESSALY: 0.5, QUIET: 1.0}
ARM_SIZE = {GRIMSBY: 1.0, VARRO: 2.4, AMBROSE: 2.0, THESSALY: 0.6, QUIET: 1.0}

# Keyed moves of the manifested figure, the same curves SpiritFigure.Model poses in code (radians, model pixels; t in ticks).
# Blockbench gets them as animations in bbexport.py so Andy can see and change them; the code is what the game runs.
ANIMATIONS = {
    # faint and waiting: breathing, the head turning a little, the cloak's hem drifting
    "idle": dict(length=4.0, loop=True),
    # talking: the head nods and tilts, the right hand turns over as if making a point, the left hangs and sways
    "speak": dict(length=2.0, loop=True),
    # coming into being: risen from a crouch with the arms spread, a jolt (the glitch) half way
    "manifest": dict(length=0.5, loop=False),
    # level up: both arms flung up, then down to the speaking stance
    "level_up": dict(length=1.5, loop=False),
    # fading: sinking a little, the arms dropping, the head bowing
    "fade": dict(length=0.6, loop=False),
}


def fmt(v):
    s = ("%.2f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    return s + "F"


def java(kind, group, mirror=False):
    out = []
    for (cx, y, cz, w, h, d, c) in FIG[kind][group]:
        line = "box(c, %s, %s, %s, %s, %s, %s, g);" % tuple(fmt(v) for v in (cx, y, cz, w, h, d))
        if mirror:
            line = line.replace("box(c, ", "box(c, side * ", 1)
        if c:
            line += " // " + c
        out.append(line)
    return out


if __name__ == "__main__":
    for k in range(5):
        for grp in ("torso", "skull", "tail", "arm"):
            print("//", NAMES[k], grp)
            print("\n".join(java(k, grp)))
