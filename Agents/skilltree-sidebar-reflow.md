---
status: in-review
task: skilltree-sidebar-reflow
branch: claude/skilltree-sidebar-reflow
with:
pr: 1190
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/SkillScreen.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/SkillWeb.java
---
# Skill tree sidebar reflow

**Task:** The Attributes and Jobs panel sits on the left of the skill tree and stays there, over the web. Make it duck away so the web is fully usable behind where the panel used to be. Getting it back should be obvious without a tooltip.

## Plan
- Add a `panelCollapsed` static boolean to track collapse state (persists across screen opens).
- When on Attributes/Jobs tab and collapsed: render the web in the background + a thin vertical tab on the left edge to expand.
- When on Attributes/Jobs tab and not collapsed: current behavior (panel on left, web not rendered).
- Clicking the left-edge tab toggles collapse state.
- The web must be fully interactive when panel is collapsed (perks clickable, drag/zoom works).

## Notes
- Keep changes minimal; Tiv will iterate if he doesn't like the result.
- Don't change what the panel shows — only where it sits and whether it can get out of the way.

## Done
Work in progress; not yet tested in game.