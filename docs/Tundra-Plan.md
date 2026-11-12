# The tundra (Klei_Wright's "Tundra game drop" concept)

Andy, 2026-11-12: all 34 rows of the picklist (https://claude.ai/artifact/UdpPvQpQvh1NnzSrRaSXDL), his note on the art
row: "use fable for all model building". Source: Klei_Wright's video "How can we make Minecraft feel alive? (It isn't
structures)" and its asset pack. The ideas are ours to build (he asks people to); his art is CC BY-NC-SA 4.0 ("same
license as Matcha"), so it never goes into our jars: our own art is built in, and his rides as a separate resource
pack on top (track D). He asks not to be sent AI-made mods: never send or post this as an answer to his challenge.

Three rules every piece follows (also going into CLAUDE.md, track D):
- **Sympathy**: an animal or plant does what the real one does.
- **Feedback loops**: it changes its surroundings, and those changes come back on it.
- **Stewardship**: a player who looks after it is rewarded; nobody is punished for ignoring it.

Lore: the far cold of the Overworld, old bare rock nobody made. No people of its own; Frostborn villages in the cold
lands may keep muskox. No lore text on screen. Placement paragraph: `Lore.md`, "(new) The tundra".

One branch (`claude/project-thread-1y9883`), one PR. Tracks own separate files.

## Track A: blocks, plants and living lichen (Powers, package `com.jaymospowers.tundra`)

Owns: `src/main/java/com/jaymospowers/tundra/**`, `src/client/java/com/jaymospowers/client/tundra/**`, the assets and
data for the ids below, `lang/parts/tundra.json`, `tools/gen_tundra.py`, `changes/powers/tundra.md`. Touches
`JaymosPowers.java` (and the client entrypoint) with one `Tundra.init()` line only.

Blocks (all with items, loot tables, tool tags, creative tab entries, recipes where named):
- `banded_iron_ore`: iron ore in smooth basalt, pale bands. Drops raw iron, `BANDED_IRON_MIN/MAX` = 2..4 (vanilla 1),
  Fortune as vanilla ore. Stone pick or better. Smelts like iron ore.
- `dwarfed_spruce_log` (a 10x10 pillar, the "dwarf log wall") and `dwarfed_spruce_post` (6x6 pillar), both with an
  axis, waterloggable, and `stripped_` forms by axe. One log or two posts craft back into 1 spruce planks... no:
  into spruce **wood** as the video says: 2 logs -> 1 spruce log, 4 posts -> 1 spruce log (shapeless). In `logs_that_burn`
  and the chop/felling tags other logs are in (check how `Timber`/felled trees pick logs).
- `stunted_red_shrub`, `orange_shrub`, `yellow_shrub`: small bush plants (cross model) on dirt, grass, moss, yellow
  moss, gravel, coarse dirt. Shears or silk touch to keep them.
- `yellow_moss` (full block, hoe-mined like moss) and `yellow_moss_carpet`. Layer blocks through the Saw if the layers
  system takes a simple registration (see `layers/`), else skip and say so.
- `white_ice`: ice that doesn't melt and isn't slippery-er than ice; opaque white. Tundra water freezes to it (track C).
- `sprouted_glow_lichen`: a sister of vanilla glow lichen (same multiface shape, light 7), with `snipped` (boolean).
  Bone meal on vanilla `minecraft:glow_lichen` turns it into sprouted (keeping its faces). Drops glow lichen with shears.
- `snipped_glow_lichen`: vanilla lichen after shears (use shears on it: same faces, never spreads, muskox won't eat
  it). Looks the same but for clipped tips.

Lichen rules (`LichenLife`, random ticks on sprouted, unsnipped lichen; constants at the top):
- Needs sky light >= `SUN_LIGHT` (9) at its own block and daytime.
- Each random tick that passes `ACT_CHANCE` (1 in 4): if the block it clings to (the floor face first) is in the
  breakdown table, 50/50 it either breaks that block down one step or reproduces; if not in the table it can only
  reproduce, and only while some block within `SPREAD_RANGE` (8) is still breakable ("it runs out").
- Breakdown table (data map or block tags `jaymospowers:lichen/…`): stone, deepslate, basalt kinds, granite, diorite,
  andesite, tuff, calcite, cobblestone, cobbled deepslate, sandstone -> gravel; gravel -> sand (60%) or coarse dirt
  (40%); coarse dirt -> dirt. Dirt, sand, mud, grass, moss = spent. Never touches ores, banded ore, bedrock, or
  anything with a block entity; never in a claim's protected blocks if a cheap check exists (ask how `layers`/other
  world-editing code checks; else skip).
- Reproduce: put ordinary `minecraft:glow_lichen` on the top face of a random breakable block within 8 (air above,
  sky light). Never onto mud. Cap: no more than `MAX_NEAR` (12) lichen of any kind within 8 blocks, so a colony
  can't carpet a chunk or lag a server.
- Shears on sprouted lichen set `snipped`: no more eating or spreading. Decorative builds are safe.
- Succession ("plants arrive in order"; our extension): `yellow_moss` spreads (slow random tick, like grass) onto
  dirt or coarse dirt next to it only when some glow lichen is within 4 blocks; a yellow moss block under open sky
  has a small chance per random tick to grow one of the three shrubs on top (stunted red where the block under the
  moss is still stone-like, else orange/yellow), capped at 3 shrubs within 5 blocks.
- Phosphor: `minecraft:glow_ink_sac` is renamed "Phosphor" (lang override in our built-in pack, plus the item texture);
  glow lichen (either kind) smelts into 1 glow ink sac (furnace, 0.1 xp). Check JEI shows it.
- Glow lichen look: vanilla `glow_lichen` (and ours) get ground tufts/flat patches on the floor face and hanging
  strands on the ceiling face. Our own models and textures.

Art: every texture painted from nothing by `tools/gen_tundra.py` in the pack's vanilla style (see `tools/gen_frost.py`
for the pattern); no pixel of Klei's copied. Use his **file names** where a texture has a counterpart, so his pack can
override ours by path: `dwarfed_spruce_log_end`, `dwarfed_spruce_post_end`, `stripped_dwarfed_spruce_log_end`,
`stripped_dwarfed_spruce_post_end`, `orange_shrub`, `yellow_shrub`, `stunted_red_shrub`, `yellow_moss`, `white_ice`,
`smooth_basalt_iron_ore` (texture name for `banded_iron_ore`), `glow_lichen_ground*`, `glow_lichen_ceiling*`.
26.3 models need `#variable` face textures (raw sprite ids render purple). Small models with full collision shapes
cull neighbours: give thin blocks proper shapes / `noOcclusion`.

## Track B: muskox, herds, fed predators (Kingdoms, package `com.jaymoskingdoms.wild`)

Owns: `kingdoms/.../wild/Muskox*.java`, `wild/HerdPull.java`, `wild/FedPredators.java`, their client classes under
`kingdoms/src/client/.../client/wild/`, mixins they need (new files), `lang/parts/tundra.json`, tags, loot table,
`tools/gen_muskox.py`, `docs/Muskox-Model-Spec.md`, `changes/kingdoms/tundra.md`. Touches `Wild.java` and the client
registration with registration lines only.

- **Muskox** (`jaymoskingdoms:muskox`, CREATURE, about 1.5 wide x 1.6 tall; calf half size). A giant shaggy goat
  cousin: long skirt of hair to the knees, shoulder hump, broad horn boss curving down then up. Coats brown and black
  (by UUID). 24 HP, slow, knocks back hard if hit (no damage unless provoked: neutral). Bred by hand with glow lichen.
  Drops like a goat/cow would in this pack (check how Moose / Kitchen corpses do it), leather. Spawn egg.
  Model the REA way (rea-for-models): `docs/Muskox-Model-Spec.md` first (references, facts with source, guesses
  marked UNKNOWN, palette, UV layout), own boxes in Java, coat painted from nothing by `tools/gen_muskox.py`, pose
  previews rendered to `/mnt/project-files/art-overhaul/muskox/`. `Grizzly`/`GrizzlyModel` are the example.
- **Grazes lichen**: walks to `minecraft:glow_lichen` within 10 blocks and eats it (removes the block, eat animation).
  Only vanilla glow lichen: never `jaymospowers:sprouted_glow_lichen` or `snipped_glow_lichen` (it only matches the
  vanilla block, so no Powers dependency). `GRAZE_EVERY` about 2400 ticks per animal.
- **Sheds wool**: after each feed it drops string, count by comfort: open space (no solid block within 3 at head
  height on at least 3 sides and 2 above), 2 to 8 other muskox within 16 (more than 12 within 8 = overcrowded = 0),
  sky visible and daytime, in a `jaymoskingdoms:muskox_home` biome. 0 conditions -> 0 or 1 string; each condition
  roughly doubles; all four -> 6..8. Overcrowded -> always 0.
- **Herd pull** (`HerdPull`): a weak, low-priority goal: every `PULL_EVERY` (200) ticks, if fewer than 2 of its kind
  within 6 and some within 24, stroll toward the centre of those. Muskox also drift toward lichen within 24.
  Switched off while fleeing. On muskox always; on wild cows, sheep, pigs, chickens and goats too (entity tag
  `jaymoskingdoms:herds_together`), but never on a leashed, named, or fenced-in animal (cheap pen test: can't path 12
  blocks any way) so pens don't pile into a corner.
- **Recover**: when a muskox dies, others within 32 remember it (`LOSS_TICKS` = 3 days). A muskox with that memory,
  no calf within 16, next feed -> in love for `LOVE_TICKS` (2400, four times a hand-fed one). Herd size cap 10 within 24.
  Muskox must not be refilled by Respawning Animals (find how it picks mobs; exclude ours).
- **Flee**: muskox run from `jaymoskingdoms:predators` (polar bear, wolves, feral wolf, coyote, grizzly) within 12,
  each its own way (herd pull off), through `Herds` if its API fits.
- **Fed predators** (`FedPredators`): after a predator in entity tag `jaymoskingdoms:sated_predators` (polar bear,
  wolf, fox, feral wolf, coyote, plus optional `naturalist:`/`cubeanimals:`/`bearminimum:` hunters with
  `"required": false`) kills an animal, it will not start hunting **animals** again for `FED_TICKS` (48000 = 2 days).
  Players, and anything that hurts it, are still fair game; `WolfStalkers` is untouched. Polar bears hunt muskox
  (add the target goal). Saved on the entity (attachment), so it survives a reload.
- **Young first**: when one of those predators picks an animal target, a baby within range is chosen before an adult.
- Kitchen's `Scavengers` already makes predators eat their kills; check a predator-killed muskox leaves no meat lying
  and fix only if it does.

## Track C: the land (Powers worldgen; after A, by the lead)

`jaymospowers:tundra` and `jaymospowers:lichen_woodland` in the Overworld (not a dimension). No biome library is in
the pack: place them by mixin on the Overworld biome builder (part of snowy plains' cells -> tundra; a band of snowy
taiga cells next to them -> lichen woodland) and add the surface rule by mixin (deepslate from bedrock up in tundra).
Check Wythers/Geophilic don't replace the Overworld preset. Features: greenstone belts (smooth basalt strips with a
small `banded_iron_ore` + raw iron block vein under each), extra iron placement, boulders (deepslate/basalt, small
ones as loose rocks via `ground/`), dwarfed spruce trees, shrubs, yellow moss and sprouted lichen patches, white ice.
Spruce saplings grow dwarfed in both biomes. Pale sky, drifting snow motes (client, off in low-end mode), grass tints
grading through the woodland, autumn palette in Kitchen `SeasonTints`. Muskox and polar bear spawns by biome tag.

## Track D: Klei's art pack, the rule, the notes

`packResourcePacks` pack "Klei_Wright's Tundra art" (CC BY-NC-SA 4.0, credited, his files unchanged, mapped onto our
ids; his muskox CEM through EMF if it fits our entity, else his coat only). CLAUDE.md wildlife rule. Lore paragraph.
Change notes. Not built: qiviut clothing (string for now), burnt taiga regrowing as lichen woodland.
