---
status: done
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: hermes-a
pr: 1953
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
text_pass: 
---

# Z Fighter gaps 5: training (drills from masters, grades and challenges, pod pilot drill, shadow stat share)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Drills taught by masters**: each Training Post drill is learned from a master over a few runs (the first, Rhythm, stays open), and training near your master pays a mentor TP bonus. Existing characters keep every drill they have played.
- **Drill grades and challenge runs**: a grade per run (S to D) and a challenge that strings stages together and restarts on a miss.
- **Pod pilot drill** (DMZ gravity minigame): a sixth drill, fly a pod through gaps in rock pillars while gravity pulls it down.
- **Shadow with a stat share** (DMZ Shadow Dummy): pick the share of your strength the Sparring Dummy gets; you lose that share while it lives.

**Where to start:** ZFighterDrills.java, ZFighterDrillScreen.java, ZFighterMasters.java, ZFighterSparring.java, TrainingPostBlock.java.

## Rules
- One PR, branch `claude/zfighter-gaps-5-training`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
- 2026-11-20 13:08 Text pass on PR #1953 zfighter-gaps-5-training: reviewed all player-visible text - lang/parts/zfighter.json (5 new keys: pod_pilot, pod_pilot.help, pod_crashed, zfighter_drill_unlocked, zfighter_challenge_done), changes/powers/zfighter-gaps-5-training.md, and the translatable chat/screen keys in ZFighterDrills.java, ZFighterSparring.java, ZFighterDrillScreen.java. All text is plain and in voice; no rewrites needed, no code changed (text-pass rule). BUILD BLOCKED: :compileClientJava fails with 4 errors in ZFighterDrillScreen.java - mouseClicked near line 496 is never closed, cascading into hitLane/progress/kiControl/precision. Pre-existing in the PR branch; nothing for a text pass to fix. Change note already flags the syntax issues. Tree left clean; nothing to commit or push. Unblock when the code compiles, then hand the pass in.
- 2026-11-21 23:36 Status blocked -> changes (pending-work pass, 2026-11-21): nothing here waits on Andy. PR #1953's Build Powers check fails, so it needs a bot's fixes before review.
- 2026-11-22 8:45 AM Claude (desktop session): fixed the compile failure (gaps-2: unclosed charging block in ZFighterPower; gaps-5: ZFighterDrillScreen rebuilt from master with its indentation and braces back, Pod Pilot re-applied, off-screen pillars now dropped). All checks green. Status changes -> in-review.
- 2026-11-22 10:08 PR #1953 is merged into master; moved to Inbox/Done.
