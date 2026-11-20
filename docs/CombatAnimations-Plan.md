# Combat animations

Andy (2026-11-15): Epic Fight style combat animations, eventually for every humanoid mob, boss and player, as much of
the body as possible visible in first person. Blender or Blockbench unknown, so a trial of both first. His answers:
Claude animates (Blender run headless by script); keep the blocky body (no smooth weighted mesh); longsword.

## Patch 1, the trial (this PR)

Three Still Water longsword moves (Lore.md, "The schools' styles"): a middle guard, a first cut, a heavy overhead.
Both routes key the right arm with the same poses and timing, so they differ only in what each tool adds:

| | Blockbench route (default) | Blender route (resource pack "longsword trial, Blender moves") |
|---|---|---|
| Source | `art/combat_trial/longsword_blockbench.bbmodel` | `art/combat_trial/longsword_blender.blend` |
| Script | `tools/combat_trial/blockbench_route.py` | `tools/combat_trial/blender_route.py` |
| Left hand | keyed by eye at each key (forward kinematics) | inverse kinematics to the hilt every frame |
| Curves | Catmull-Rom; linear into the strike | Bezier; quadratic ease-in into the strike |
| Left hand off the hilt, worst | guard 2.0, cut 4.7, overhead 6.7 px | guard 2.0, cut 4.4, overhead 3.3 px |

The guard's 2 px is out of reach for both: the blocky arm reaches about 10 px from the shoulder, and the hilt sits about 12 px away.
The sword's geometry is the longsword's own sprite through its third-person display (tools/combat_trial/body.py).
`art/combat_trial/longsword_trial.png`: the key frames, a row per move and route (guard, first cut, overhead; Blockbench
then Blender in each pair).

Facts found on the way (Player Animation Library 1.2.8, Better Combat):
- PAL reads Emotecraft JSON (`player_animations`) and GeckoLib-format files; ours stay Emotecraft, like our other swings.
- PAL keeps a `bend` per limb but draws nothing with it: SwingElbows draws it with KneeBend and turns the held item.
- PAL fades an attack out after `endTick`; keys after it don't show. Better Combat's held pose (`pose`) plays on the
  `bettercombat:pose_main_hand_body` layer, the attack on `bettercombat:attack`.
- PAL turns an Emotecraft torso yaw the other way from the game's (stepped outside the game). Whether the arms turn with
  it in game wasn't checked, so the trial keeps the torso still.
- No wrist: the blade lies across the fist, so the hands lead and the blade follows the forearm.
- Without the elbow bends (mobs, a left-handed swing) the first cut's windup shows the sword raised forward from the
  shoulder instead of drawn back over it; the other keys look about the same.

## After Andy picks a route

Weapon stances (idle, walk, run per weapon), hit reactions, dodge and guard; elbows and the guard pose on mobs
(Kingdoms' PalMobAnimator); every humanoid mob; the bosses; the first-person body and camera.

## Parkour animations (added 2026-11-20)

Andy (2026-11-20, Claude app): overhaul the movement/parkour system and its animations, using Assassin's Creed Unity's
freerun animations and real parkour videos as the inspiration. Plan only; nothing built yet.

**How the references are used.** Footage is watched for timing, weight shift, hand placement and silhouette, then the
moves are keyed by hand on our own blocky rig, the same way as the longsword trial. Nothing is taken out of Unity's game
files (Ubisoft's, all rights reserved), and nothing is traced off someone else's video. If motion capture from video is
tried (DeepMotion, Rokoko Video, Move.ai and similar), it is run only on footage we film ourselves. A free motion
library (CMU's, Mixamo) is used only if its licence lets us ship the result.

**What we have.** The moves exist already; what they lack is keyframed motion. Their poses are bent in code, frame by
frame:

| Move | Code | Pose today |
|---|---|---|
| Climb, cling, slip | `Freerun`, `FreerunClient` | `WebParkourPose`, `ClimbFirstPerson` |
| Vault (kong, lazy, monkey flip, thief, at random) | `Freerun` (Freerunner 1) | `WebParkourPose` |
| Wall kick, wall run, vertical wall run, pole climb | `Freerun`, `FreerunWalls` | `WebWallRunPose`, `UpRunPose` |
| Ledge hang, mantle, shimmy, castaway, crouch grab | `Ledge`, `LedgeClient` | `LedgePose` |
| Bar swing | `Freerun` | `BarHangPose` |
| Balance on narrow tops | `Freerun` | `BalancePose` |
| Fast run, slide | `FastRun`, `Slide` | `RunPose` |
| Landing roll, safety tap, hero landing | `LandingRoll` | |
| Air flip, trick jump, backflip, air tricks, air roll | `AirFlip`, `TrickJump`, `Acrobatics`, `AirRoll` | `RunMovesPose` |
| Long jump, charge jump, sky dive, leap into prone | `moves/` package | `RunMovesPose` |
| Leap of Faith | `Freerun` | |

`WebParkourPose` notes that your own moves don't show in first person, apart from what `ClimbFirstPerson` and
`CameraFollow` add.

**Part A: re-key the moves we have.** Each pose class above swaps its code-bent pose for a Blockbench clip played with
`KeyframeClip` (as `CrawlPose`, `MountClimbPose` and `SlingPose` already do), with elbows and knees bending, blended in
and out so nothing snaps. Unity is the reference for the order of a move: hands land, weight drops, legs follow. First
person gets the same clip through the body, as far as First-person Model and `CameraFollow` allow.

**Part B: Unity moves we don't have (Andy to pick).** Found by searching the codemap for each; not found doesn't mean
impossible to have missed, so each is checked again before it's built.
- **Climb down (controlled descent):** Unity's own addition. Walk off a roof edge holding a key and you drop to hang on
  the edge, then climb down the wall hand under hand.
- **Drop to hang:** from a ledge hang, let go to catch the next ledge below.
- **Cat leap:** a running jump across a gap that catches a wall's top edge with the arms and pulls up.
- **Tic-tac:** one step off a wall to get over or around something in front of it.
- **Corner wrap:** climbing round an outside corner of a wall without letting go.
- **Side and back eject:** from a climb, push off sideways or backwards to a ledge or bar.
- **Window dive:** a run at an open window or gap dives through it and rolls.
- **Vault by speed:** pick the vault from the run's speed and the obstacle's height, not at random.

**Lore.** The moves are the Kingdoms Stealth tree's second page (Freerunning), and Lore.md already has rooftop runners:
the Ash Whisperer's cat burglar, and the jacks that leave their wearer "quick enough to climb". New moves are placed
there once Andy has picked them.

**Questions for Andy.**
1. Re-key first (Part A), add the new moves first (Part B), or both together?
2. Which Part B moves?
3. Blockbench for these, as with the NPC actions (2026-11-19)?
