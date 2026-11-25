# Space pod: the submarine look (REA method)

Andy (2026-11-24, Alex's Caves picks): "the submarine stays, and its model should be interpolated into the space pod".
The pod keeps its own shape and slots (keel, fins, bell, tank, four plates, canopy, mast; `PodModel`, `PodRenderer`);
what it takes from Alex's Caves' submarine is its read. Nothing of the submarine's boxes, UVs or pixels is used:
`tools/gen_pod.py` builds the boxes and paints the sheet from nothing.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Alex's Caves' submarine in game and its texture sheet, looked at, not copied | the look: copper plating, slate frame, round windows, stern propeller, periscope |
| R2 | The pod before this change (0.18.49) | the shape and slots, kept as they were |

## 2. Facts (what the model must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | Hull of riveted copper-red plating with panel seams | R1 | the four plates repainted copper (194,112,86), rivet rows kept, one seam across each outer face |
| F2 | A dark slate frame | R1 | keel, posts, fins, mast and porthole rims slate (78,82,104), was iron grey |
| F3 | Big round windows | R1 | round portholes: radius 5 on the front, 3 on the left and back (the back's raised over its propeller) |
| F4 | A propeller at the stern | R1 | on the back plate: a 2 px hub and a cross of brass blades (10 x 1 x 1 each way) |
| F5 | A periscope | R1 | the mast gets a forward eyepiece (2 x 2 x 3) below its brass cap |

## 3. UNKNOWN / decided here

- U1: which pieces carry the propeller. Chosen: the back plate, so it comes off and goes on with that plate.
- U2: the tank stays brass with its red band, and the canopy stays glass (they read as the pod's own).
- U3: the sheet grows to 256x128 so the left and back plates can carry their own portholes.

## 4. Differences that remain

The submarine is long and lies flat; the pod stands upright on its keel and keeps its stepped canopy, thruster bell and
fins. The submarine's headlights, claw and wake are not taken.

## 5. The cockpit and canopy (Andy, 2026-11-25)

Andy, on a clip of the pod in game: "the space pod item icon, flight png and in game model need work. you should be
sitting in that cockpit, and there needs to be a glass enclosure over it. maybe it animates tilting up then back down
after you enter. it can even use the horse mounting".

With Alex's Caves loaded the pod is drawn from his own `SubmarineModel` (client `compat/PodSubmarine`, PR #2112):

| What | Before | Now |
| --- | --- | --- |
| Thrusters and motor | stood on end beside and behind the hull | on their arms as he built them; a damaged one droops its far end 20 degrees |
| Size | 0.6 of his | 0.7, so a seated pilot's head clears the glass (seat top 1.0 blocks up, canopy top 2.45) |
| Pilot | sat 1.1 blocks up, on top of the hull | in his seat: `PodEntity.SUB_SEAT` 0.38 up (seat top less a seated body's 0.625), 0.31 forward, body kept to the bow |
| Levers | at his x 7, inside a seated pilot's knees | moved out to x 10.5 |
| His cabin box | drawn (its texture is mostly clear) | not drawn; our glass canopy covers the cockpit |
| Climb in | appears in the seat | the horse mount clips from either side (`SeatSteps`), the canopy tilted up 50 degrees while they climb |

The canopy (`compat/PodCanopy`, `tools/gen_pod_canopy.py`, `textures/entity/space_pod_canopy.png`) is ours: three
glass steps (low over the walls and bow, middle round the shoulders, crown over the head, flush with the hull's top),
a slate rib up each side, along each ledge and over the top, and a slate sill on the bow. Its colours are the
submarine's slate and copper read off it in game; the glass is our own. Only the outer faces are glass (the undersides,
the backs against the hull and the parts of each step's top under the next are clear), so the pilot is seen through one
pane. It is hinged at the top edge of his hull's core behind the pilot's head (hull x 0, y -21, z -7).

The icon and the flight screen's picture (`tools/vanilla_style/space_pod.py`) are the same side view: the model's boxes
as rectangles, drawn with vanilla's copper ramp and the submarine's slate, by the vanilla style rules.
