---
status: done
task: Inbox/alexs-caves-integration
branch: claude/project-thread-dzutw2
with: cloud Claude (Reason Art project thread "Integrate Alex's Caves")
pr: 2105
mods: powers, kingdoms
versions: 
started: 2026-11-24
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/compat/AlexsCavesCompat.java (new)
  - src/main/resources/data/alexscaves/ (worldgen overrides, behind load conditions)
  - kingdoms/src/main/resources/data/alexscaves/
---
# Alex's Caves, track 1: world gen

**Task:** Andy (2026-11-24): add Alex's Caves Continued, reshaped to our lore; "i want to target world gen first".

## Plan
- Add Alex's Caves Continued to packMods.
- Cave biomes sit mostly under far lands like Mohknee.
- Magnetic Caves built from the Aether magnetic blocks (ferrosite etc.), no red/blue neodymium.

## Notes
- 

## Done
PR #2105. Alex's Caves Continued 1.1.3 in the pack (Modrinth, packModsOverride). Its cave biomes sit under Mohknee and
its sea; none within 24000 blocks of the middle; one cell in two past that; cave maps search ~35000 blocks.
Magnetic Caves walls are ferrosite with ichorite bands, no red/blue nodes, our pillars/spikes/sand/shrooms on floors,
ruins swapped. Lore paragraph "The deep places under the far land" (new), awaiting Andy. Compiled; not played.
Neodymium no longer generates (later track). Other tracks (Rubber-folk, flood, demon, Toxic, future items) not started.
- 2026-11-24 16:50 PR #2105 is merged into master; moved to Agents/Done.
