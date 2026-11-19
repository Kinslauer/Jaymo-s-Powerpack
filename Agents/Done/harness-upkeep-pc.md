---
status: done
task: harness-upkeep
branch: claude/harness-upkeep-2
with: Claude Code (PC)
pr: 1870
mods: kingdoms, powers
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Fitting.java
  - src/main/java/com/jaymospowers/core/wet/ArmourOil.java
  - src/main/java/com/jaymospowers/core/wet/Rust.java
---
# Harness 5: upkeep (PC Claude)

**Task:** dents, rust and oil, fitting (Harness Pattern Book rows wr-dents, wr-rust, wr-fitting). Andy: "merge all the harness stuff when complete".

## Notes
- Powers already rusted the four armour slots (Andy: "nothing removes rust"): oil only keeps new rust off.

## Done
PR #1870, merged. Plate shows dents as it wears; the harness rusts; Armour Oil keeps rust off for three days; an armourer fits your plate to you (lighter, +0.5 armour each). Built on the Deck and CI; not played.
