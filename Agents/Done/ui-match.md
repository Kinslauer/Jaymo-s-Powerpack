---
status: done
task: 
branch: claude/project-thread-0hjevc
with: Claude (cloud, "Make every UI screen match" thread)
pr: 1434
mods: powers, kingdoms, kitchen
versions: 
started: 2026-11-13
owns:
  - src/client/java/com/jaymospowers/client/UiStyle.java
  - src/client/java/com/jaymospowers/client/ui/
  - src/client/java/com/jaymospowers/client/tinkering/TinkerUi.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/Ui.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ui/
  - kitchen/src/client/java/com/jaymoskitchen/client/KitchenUi.java
  - kitchen/src/client/java/com/jaymoskitchen/client/ui/
  - tools/check_ui_style.py
---
# Make every UI screen match

**Task:** Andy: "I want to make sure all the UI and screens match." He ticked nearly every row of the UI Match Checklist
(https://claude.ai/artifact/MCnSHSpeD58VBBrr7PMrSb, picks in its db `picks/andy`).

## Plan
- Patch 1 (this PR): the shared style in step across UiStyle / Ui / KitchenUi / TinkerUi, one gold/red/green, a shared
  flat button, a check that the copies match. Vanilla buttons on our panels swapped to the flat button.
- Patch 2: H menu, every tab on the solid bevelled window (Andy: "Solid for H menu, vanilla for inventory").
- Patch 3: shared header, no cut text, text shadow rule, detail box over tooltips.
- Patch 4: stations and books. Patch 5: HUD pieces onto HudStyle and HudZones. Patch 6: notices.
- Wheels: Andy wants to see all six side by side before anything is built (he likes the symbiote's best).
- A second list: other mods' screens.

## Notes
- Baubles stays vanilla light grey (row not ticked; "vanilla for inventory").

## Done
- 2026-11-13 12:15 PR #1434 is merged into master; moved to Agents/Done.
