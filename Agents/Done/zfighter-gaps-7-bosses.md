---
status: done
task: zfighter-gaps-7-bosses
branch: claude/zfighter-gaps-7-bosses
with: 
pr: 1986
mods: powers
versions: Powers 0.26.22
started: 2026-11-20
owns:
  - New set-piece world boss (powers): player-model fighter, telegraphed phases, power clash, weak point, shared revives
---

# Z Fighter gaps 7: a set-piece world boss with shared revives

**Task:** Build one set-piece world boss for the Z Fighter power: a player-model fighter (no new mob model), with telegraphed phases, a power clash and a weak point, in a place that fits the lore. Each fighter in the fight has a few revives friends can use on them (the pack's downed/revive), and the reward is split by contribution.

## Plan
- Reuse `ZFighterRogue` (tyrant, player-model fighter: combos, ki blasts, flash step, Super/Super 2 phases, boss bar, hurt-tracking + prize split via `ZFighterRaids.defeated`) as the boss base — no new mob model.
- Add a power clash phase using `com.jaymospowers.core.BeamClash.npcBeam(...)` (NPC side is supported: rank, surges, last stand) so players lock beams with the boss and the knot's position decides who is overwhelmed.
- Add a weak point (a telegraphed exposed moment/part that takes extra damage) and clear phase tells (roar/flash before Super 2, charge-up before clash).
- Shared revives: build on the pack's downed/revive (`ZFighterPower.onDowned`, `Downed.DOWNED_TAG` = `jaymospowers_downed`); each fighter gets a few revives friends can spend on them; reward split by contribution like `ZFighterRaids.defeated`'s hurtBy share.
- Spawn in a lore-appropriate place (see Notes: awaiting Andy's lore pick) and announce it like a raid (`message.jaymospowers.zfighter_raid` style).
- Change note `changes/powers/claude/zfighter-gaps-7-bosses.md`; never touch version/MODLOG/codemap.
- Constraints from the task: the aura task owns `ZFighterPower.java`/`ZFighterKiAura*.java`/`ZFighterAuraFlame.java` — one-line hooks only or wait; no slash commands, no discovery chat text, `Abilities.md` rows for new inputs; every API checked against 26.3 sources.

## Notes
- **Lore (awaiting Andy):** the task says "in a place that fits the lore; quote the lore to Andy before building." Existing lore I can anchor on: the old temples' proving grounds (a patron's champion called down by an offering, a bare stone floor in a dark of its own, Lore.md "The proving grounds"), the demon country's roosts/ring/den (a gorgon keeps an old ruin, violently territorial), or a Z Fighter site (Tower/Lookout, Keeper's blessing). I haven't picked one yet — Andy, which lore/location should the boss live in? Once you pick, I'll build.
- `zfighter-aura-rework` (claude/zfighter-aura-rework) is open and owns `ZFighterPower.java`, `ZFighterKiAura*.java`, `ZFighterAuraFlame.java`: I will not touch those files beyond one-line hooks.
- zfighter-gaps tasks build in number order; this is #7. #4 (forms), #8 (raids), #9 (afterlife) are open but wait on this branch's merge per the standing rule unless Andy says otherwise.
- `BeamClash.npcBeam(Mob, from, dir, reach, power, rank, kind, stop)` is server-authoritative and supports an NPC beam side with `KIND_KI` — good fit for the boss's clash phase.
- `ZFighterRaids.defeated(level, tyrant)` already splits `RAID_TP`/emeralds/xp by `hurtBy` contribution and broadcasts `zfighter_raid_won`; I can reuse that shape for the boss reward.

## Done
- PR #1986: ZFighterLookoutBoss (new), hooks in ZFighterRogue, ZFighterRaids, ZFighterSites; lang in zfighter_lookout.json; Lore.md (new) paragraph. CI build only; not played in game.
- 2026-11-22 10:08 PR #1986 is merged into master; moved to Agents/Done.
