---
status: running
task: shift-click every harness piece on and off
branch: claude/harness-shift-click-swap
with: Claude (side session of the coordinator)
pr: 
mods: kingdoms
versions: 
started: 2026-11-28
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HarnessMenu.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HarnessQuickEquip.java
---
# Shift-click every harness piece

**Task:** Andy (clip 2026-11-28 8:18 AM): "you should be able to shift click all harness gear". In the clip the
Heraldic Tabard is shift-clicked in the Harness screen while the Cloak is on (both go in the Over place), and it only
hops between hotbar and inventory.

## Plan
- Shift-click a piece whose place is already filled: swap it in, the worn one comes back to the inventory.
- Same in the harness screen (harness places and the four armour slots) and the survival/bauble inventory.

## Notes
- Every harness part already has a place (HeraldryArmour.part); the gap was only the full place.
- No T3/Codex tool in this session and the Hermes bots are off today, so Claude wrote it.

## Done
