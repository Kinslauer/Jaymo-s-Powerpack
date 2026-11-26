---
status: blocked
task: Structures 3: aqueduct curse quests - Overworld aqueduct, cursed villages within 300 blocks, four board quests to clear the ruin and lift the curse
branch: claude/structures-3-aqueduct-curse
with:
pr:
mods:
  - kingdoms
versions:
  - 0.7.x
started: 2026-11-26
owns:
  - kingdoms new SettlementCurse.java
  - kingdoms lang/parts/aqueduct-curse.json
  - kingdoms Overworld aqueduct structure (new or existing)
---

# Structures 3: aqueduct curse quests

**Task:** Build the Overworld aqueduct structure (Veradexian is Aether-only), a SettlementCurse system marking villages within 300 blocks as cursed, four quests from the village board that clear the aqueduct ruin, and a lifted state that changes the village.

## Plan
- [x] Research existing aqueduct structure (Veradexian in Aether) and Overworld structure placement in Kingdoms
- [x] Design Overworld aqueduct structure (new structure in Kingdoms, tagged in ruins_with_heirs)
- [x] Design SettlementCurse attachment/system for cursed villages (4 stages: dormant→signs→full→lifting→lifted)
- [x] Design four board quest templates: Stem the Flow, Clear the Pillars, Purify the Stones, Break the Anchor
- [x] Design lifted village state changes (blessed water, Aqueduct Keeper villager, 2x crops, iron golem patrols)
- [x] Draft full specification for Andy's approval — see Inbox/structures-3-aqueduct-curse-design.md
- [ ] Implement after Andy's approval

## Notes
- Task notes: "Identify the Overworld aqueduct and recover full section 5/four quest specifications before implementation; do not invent the missing plan."
- "YOUTH delegated this to Apollo (Claude approved): draft the four aqueduct-curse quests and the cursed-village lift from Lore.md; Andy's yes before any building."
- Previous run stopped before finishing; carried on from research/design phase.
- The "plan section 5" refers to "Structure placement and factions plan" (Claude artifact) which I cannot access directly — reconstructed from context.
- Design draft posted to vault, task set to blocked awaiting Andy's approval.

## Done
- Design document created at E:/Jaymo Vault Live/Inbox/structures-3-aqueduct-curse-design.md
- Task status: blocked (awaiting Andy's review/approval)