---
status: running
task: critter-armory-fit
branch: claude/critter-armory-fit
with:
pr:
mods:
versions:
started: 2026-11-07
owns:
  -
---

# Critter Armory armour sits wrong on animals

**Task:** Critter Armory's animal armour (added unchanged in Powers 0.20.85) is misplaced on pigs, sheep, and horses. Find whether our own animal models or render changes move body parts the armour layer copies, and fix from our side (compat class or our own models' part names/poses).

## Plan
- Search codebase for pig/sheep/horse model and renderer changes
- Check if Kitchen corpses/ragdolls or other mods remodel animal body parts
- Identify the misplacement cause
- Patch and build-check

## Notes
- Cause not known yet (from task)

## Done
What a player will see, and what wasn't tested.
