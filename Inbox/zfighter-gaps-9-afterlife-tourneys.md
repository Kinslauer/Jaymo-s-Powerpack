---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 9: borrowed time, spared masters' homes, themed tournaments

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Borrowed time from the dead** (DMZ Baba's temporary revive): the fortune teller lends minutes among the living; forms burn it faster; only a wish makes it lasting. Keep her lasting return too, and the death rulings (#998/#1011).
- **Masters' homes are spared**: ki doesn't break blocks in masters' homes and arenas can't be built over. Mob griefing stays on everywhere else (PackRules).
- **Themed tournaments**: tournaments with their own host, rules and prize (a tyrant's games, the fortune teller's five fighters), beside the kingdom tournaments, last-one-standing and the dead's tournament.

**Where to start:** ZFighterAfterlife.java, ZFighterLookout.java, ZFighterArena.java, ZFighterTournaments.java.

## Rules
- One PR, branch `claude/zfighter-gaps-9-afterlife-tourneys`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
