# Hooded cloak spec (REA method)

Andy (2026-11-06): "The archer hood should attach to a cloak that fits the player, and allows the hood to be removed and
worn in the back, visible to all, and lowering visibility while up". The hood is the Archers mod's `archers:archer_armor_head`
(All Rights Reserved, drawn by armor_model_api from a geo model). Following REA-Prompts section 4 (clean room), the hood
itself stays theirs and unchanged; the cloak and the lowered hood are our own boxes and pixels.

## 1. References

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Archers' archer hood geo model, read as a black box: a 10x10x10 hood over the head reaching 0.7 px below the neck, a peak hanging back from the crown; the tunic stands about 1 px out from the body | where the collar meets the hood, how far out the cloak sits |
| R2 | Archers' armour texture: colours only (hood leather #6d3e26, inside #372523, light edge #834d32) | palette |
| R3 | Vanilla player model bones and cape | the cloak hangs on the body bone and lifts as you run |
| R4 | Our worn looks (docs/Worn-Looks-Spec.md) and costume hoods (`Costume.hooded`) | how worn things are drawn and how a hood hides you |

## 2. Facts and choices

| # | Fact / choice | Source | In the build |
| --- | --- | --- | --- |
| F1 | The hood's hem ends just below the neck | R1 | an 11x3x6 collar round the top of the body, a brass clasp at the front |
| F2 | A cloak that fits: close over the shoulders, down the back to mid-calf | Andy | mantles on each arm (ride the arm swing), back in two hinged halves (11 and 12 wide, 10 tall each) |
| F3 | Lowered hood lies on the back, seen by everyone | Andy | `HoodedCloak.DOWN` attachment synced to all; hood taken off the head in the render state; 9x4x4 cowl at the nape, its 5x4 point hanging on the cloak |
| F4 | Hood up lowers visibility | Andy | `SecretIdentity.HOODED` tag: Kingdoms' mob senses see you at 0.75 of the range, witnesses see "a hooded figure", other players' name tag only within 8 blocks |
| F5 | Toggling | chosen | the costume mask key (' by default, `/costume mask`) when no costume is worn |

## 3. UNKNOWN / chosen

- U1: the cloak's colour. Chosen: a wool a shade deeper than the hood's leather, lighter trim at the hem, dark lining inside.
- U2: how much it moves. `HoodedCloakLayer` knobs: rests at 0.05 rad, lifts 0.32 more at a run, hem 0.14 more, small flutter and idle sway.
- U3: without chest armour the collar and mantles are pulled in (0.8x) and the back moves 0.9 px closer.
- U4: the hood is the only piece with a cloak; the ranger hood could join `#jaymospowers:cloak_hoods` later.

## 4. Texture

`tools/gen_hooded_cloak.py` paints `textures/entity/worn/hooded_cloak.png` (64x64) from flat colours, per-face shading, seeded
noise, a twill weave on the wool, grain on the hood leather, a stitched seam down the back and a dagged (notched) hem. Its box
table must match the texture offsets in `HoodedCloakLayer.build`.

## 5. Differences left

- The hood's own model and art are Archers'; ours only joins it at the collar.
- Not checked in game; preview in project files `art-overhaul/hooded-cloak/hooded-cloak-preview.png` (hood drawn as a flat stand-in).
