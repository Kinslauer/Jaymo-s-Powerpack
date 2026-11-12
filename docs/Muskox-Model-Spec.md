# Muskox model spec (REA method)

The same clean-room method as `docs/Grizzly-Model-Spec.md`: study the references as a black box, write down what is
seen (facts with their source, guesses marked UNKNOWN), build only from this spec with our own boxes and pixels, then
list every difference left. No other mod's or pack's boxes, UVs or pixels are copied. A third-party muskox art pack
exists (CC BY-NC-SA 4.0, `docs/Tundra-Plan.md`); it was not opened, searched for or looked at for this model, and
none of it is in our jars.

Built: `kingdoms/src/client/java/com/jaymoskingdoms/client/wild/MuskoxModel.java` (boxes and poses),
`MuskoxRenderer.java`, coats and spawn egg painted by `tools/gen_muskox.py`.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Wikipedia, "Muskox" (*Ovibos moschatus*), read 2026-11-12 | size, tail, coat colours and length, both sexes horned, diet, herds, predators, kinship |
| R2 | General field-guide knowledge of the living animal (as in wildlife-agency species profiles); not re-read this session | horn shape, saddle, stockings, hump, calves |
| R3 | Our own grizzly (`GrizzlyModel`, `tools/gen_grizzly.py`) | how an own model, coat generator and preview are laid out in this repo |
| R4 | The pack's style: vanilla-style cuboids, 1 texel = 1/16 block (`docs/Vanilla-Style.md`) | box sizes, texture density |

## 2. Facts (what the model must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | 1.1 to 1.5 m at the shoulder; body 135 to 250 cm long | R1 | 22 texels to the hump, 31 long, drawn 1.1x: 1.5 blocks to the hump, 2.1 long |
| F2 | "More closely related to sheep and goats than to oxen" (a caprine) | R1 | goat proportions under the hair: narrow legs set under the body, no dewlap, no cow's long face; a goat's voice pitched down |
| F3 | Barrel-bodied, short-legged; "the thick coat and large head" make it look bigger than it is | R1 | one 12x11x20 barrel, 9-texel legs, a head 8 wide on a 12-wide body |
| F4 | A hump over the shoulders is its highest point; the back falls away behind it | R2 | `hump` box 4 texels above the barrel, standing 1 texel forward of the chest as a mane |
| F5 | Long guard hairs "almost reach the ground": a skirt | R1 | `skirt` shell round the barrel from mid-flank down to the knees (4 texels of leg show); its hem ragged (cut-out texels) |
| F6 | Coat "a mix of black, grey, and brown"; a pale saddle on the back behind the shoulders | R1, R2 | two coats (brown, black); pale saddle painted on the barrel's top behind the hump, spilling onto the flanks |
| F7 | Both sexes have long curved horns: a broad boss across the brow, each horn curving down beside the cheek, then forward and up to a point | R1 (both sexes, curved), R2 (shape) | `horns`: boss 9x2x3 on the brow, a 2x8x2 drop beside each cheek, a 2x2x3 hook forward, a 1x3x1 tip turned up; pale horn darkening to the tip |
| F8 | Head carried low, in front of the chest; broad pale muzzle | R2 | head pivot at mid-chest height, its crown 3 texels under the hump's; muzzle paler than the face |
| F9 | Lower legs pale ("stockings"), dark hooves | R2 | legs painted pale below the skirt line, 1-texel dark hoof |
| F10 | Calves have no visible horns at first and a short woolly coat | R2 | a calf hides `horns` and `skirt`, head 1.25x for its size, drawn at half size |
| F11 | Tail about 10 cm, "often hidden under fur"; ears small, in the hair | R1 (tail), R2 (ears) | no tail box, no ear boxes |
| F12 | Eats grasses, willows, lichens and mosses, head down | R1 | graze pose: head turned down 1 rad and dropped 3 texels, a chewing nod |
| F13 | Hunted by wolves and bears; adults face a threat with the horn boss | R1 | butt pose (head dropped, thrust forward 3 texels); in the game it runs from hunters instead (section 6) |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: how much of the leg shows under the skirt. Chosen: 4 of 9 texels (the knee down).
- U2: eye placement and colour. Chosen: 1 dark texel at each front corner of the skull, wrapped onto the side, a
  slightly paler texel beside it.
- U3: how wide the saddle is. Chosen: about half the back's length, full width, fading at its edge.
- U4: the share of black coats. Chosen: one in three (by the animal's id, so it never changes).
- U5: a calf's proportions. Chosen: the adult at half size with a 1.25x head; real calves are leggier.
- U6: the gallop. Chosen: fore pair and hind pair each together, blending in above a limb speed of 0.5.
- U7: exact horn colours. Chosen: cream boss (224,210,174), shaded (178,160,124), dark tip (56,46,39).

## 4. Coats (painted from nothing by `tools/gen_muskox.py`)

Palette (ours). Shared: hoof (38,32,28), nose (34,27,25), eye (14,11,10), horn as U7.

| | hair | dark | light | underwool | saddle | stocking | face | muzzle |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| brown | 74,52,37 | 47,33,25 | 108,79,55 | 136,114,92 | 192,172,136 | 216,206,186 | 54,39,30 | 172,156,134 |
| black | 40,35,34 | 22,19,19 | 68,60,56 | 112,102,94 | 164,154,136 | 204,200,190 | 27,23,23 | 150,142,130 |

Per texel: every column is a strand of hair with its own tone, running a few texels before the next strand begins
(longer strands on the skirt, shorter on the back), a lighter hair here and there, the skirt and flanks darkening
downward, a fleck of underwool showing through. The hump is the darkest. The skirt's last two rows are cut out in
places so the hem hangs unevenly. Seeded, so the files are reproducible.

## 5. Layout (128x64)

| Box | Part | Size (w,h,d) | UV |
| --- | --- | --- | --- |
| barrel | body | 12,11,20 | 0,0 |
| hump | body | 10,4,10 | 64,0 |
| skirt | skirt (child of body) | 14,11,22 | 0,31 |
| skull | head | 8,8,7 | 72,31 |
| muzzle | head | 6,5,3 | 104,0 |
| boss | horns (child of head) | 9,2,3 | 104,8 |
| horn drop (x2) | horns | 2,8,2 | 64,14 |
| horn hook (x2) | horns | 2,2,3 | 72,14 |
| horn tip (x2) | horns | 1,3,1 | 82,14 |
| front leg (x2) | legs | 4,9,4 | 72,46 |
| hind leg (x2) | legs | 4,9,4 | 88,46 |

Pivots (model space, y down, ground at 24): body 0,0,0; skirt 0,9,0; head 0,9,-10; legs +-3.5,15,-7 and +-3.5,15,7.

## 6. Differences left (validation)

Checked with `tools/gen_muskox.py --preview` renders (stand, graze, run, calf beside adult, turnaround) in
`/mnt/project-files/art-overhaul/muskox/`. Not yet seen in the game.

- The back is level behind the hump: a real muskox's back slopes down to a lower rump. The hump is one step.
- The skirt is a box: it hangs straight and square, where real guard hair drapes and sways. It only swings a little
  with the stride. The ragged hem is two texels deep.
- The horns are four straight pieces a side, so the curve is a square J. The boss is one slab; a bull's heavier boss
  and a cow's lighter horns are not told apart (one model for both sexes).
- Legs are single boxes (no knee), and thicker for their length than a real muskox's.
- No tail, no ears (both hidden in the hair in life, F11); no beard or separate neck ruff.
- A calf is the adult scaled down with a bigger head, without horns or skirt, not a model of its own.
- No seasonal moult on the coat itself: shedding is shown only by the string it drops.
- Behaviour, not the model: real muskoxen form a ring facing a predator; ours run from hunters, each its own way
  (`docs/Tundra-Plan.md`), and butt back only at what strikes them. Real herds run to 8 to 24; ours spawn 3 to 6 and
  top out near 10 so a server isn't loaded down.
