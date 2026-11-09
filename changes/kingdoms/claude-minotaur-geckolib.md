# Minotaur moves to GeckoLib

The minotaur keeps its stats, AI, hammer, scale and shadow, but is now rendered by GeckoLib with its calm and hostile idles, previously unused walk, charge run, alternating hammer swings and overhead ground slam. Its head still turns toward what it is looking at.

The editable Blockbench source, CC BY 4.0 licence, credit and inspected renders are in `kingdoms/art/`. Original model, texture and animations: “Minotaur” by eanncrod (https://blockbenchworkshop.com/model/eanncrod/minotaur). The rig and geometry were raised together to put its feet at y=0, animation names were shortened, and `swing_2` was made one-shot; no cubes or texture pixels were removed.

The Kingdoms and root builds pass, and a dedicated development server reaches `Done` with GeckoLib 5.5.7 loaded.

Not tested in game: facing, final size, ground contact, hammer placement and animation timing still need a visual playtest.
