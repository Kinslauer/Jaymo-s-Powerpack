---
status: claimed
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: hermes-b
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 1: stats (strike stat, crits, piercing, legs, stat web, weight readout)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **A sixth stat for rush blows** (DMZ Strike Power): its own stat for rushes, grab slams and the hold combo's finisher; Strength stays for plain blows. Old characters keep their levels.
- **Critical blows** (DMZ crit chance/damage): late TP buys for a chance of a much heavier blow. **Andy's note:** "can impale through humanoids on kill. leaving them standing shortly as they realize what happened" (a killing crit runs the fist through a humanoid, who stays standing a moment before falling; fits keel-over deaths and the downed/beheaded rulings: only a kill, never on a downed captive).
- **Armour-piercing and wound-deepening blows** (DMZ Defense Penetration, Healing Reduction): ignore part of armour; hits slow the foe's healing for a few seconds (ties into wounds/bleeding).
- **Jump and sprint training** (DMZ Jump/Sprint skills): small leg bonuses trained by running and jumping, ZFighterGrowth-style.
- **Stat web on the training screen** (DMZ hexagon stats display): the stats drawn as a web on the stats tab.
- **Weight and gravity readout**: tooltip with the weight you carry, the ideal load, the gravity you stand in and the TP bonus it gives.

**Where to start:** ZFighterTraining.java, ZFighterGrowth.java, ZFighterTrainingScreen.java, ZFighterRush.java, ZFighterGrab.java, ZFighterCombo.java.

## Rules
- One PR, branch `claude/zfighter-gaps-1-stats`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
