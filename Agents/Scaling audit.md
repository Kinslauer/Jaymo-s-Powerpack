---
status: in-review
task: Scaling audit (read-only): every progression line in Powers, Kingdoms, Kitchen; pick-list for Andy
branch: none (read-only)
with: 
pr: 
mods: powers, kingdoms, kitchen
versions: 
started: 2026-11-17
owns:
  - Agents/Scaling audit.md
---
# Scaling audit

**Task:** Andy: "Run the audit" (prompt in `Scaling audit prompt.md`). Read-only audit of how power scales from a
fresh start to the late game; keep the early game weak and earned. Result: a short report and a tickable pick-list page.

## Plan
- 10 scanner groups, 2 at a time: powers growth; Z Fighter/symbiote/Rig/corruption; skill tree + classes (incl. draft
  PR #1698 class trees); heritages/flaws/feats/attributes; faith and curses; weapons/armour; enchanting/potions/food;
  money and land; mob scaling; bosses/champions/Retaking/stealth.
- One verifier per group re-checks every finding against the code.
- Stacking worked per archetype (melee, caster, stealth, gun, Z Fighter); ten worst ranked.

## Notes
- Pick page: https://claude.ai/artifact/EFNrmE5TrKiHQuEGmqkiSJ (picks save to its db at picks/andy). 45 rows, grouped by mod and line for parallel tracks.
- Open questions for Andy: should Ogre King holds / Piglin King / Skeleton Lord stay meetable at Hearth (Lore says legends ignore tiers)? Keep both class XP and pillar XP from one blow (PR #1698)? Early-marked rows (Web physique, ZF fist, Strength XP, day-one attributes, Sworn kit, Dawnborn heal, vampire/werewolf strength, junk rate, metal tiers) make the early game harder: only if he wants that.
- Draft PR #1698: its Assassin fork (ThiefSneak x2.5 + 4) and class XP track feed rows thief and class-xp; its cooking changes no buff numbers.

## Done
Read-only: 10 scans + 5 verifier batches, every finding re-checked against master; wrong claims dropped. Ten worst: uncapped crippled stabs on bosses, armour x1.4 on mobs, player armour capped at Hearth, empty Legend tier for players, blight modifier bug, guns never scale, Z Fighter punch cap eats Super forms, Thief opener stacking, patron champion solo/group pacing, no late money sinks.
