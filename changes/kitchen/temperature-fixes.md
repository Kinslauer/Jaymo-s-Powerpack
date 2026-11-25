# body heat fixes: hot springs warm, rain counted once, cloth doesn't bake like metal
- **Hot springs warm you**: in water over a magma block (the hot springs Powers digs in snowy, taiga and mountain lands) the water's wet no longer chills you and holds you at least comfortably warm (`BodyHeat.HOT_SPRING` 20). Before, a soak in a snowy hot spring froze you in about 20 seconds. You still come out wet.
- **Rain counted once**: rain took the sun away, soaked you (the wet meter, -25) and also had its own -15, so a summer shower at night on the plains reached Freezing. The extra -15 is gone; falling snow keeps its -20.
- **Rain where you are**: the sun's warmth and the sea damp went by rain anywhere in the world, so a desert under a clear sky lost its sun whenever it rained elsewhere. Both now go by what falls at your head.
- **Cloth doesn't bake like metal**: armour in the sun on a warm land added heat for every armour point, leather and wool included (on top of leather's stuffiness). Now only the points of pieces that aren't warm or cool clothing count (`BodyHeat.metalArmour`).
- **North cold not counted twice**: with Jaymo's Kingdoms the overworld's lands already follow its climate map (cold north, hot south), so `Warmth.north`'s extra cold to the north of the world spawn (and warmth to its south) is only added when Kingdoms isn't there.
- **Worn pieces from other mods**: `BodyHeat.addWorn(warm, pieces)` lets a mod count warm or cool pieces worn outside the armour slots (Kingdoms' harness).
- Built; not played.
