---
status: in-review
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by:
pr: 1937
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
text_pass: done
---

# Z Fighter gaps 10: camera and HUD (front camera settings, HUD options, ki sense damage tally, travel and combat flight)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Front camera settings**: **Andy's note:** "make it so our f7 cam can be modified". The F7 front camera (client/FrontCamera.java) gets settings: distance, height, side offset and smoothing, in the pack's client settings screen.
- **HUD options**: hide numbers, always-visible values, menu scale, markers over party members.
- **Damage tally on ki sense**: ki sense shows how much harm a sensed foe has taken.
- **Travel and combat flight** (DMZ search/combat fly): switch between a fast travel flight and a nimble combat flight, with a lock.
- Dropped: **Your body in first person** (Andy's note: "we have this").

**Where to start:** FrontCamera.java, CameraPreset.java, ZFighterHudLayout.java, ZFighterClientConfig.java, ZFighterKiSenseClient.java, ZFighterFlightClient.java.

## Rules
- One PR, branch `claude/zfighter-gaps-10-camera-hud`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
- 2026-11-20 02:55 Text pass on PR #1937 (zfighter-gaps-10-camera-hud): reviewed every player-visible string the PR adds or changes. powers lang camera.json: new front camera labels/tooltips (distance, height, side offset, smoothing) - plain, kept. powers lang core.json: four carry messages use "sneak + right-click" to match the sibling messages - consistent, kept. powers lang hud_bars.json: three keys removed (taking_hold, winding_up, heaving) along with the code that used them - consistent, kept. powers lang wounds.json: downed.cant_take_hold renamed to downed.kneel_first with new kneel instruction; Downed.java references only the new key - kept. powers lang zfighter_senses.json: new HUD option, damage tally and flight mode labels/tooltips - plain, kept. kingdoms gui-story.json hint.4 and kneel-first.json hint.169: reworded to the current "hold use / kneel" instruction - kept. change note changes/powers/claude-zfighter-gaps-10-camera-hud.md: describes the player-facing features - kept. Every lang key, %s placeholder and format code kept; no code changed. No string needed rewriting - all are already plain, brief and in the existing voice, so no files were edited.
- 2026-11-20 08:38 Review (PR #1937, vault coordinator, reviewer away): changes needed. 1) Camera settings are only config getters/setters; nothing adds them to the pack's client settings screen as the task asks. 2) HUD options (hide numbers, always visible, menu scale, party markers) are read nowhere in the HUD drawing. 3) Ki sense damage tally is stored in ZFighterKiSenseClient but never drawn. 4) No key or toggle to switch/lock travel vs combat flight; only a config flag. 5) FrontCamera.updateSmoothPosition calls CameraPreset.syncFrontCameraSettings every client tick in front view, rewriting shouldersurfing-client.toml 20x/s (and Shoulder Surfing likely won't reload it); write only when a setting changes. The smoothing text '(0 = instant, 1 = no movement)' is the inverse of the lerp. 6) CameraPreset MARKER bump to v3 re-applies the preset over players' own Shoulder Surfing settings; also '\\t' became literal tabs. 7) Indentation mangled in ZFighterFlightClient, ZFighterHudLayout, ZFighterKiSense, ZFighterSensesNet. 8) Change note reads as a dev note (code paths, Files Changed list); write it MODLOG-style for players. 9) 'Always Show HUD' shows the Z Fighter HUD to non-Z Fighters; gate it on the power.
- 2026-11-20 06:21 hermes-a's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-20 06:21 hermes-a's last run stopped before finishing; handed back as changes so it can carry on.
- 2026-11-20 15:20 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-20 15:20 hermes-fixer's claim went stale (nothing changed for 93 min); handed back as changes so a bot can take it.
- 2026-11-20 18:11 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-20 18:11 hermes-fixer's last run stopped before finishing; handed back as changes so it can carry on.
- 2026-11-20 18:57 Text pass: camera.json front camera smoothing tooltip made plain (dropped the inverted '(0 = instant, 1 = no movement)' lerp claim); changes/powers/claude-zfighter-gaps-10-camera-hud.md rewritten player-facing (no code paths, method names, file list or 'Files Changed' section; keeps a not-played-in-game line); zfighter_senses.json checked and kept plain and brief. No code changed. Built with sh ./gradlew build (BUILD SUCCESSFUL) and pr_checks.py passed. Pushed to PR #1937 branch claude/zfighter-gaps-10-camera-hud as 640c383fe.
- 2026-11-20 19:04 Correction: the final push to PR #1937 is d31909c9b (text pass 640c383fe merged onto the PR branch's code base 97c001fe1 so the hand-in check finds the branch head on GitHub). Both text fixes are on GitHub's claude/zfighter-gaps-10-camera-hud; pr_checks passed and PR checks are green.
