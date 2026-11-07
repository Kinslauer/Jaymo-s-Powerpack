---
name: zombie-awareness
description: Zombie Awareness (ARR) remade as Kingdoms Awareness: zombies hear noise, see lights, smell blood, call hordes; tied to Stealth perks. PR #402 MERGED 2026-10-22 02:2xZ (Kingdoms 0.7.155 / pack 0.15.73)
metadata:
  type: project
  modified: 2026-10-22T02:10:24.334Z
---
2026-10-22 Andy: "player zombies should work like this [modrinth zombie-awareness], and maybe work it in to help with our stealth". Read as all zombies (tag `jaymoskingdoms:aware`: zombie, husk, drowned, zombie villager, Kitchen zombie_player = reanimated player corpses, Andy 02:13Z; zombie corpses rise as plain zombies); the mod's defaults also had skeletons, strays, creepers, witches (add to the tag if Andy wants).
`Awareness` lures via `Senses.lure` (the lost-target search; louder lure wins; who=player shows "searching" on the gauge and counts as unaware). Sound through `ServerLevelGameEventMixin` (ServerLevel.gameEvent HEAD), reach per GameEvent in `SOUNDS`; sprint footsteps 12. Light in the dark 40 blocks. Blood spots (red dust) 60 s, drip below 30% health, zombies follow fresher spots. Horde groan 16 blocks every 15 s.
Perks: sneaking halves noise, Quiet Step 0.35/0.2; Shadowed -20% light reach per rank; Slip the Net halves blood life. No wandering hordes.
PR #402 MERGED on Andy's "merge", built, not playtested; jar /mnt/project-files/zombie-awareness/. Related: [[skill-tree]], [[medicine-tree]], [[farsight-stalkers]].
