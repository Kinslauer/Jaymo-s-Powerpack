# hits and pickups heard again - downed hits land, ImmersiveFX stands down for Sound Physics
- **Hitting a downed body sounds and looks like a hit** (`Downed.blowLands`): a player's blow on anyone downed (every
  humanoid, villagers, tamed animals) takes no health, so vanilla only played its soft no-damage swish and the only
  answer was a quiet, low player grunt. Now each blow that counts a wound gives the thud of a full swing (arrows keep
  their own hit sound), the red hurt flash and flinch every client sees, and the body's own hurt cry at its own voice
  (a vindicator grunts as a vindicator). The killing blow (three hearts or more, or the tenth wound) gets the thud and
  flash, and the death cry follows as before. Andy: "attacking the vindicator didnt have any impact or reaction sounds".
- **Sounds no longer drop out with ImmersiveFX beside Sound Physics Remastered** (`client/SoundModsPreset`, new
  preLaunch entrypoint; `client/TomlPreset` holds the TOML writer `CameraPreset` used): both mods put their own echo and
  muffling filters on every sound. ImmersiveFX stands down when it sees a sound physics mod, but it looks for the old id
  `soundphysics`, not Remastered's `sound_physics_remastered`, so both ran on the same sounds: the likeliest reason hits, pickups and at
  times all sound went missing (Andy: "pickup sounds are broken too", "the sound dropped while sprinting"). Once per
  install, ImmersiveFX's "Enable Enhanced Sounds" is switched off in `config/immersivefx.toml` (marker
  `jaymospowers-sound-mods-preset-v1`); its ambience and footsteps stay, and Sound Physics keeps the echoes.
- Built; not played. The ImmersiveFX fix can't be tried in the dev game (ImmersiveFX is kept out of it).
