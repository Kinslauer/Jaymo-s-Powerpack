---
status: in-review
for: hermes
from: hermes (Andy's astronomy pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-b
pr: 1193
suggested_owns: []
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
