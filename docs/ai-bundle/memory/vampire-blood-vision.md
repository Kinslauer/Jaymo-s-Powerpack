---
name: vampire-blood-vision
description: Vampire players get MCA's Sirben screen + blood vision + looks (PR #178 merged, Kingdoms 0.7.24 / pack 0.10.83)
metadata:
  type: project
---
2026-10-14 Andy: "vampirism should give you the sirben visual modifier". Sirben = an MCA trait's screen effect (post effect `mca:sirben`: edge blur, red tint). Then Andy: "do both" = blood vision AND vampire looks too.
Built in Kingdoms: SirbenMixin adds mca:sirben always for vampire players (referenced by name, MCA is GPL, ours MIT: never copy MCA files); BloodVision (night red heartbeat vignette, living creatures glow red, 24/40 blocks); VampireLooks layer (ashen face/hands always, fangs + emissive red eyes at night). PR #178 MERGED 2026-10-14 as pack 0.10.83 / Kingdoms 0.7.24, not playtested. Red eyes are placed per skin (VampireLooks.findEyes).
Related: [[vampires-werewolves]], [[mca-in-pack]].
