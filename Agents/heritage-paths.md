---
status: stalled
task: "[[heritage-paths]]"
branch: claude/heritage-paths
with: 
pr: 1853
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HeritagePaths.java (new)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/heritage-paths.json (new)
  - changes/kingdoms/heritage-paths.md
  - changes/powers/heritage-paths.md
  - hooks (a few lines each): AlarmBells, Bearings, HeritageKits, JaymosKingdoms, Lanterns, Lineage, Patrons, TideFishing, Torches, equestrian/Equestrian, mixin/BlockItemMixin, sailing/SeaEvents, sailing/Ship, client/HeritageScreen, client/MenuCompat
  - Powers: fishing/FishBites, fishing/RodPodEntity, compat/KingdomsCompat
  - Lore.md: four (new) paragraphs after "The blood wakes with the ladder"
---
# Heritage paths: core + Stoneborn, Heartlander, Tideborn

**Task:** Andy's picked heritage paths (Inbox `heritage-paths`). This PR: the shared path system (deeds open paths,
the first to open is yours for this life, five steps wake with the Knowledge ladder while you keep living it, Lineage
carries it to children opened not chosen, nothing fades for leaving it) and the picker's Paths section, plus the
Stoneborn, Heartlander and Tideborn paths. Lore paragraphs drafted as (new) and quoted to Andy; PR waits on his yes.

## Plan
- Read Heritage, Knowledge, Lineage, PickerLayout/HeritageScreen, the counters the paths sit on
- Core: HeritagePaths (attachment, deed counting, opening, steps, sync to client)
- Nine paths' effects
- Picker Paths section
- Lore (new) paragraphs, change note, CI build

## Notes
- Builds on CI (Andy's PC runs only the game).

## Done
- 2026-11-19 20:14 No sign of work for 30 min: marked stalled (whoever ran it stopped).
