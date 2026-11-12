---
status: running
task: 
branch: claude/project-thread-rpgtwe
with: Claude (cloud thread)
pr: 
mods: powers
versions: 
started: 2026-11-12
owns:
  - src/main/java/com/jaymospowers/core/wounds/Crutches.java
  - tools/gen_crutches.py
  - src/main/resources/assets/jaymospowers/lang/parts/crutches.json
---
# Legless crawl, cane and crutch

**Task:** Andy: "you shouldnt be able to walk upright with no leg, unless you make a cane or crutch", and "crawling players should still be able to haul themselves up one block".

## Plan
- `Crutches.legless` joins `Breaks.crawlOnly`; a server tick puts the legless down and stands them when a cane comes to hand.
- Walking Cane and Crutch items (grid recipes, wood and leather).
- `Crawl.tickHaul`: a full-block step while crawling, slowed after each rise.

## Notes
- Small edits in `Breaks.crawlOnly`, `Wounds.init`, `Crawl`.

## Done
