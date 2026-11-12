# hits and pickups heard again - downed hits land, ImmersiveFX leaves the pack
- **Hitting a downed body sounds and looks like a hit** (`Downed.blowLands`): a player's blow on anyone downed (every
  humanoid, villagers, tamed animals) takes no health, so vanilla only played its soft no-damage swish and the only
  answer was a quiet, low player grunt. Now each blow that counts a wound gives the thud of a full swing (arrows keep
  their own hit sound), the red hurt flash and flinch every client sees, and the body's own hurt cry at its own voice
  (a vindicator grunts as a vindicator). The killing blow (three hearts or more, or the tenth wound) gets the thud and
  flash, and the death cry follows as before. Andy: "attacking the vindicator didnt have any impact or reaction sounds".
- **ImmersiveFX: Resurrected leaves the pack** (Andy: "having both is making everyone lag so you can remove the
  immersive"). It and Sound Physics Remastered both put their own echo and muffling filters on every sound: ImmersiveFX
  stands down beside a sound physics mod, but it looks for the old id `soundphysics`, not Remastered's
  `sound_physics_remastered`, so both ran. That was the likeliest reason pickups went silent and all sound dropped out
  while sprinting, and the two together cost frames. The build moves it out of the CurseForge profile
  (`removedIdsFromProfile`). AmbientSounds 6, which it replaced, is not brought back; Sound Physics keeps the echoes.
- Built; not played.
