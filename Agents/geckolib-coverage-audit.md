---
status: running
task: geckolib-coverage-audit (Kanban t_68218287; parent omen-geckolib)
branch: claude/geckolib-coverage-audit
with: 
pr: 
mods: 
versions: 
started: 2026-11-09
owns:
  - docs/geckolib-coverage.md
---
# GeckoLib coverage audit

**Task:** Enumerate every registered mob and produce a grouped, definitive checklist of GeckoLib models, textures, animation files and controller wiring so the follow-up modelling work can be split cleanly.

## Plan
- Enumerate registered mob entity types and map each to its entity class and renderer.
- Match GeckoLib 5 model/animation paths and entity textures; inspect animation controller and triggerable-animation wiring.
- Commit `docs/geckolib-coverage.md`, run builds and PR checks, then open a PR with counts per group.

## Notes
- Read-only audit outside the checklist; no mob code or art is owned by this branch.

## Done
What a player will see, and what wasn't tested.
