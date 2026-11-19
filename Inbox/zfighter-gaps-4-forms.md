---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 4: forms (Earthling, Rubber-folk and Divine-born lines, Bio-built past Perfect, mastered steps, outline)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **An Earthling form line of its own** (DMZ Human Buffed/Full Power/Overdrive/Solaris + legendary line): Earthlings get their own steps (bulk, full power, a sun-gold overdrive) instead of borrowing Super. Decide with Andy whether Super stays open to them.
- **A Rubber-folk form line** (DMZ Majin Kid/Evil/Super/Ultra/Innocence Demon): a ladder up to a giant form.
- **A Divine-born form line** (DMZ Glind Buffed/Out of Control/Super/True Form/Transcended/Dark King/Time Power): light and dark branches, a time-slowing top step.
- **Bio-built past Perfect, and a corrupted line** (DMZ Super Perfect/Ultra Perfect/Xeno): two steps past the three Bio forms and a corrupted branch (Corruption).
- **Sign then mastered** (DMZ UI/UE): silver instinct and Ego each get a mastered second step that lasts longer and costs less.
- **Transformed outline** (DMZ transformation outlines): a thin glowing outline round a transformed fighter, client setting to turn it off. Not part of the aura task; don't touch the aura files.

**Where to start:** ZFighterBranchForms.java (uses ids 10+; new ids after REACTOR), ZFighterEarthlingRace.java, ZFighterRubberBody.java, ZFighterDivineGrace.java, ZFighterBioForms.java, ZFighterInstinct.java, ZFighterEgo.java, ZFighterFormMenu.java, ZFighterClientConfig.java.

## Rules
- One PR, branch `claude/zfighter-gaps-4-forms`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
