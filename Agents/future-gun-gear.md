---
status: blocked
task: future-gun-gear
branch: claude/future-gun-gear
pr: 
mods: powers
versions: 
started: 2026-11-16
owns:
  - src/main/java/com/jaymospowers/armory/TaczGuns.java
  - src/main/java/com/jaymospowers/power/chrono/future/
---
# Future's gun gear (tracers, magazines, armour, TaCZ Additions)

**Task:** Build Forge/NeoForge-inspired future gun features as our own Fabric code in Jaymo's Powers (armory/TaczGuns): fu-tracers (glowing streak + dynamic light along a shot), fu-mags (magazine item per gun family, hand-loaded reload), fu-additions (research TaCZ Additions feature list and bring Andy a short pick-list BEFORE building), fu-armour (tactical armour/kit models worn by FutureGuns and lootable). Copy no code or art from the mods. Effects only through typed FX (docs/MagicFx.md). One PR per row is allowed.

## Plan
- Read armory/TaczGuns.java, FutureGuns, MuzzleFlash/TaczMuzzleFlashMixin, ArsenalFutureGun, TaczFirstPerson (docs/codemap/powers.md)
- Decide scope: fu-additions needs research (TaCZ Additions feature list) + pick-list for Andy before any build
- Set up armory module if needed; implement one row per PR; sh ./gradlew clean build + tools/pr_checks.py; changes/powers note; PR; vault.py --set in-review

## Notes
- Builds on Deck/CI, never Andy's PC.
- fu-additions: research first, pick-list before building.
- Can be split into one PR per row (per-row change notes).

## Done
What a player will see, and what wasn't tested.
- 2026-11-18 20:12 PR #1671 is merged into master; moved to Agents/Done.
