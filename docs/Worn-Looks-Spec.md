# Worn bauble looks spec (REA method)

Andy (2026-11-06): "Would this trinkets' system animate any other baubles? If so i want them to be integrated", then
picked "also our own 7 starter baubles". No mod in the pack draws worn items through Trinkets except Archers' quivers
(done in Kitchen 0.5.82), but the Artifacts mod draws its baubles on the player and our port of it (PR #921) left that
out. This spec follows REA-Prompts section 4 (clean-room): references studied as a black box, facts and guesses written
down, then built only from this spec with our own boxes and pixels. No Artifacts boxes, UVs or pixels are copied.

**Update (Andy: "Can we not port their looks too?"):** the 25 baubles that kept their Artifacts look now use Artifacts'
own worn models and textures, ported under its MIT licence (`client/baubles/artifacts/worn/`). This spec now covers only
the 24 looks that stay our own: the 7 starters, the sky mask, and the 16 Artifacts items we repainted medieval (alewife
cap, jester cap, reed breather, owl mask, soothsayer hood, rider's hat, lodestone charm, wind bladder, ender pouch, iron
knuckles, ram bracer, smelter's bracer, glutton's ring, courier's boots, waterstrider boots, frogskin flippers).

## 1. References

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Artifacts (MIT) worn items in game: which body part each item sits on | placement only |
| R2 | Our own item icons in `textures/item/bauble/` (the medieval repaints) | shape and colours of each look |
| R3 | Vanilla player model bones and pivots; vanilla armour inflation (helmet/chest/boots 1.0, leggings 0.5) | where boxes go, how far over armour |
| R4 | Our worn lanterns, fairies and quiver (stride sway) | how hanging things move |

## 2. Facts

| # | Fact | Source | In the build |
| --- | --- | --- | --- |
| F1 | Hats, hoods, masks and circlets sit on the head | R1 | head parts, pushed out 1.22x over a helmet |
| F2 | Scarves wrap the neck with a loose tail; pendants hang on a V of cord at the chest | R1, R2 | neck wrap + swinging tails; two cords + swinging pendant |
| F3 | Gloves and claws are on both hands; gauntlets, bracers and bracelets on one | R1, R2 | `sides` both/right/left |
| F4 | Rings show on the fingers | R1 | thin band at the hand; ring slot 1 right hand, slot 2 left |
| F5 | Shoes cover the feet | R1 | both legs, pushed out over boots |
| F6 | Belt gadgets hang at the hip | R1 | belt strap + a hanging item on the right hip (the coin purse and lanterns keep the left) |
| F7 | Charms dangle | R2 | small cord and charm at the front right of the belt |
| F8 | A cloak hangs down the back and lifts as you run | R4 | cape-kind swing, vanilla cape hidden while worn |

## 3. UNKNOWN / chosen

- U1: hip side for belt gadgets. Chosen: the right hip (x -4.9), clear of the purse and lantern.
- U2: how much things swing. Chosen per part in `tools/gen_worn_looks.py` (0.08-0.22 rad), plus a slow idle sway.
- U3: the bottles, skull and cushion that already hang at the hip as item sprites (`#jaymospowers:hip_shown`) stay as they are.

## 4. Textures

Painted from nothing by `tools/gen_worn_looks.py`: each face is its look's flat colour (picked by eye from our own icon),
shaded per face (top lighter, underside darker), seeded noise, a darker one-texel rim, and a pattern by material
(leather stitching, knit, straw weave, feathers, fur, metal glint, gem highlight, reed nodes, mesh, flame, bark).
The same script writes `worn_looks.json` (the boxes, packed UVs, swings), which `client/baubles/WornLooks` reads.

## 5. Differences left

- Looks are blocky approximations of our icons, not Artifacts' shapes; some items (the hip bottles) are still sprites.
- No glowing overlays (Artifacts' goggles/gauntlet glow): every look is drawn with the entity cutout render type.
- Not checked in game; previews in project files `art-overhaul/worn-baubles/worn-looks-preview.png`.
