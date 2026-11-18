---
status: running
task: inferno-fire
branch: claude/inferno-fire
with: 
pr: 
mods: powers
versions: 
started: 2026-11-17
owns:
  - build.gradle (packMods Inferno + StrawberryLib lines)
  - src/main/java/com/jaymospowers/core/InfernoPreset.java
  - src/main/resources/fabric.mod.json (preLaunch entry)
  - changes/powers/inferno-fire.md
---
# Inferno fire

**Task:** Ship Inferno + StrawberryLib unchanged with Andy's default config (scorched earth off), credited.

## Plan
- packMods lines, InfernoPreset prelaunch writes config/inferno.json once, change note, check compat.

## Notes
- Compat read from source: no clash (see Done).
