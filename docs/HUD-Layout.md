# HUD layout (pack 0.19.79)

Andy (2026-11-03): "rework the hud so nothing is clashing, there are alot of new elements now, so everything needs to
feel coherent". Every HUD piece of Powers, Kingdoms and Kitchen that isn't a full-screen wash (vignettes, veins,
curtains, film bars) is placed by one of seven zones. Previews: project files `art-overhaul/hud-rework/` (`hud-before.png`,
`hud-after.png`, `hud-zones.png`, `mock.py` draws them with the same maths).

## How it works
- `client/hud/HudZones` (Powers; Kingdoms and Kitchen keep identical copies in their own `client/hud/`, since the mods
  don't depend on each other). They share one table through Fabric Loader's object share (`jaymos:hud_zones`).
- A piece calls `HudZones.place(zone, id, row, width, height)` on each frame it draws and draws at the corner it gets
  back. `null` means a film hides the HUD: don't draw.
- Lower rows sit nearer the zone's anchor; pieces in the same row sit side by side. Positions come from every piece
  seen in the last 150 ms, so draw order between mods doesn't matter and nothing needs to know when anything else shows.
- Films call `HudZones.hideFor(ms)` every frame they hide vanilla's HUD.
- `client/hud/HudStyle`: the one look (Wind's chips). Plates 0x90000000 (soft 0x60000000 behind text), a 1 px rim in
  the piece's colour when ready (grey 0xA0505050 when not), 20 px chips 2 apart, flat 3 px bars in a black frame over a
  faint track, labels centred over bars, shadowed vanilla text. No resource-pack art, so Excalibur on or off reads the same.
- `client/hud/PowerDock`: the power's own block is the right dock's row 10; powers put their chip row on its bottom
  edge with `PowerDock.origin(CHIP)` and their lines over it. `PowerClient.hudHeight` (default 48) sizes the block.

## Zones and rows
| Zone | Anchor | Rows |
|---|---|---|
| `DOCK_RIGHT` | right of the hotbar from cx+121, stacks up, left-aligned | 10 power block · 20 Flame Avatar · 30 beam-clash panel · 40 riding · 41 dragon · 50 Rig panel |
| `DOCK_LEFT` | left of the hotbar ending at cx-122, stacks up, right-aligned | 5 Spell Engine's skill bar (RPG Series class skills and Weapon Forms, Kingdoms `SkillBarDock`) · 10 stomach · 15 valor · 20 symbiote arm · 30 body row: stance, wounds doll, body heat |
| `FOCUS` | centred, bottom at H-76 (over the action-bar line), stacks up | 10 struggles (seizure, symbiote) · 11 Grue line · 15 ape rage · 20 forms belt · 30 spirit line · 40 clash timing |
| `ABOVE` | centred, bottom at cy-12, stacks up | 10 awareness eye · 20 the power's charge bar · 21 a second power bar · 25 Chrono combo · 30 Ember heat · 31 Heritage charge |
| `BELOW` | centred, top at cy+10 (cy+20 while vanilla's attack indicator shows there), stacks down | 10 guard · 20 finisher bars · 30 Chrono roll pips · 40 horse tag / wild stats · 50 smithing · gem cutting · mortar / stir bar (only one shows at a time) |
| `INSPECT` | left edge cx+48, top cy-4, stacks down | 10 forms chain · 20 alchemy lens · 30 Z Fighter scouter |
| `RAIL` | top-left x 4, top 112 under Xaero's minimap (4 without it), stacks down | 10 power badge · 20 kin sense · 30 party |

On a narrow screen (less than 150 gui px right of the hotbar) both docks move over the hotbar (bottom at H-76) and the
focus band goes over them.

Left where they were: vanilla-style icon rows (feathers, thirst), the quest tracker (top-right under the effects),
crosshair marks (Gravity lift, Web reticle, Resonance ring, lock-on brackets, aim crosshair), Web's spider-sense
bolts, full-screen washes, the film bars and what films draw inside them.

## Adding a piece
Pick the zone by what it is (an ability or mount: right dock; your body: left dock; a struggle or stance strip: focus;
a charge: above; a timing or target readout: below; a look-at readout: inspect; standing status: rail), add its row
to the table above, and draw with `HudStyle`. Never pick a fixed screen position.

## Known
- Z Fighter's HUD editor still measures Spirit Bomb, Ki Wave, ape rage and scouter from their old fixed spots, so a
  custom scale pivots about the old spot (an offset still works).
- Xaero's minimap size isn't read; the rail starts at 112 gui px, which fits its default size.
