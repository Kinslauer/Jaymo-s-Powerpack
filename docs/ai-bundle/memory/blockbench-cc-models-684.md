---
name: blockbench-cc-models-684
description: Six CC BY Blockbench models Andy added (PR #684): pistol, mandrake, Kintsugi, Primal Visage, Launch whaleboat, Skeleton Lord
metadata:
  type: project
  modified: 2026-10-29T16:42:52.950Z
---

PR #684 (MERGED 2026-10-29 19:1xZ, pack 0.17.201 / Kingdoms 0.7.351; next free 0.17.202 / 0.7.352, recheck) adds six CC BY Blockbench Workshop models Andy uploaded:
- Dueling Pistol is the Flintlock's held 3D model; Mandrake sword is the Echo Cleaver's.
- Kintsugi armour (by eanncrod) is a NEW extra set; Andy ruled it must replace nothing AND must not be craftable: only found on possessed MCA zombie villagers walking Nether fortress halls (nether/FortressHaunts, 1-2 guaranteed-drop pieces each).
- Primal Visage armour (by eanncrod): one random piece drops from fallen Nether warlords (NetherForts).
- Launch: whaleboats are drawn as it (Whalers.LAUNCH synced attachment + WhaleboatLookMixin).
- Skeleton Lord boss: sleeping statue in 35% of Overworld golem lairs, wakes laughing, knockback spell.
Tool: tools/blockbench/freeform.py turns .bbmodel cubes into Java item-model elements with free x/y/z rotations (26.3 allows them). Credits in textures/entity/bb/CREDITS.txt. Compile-checked only when opened. Doc listing all Arsenal weapons + model status: https://claude.ai/artifact/AugDLnvbLKZC5GDN7WWRTQ. See [[blockbench-mcp]].
