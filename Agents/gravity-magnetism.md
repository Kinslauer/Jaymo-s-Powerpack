---
status: done
task: Work the X-Men mod's Magnetism power into Gravity
branch: claude/project-thread-m8uaao
with: Claude (project thread)
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2125, https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2128 (both merged)
mods: powers
versions: 
started: 2026-11-24
owns:
  - src/main/java/com/jaymospowers/core/BlockCluster.java (of: removal order)
  - src/main/java/com/jaymospowers/power/gravity/GravityMagnet*.java
  - src/client/java/com/jaymospowers/client/power/gravity/GravityMagnet*.java
---
# Gravity: magnetism

**Task:** Andy: "C:\Users\toopo\Videos\Clips\Video DLs 16:30 is where the magnetism power is shown, i'd like to work that into gravity". The video is "This Is the BEST X-Men Mod EVER.mp4"; Magnetism runs 16:38 to 19:50.

## Plan
- Watch the Magnetism section, list its abilities.
- Map Gravity's keys and free slots, place magnetism in the lore.
- Build the metal kit into Gravity, open a PR.

## Notes
- Magnetism in the video: Magnetic Bolt, Force Field, Magnetic Shockwave, Magnetic Cannon, Rip Away, Gravity Squeeze (ult), Metal Grasp, Disarm, Polarity, Magnetic Flight, Metallic Curtain, Magnetic Forge, Metal Prison, Collapse, Scrap Barrage.

## Done
- 2026-11-24 19:12 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- PR #2125 open: Tear Loose (sneak + right click on a held creature), heavy metal rips, scrap with the rocks. Lore "Iron answers first" (new). Waiting on Andy: disarm players? Forge as a follow-up? Not played; CI build only.
- 2026-11-24 20:02 PR #2125 is merged into master; moved to Agents/Done.
- 2026-11-25: #2125 merged by Andy at its 4th commit. The review fixes (torn-chunk dupe in BlockCluster.of, mob torn only once, gems not metal, ward spends scrap) came after and are in #2128. Lore "Iron answers first" (new) awaits Andy. Not played.
- 2026-11-25: #2128 merged (Andy: "merge"). Lore "Iron answers first" approved. A player sees: sneak + right click on a creature held in Lift tears its metal out (a player's is flung away; at the last Mastery rank it comes to your hand); metal blocks rip faster with the metal touching them; nuggets fly with the rocks and hit twice as hard. Not played.
