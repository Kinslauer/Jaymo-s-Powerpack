---
status: stalled
task: hands-on-cooking-realtime
branch: claude/hands-on-cooking-realtime
with: 
pr: 
mods: Kitchen
versions: 
started: 2026-11-19
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/HandsOnStation.java
  - kitchen/src/main/java/com/jaymoskitchen/cooking/HandsOnSession.java
  - kitchen/src/main/java/com/jaymoskitchen/net/payloads/HandsOnStartPayload.java
  - kitchen/src/main/java/com/jaymoskitchen/net/payloads/HandsOnInputPayload.java
  - kitchen/src/main/java/com/jaymoskitchen/net/payloads/HandsOnSyncPayload.java
  - kitchen/src/main/java/com/jaymoskitchen/net/payloads/HandsOnFinishPayload.java
  - kitchen/src/main/java/com/jaymoskitchen/net/payloads/HandsOnAbandonPayload.java
---

# Hands-On Cooking / Realtime Minigames (Phase 1+2)

**Task:** Implement Phase 1 (foundation: HandsOnStation interface, per-player sessions, packets, network, config toggle) and Phase 2 (CHOP: cutting board in-world chopping with knife). Per spec at Agents/Done/hands-on-cooking-realtime-SPEC.md.

## Plan
- [ ] HandsOnStation interface + default impl in Station enum
- [ ] HandsOnSession sealed interface with ChopState, StirState records
- [ ] Packet classes: HandsOnStart, HandsOnInput, HandsOnSync, HandsOnFinish, HandsOnAbandon
- [ ] Network handler registration in Payloads.register()
- [ ] Config toggle: handsOnCooking.enabled
- [ ] CuttingBoardBlockEntity stores Map<UUID, ChopState>
- [ ] ChopHandsOn client handler (swing detection, timing)
- [ ] CuttingBoard BE renderer draws sliding veg + cut marks
- [ ] Build check, pr_checks, open PR

## Notes
- Spec at Agents/Done/hands-on-cooking-realtime-SPEC.md
- Andy approved Phase 1+2 as first PR (2026-11-19 20:17)
- Kitchen mod only; Powers/Kingdoms have no cooking
- Reuse existing Minigames.judgeChop() for server-side judgment
- Reuse existing Payloads patterns for network code

## Done
- 2026-11-20 00:37 No sign of work for 30 min: marked stalled (whoever ran it stopped).
