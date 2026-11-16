# journal map shows the land you stand in, not the Aether
- **Journal map, Last Bell map**: in a One Dimension world the stacked level's heightmaps see the whole column, so both maps drew the top block of each column: the Aether's islands (and the Cloud Sea) over the Overworld. They now read each column from the top of the band you stand in down to its floor, so the Overworld is mapped under the Aether, and the Aether once you're up in it. An ordinary world draws as before.
- New `client/MapBand` (band floors from CustomChunks' stack layout by reflection, like Powers' AetherVeil does for Xaero's Minimap); `JournalScreen` and `BellMapScreen` use it.
- Compile-checked; not played.
