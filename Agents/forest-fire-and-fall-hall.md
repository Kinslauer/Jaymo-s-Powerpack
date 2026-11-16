---
status: done
task: forest-fire-and-fall-hall (research only: two integration checklists for Andy)
branch: none (research; no branch or PR until Andy says go)
with:
pr:
mods:
versions:
started: 2026-11-16
owns:
  - (nothing in the repo; this note only)
---
# forest-fire-and-fall-hall

**Task:** Andy: "https://www.curseforge.com/minecraft/mc-mods/peterwolfs-forest-fire-firefighting can this be
integrated to our pack? can you make a checklist of what you would change?
https://www.curseforge.com/minecraft/worlds/fall-hall-paleotech I'd also like this integrated as one of the
kingdom's castles". Deliverable: one checklist per link, in chat. Research only: no pack changes, no branch, no PR.

## Plan
- [x] Read CLAUDE.md, AI-HANDBOOK, Home, Decisions, Lore.md (shape of the world, peoples, realms/creeds, wild, daily life, not-yet-placed)
- [x] Peterwolf's Forest Fire & Firefighting 1.1.11 jar: fabric.mod.json, mixins, config keys, items, recipes, API surface
- [x] Fall Hall zip: layout (a Java world folder, no datapacks, no generated/ templates), region set, CF file says 26.3
- [x] Our side: GrandSeats, Castle.site, HordeTemplates/Blueprint/Designs limits, LosPerrito fetch, build.gradle packMods /
      packModsOverride / ports/* (TRMT, Dynamic Waters) / libs (MCA), PickupPreset config writer, FelledCompat, KitchenCompat
- [x] Write both checklists in chat; questions for Andy (Peterwolf 26.3 build; PaleoTech permission)

## Notes
- Nothing in the repo or the live vault is edited except this note. Lore.md paragraphs are drafted in chat, not written in.
- Verified today: CF file 8681320 (the fire mod's only file) lists gameVersions [Client, 26.2]; the Fall Hall file 9313294
  lists [26.3]. So the fire mod still has no 26.3 build.
- Config is hand-rolled Gson at `config/peterwolfs_forestfire.json` (no Cloth Config), so a `PickupPreset`-style
  one-time writer is the way to set our values.

## Done
No player-visible change: two checklists and the questions for Andy, delivered in chat. Not playtested (nothing built).
- 2026-11-15 21:49 No sign of work for 30 min: marked stalled (whoever ran it stopped).
