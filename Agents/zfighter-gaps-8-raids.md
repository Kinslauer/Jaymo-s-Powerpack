---
status: blocked
task: zfighter-gaps-8-raids
branch: claude/zfighter-gaps-8-raids
with: 
pr: 
mods: powers
versions: Powers 0.26.22
started: 2026-11-20
owns:
  - Raid content (powers): crashed pod, razed Verdant village, demon army camp, future machine army bases — built on ZFighterInvasions siege + ZFighterRogue tyrants
---

# Z Fighter gaps 8: raids (crashed pod, razed Verdant village, demon camp, future machine army)

**Task:** Add Z Fighter raid content: a crashed pod raid (Warrior-born calls for help; under the full moon reinforcements turn ape), a razed Verdant village (attacked by a tyrant's soldiers, attackers still near), a demon army camp in the demon realm (raising its alarm brings the army), and a future machine army with bases (only in the future dimension, built from existing mechs and androids). Reuse player-model fighters, MCA villagers and existing mobs — no new original mobs.

## Plan
- Build on `ZFighterInvasions` (already exists: themed siege with waves, defenders line, boss bar, prize shared by damage, `ZFighterRogue` tyrant) and `ZFighterRaids` (lone tyrant + `defeated` prize split by `hurtBy` contribution).
- Crashed pod: a pod lands nearby, its Warrior-born calls for help; under the full moon reinforcements turn ape (tie into `ZFighterGoldenApe` / ape forms).
- Razed Verdant village: a burned `ZFighterKingdoms` Verdant settlement with tyrant's soldiers still near (reuse invasion soldier squads + `ZFighterRogue`).
- Demon army camp: a camp in the demon realm (`ZFighterDemonRealm`); raising its alarm brings the army (an invasion `Theme.DEMON`-style force).
- Future machine army + bases: only in the future dimension (`ZFighterSpaceMissions` / future), built from existing mechs and androids (`ZFighterAndroid`, mechs).
- Each raid: announce, waves, a tyrant/boss, prize split by contribution, `message.jaymospowers.zfighter_*` lang keys in their own part.
- Change note `changes/powers/claude/zfighter-gaps-8-raids.md`; never touch version/MODLOG/codemap; no slash commands, no discovery chat text, `Abilities.md` rows for new inputs; every API checked against 26.3.
- The aura task owns `ZFighterPower.java`/`ZFighterKiAura*.java`/`ZFighterAuraFlame.java` — one-line hooks only or wait.

## Notes
- **BLOCKED by number order:** the standing rule says the zfighter-gaps tasks build one after another in number order (#4 forms, #5 training, #6 items/wishes, #7 boss, #8 raids, #9 afterlife). #8 may only build after #7 merges.
- #7 (zfighter-gaps-7-bosses) is claimed by me and BLOCKED on Andy's lore answer (where the set-piece boss lives). Until #7 is unblocked, merged, and its PR closed, I will not build or PR #8.
- This note is prep work only (scan of `ZFighterInvasions.java`, `ZFighterRaiders.java`, `ZFighterRaids.java`, `ZFighterDemonRealm.java`, `ZFighterSpaceMissions.java`). No code written, no worktree yet.

## Done
(nothing yet — queued behind #7 per the number-order rule; not compiled, not tested in game)
