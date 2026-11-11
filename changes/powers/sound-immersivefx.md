# sound - ImmersiveFX in, varied sounds, the web sense turns your head
- **ImmersiveFX: Resurrected** 1.9.0 (AGPL-3.0, Fabric 26.3, client-only) joins the pack unchanged: footsteps for each
  kind of ground, armour, wading and swimming sounds, biome ambience heard from where it is, crafting and workstation
  sounds, a stomach growl, auroras, fog, jets, and a sound browser (Ctrl+I). **AmbientSounds 6 and CreativeCore leave**
  (Andy's pick), so there is one ambience system; the build moves them out of the CurseForge profile. Its blood,
  fireflies and waterfalls are left on beside ours (Andy: "the more blood the better").
- **Varied sounds** (`client/sound/Varied`, `VariedSoundEngineMixin`): every one-shot sound in the world plays one of
  seven pitch steps around what was asked (2% apart), never the step it had last time, so the same clink or hit doesn't
  repeat note for note. Loops, music, voices, menu sounds, note blocks, bells, horns, records and Chronokinesis' clock
  ticks are untouched.
- **Web sense glance** (`client/power/web/WebSenseGlance`): when an ascended web slinger's Spider-Sense picks up a new
  danger out of sight (a creature that has just turned on you, a creeper starting to swell, a skeleton drawing), the
  look snaps round to it: the first-person view swings and others see the head turn. It adds to the mouse, stops once
  the danger is in view, lasts at most 12 ticks and waits 50 ticks between glances.
- Compiled (`compileClientJava`); not played. ImmersiveFX has not been run beside Sound Physics Remastered, Subtle
  Effects or Iris yet, and its doubled effects (fireflies, waterfalls, footprints) may want turning down in its config.
