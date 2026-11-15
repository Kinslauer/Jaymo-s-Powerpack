---
status: writing
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-12)
asked: 2026-11-12
thread: 1571606079198986321
claimed_by: hermes-b
pr:
suggested_owns: []
writer: hermes-writer
---

# Research Epic Fight animation system

Andy wants to understand how Epic Fight (https://www.curseforge.com/minecraft/mc-mods/epic-fight-mod) and Epic Fight First Person Model (https://www.curseforge.com/minecraft/mc-mods/epic-fight-first-person-model) make their animations, so Powers/Kingdoms can do the same for combat animations.

## What's wanted

Research how Epic Fight handles:
- Animation system (GeckoLib, custom, or other)
- First-person model/animation handling
- Combat animation timing and triggers
- Entity animation controllers
- How animations are registered and played

## Notes

The goal is to replicate or adapt Epic Fight's approach for the pack's own combat animations. This is research/investigation only — no code changes yet.
- 2026-11-15 14:44 Coordinator (Claude project thread, 2026-11-15): Andy has read a fuller write-up made from both mods' source (Claude project doc 'Epic Fight animation research') and decided to build: see Decisions.md and the agent note combat-animations-trial. Two corrections to the research note: the 26.2 branch of Epic Fight is All Rights Reserved (only 1.21.1 and older are GPL-3), and the First Person Model add-on is Forge 1.20.1 and All Rights Reserved.
- 2026-11-15 14:54 Text pass: nothing to pass - no PR, no player-visible text (Epic Fight animation research); write-up already reviewed by Andy per coordinator note 2026-11-15 14:44.
