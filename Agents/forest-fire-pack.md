---
status: running
task: Build Andy's ticked forest-fire picks (ff-lock, ff-sim, ff-villagers, ff-pits, ff-heat, ff-kit); Fall Hall waits on the licence question
branch: claude/forest-fire-pack
with: Claude Code (PC worktree eager-hellman-eccd79)
pr:
mods: powers (pack), kingdoms
versions:
started: 2026-11-15
owns:
  - build.gradle (packMods entry + pack config copy only)
  - pack/config/fabric_loader_dependencies.json
  - kingdoms/src/main/java/com/jaymoskingdoms/compat/ForestFireCompat* (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/disasters/Calamities.java (wildfire hand-off only)
  - kingdoms/src/main/java/com/jaymoskingdoms/Firefighting.java (douse hook only)
  - kingdoms/build.gradle (dev runtime only)
---
# forest-fire-pack

**Task:** Andy's "Forest Fire and Fall Hall picks": Peterwolf's Forest Fire & Firefighting (MIT, 1.1.11, locked to
26.2) into the pack with a loader override, Deck limits, and our wildfires/villagers/pits/heat/lore tied to it.
Fall Hall (no licence) is not built until Andy answers whether to ask PaleoTech.

## Plan
- [ ] ff-lock: packMods entry, config/fabric_loader_dependencies.json shipped with the pack, Deck limits preset
- [ ] dev server boot with the mod on 26.3
- [ ] ff-sim, ff-villagers, ff-pits, ff-heat in a Kingdoms compat class (reflection, only when loaded)
- [ ] ff-kit lore paragraph in Lore.md (new)
- [ ] build, change notes, PR

## Notes
- Earlier research note: Agents/forest-fire-and-fall-hall.md (not mine).

## Done
