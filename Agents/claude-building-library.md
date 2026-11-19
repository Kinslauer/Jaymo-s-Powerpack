---
status: in-review
task: Medieval building library (Andy's 88 schematic picks)
branch: claude/project-thread-wkd1gp
with: cloud thread "Find medieval building schematics online"
pr: 
mods: kingdoms
versions: 
started: 2026-11-19
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Library.java
  - tools/library_import.py
  - kingdoms/src/main/resources/jaymoskingdoms/library.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/library/
  - docs/BuildingLibrary-Plan.md
  - docs/building-library-downloads.md
---
# Building library

**Task:** Andy picked all 88 medieval schematic rows: one library for the mason's table and the town builders, the table's limit raised for the two big castles.

## Plan
- docs/BuildingLibrary-Plan.md (5 patches), built in one PR.

## Notes
- Library castles only for a kingdom raised from nothing; else the mason's table (Andy). No raid-rebuild-castle step exists yet.
- World-only files: Andy downloads per docs/building-library-downloads.md.

## Done
- 210 pattern books, builders draw a third of their buildings from them, chapel/windmill/barn new, table 128 across + Pattern books tab. Built; not played.
