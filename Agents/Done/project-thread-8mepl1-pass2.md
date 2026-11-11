---
status: done
task: Magic FX second pass, sparks, runes, real beams (Andy, project thread, 2026-11-11)
branch: claude/project-thread-8mepl1
with: claude-cloud
pr: 1310
mods: [powers, kingdoms]
versions: 
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/core/fx/MagicFx.java
  - src/client/java/com/jaymospowers/client/fx/MagicFxClient.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Fx.java
  - tools/gen_magic_fx.py
  - docs/MagicFx.md
---
# Magic FX second pass

**Task:** Andy said "Start" to: move the end rod, electric spark and enchant particles onto types, and turn dotted
lines and circles into real beams and rings.

## Notes
- New particle types spark and rune; stock looks `MagicFx.LIGHT/CRACKLE/GLYPH/VOID/HEX/GLOW/EMBER/PALE/MURK` (same
  names on Kingdoms' `Fx`, no MURK).
- The swap touches about 260 files, one or two lines each (`ParticleTypes.END_ROD` became `MagicFx.LIGHT`, and so on).
  On a conflict keep your side, then redo the swap on the line.
- Not touched: `LoreFilmClient`, parry and clash sparks, machines, rigs, real portals. List in `docs/MagicFx.md`.

## Done
Compiles for Powers and Kingdoms; not played.
- 2026-11-11 14:13 PR #1310 is merged into master; moved to Agents/Done.
