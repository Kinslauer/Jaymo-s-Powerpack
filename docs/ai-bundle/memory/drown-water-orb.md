---
name: drown-water-orb
description: Darktide Drown drawn as an end crystal water orb (PR #213 merged, pack 0.11.2); how water orbs ride the Z Fighter ki orb packet
metadata:
  type: project
  modified: 2026-10-15T02:22:43.883Z
---
2026-10-15: Andy asked for Darktide's Drown to animate with the end crystal orb ("water inside the sphere"). PR #213 MERGED 2026-10-15 02:27Z as pack 0.11.2, not playtested.
Water orbs reuse the ki orb packet: `ZFighterKi.waterOrb` sets `ZFighterNet.Orb.WATER` (bit 24 of color); `ZFighterOrbs.waterOrb` draws a sloshing see-through sphere (cages dropped in PR #231: Andy wants just the orb). Drown orb id = -(ownerId*4+3) (ki hand orbs use +1/+2).
Other powers wanting a water/blob orb can call `ZFighterKi.waterOrb`. Related: [[darktide-resonance]], [[zfighter]].
