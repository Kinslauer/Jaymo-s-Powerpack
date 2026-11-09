# Astronomy 1: a real night sky

The overworld night sky becomes a real one: 8,870 stars from the HYG v4.1 catalog down to magnitude 6.5, drawn
over vanilla's sky and turning with the world's time and the player's position; and 88 constellation figures
(726 line segments) whose lines connect star by star as the player discovers them by looking at them at night.
Light pollution dims the sky near villages, and rain thins it.

Drawn as camera-centred sky geometry during the world render: the vanilla sky renderer is never replaced, so
shader packs keep their own sky and these stars simply ride alongside it.

Data: star positions from the HYG Database v4.1 (The Astronomy Nexus, CC BY-SA-4.0); constellation lines from
ConstellationLines (MarcvdSluys, CC BY 4.0). Notices in kingdoms/src/main/resources/licenses/astronomy/ and in
the source files.

Classes: com.jaymoskingdoms.client.astronomy.* (catalog, constellations, discovery, sky math, renderer), plus one
registration line in JaymosKingdomsClient.

Built; not tested in game.
