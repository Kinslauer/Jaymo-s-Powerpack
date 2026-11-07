---
status: claimed
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes"
claimed_by: hermes-a
pr: 
suggested_owns:
  - src/client/java/com/jaymospowers/client/blood/
  - src/client/java/com/jaymospowers/client/wet/
---
# Blood and overlay textures: pop-in and z-fighting

**Andy's words (2026-11-07 17:45):** "Id also like another look over the blood and overlaid textures for pop in and
zfighting"

**What's wanted:** go over every flat thing we draw on top of something else and stop it flickering (z-fighting) or
appearing/vanishing suddenly at distance or on spawn (pop-in):
- Ground decals: `BloodDecals`, `BloodPuddle` (also used for rain/drip water puddles, `BloodDecals.Decal`), stains on
  slopes, slabs, stairs, snow layers, carpets and at block edges; fade in/out instead of popping; draw distance and
  culling consistent with nearby particles.
- Overlays on bodies/items: `BloodCoatLayer` (blood on creatures), `BloodItemOverlay` (blood on swords/axes),
  `ArmourGrimeMixin` (grime tint), wound/cut overlays (`core/wounds`), Kingdoms Armory 3D pieces and `WornModel`
  where they sit over armour, Kitchen corpse overlays. Check render type (decal/translucent, depth offset / polygon
  offset, small outward inflate) and that they follow EMF/Fresh Animations poses.
- Test with and without Complementary shaders and Fresh Animations where possible; note what couldn't be checked.
- Report the list of fixes in the PR. Tuning (offsets, fade times, distances) as `static final` constants.

**Where to start:** `docs/codemap/powers.md` "com.jaymospowers.client.blood", `client/wet/`, memories on EMF (PR #185,
#223 WornModel/EmfWornPose). Kingdoms/Kitchen overlay files only if found guilty; add them to owns first.

## Notes
