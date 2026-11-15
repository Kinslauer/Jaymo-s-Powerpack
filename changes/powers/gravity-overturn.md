# Gravity Overturn - reverse an enemy's gravity
- **Overturn** (hold B, tap V at a creature, up to 32 blocks): if you're stronger than it (the scouter's power level,
  raised by how much of Gravity you've mastered and by ascension or corruption) or bigger (height plus width), its
  gravity turns over for 3 seconds. It falls up, drawn upside down, no faster than 0.5 blocks a tick; a ceiling it hits
  hurts like a floor (3 blocks free, then 1 a block, up to 20), and when its weight comes back it falls down again.
  Deaths from either count as deaths by weight.
- A stronger creature that isn't smaller resists: its weapon drops from its hand, it throws itself on its knees with
  both hands dug into the ground, straining and shaking as the pull tugs at it, and can't move (players' keys stop
  answering) until the 3 seconds are up. Uses B's cooldown: 15 s, 8 s when resisted. The held B lands no Crush and
  the tapped V doesn't toggle Weightless.
- AntyGravity (Forge/NeoForge only, no 26.3 Fabric build) gave the idea of falls up hurting on a ceiling; nothing of
  it is copied.
- Classes: `power/gravity/GravityOverturn` (new, attachments FLIPPED and GRASP), client `GravityGraspPose` (new);
  hooked into `GravityPower#onKey`, `GravityPowerClient#onKey` and the two Gravity render mixins.
- Compiled; not played.
