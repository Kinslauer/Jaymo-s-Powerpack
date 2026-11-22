---
status: done
for: any
from: coordinator
asked: 2026-11-20
thread: Claude project "Powerpack" (Claude app), Assassin's Creed Unity parkour overhaul
claimed_by:
pr: 1959
suggested_owns:
  - src/client/java/com/jaymospowers/client/freerun/ (the pose classes: BalancePose, BarHangPose, RunPose, UpRunPose, ClimbFirstPerson)
  - src/client/java/com/jaymospowers/client/freerun/moves/RunMovesPose.java
  - src/client/java/com/jaymospowers/client/LedgePose.java
  - the Freerun parts of src/client/java/com/jaymospowers/client/power/web/WebParkourPose.java (the web slinger's own moves stay as they are)
  - src/main/java/com/jaymospowers/freerun/Freerun.java, FreerunWalls.java; src/client/java/com/jaymospowers/client/freerun/FreerunClient.java
  - src/main/java/com/jaymospowers/core/Ledge.java, src/client/java/com/jaymospowers/client/LedgeClient.java
  - src/main/resources/assets/jaymospowers/geckolib/animations/entity/player_freerun_*.animation.json (new)
  - src/main/resources/assets/jaymospowers/bbmodels/player_freerun*.bbmodel (new)
writer: hermes-writer
text_pass: done
---
# Parkour animations: re-key the freerun moves, add climb down, corners and adaptive vaults

**Andy's words (2026-11-20, Claude app):** he wants to overhaul the movement/parkour system and its animations "using
the animation suite from assassin's creed unity and other parkour videos". On the plan (docs/CombatAnimations-Plan.md,
"Parkour animations", PR #1954) he answered: "1. Both together, 2. down climbing, around corners, and adaptive speed,
3. yes, blockbench and geckolib have the best results for our animations".

**What's wanted:** freerunning that moves like Unity's: weighted, with elbows and knees bending, and blended from one
move into the next instead of snapping. Both parts in one patch:
- **Part A, re-key every freerun move we have** as a Blockbench clip played through `KeyframeClip` (GeckoLib-format
  `.animation.json`, the way `CrawlPose`, `MountClimbPose` and `SlingPose` play `player_getup` and `player_mount_heave`):
  climb, cling and slip; the four vaults; wall kick, wall run, vertical wall run, pole climb; ledge hang, mantle, shimmy,
  castaway, crouch grab; bar swing; balance; fast run and slide; landing roll, safety tap, hero landing; air flip, trick
  jump, backflip, air tricks, air roll; long jump, charge jump, sky dive, leap into prone; Leap of Faith. The full table
  of which class poses which move is in the plan. Your own moves should show in first person as far as First-person
  Model and `CameraFollow` allow.
- **Part B, three new moves:**
  - **Climb down:** walk off a roof or wall top while holding the key for it (pick one that doesn't clash with Sneak's
    many jobs) and you swing down to hang from the edge, then climb down the wall hand under hand; the reverse of the
    climb up.
  - **Around corners:** climbing a wall or hanging from a ledge and shimmying sideways, you carry on round an outside
    corner (and an inside one, where there's a wall to turn onto) without letting go.
  - **Adaptive speed (reading taken: the vault chosen by speed):** the vault you do follows how fast you're going and how
    high the obstacle is, instead of one of four at random: a slow approach is a lazy or safety-style vault, a fast run a
    kong or speed vault, a tall obstacle the monkey or thief. If Andy meant something wider (the run itself speeding up
    and slowing with the ground, as Unity's does), ask before building it.

**Reference rule (Andy agreed, 2026-11-20):** Unity footage and parkour videos are watched for timing, weight and hand
placement only. Nothing is taken out of Unity's game files, nothing traced off someone else's video; motion capture
only from footage we film ourselves.

**Where to start:** docs/CombatAnimations-Plan.md ("Parkour animations" and the trial's notes on Player Animation
Library); `.claude/rules/blockbench-animation.md`; `KeyframeClip` and how `CrawlPose` uses it; `Freerun`, `Ledge`,
`FreerunWalls`, `WebParkourPose` (the vault variants and the `Freerun#MOVE` hand-off at line ~137).

## Notes
- Lore: no new thing in the world, so no Lore.md paragraph, as with the ParCool moves and the combat trial. The moves
  stay the Kingdoms Stealth tree's freerunning; climb down and corners go to whoever can climb now (everyone, Climber's
  ranks for grip and speed), the adaptive vault to Freerunner 1 like the vault it replaces. Ask Andy if a rank should
  gate them.
- Big patch: split it into two tracks that don't share files (for example A1 climbing and ledge with Part B's climb down
  and corners, A2 running, air and landing moves with the adaptive vault), 2 at a time.
- Not checked when this was written: nothing built or played; the move list comes from the codemap and the classes'
  own docs.
- 2026-11-20 22:53 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-20 22:53 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-20 23:02 Text pass: PR #1959 parkour-animations (freerun ledge hang/mantle keyframed clips). Only player-visible string is changes/powers/parkour-animations.md; LedgePose.java edits and animation JSONs are code/data with no player-facing text. Change note already plain, brief, player-facing and states testing status; no rewrite needed.
- 2026-11-20 23:07 Text pass: rewrote changes/powers/parkour-animations.md change note body from implementation jargon (GeckoLib clips, hand-coded joint math, resource ids) to a player-facing description of smoother ledge hang and mantle motion; LedgePose.java and the new animation JSONs carry no player-visible text.
- 2026-11-21 23:36 Also open: PR #1954 (Plan: parkour animations, docs only, checks green) waits on review and Andy's 'merge' alongside #1959.
- 2026-11-22 02:30 PR #1959 is merged into master; moved to Inbox/Done.
