---
status: done
task: rig-beetle-3-back-rig
branch: claude/rig-beetle-3-back
with: Claude Code (PC)
pr: 1777
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigBack.java (new)
  - src/client/java/com/jaymospowers/client/rig/RigBackClient.java (new)
  - hook lines: RigPicks.java, RigMoves.java (length default), RigMenuScreen.java (5 entries), RigSuit.java (extract + submit)
  - tools/gen_rig.py (back() only) + textures/entity/rig/back.png, back_glow.png
  - src/main/resources/assets/jaymospowers/lang/parts/rig_back.json
  - changes/powers/rig-beetle-3-back.md
---
# rig-beetle-3-back

**Task:** Rig patch 3: the back rig (folding wings, wing slash, spear legs, pincers grab/throw, grapple claw, horn lightning).

## Notes
- Taken over from a stalled Hermes run (no branch was left).
- Menu ids 60-64 and move ids 40-45, clear of rigs 1, 2 and 4, which already collide with each other (moves 13+, menu ids 29+): whoever merges second among those must renumber.
- Not done: the grapple doesn't use WebHeft's rope weight (a straight pull), and the wings don't add lift in turns.

## Done
Wing vanes spread in full flight; Wing Slash, Spear Legs, Pincers (grab/throw), Grapple Claw and Horn Lightning are Rig menu entries. Compiled on the PC, CI passes; not played.
- 2026-11-18 19:37 PR #1777 is merged into master; moved to Agents/Done.
