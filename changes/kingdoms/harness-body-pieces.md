# Harness 2: wider makes of cuirass, the coat of plates, gorget, tassets, rondels and the tabard of arms
- **Two more cuirasses**: the **Fluted Cuirass** and the **Rounded Breastplate**, beside the plain one. Each realm's knights
  wear their realm's own make where its creed or land doesn't choose one. Both take every finish.
- **Coat of Plates**: plates riveted inside a cloth coat in the realm's colour (chestplate slot, dyeable). A little
  weaker than plate (4 armour, 0.5 toughness) and mended with iron. A realm's armourer sells it, and a poorer realm's
  guards (one without iron to spare) wear it.
- **Two new harness places**: **Neck** for the **Gorget** and **Waist** for the **Faulds and Tassets**. Both are plate,
  take every finish, give 1 armour and 0.5 toughness each, and sit in the free corners of the harness screen. Realm
  knights wear both. Harnesses saved before this load as they were, with the new places empty.
- **Spaulders with Rondels**: plate spaulders with a disc guarding each armpit, a little tougher than plain ones. Some
  realms put them on their knights.
- **Heraldic Tabard**: now drawn, in the realm's arms: dyed the realm's field, with its ordinary (cross, saltire, bend,
  chief, lozenge...) laid front and back in the charge's colour. A realm's tailor sells one in its arms, and the guards'
  and Crown kings' tabards carry them too.
- **Fix**: the harness art generator (`kingdoms/tools/gen_heraldry_armour.py`) had stopped making plate and cloth art
  (half its main step was indented into a helper after its `return`). So Harness 3's creed and land pieces had no
  models and were invisible: faith, etched and gilt-banded cuirasses, spiked and gilt-banded spaulders, oath sleeves,
  the fur-lined set, the veiled helm, quilted cotton, hide, bronze and lamellar. They are drawn now.
- Lore: "The armourers' wider makes" (Andy approved 2026-11-18). Classes: `Harness`, `HarnessMenu`, `HeraldryArmour`
  (`ARMS`, the coat's material), `HeraldryWear`, `GuardKit`, client `HeraldryLayer`.
- Built; not played: the new pieces, the tabard's arms and the regenerated art haven't been seen in game.
