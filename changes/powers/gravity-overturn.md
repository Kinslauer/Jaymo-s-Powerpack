# Gravity Overturn - reverse an enemy's gravity; people hold on against Lift
- **Overturn** (hold B, press V at a creature up to 32 blocks off, and keep V held): if you're stronger than it (the
  scouter's power level, raised by how much of Gravity you've mastered and by ascension or corruption) or bigger
  (height plus width), its gravity turns over for as long as you hold V. It falls up, drawn upside down, no faster than
  0.5 blocks a tick; a ceiling it hits hurts like a floor (3 blocks free, then 1 a block, up to 20). With open sky above
  it keeps rising, so letting go drops it from however high it got. Deaths from either count as deaths by weight.
- Let go of V within 1.5 s and it carries on by itself for 4 s, plus 1 s for each ability mastered (up to 8 s).
- A stronger creature that isn't smaller resists for as long as you hold: its weapon drops from its hand, it throws
  itself on its knees with both hands dug into the ground, straining and shaking as the pull tugs at it, and can't move
  (players' keys stop answering).
- The user stands with both hands aimed at the target while holding. Holding costs hunger like Lift; it ends when V is
  let go, the target gets 48 blocks away, or the user dies. B's cooldown starts on letting go: 15 s, 8 s when resisted.
  The held B lands no Crush and the V doesn't toggle Weightless.
- **Lift on people**: a player or humanoid mob Lift takes off the ground grabs it and holds on for 1.5 s (the same
  clutching pose) before it's torn loose in a puff of dirt and pulled away.
- AntyGravity (Forge/NeoForge only, no 26.3 Fabric build) gave the idea of falls up hurting on a ceiling; nothing of
  it is copied.
- Classes: `power/gravity/GravityOverturn` (new, attachments FLIPPED, GRASP and AIM), client `GravityGraspPose` (new);
  hooked into `GravityPower` (onKey, Lift's hold and tick), `GravityPowerClient#onKey`, `GravityArmPose` and the two
  Gravity render mixins.
- Compiled; not played.
