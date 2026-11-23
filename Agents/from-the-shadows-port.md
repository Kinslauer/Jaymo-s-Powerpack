---
status: running
task: From the Shadows Reborn port into Kingdoms (Inbox/from-the-shadows-port.md)
branch: claude/fts-froglin-plague (track A), claude/fts-nether-beasts (track B), claude/crocs-hunt-monsters (#2012)
with: 
pr: 2012
mods: kingdoms
versions: 
started: 2026-11-22
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/fts/ (new)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/inhabitants/fts/ (new)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/ fts files, sounds/fts/, textures/entity/fts/
  - kingdoms/src/main/resources/licenses/from-the-shadows/ (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/wild/Crocodiles.java
---
# From the Shadows Reborn port

**Task:** Andy picked all six From the Shadows Reborn rows (2026-11-22); "pick up the from the shadows reborn" (desktop
session, 2026-11-22). Nehemoth and Bulldrogioth in the demons' country approved, "but make them hostile to any life down
there". Also: "crocodiles in the overworld should also be hostile to other hostile mobs" (PR #2012).

## Plan
- Jar: `E:/Downloads/fromtheshadows-3.0.jar` (NeoForge 3.0, Sonmok14 and L_Ender; CurseForge page: AGPL v3). Unpacked and
  decompiled (Vineflower) in the session scratchpad, read only.
- Track A (editor agent): Froglin + Plague Bearer, one PR each.
- Track B (editor agent): Nehemoth + Bulldrogioth, one PR each.
- Then: gear (diabolium, crust armour, weapons, horn, heart), then effects and enchants.

## Notes
- Demons' country = the Nether's depths (Powers' `ZFighterDemonRealm` biomes; Lore.md "The demons' country").

## Done
