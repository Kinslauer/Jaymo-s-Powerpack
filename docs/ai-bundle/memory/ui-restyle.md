---
name: ui-restyle
description: Andy's "change all the UI" ask: restyle every screen like the Z Fighter training screen; batch 1 = H screen PR #151, batch 2 = PR #204
metadata:
  type: project
---
2026-10-14 Andy: restyle every screen like the H / Z Fighter screens (dark see-through panel 0xD0101018, 2px accent stripe, header, striped rows lighting on hover, detail box at bottom with the hovered row in full).
Batch 1 (H screen + Kitchen dish list) PR #151 MERGED (pack 0.10.40). Every key lang line has a `.name` short name.
Batch 2 PR #204 MERGED 2026-10-15 01:4xZ (pack 0.10.127 / Kingdoms 0.7.40): K skill tree (tiles, click to learn, pillar colours), class pick (list left + pane), journal, stable/farrier, tack, quest/talk palette, Tinkering Part Builder/Tool Station/Smeltery (no more Tinkers textures). Shared helpers: kingdoms client `Ui`, powers `tinkering/TinkerUi`. Screenshots + jars in /mnt/project-files/ui-restyle/batch2/.
Size rule: at 1280x720 GUI scale 3 the window is only 240 high, so keep panels <= ~232 high.
Batch 3 PR #211 MERGED 2026-10-15 ~02:05Z as pack 0.11.0 (Andy asked for '.11') / Kingdoms 0.7.44 / Kitchen 0.4.40: all Kitchen screens (KitchenUi), Affinity (pick row then Lean), Z Fighter wish/drill (row click starts drill), symbiote wheel, every mount inventory (redirects in AbstractMountInventoryScreenMixin + MountLabelsMixin), quest screen (rows + detail), talk. Powers helper UiStyle. Every screen is now restyled; HUD chips were out of scope. Kitchen ships via the tracked libs/jaymos-kitchen-*.jar: replace it when bumping Kitchen.
Cloud playtest: Kingdoms runClient flat world (see [[cloud-playtest-setup]]); needs apt-get update before installing mesa-vulkan-drivers/xdotool.
