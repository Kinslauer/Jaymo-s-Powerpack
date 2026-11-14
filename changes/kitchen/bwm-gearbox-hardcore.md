# Gearbox, and Better With Mods' hardcore lumber, hunger and beds
- **Gearbox** (after Better With Mods; its MIT art): a shaft turns it through its input face (the face set against the shaft), and it turns a shaft out of each of its other five faces, the turn shared out evenly among the machines at their ends. A redstone signal disengages it. One gearbox per run: it never drives another. Planks, shafts and redstone (`PSP / SRS / PSP`). `mill/power/GearboxBlock`, `GearboxEntity`.
- **Tougher wood**: Kingdoms already gives nothing for a log broken without an axe; now a bare hand or wrong tool strips 1 bark and 2 sticks from it. `hardcore/Hardcore`.
- **Harsher hunger**: at 6 food or less you walk 15% slower, at 4 or less 30% slower with Weakness, at 2 or less half speed with Weakness II and Mining Fatigue. Jumping is left alone. Androids and the Divine-born are kept full, so they never feel it. `hardcore/Hardcore`.
- **Beds don't set your spawn**: you still sleep in them. This covers vanilla sleep, Kingdoms' bed rest and ship berths. Respawn anchors, lit outpost campfires and the world spawn are left. `mixin/BedSpawnMixin`.
- Changed classes compile against 26.3; not played.
