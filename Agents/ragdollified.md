---
status: in-review
task: Ragdolls track of "Ragdolls and Cover": add Ragdollified and Ragdollified Reactions, Kitchen's ragdolls stand down
branch: ragdollified
with: Lean/Cover track (Kingdoms Cover.java, lean code, keybinds; not touched)
pr: 1661
mods: powers (pack), kitchen
versions: 
started: 2026-11-16
owns:
  - build.gradle (the Ragdollified lines)
  - kitchen/src/main/java/com/jaymoskitchen/compat/RagdollifiedCompat.java
  - kitchen/src/client/java/com/jaymoskitchen/client/ragdoll/RagdollifiedBridge.java
  - changes/powers/ragdollified.md, changes/kitchen/ragdollified.md
---
# Ragdollified

**Task:** Andy's rd- picks (Decisions.md, 2026-11-16): Ragdollified 1.2.2 and Ragdollified Reactions 0.1.3-BETA in the pack, Kitchen's ragdolls stand down for them, Kitchen's CorpseEntity takes over where the ragdoll rests.

## Notes
- Ragdollified API found by javap on the Deck (jar in ~/rdwork): `RagdollifiedApi` (client: hasRagdoll, getState, remove, addSettleListener), `RagdollifiedServerApi` (addSeveredPartProvider ...). Ragdolls are client-side physics, so the server never knows the pose; the settling client reports it.
- Not compiled on this PC (Andy's rule); CI builds it.

## Done
Pushed for CI; see the PR.
