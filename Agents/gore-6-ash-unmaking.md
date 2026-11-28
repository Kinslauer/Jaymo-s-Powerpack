---
status: blocked
task: gore-6-ash-unmaking
branch: none
with: ash crumble/drift particle sprites and any ash overlay art
pr: 
mods: jaymospowers
versions: 
started: 2026-11-27
owns:
  - 
---
# Ash unmaking: art for a body crumbling to ash

**Task:** Provide the ash crumble/drift particle sprites and any overlay/ash-pile texture for the ash-unmaking gib effect.

## Plan
- Check existing particle styles in jaymospowers/textures/particle/air and blood.
- Draw grey ash crumble and drifting ash sprites (limited palette, no pixel blood).
- Run `texture_lint.py` and fix CHECK lines.
- Hand off to the builder that wires the power(s).

## Notes
- Prometheus is art-only. This task also needs a new power class and a decision on which dark/void/disintegrating powers get the unmaking; that should be handled by a code builder once Andy lists the powers.

## Done
