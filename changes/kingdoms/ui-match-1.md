# one look for screens, part 1: the shared style and a flat button
- **Flat buttons**: the buttons on Kingdoms screens (skills, quests, the life story, stables, patrons, letters, the mason's table and the rest) are flat plates with a thin rim in place of Minecraft's stone buttons, lit under the mouse and dim when they can't be pressed. Buttons that were 18 or 20 high are 16 like the others. The Sleep button in bed, MCA's pages and the Tack button on a mount's inventory keep the vanilla look.
- **One gold**: the Kingdoms gold (panel stripes, titles) is now the lighter gold the H menu and the HUD use.
- **A shared header at last**: `Ui` is now written by `tools/ui_style.py` from the same source as Powers' `UiStyle` and Kitchen's `KitchenUi`, so it has the header, centred sliding line, wrapped block and item wells the others had. Screens still place their own titles; moving them onto the header is a later patch.
- Compiled; not played.
