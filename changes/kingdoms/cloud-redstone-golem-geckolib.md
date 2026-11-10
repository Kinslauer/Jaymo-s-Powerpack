# Redstone golem moves to GeckoLib

The redstone golem keeps its stats, AI, moves, size, shadow and glowing core and veins, but is now drawn by GeckoLib. Its pound and eruption are proper keyframed animations instead of two arms swung in code: it rears back with both fists overhead, then leans in and brings them down on the ground on the same tick the blow lands; in the eruption it holds its fists on the ground, trembling, while the rock under its foes burns. It also flinches when hit (not mid-move, so a blow never cancels a pound) and, when killed, its knees give and it topples forward onto its face instead of tipping onto its side. Its head still turns toward what it looks at.

The animations are written by `kingdoms/tools/gen_redstone_golem_geo.py` into the editable `kingdoms/art/redstone_golem.bbmodel`. Model, textures, idle and walk: "Redstone Golem (Minecraft Dungeons)" by polyjo (https://blockbenchworkshop.com/model/polyjo/redstone-golem-minecraft-dungeons), CC BY 4.0; credit, licence and renders (old pose beside new) are in `kingdoms/art/`.

The Kingdoms build passes, and `runServer` reaches `Done`. Not seen in game: facing, timing against the server's blows, the glow and the death fall still need a visual playtest.
