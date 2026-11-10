---
status: open
for: prometheus
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-modeller
pr:
suggested_owns:
  - src/main/java/com/jaymospowers/armory/FlintlockDraw.java
  - src/main/java/com/jaymospowers/armory/DualFlintlocks.java
  - src/main/java/com/jaymospowers/client/armory/MusketSights.java
  - src/main/java/com/jaymospowers/power/arsenal/ArsenalFlintlock.java
  - src/main/resources/assets/jaymospowers/models/item/flintlock_3d.json
  - src/main/resources/resourcepacks/held_guns/assets/weaponmod/items/musket*.json
  - src/main/resources/resourcepacks/held_guns/assets/weaponmod/items/musketbayonet*.json
  - build.gradle (Powers)
  - src/main/resources/fabric.mod.json
---

# Musket and flintlock: GeckoLib animations with smoke and fire on the blast

Both guns fire without any motion at all. Tiv wants them animated, with a proper muzzle blast.

## What's wanted

Tiv, in the Tasks thread: "create a task for using gecko lib to animate the musket and flintlock they need alotta smoke and fire to acoompany the blast."

## What exists

- The flintlock is two guns: Arsenal's Void Flintlock (`jaymospowers:flintlock`, 3D item model `flintlock_3d.json`, credit Dueling Pistol Blockbench Workshop CC BY 4.0) and WeaponMod's `weaponmod:flintlock` (dual wielded via DualFlintlocks).
- The musket is WeaponMod's musket (wood, stone, iron, gold, diamond, netherite, plus bayonet variants), held as 3D item models in the `held_guns` resourcepack.
- Neither moves: static item models, no GeckoLib, no animation. ArsenalFlintlock already spawns smoke (SMOKE, LARGE_SMOKE, REVERSE_PORTAL) and a flash (FLASH particle) plus GENERIC_EXPLODE and CROSSBOW_SHOOT sounds on fire — so the blast's particle effects exist; they just aren't tied to any animation.
- Powers does not yet compile against GeckoLib; Kingdoms does (`kingdoms/build.gradle`).

## Where to start

- `src/main/java/com/jaymospowers/power/arsenal/ArsenalFlintlock.java` — the Void Flintlock's fire, its particles and sounds.
- `src/main/java/com/jaymospowers/armory/FlintlockDraw.java` and `DualFlintlocks.java` — how flintlocks are drawn and dual-wielded.
- `src/client/java/com/jaymospowers/client/armory/MusketSights.java` — first-person hands for guns.
- `src/main/resources/assets/jaymospowers/models/item/flintlock_3d.json` — the Void Flintlock's static model.
- `src/main/resources/resourcepacks/held_guns/assets/weaponmod/items/musket*.json` and `musketbayonet*.json` — the musket models.
- `tools/bbmodel_geckolib.py` — converts a Blockbench model into GeckoLib's files (see `docs/AI-HANDBOOK.md` "Animating a Blockbench model with keyframes of our own"; worked example Notus and Boreas, PR #1177).

## How

- Add GeckoLib to Powers' `build.gradle` and as a `depends` in `src/main/resources/fabric.mod.json`, the way Kingdoms does.
- Model each gun in Blockbench (the existing flintlock model may be reused; credit stays with it), write keyframes with a script, convert with `tools/bbmodel_geckolib.py`.
- Animation: the draw-up, the aim, the blast (recoil, muzzle lift), smoke and fire pouring out of the barrel at the shot, the spent state. The musket should feel heavier and slower than the flintlock.
- Wire the animation into the item renderer / first-person arms so it plays on use, not just sitting in the hand.
- Keep credits and licences beside the art and update `textures/entity/bb/CREDITS.txt`. Add a change note.
- Build, and boot `runServer` once. Say plainly it hasn't been seen in game.

## Notes

- "Alotta smoke and fire": the blast should look like a blast — more and thicker smoke than today's eight LARGE_SMOKE particles, fire particles at the muzzle, lingering smoke after the shot. Say how many and how long in the PR.
- Don't change how the guns behave (damage, range, reload, dual wield) — only how they look and sound when they go off.
- The Void Armory's summoned flintlocks already dissolve with smoke; keep that consistent with the new animation.
- The musket and flintlock each get their own animation rig — they do not share one.

## Report

PR says what a player sees when the guns fire (smoke, fire, recoil), and a "not tested in game" line if it hasn't been placed in a hand and fired.
