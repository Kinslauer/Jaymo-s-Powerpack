---
name: vampire-eyes-bat-wings
description: Vampire eyes drifted off MCA's face and bat wings were elytra slabs; PR #1052 (Kingdoms 0.7.609 / Powers 0.21.13) moves them onto MCA's body/eyes and remakes the wings
metadata:
  type: project
---
2026-11-05 Andy's clip (Nether start, vampire gliding): "eyes are not synced and whatever wings those are look awful".
- Cause of the eyes: VampireLooks (also WolfTells, EmberLook, heritage lit eyes) drew on the plain PlayerModel, with eyes found on the Mojang skin, while MCA draws its own body and face over players.
- Fix PR #1052 MERGED 2026-11-05 23:26Z (branch claude/project-thread-2mn0jm), built, not played. VampireLooks.skinBody/glowBody use McaLookModels.playerMarks/playerGlow, and VampireLooks.faceEyes uses McaEyes (MCA's face gene eye pixels).
- **How to apply:** any new glowing-eyes or skin layer on players should go through Kingdoms VampireLooks.skinBody/glowBody/faceEyes or Powers client/McaLooks (same API; Powers now compileOnly mcaJar), or it drifts under MCA. Follow-up commit moved Valor Hunter eyes, Wind storm eyes, Flame Avatar char too.
- Wings: new client/BatWings (WornModel: arm + hand bones, scalloped membrane, thumb, unfolds/ripples/sweeps). Texture from tools/gen_bat_wings.py. Jar and preview in /mnt/project-files/vampire-wings-eyes/.. See [[vampires-werewolves]], [[armour-double-pose-mca]].
- 2026-11-06 Andy: "how do i feed in the nether?" -> "yes as long as there arent zombies". PR #1058 MERGED 2026-11-06 01:23Z (same branch, Kingdoms 0.7.615 / Powers 0.21.17): Vampires.netherPrey (grown piglins/brutes/non-zombified Hornkin, bites never infect), bottled blood resets the starve clock (VampirePlayers.fedFromBottle), hint 119.
- 2026-11-06 Andy spec'd the grab-drink (double-tap use = one-hand throat hold, walk, fast drain; release = strong throw; food 16+ breaks blocks softer than stone within 6). Built in PR #1058 (now Kingdoms 0.7.615): BloodGrab (server, GRABBING attachment, Start/Release payloads), BloodGrabClient, BloodGrabPose, hint 120. Never players or downed.
- 2026-11-06 PR #1072 (Kingdoms 0.7.626, MERGED 2026-11-06 fa60e21): grab/bite work on downed prey (drain = Powers wound, 10th kills ~5 s); Kingdoms UseEntity phase "vampire_first" runs before Powers jaymospowers:downed_first so held use doesn't fill revive bar and vampire sneak+use on downed bites instead of Carry lift.
- 2026-11-06 PR #1077 (Kingdoms 0.7.632 / Powers 0.21.30, MERGED 2026-11-06 f4ce382): vampire Dash/Pound/Blood Gaze on Spell Engine bar (FormSkills night_<move>); Gaze = Powers FrostBeam via tag jaymospowers_blood_gaze, 20 ticks from both eyes, red, no frost. My reading: Frost-born damage/hit pace (not Z), 3 s bar cooldown.
- 2026-11-06 PR #1081 (Kingdoms 0.7.634, MERGED 2026-11-06 2fae4cc): Feed bar spell (night_feed -> BloodGrab.feed: throw if holding, else grab/sleeper bite/bite, generous aim). Andy "the current right click ability can move": my reading = removed sneak+use bites and double-tap grab (BloodGrabClient deleted); pool lapping still sneak+use.
