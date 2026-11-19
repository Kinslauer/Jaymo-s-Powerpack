---
status: done
task: rig-beetle-5-the-mind
branch: claude/rig-beetle-5-mind
with: Claude Code (PC)
pr: 1773
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigTalk.java (new)
  - src/main/java/com/jaymospowers/rig/RigPilot.java (new)
  - src/main/java/com/jaymospowers/rig/RigEvac.java (new)
  - src/main/java/com/jaymospowers/rig/RigLog.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigPilotClient.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigLogClient.java (new)
  - hook lines: Rig.java (init), RigSentry.java (wreck skip), RigClient.java (init, flight), RigHud.java (log fold-out), RigHelmView.java (verdict)
  - src/main/resources/assets/jaymospowers/lang/parts/rig_mind_talk.json
  - changes/powers/rig-beetle-5-mind.md
---
# rig-beetle-5-mind

**Task:** Rig patch 5: the Mind talks in chat, flies you (autopilot), calls threats, evacuates you, fights while you're down, and keeps a combat log.

## Notes
- Branch claude/rig-beetle-5-mind (an older E:/wt/rig-beetle-5-the-mind worktree from a stalled run is left untouched).
- md-voice not built: needs a voice source Andy is happy with (ask him which voice).

## Done
The Mind answers chat commands privately (light, dig out, report, scan, where, recipes, autopilot), flies you to a village or home, adds threat verdicts on the visor, throws you clear when the suit breaks, fires while you're downed, and logs blows with vitals on the HUD. Voice waits on Andy. Compiled on the PC, CI passes; not played.
- 2026-11-19 01:59 PR #1773 is merged into master; moved to Agents/Done.
