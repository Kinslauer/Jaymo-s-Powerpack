# shift-click puts baubles on in the creative inventory and from the harness screen
- **Creative inventory**: shift-clicking a bauble in the creative inventory used to do nothing at all (the creative screen moves items on the client only, so the server never saw the click while the client held its own move back). It now tells the server (new packet `baubles_quick_equip`) and the bauble goes on in a free slot just as in the survival inventory, and the bauble screen opens as it does there.
- **Bauble screen**: shift-clicking a harness piece in the bauble screen's inventory now puts it on in Jaymo's Kingdoms' harness (`KingdomsCompat.quickEquipHarness`); before, nothing happened. The harness screen in turn sends baubles to the bauble slots (Kingdoms side).
- Survival shift-click on baubles (put on from the inventory, take off in the bauble screen) is unchanged.
- Classes: `BaublesQuickEquip` (`creativeSend` hook, `creativeShiftClick`), `BaublesNet.QuickEquip`, `BaublesMenu`, `KingdomsCompat`, client `BaublesClient`.
- Built; not played.
