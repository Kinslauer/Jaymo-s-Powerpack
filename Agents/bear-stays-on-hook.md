---
status: in-review
task: bear-stays-on-hook
branch: claude/bear-stays-on-hook
with: local_29ede7b7-7e11-4e20-8506-df7674edc787
pr: 2068
mods: kitchen
versions:
started: 2026-11-23
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (HOOKED flag, setHook, onHook)
  - kitchen/src/client/java/com/jaymoskitchen/client/ragdoll/ClientRagdolls.java (settle call)
---
# A bear stays on the meat hook

**Task:** Andy, with clip `why he fall.mp4`: "why doesnt the bear stay on the hook?"

## Done
- Clip: the bear is hung ("Hung up. A cleaver bleeds it") and tips over onto the floor about half a second later, twice.
- Why: the server kept it on the hook. The client draws a still four-legged body as lying on its side at its lowest point, and it didn't know the body was hung. Hung from a 2-block hook, the bear's head rests on the floor, so it went still at once and was drawn lying down.
- Fix: a synced HOOKED flag; a hung body is never eased onto its side. PR #2068, Auto-fix on.
- Not tested in game; the server physics were checked offline with a standalone ragdoll run.
