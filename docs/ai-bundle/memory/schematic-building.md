---
name: schematic-building
description: Schematic files (.nbt/.schem/.litematic) as Kingdoms designs, Mason's Table screen (no commands), Building Plan, offering plans to settlements (PR #870 merged, Kingdoms 0.7.488)
metadata:
  type: project
  modified: 2026-11-03T21:30:35.098Z
---
Andy asked 2026-11-03 for a Create-schematicannon-like survival builder "strictly through schematics", plus submitting plans for kingdom expansion. PR #870 (Kingdoms 0.7.488, merged 2026-11-03):
- Designs (`Designs`, `<world>/jaymoskingdoms_designs/`) now read .nbt, .schem (Sponge v1-3) and .litematic via `SchematicFiles`. 26.3 gotcha: palette entries must be `id`/`properties`; Name/Properties read as AIR silently (fixed by renaming after the data fixer).
- Andy ruled: NO commands for any of this, a UI like Create's schematicannon. The Mason's Table screen (`MasonScreen`/`MasonNet`) lists the client's `schematics/` game folder (Open folder button) and the world's designs; Draw on plan, Offer to settlement, Save marked build, Take plan, Retry. Uploads go in 30 KB pieces (about 1.9 MB max); a name taken by another player gets _2.
- Building Plan (2 paper + charcoal, drawn on at the table's screen) laid on the ground, then a Mason's Table builds it from adjacent containers (5 ticks/block). Tested on a dev server: Structura Medieval House 1409/1410 exact.
- Andy 2026-11-03: plans should work like the ship build kits. A laid plan stays as see-through ghost blocks (`PlanSites` server, `PlanGhosts` client render via submitCustomGeometry + entityTranslucent block atlas); hand-placed right blocks fill it; built at QUOTA 90%; max 3 per player.
- Offer to settlement button (member or 100+ rep) queues it for the settlement's crew ahead of its catalogue (`Commissions`, `Planner.eligible`).
Structura (structuramc.com) terms: personal use, no redistribution, so players upload their own copies and we never bundle them. See [[reference-notes]].
