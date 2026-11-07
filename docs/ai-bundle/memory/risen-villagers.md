---
name: risen-villagers
description: Darktide Shadow Harvest on villagers raises the SAME villager; non-guards go back to life, townsfolk notice/panic (PR #285 merged, pack 0.11.103 / Kingdoms 0.7.76)
metadata:
  type: project
  modified: 2026-10-16T07:17:55.475Z
---
2026-10-16 Andy: harvested villagers should reanimate as the same villager, go about normal life, people notice something wrong, comment, may panic and run; shadow guards fight for you like usual.
PR #285 MERGED 2026-10-16 07:3xZ as pack 0.11.103 / Kingdoms 0.7.76 (master took 0.11.102), not playtested. Next free: 0.11.104 / 0.7.77. Stored shadow = Kitchen corpse look SNBT for villagers (plain type id otherwise). Guard = MCA/vanilla profession guard/archer -> normal risen. Others: `darktide_hollow` attachment (saved, ashen tint), tag `jaymospowers_odd:risen`, never melt, no corpse. Kingdoms `Oddities.risenSeen`: lines odd.risen / fear.risen / risen.child / risen.self / gossip.odd.risen (12 each).
Defaults Claude picked: hollow villagers don't count toward the 3 risen and persist until killed (then melt). Related: [[darktide-resonance]], [[townsfolk-oddities]].
