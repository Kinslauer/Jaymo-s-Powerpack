# Moog's Ocean Structures in the pack
- **Moog's Ocean Structures** (LGPL-3.0, by FinnDog; shipped unchanged, 1.1.1) joins the pack: long ships, hauling
  ships, a rare travel ship, wanderer rafts, wrecks in three woods, sunken ships, Atlantis ruins, small ocean temples and
  shrines, sea dragon skeletons, and ruined castles, churches and houses on the sea floor. It needs Moog's Structure
  Lib, which the pack already has. In `packModsNoCf` (no CurseForge file id found for 26.3).
- Lore: the ruins are the Tideborn's drowned courts (`Lore.md`, "The drowned courts").
- `docs/ReaperHost-Plan.md`: the patches for the rest of Andy's picks from Immersive Pillagers, Mebahel's Draugr and
  Moog's Ocean Structures.
- Not played. The boats stand empty until the crews patch.
- The hot air balloon carries illagers as well as players (`HotAirBalloon.canAddPassenger`), for Jaymo's Kingdoms' hit
  squads that come down from the sky.

- **Knocked apart.** A knock on one of the undead that can lose parts may take an arm or a leg off it in place of
  throwing it back: 6% for any of them, 12% for a zombie, 35% for a husk or a weakened zombie (under half its health,
  or under Weakness). `Dismember.knockedApart`, called from `KnockApartMixin` on `LivingEntity.knockback`. Never
  players, and never MCA's newly turned villager zombies. Compiled, not played.
