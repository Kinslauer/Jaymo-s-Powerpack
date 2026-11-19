---
status: done
task: smoothmount-6-heave-downed
branch: hermes/smoothmount-6-heave-downed
with: 
pr: 
mods: Powers
versions: 
started: 2026-11-19
owns:
  - powers/src/main/java/com/jaymospowers/core/Carry.java
  - powers/src/main/java/com/jaymospowers/core/wounds/Downed.java
---

# SmoothMount climbing, patch 6. Heave a downed friend onto a horse (Kitchen + Powers Carry + Downed)

**Task:** When carrying a downed player, right-click a horse/donkey/mule/camel to heave them up and sling them over its back. They ride slung until they come to, then can sit up or get off. Respect downed rules (no respawn roll while carried, revive at safety).

## Plan
- Study CorpseEntity.slingOnto (Kitchen) as the model for slinging over a horse
- Add heave logic to Carry: carrying a downed player + right-click horse → heave onto horse's back
- Mark slung player with an attachment (horse they're on), pause downed bleed-out/give-up while slung
- On recover (getUp), end the slung state so they can ride normally or get off
- Add change note changes/powers/smoothmount-6-heave-downed.md
- Build check, pr_checks, open PR

## Notes
- Patch 1 (MountClimbPose, CLIMB_ON_BACK etc.) already on branch hermes/smoothmount-1-climb-on-off; patch 6 reuses its pose class
- slingOnto is Kitchen's CorpseEntity method for corpses; this patch does the same for living downed players via Powers Carry
- Never change version=, MODLOG, or codemap

## Done
- 2026-11-19 22:36 Vault coordinator: taken over by the cloud SmoothMount thread (Andy); this run stops.
