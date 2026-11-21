---
status: in-review
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: hermes-a
pr: 1961
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
text_pass: done
---

# Z Fighter gaps 8: raids (crashed pod, razed Verdant village, demon camp, future machine army)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Crashed pod raid** (DMZ Saiyan Assault): a pod crashes nearby, its Warrior-born calls for help, under the full moon the reinforcements turn ape.
- **A razed Verdant village** (DMZ Frieza's Invasion): you come on a Verdant settlement burned by a tyrant's soldiers, attackers still near.
- **Demon army camp** (DMZ Gomah's Army): a camp in the demon realm; raising its alarm brings the army.
- **A machine army and its bases** (DMZ Red Ribbon). **Andy's note:** "only in the future with our mechs and androids": only in the future dimension, built from our existing mechs and androids.

**Where to start:** ZFighterInvasions.java, ZFighterRaiders.java, ZFighterRaids.java, ZFighterDemonRealm.java, ZFighterSpaceMissions.java.

## Rules
- One PR, branch `claude/zfighter-gaps-8-raids`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
- 2026-11-20 19:35 hermes-b's claim had no sign of work for 30 min: handed back as open.
- 2026-11-20 19:35 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-20 23:56 Text pass: reviewed powers/src/main/resources/assets/jaymospowers/lang/parts/zfighter_tournaments.json — 19 new gap_raid raid strings (kind names, announcements, entity names); keys, %s placeholders and announcement voice all match existing strings, no rewrite needed. Rewrote changes/powers/zfighter-gaps-8-raids.md — dropped the internal checklist meta line and replaced the ZFighterRogue class name with the player-visible 'Rogue Fighters'. Built: sh ./gradlew build BUILD SUCCESSFUL; pr_checks.py passed. Pushed to claude/zfighter-gaps-8-raids (PR #1961).
