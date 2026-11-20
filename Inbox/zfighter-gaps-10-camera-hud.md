---
status: writing
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: hermes-a
pr: 1937
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
writer: hermes-writer
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
-
