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
