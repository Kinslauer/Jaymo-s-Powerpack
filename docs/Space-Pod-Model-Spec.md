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
