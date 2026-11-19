---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 3: techniques (ki shell, healing ki, twin discs, ring beam, sword technique, purging beam, sticky bind, rift moves, ki-drinking barrier, ki cell)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Ki shell** (DMZ Ki Protection): a trained passive that soaks part of every blow from ki when not guarding.
- **Healing ki for anyone**: a heal shape in the technique creator (a ball or touch that heals a friend).
- **Twin discs** (DMZ Double Kienzan): two discs at once, a step up from Ki Disc.
- **Ring beam** (DMZ Kill Driver): a spinning ring of ki that cuts along a line.
- **Sword technique** (DMZ Shining Sword Attack): a blade flurry ending in a ki burst, for fighter weapons.
- **Purging beam** (DMZ Spirit Breaking Cannon / Soul Punisher): hurts the corrupted and the dark far more, breaks possession and living nightmares. **Andy's note:** "patrolmen like this technique" (Galactic Patrolmen use it: ZFighterPatrol/ZFighterNpcKi, and they can teach it).
- **Sticky bind** (DMZ gum bind): a Rubber-folk wad that sticks a foe; mash to tear free (the out-cold mash).
- **Rift fist and rift step** (DMZ Dimensional Fist/Warp): Divine-born; punch into a small rift to hit from behind, step through a rift over a short gap.
- **Ki-drinking barrier** (DMZ Absorption Barrier): the android's G; ki blasts that hit it fill his ki.
- **Ki cell** (DMZ Ki Battery): a Tinkerer craft that gives back a chunk of ki.

**Where to start:** ZFighterArts.java, ZFighterKi.java, ZFighterDisc.java, ZFighterCreator.java, ZFighterSlots.java, ZFighterPatrol.java, ZFighterNpcKi.java, ZFighterAndroid.java, ZFighterDivineMoves.java, ZFighterItems.java.

## Rules
- One PR, branch `claude/zfighter-gaps-3-techniques`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
