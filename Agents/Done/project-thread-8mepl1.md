---
status: done
task: Magic FX typed system (Andy, project thread, 2026-11-11)
branch: claude/project-thread-8mepl1
with: claude-cloud
pr: 1306
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
# Magic FX: every effect has a type

**Task:** Andy: "Magic FX need an overhaul, i dont like the recolored firework or redstone particles. Every effect should
have a type, (beam, blast, miasma, etc) and go to the same asset for each one, like the beacon for beams".

## Plan
- Types in Powers (`MagicFx`): beam, blast, ring, orb, sigil (packets, drawn with the beacon beam texture), mote and
  miasma (particles). Kingdoms reaches them through `Fx`.
- Swap every recoloured dust and firework flash in magic and ki effects for a type.

## Notes
- The swap touches about 130 files, one or two lines each (`new DustParticleOptions(` became `MagicFx.wisp(`). Anyone
  with an open branch on those files: keep your side on a conflict, then rerun the swap on the line.
- Not touched: `LoreFilmClient` (film thread, PR #1284). Film effects can call `MagicFxClient.beam/blast/ring/orb/sigil`
  on the client and `Fx.wisp` for particles once this is merged.
- New effects: see `docs/MagicFx.md` (rule also in CLAUDE.md).

## Done
PR #1306 is up (2026-11-11). A player will see: spell, golem and ritual beams as beacon-style beams; transformations,
surges and flashes as swelling blasts; rings as shockwaves; a Sanctuary as a turning sigil; every recoloured dust as a
glint (mote) or a soft dark puff (miasma). Compiles for Powers and Kingdoms; not played.
Merged 2026-11-11 (Andy: "They look great merge").
