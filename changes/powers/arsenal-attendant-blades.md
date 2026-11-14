# Arsenal: Attendant Blades (flying blades, patch 1 of docs/FlyingBlades-Plan.md)
- **Attendant Blades**: hold Sneak + V for a moment (the Void Arsenal: Sneak + X) and weapons slide out of small rifts and
  stay, hanging round you in formation. Two at first, four once every ability is unlocked, six for the Armory and the
  Void Arsenal. Each goes home after 60 s; hold the key again to send them all home early. Calling them again takes 20 s.
- **Formations**: tap Sneak + V with the blades out to change between Fan (over the shoulders), Halo (a slow ring) and
  Flank (a column down each side).
- **They strike with you**: every hit you land sends the next ready blade at the same target (your lock-on target first);
  it is back in its place 1.5 s later. They also go for whatever hurt you in the last 5 s. Until the power is mastered
  they pick fights with monsters within 10 blocks on their own. They never strike your own animals.
- **HUD**: a row of icons, one a blade, dimmed while it is away, each with a bar for its time left.
- Classes: `power/arsenal/ArsenalWing` (attachment `arsenal_wing`), client `ArsenalWingRenderer`; strikes are `RiftBlade`s.
- From Andy's picks on Yujian Craft (MIT; our own code) and The Zenith (ideas only). Compiled; not played.
