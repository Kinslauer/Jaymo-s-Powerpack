---
status: done
task: inferno-fire
branch: claude/inferno-fire
with: 
pr: 1716
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

## Done
A player gets Inferno 26.3-r1 and StrawberryLib 26.3-r1 (unchanged, in the pack): smog near big fires, burning mobs spreading fire, torches as weapons, blue soul fire, green copper fire, charred trunks, lower fire overlay. Scorched earth is off (core/InfernoPreset writes config/inferno.json once). Compat read from source: charred_log is not in #minecraft:logs so BurntLogMixin still scorches; Inferno wraps removeBlock inside checkBurnOut, ours is HEAD/RETURN; Calamities only places fire; Vampires' torch ignites the same victim (no stacking). Not played; config filename inferno.json and the exact MidnightLib format are from source, not run. CI green.
