---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 7: a set-piece world boss with shared revives

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **World bosses with set mechanics** (DMZ: orb guardians with duel challenge/counter stance, a demon king whose eye you knock loose from behind while pushing back his sphere in a clash, a machine core in phases). **Andy's note:** "can we make a set peice boss?" Build one set-piece boss first (a player-model fighter, no new mob model), with telegraphed phases, a power clash and a weak point, in a place that fits the lore; quote the lore to Andy before building.
- **Shared revives and a top fighter**: in the boss fight each fighter has a few revives friends can use on them (the pack's downed/revive), and the reward is split by contribution.

**Where to start:** ZFighterRogue.java, ZFighterRaids.java, ZFighterBeams.java (clash), ZFighterImpact.java.

## Rules
- One PR, branch `claude/zfighter-gaps-7-bosses`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
