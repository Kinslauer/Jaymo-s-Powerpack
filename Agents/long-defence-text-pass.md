---
status: done
task: long-defence-text-pass
branch: claude/project-thread-0nu9tv
with: claude (cloud thread)
pr: 1268
mods: kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/lore-films.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/lore/the_long_defence.json
---
# Text pass: The Long Defence

**Task:** check the Long Defence film's player text (book, court wizard button, demon master lines) against Lore.md and rewrite what needs it.

## Done
PR #1268: three lines rewritten (demon master, book pages 5 and 6). Text only.

## 2026-11-11 restage (Andy)
Film restaged in PR #1268: forest + Keep set (block displays), battle engine running the whole film (pairs, finishers with heads/limbs/blood), lightning at the summons, dead rising in random groups, Varro's disarm-and-kneel in-balming, the fifth's run through trees past mana bolts and the sorcerer fading; cameras keep the 180 degree rule and thirds. New file `client/films/FilmPose.java`; also touched `ModelPoses`, `MobSwingLivingEntityRendererMixin`, `Draugr` (stageMaim, stageSwing), `InhabitantsClient`, `LoreFilms.FILM_TICKS` (800). Compiles; not watched in game. A browser preview of the staging is in the thread.

## 2026-11-11 the Quiet One's cloak (Andy)
White hooded look (`gen_film_skins.py` stranger), a living cloak of block-display cloth strips, tethers that wrap dart trees and nearby legion and throw them, swings and flips off trees (`FilmPose.FLIP`), two tethers at the sorcerer on the lunge. All in `LoreFilmClient` (`cloak`, `tether`, `stretch`, `cloth`, `fifth`). Compiles; not watched in game. Preview artifact updated. The model/GeckoLib side of the cloak belongs to the GeckoLib thread.
