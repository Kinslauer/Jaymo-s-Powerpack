---
status: running
task: fishing-fire-and-crates
branch: claude/fishing-notes-and-bench
with: 
pr: 
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/fishing/ (FishTackle, TackleBench*, TornNote)
  - src/client/java/com/jaymospowers/client/fishing/ (TackleBenchScreen, JournalScreen)
  - lang/parts/fishing_tackle.json
  - tools/gen_tackle.py
  - changes/powers/fishing-notes-and-bench.md
---
# Fishing notes and tackle bench

**Task:** Second half of fishing-fire-and-crates (Andy said go ahead, 2026-11-18): wet fishers' scraps (torn notes in crates and shipwrecks, read into the Fisher's Journal) and a tackle bench (hooks, lines, floats fitted to any rod). Stacks on #1715.

## Plan
- FishTackle: rod component (hook, line, float), effects in the fight and lure depth
- Tackle bench block, menu and screen
- Torn notes: loot into crates and shipwrecks, Journal shows the hints

## Notes
- Base is origin/claude/fishing-fire-and-crates (PR #1715 not merged yet).

## Done
