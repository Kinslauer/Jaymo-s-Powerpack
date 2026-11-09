---
status: writing
for: hermes
from: hermes (Andy's astronomy pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: 
pr: 1193
suggested_owns: []
writer: hermes-writer
text_pass: 
---

# Astronomy 1: a real sky

Foundation for the whole astronomy set. The night sky becomes a real one: a star catalog, constellations that connect as you find them, and a sky that depends on where and when you stand.

## What's wanted

From the astronomy pick-list ("add this but in the high renaissance artstyle"), all three sky rows ticked:

- **A real star catalog**: the source ships 8,920 stars from the HYG 4.1 catalog, to magnitude 6.5, drawn over vanilla's sky.
- **Constellations that connect as you find them**: 88 figures, 695 line segments; a figure only shows once its stars are discovered.
- **Sky that depends on where and when you stand**: position and world time decide what is up; light pollution dims it near settlements.

## Notes

- **Start by reading how 26.3 draws its sky.** A replacement sky renderer that fights Iris or Sodium is not worth having; if it cannot coexist, say so and propose a smaller version (stars as an overlay on the vanilla sky) rather than forcing it.
- The catalog and figures are MIT — keep the notice and the attribution in the source and in the mod's credits.
- Light pollution should read as a place feeling different at night, not as a number in a menu.
- Which mod this lives in is a real question: sky rendering is client-heavy. Suggest Kingdoms, since the same system's journal is the scholar side (see `astronomy-scholar-journal`), but do not split it across two mods.
- Nothing downstream works without this: telescope, armillary sphere and journal all need discovered stars to point at.
- 2026-11-09 12:46 Text pass: rewrote change note (changes/kingdoms/claude-astronomy-sky.md) to lead with what the player sees, cut implementation details, keep data credits concise
- 2026-11-09 13:05 Review (head 69db789): changes needed. 1) Discovery.java keeps discovered stars only on the player's PC (config/jaymoskingdoms/astronomy/discovery-<uuid>.json; 'nothing is sent to or kept by the server'). The task says the telescope, armillary and journal all need discovered stars, and their readings run on the server, which can't see them. It is also shared across every world and server the player joins, lost on another PC, and editable. Keep discovery on the server (a player attachment, synced to the client), with the client only reporting the look. 2) SkyClient.java DOME = 60: the stars and lines are real geometry 60 blocks from the camera, drawn with depth in the world pass, so at night anything farther than 60 blocks (mountains, tall builds, clouds) gets stars drawn in front of it. Draw them as sky: in the sky pass/behind terrain (check what 26.3 and Fabric offer for after-sky rendering) or without depth against far terrain. 3) SkyClient.render calls pollution() every frame, which scans for villagers in a 192-block box per frame; its own tick() javadoc says pollution is worked out per tick so the render stays cheap. Compute it in tick() and keep the value. 4) Minor: render() allocates camera.leftVector()/upVector() Vec3s per star per frame (8,870 stars): take them once before the loop. Latitude is fixed at 45 degrees (SkyMath.LATITUDE), so only X moves the sky; say so in the PR or use Z. The rest is good: the vanilla sky isn't replaced, the HYG (CC BY-SA 4.0) and ConstellationLines (CC BY 4.0) notices are in licenses/astronomy/NOTICE.txt, and rain and daylight fade the stars.
- 2026-11-09 13:22 hermes-fixer took over the review fixes from hermes-b.
- 2026-11-09 14:33 hermes-fixer's claim went stale (nothing changed for 50 min); handed back as changes so a bot can take it.
