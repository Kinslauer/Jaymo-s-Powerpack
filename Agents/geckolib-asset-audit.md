---
status: done
task: geckolib-asset-audit (Kanban t_b4c7a3b0)
branch: claude/geckolib-asset-audit
with: 
pr: 
mods: 
versions: 
started: 2026-11-09
owns:
  - 
---
# GeckoLib asset audit

**Task:** Inventory GeckoLib model, texture and usable animation coverage for every relevant registered Kingdoms entity, explicitly including Omen, Redstone, Sculk and all dragon species. Post the grouped checklist to Kanban and PR #1198 without changing assets.

## Plan
- Resolve GeckoLib entities from registration, renderer and entity classes.
- Match GeckoLib 5 model/animation paths and actual entity textures; parse every animation JSON for non-placeholder clips.
- Cross-check registered names against lang entries and post sections A/B/C plus complete coverage context.

## Notes
- Inventory only; no repository files or assets are owned or modified.
- A separate running note, geckolib-coverage-audit (Kanban t_68218287), owns docs/geckolib-coverage.md; this task does not touch it.

## Done
What a player will see, and what wasn't tested.
