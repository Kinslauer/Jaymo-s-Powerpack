# Vanilla+ plan: Andy's picks from "Vanilla+ Mods Finale" (2026-11-19)

Andy ticked 22 rows on the Vanilla+ pick list (https://claude.ai/artifact/JnNRygKBadXX37Ej3rizwS, db `picks/andy`).
Eight "ship" rows went out unchanged in PR #1796. This plan covers everything else: Stellarity, Incendium, his four row
notes and the twelve "port" rows. Each patch is one PR. The rule "no new original mobs until we can model and texture
them better" (Andy, 2026-11-19) holds throughout: no patch here draws a new creature of our own.

Lore paragraphs marked **(new)** are Claude's placements for Andy to approve before they go into `Lore.md`.

---

## Patch A: Incendium, and the demon king's start

**Andy:** "place our demon king in one of its new structures and make us spawn there with that start."

**The Nether file.** Incendium 5.5.4 ships `data/minecraft/dimension/the_nether.json` with its 13 biomes listed outright
(`multi_noise` with a `biomes` list, no `preset`). Fabric's biome API adds Nether biomes only to the vanilla preset
(`NetherBiomePresetMixin`; checked in fabric-biome-api-v1 20.1.0). So with Incendium in, `ZFighterDemonRealm.addBiomes`
adds nothing and the Gloam, the Ash and the Abyss stop generating. That is why Incendium was held back from #1796. Only
Incendium (of the cached pack jars) overrides the Nether file. Enderscape overrides the End file.

**Fix.** At `SERVER_STARTING`, take the Nether `LevelStem`. If its biome source is a `MultiNoiseBiomeSource` holding a
direct list, rebuild that list with every biome Fabric's API was asked to add (our three, plus any other mod's), using
their climate points. Then set it back through a `@Mutable @Accessor` on `MultiNoiseBiomeSource.parameters`, and give
`BiomeSource.possibleBiomes` a fresh memoised supplier. Before writing it, check against the 26.3 sources that neither
`ChunkGenerator.featuresPerStep` nor `possibleBiomes` has been read by then. If either has, hook the codec decode
instead. A stacked world's Nether band builds from the same stem, so the fix covers it too.

**Biome mix.** Our points are Gloam (0.6, -0.65), Ash (-0.7, -0.7) and Abyss (0.85, 0.85). Incendium's ash_barrens sits
at (-0.3, 0) and volcanic_deltas at (-0.625, 0), so our Ash takes the cold, dry corner next to them. Then sample biome
shares with the small noise harness (see cloud-sea memory), and check that each demon layer still covers a fair share.

**The start.** Incendium's **Forbidden Castle** (`incendium:forbidden_castle`) generates only in `incendium:ash_barrens`
(tag `incendium:structure/has_castle`). The demon king is the master the demon start already puts you beside (Andy,
2026-11-19: "The master that emberborn spawn next to for their nether start"). That is the **Master of the Gloam**,
because `ZFighterStarts.demon` looks for the Gloam first. So:
- Append `jaymospowers:demon_gloam` to `incendium:structure/has_castle`. That adds a tag value; nothing in Incendium's
  files is changed.
- `ZFighterStarts.demon` looks for the nearest castle in the Gloam with `ChunkGenerator.findNearestMapStructure`, with a
  small radius (Andy's log showed long chunk-generation waits on wide searches). It falls back to today's demon hall
  when no castle is found.
- The start puts you in the castle's throne hall, beside the Master of the Gloam (`ZFighterDemonRealm.master`, an MCA
  villager as now). The WARD and the old portal nearby stay as they are. On a castle another player already started in,
  the start passes it over, as it does for halls.

**(new) The Forbidden Castle.** *On the Gloam's edge, where the demons' outskirts meet the ash, stands a black castle
older than their villages, and the Master of the Gloam keeps his court in it. The demons call it the Forbidden Castle
because no one goes in uncalled. A child he takes as his ward is raised in its halls, and walks out of its gate into
the Nether when they are grown.*

---

## Patch B: Stellarity as the End, Enderscape as a band under the Nether

**Andy (first):** "what if we shipped this as a layer underneath the nether, and the enderscape end is still accessible
through the portal as a proper end dimension?" **Then (2026-11-19):** "Can we ship stellarity as the end and
enderscape underneath?" This plan follows the second ask. It is Andy's own exception to the no-new-realms rule, like the
high sky and the Cloud Sea. No new portal: you dig through the Nether band's floor (One Dimension turns its bedrock to
obsidian) and drop into it.

**Licence: clear both ways.** Stellarity 6.0.1 (26.3 Fabric, Modrinth `WBiwFsvZ`) ships unchanged, which its licence
allows in modpacks. Only a *modified* Stellarity would need kohara's permission. Enderscape is MIT, so we may build our
band from its data and code.

**How the two fit today.**
- Stellarity is a pure datapack in a jar. It replaces `minecraft:dimension/the_end.json` (a `multi_noise` biome list of
  its own), `dimension_type/the_end.json`, `worldgen/noise_settings/end.json`, the four vanilla End biomes, `end_city`
  and `stronghold`.
- Enderscape adds its biomes in code, through Fabric's `TheEndBiomes`, which only feeds the vanilla
  `minecraft:the_end` biome source. Once Stellarity's list replaces that source, Enderscape's biomes stop generating in
  the real End on their own, which is what Andy wants there.

**The band.**
1. New dimension `jaymospowers:the_beneath`: an End-style dimension type and a `minecraft:the_end` biome source, so that
   Fabric's API fills it with Enderscape's biomes. It gets its own noise settings, a copy of vanilla's End noise,
   because Stellarity rewrites `minecraft:end`.
2. One Dimension order: add it under `minecraft:the_nether` (`compat/OneDimensionDefaults`). For new worlds only.
3. Stacked helpers (Powers, Kingdoms, Kitchen) learn the band, as they did for the Cloud Sea.
4. Enderscape's sky, fog and lighting come from a Lithostitched modifier on `minecraft:the_end`. Add the same
   attributes to our band's dimension type.

**What stays tied to the real End.** Enderscape's End-only code (its End city and gateway mixins, the dragon fight
hooks) still acts on the real End, where Stellarity's End cities now stand. Check that pairing in a plain server before
merging. Our relic, writ and quest hooks that name Enderscape's End structures move to Stellarity's (`end_city` and its
own structures). Strongholds come from Stellarity too.

**To check before building:** that Fabric's End biome mixin feeds a second `the_end` biome source and not only the
real End's; which Enderscape features need `Level.END`; and the biome mix in the band (small noise harness).

**(new) The Beneath.** *Under the Abyss the dark ends. Dig through the demons' deepest floor and there is open air
again: pale islands hanging over nothing, with their own frost and their own strange growth. The demons do not go
there and will not say why. It is not the End the old portals reach. It is simply what lies underneath.*

---

## Patch C: the back slot by default; YDM unlocks with a tool belt

**Andy (Back Weapon Slot row):** "make this the default and vdm unlocks when using a tool belt." I read "vdm" as YDM's
Weapon Master, which we already gate.

**Now.** `WeaponMasterShownMixin` lets YDM's Weapon Master show up to 3 hotbar items on the body, or all of them while a
Traveler Tool Belt is worn in the Baubles belt slot (`ToolBeltCompat.wears`).

**Change.**
- Add a new **back** slot to our Baubles slots (`baubles/`, `BaublesScreen`). It holds one weapon or tool: anything in
  the sword, axe, bow, crossbow, trident, mace or spear tags, plus Tinkers weapons and muskets.
- The item in it is drawn on the back, using the same YDM layer pass (or our own `WornPose` layer when YDM isn't loaded).
- One key (unbound by default) swaps the back item with your held item. It needs an empty hand or a free hotbar slot.
- Without a tool belt, YDM draws only the back slot's item, so `MAX_SHOWN` 3 goes away. With a belt, it draws everything,
  as it does now.
- The back item is not in the hotbar, so it never shows twice.

Back Weapon Slot (MIT, 1.20.1) is the idea only. This is our own code on our own slot system.

---

## Patch D: Horse Expert in the tree

**Andy:** "add to the tree." Horse Expert shows a mount's speed, jump, health and storage while you wear its Monocle
(the monocle is the gate; its client config also has `mustBeTamed` and `requiresSneaking`).

Andy confirmed (2026-11-19) that "the tree" is the K skill tree, where the Horseman skill already lives there (`Skills.HORSEMAN`, rank 1 the
horse whistle, rank 2 tricks). So:
- **Horseman rank 1** reads a mount's stats with no monocle needed. This is a compat mixin on
  `AttributeOverlayHandler.getInspectableEntity`: when the monocle check fails, it asks `Skills` instead.
- The monocle still works for anyone, so nobody loses it.
- A line on the Horseman tile says so.


---

## Patch E: soul harvest and spawners

**Andy (Spawners+ row):** "there needs to be a spell you can learn to harvest souls for the spawners."

Spawners+ (MIT, up to 1.21.1) gives spawners upgrades and lets you pick them up. Ours, after it:
- **Reap a Soul**, a **Death Sorcerer** spell, learnt like the element's others. Cast on a mob at low health (or as the
  killing blow), it binds the mob's soul into an empty soul jar from your inventory: a glass bottle with soul sand.
  `Fx` mote, no new mob.
- **Soul jars** set or feed a spawner: right-click a spawner with a jar of the same kind to add one charge. Charges are
  spent on our upgrades: range (+4), delay (-10%), count (+1), and quiet (no particles, no sound). It takes 3, 6 and
  9 charges to reach each rank.
- A jar of a different kind changes the spawner's mob, but only for kinds a spawner may hold. Bosses, villagers,
  players and our named characters never go in.
- **Picking up a spawner** needs a Silk Touch pickaxe and a full soul jar of its own kind, which is used up. The spawner
  keeps its mob and upgrades.
- The barrow spawners (`Barrows`) are graves. Taking one up counts as robbing a grave for the Lantern Warden's faith.

**(new) Reaping.** *A Death Sorcerer can draw a dying thing's soul out before it goes and stop it in glass. It is not
the Reaper's work: the soul is not taken anywhere, only kept, and a jar broken lets it go. The old spawning cages in the
barrows and the dungeons were fed this way once, and a sorcerer who knows the craft can feed them again.*

---

## Patches F to L: the port rows

All are MIT, so ideas and art may be used with the notice kept. None needs a new mob of ours.

| Patch | Row | What we build | Where |
|---|---|---|---|
| F | Ocean's, Nether's and Ender's Delight | Their dishes as Kitchen recipes on our stations (`Station`), using Enderscape's End ingredients where they fit. Their textures are used under MIT, with the notice kept in `docs/credits`. | Kitchen |
| G | Illager Invasion | Its illager kinds (Invoker, Provoker, Basher, Inquisitor, Marauder, Alchemist, Firecaller, Archivist, Surrendered) as Kingdoms illagers in camps and raids, with temperaments and poses, and its own models and textures under MIT. It may get a 26.3 build soon. If it does, ship that instead and only hook it in. | Kingdoms |
| H | Advancements Tracker | Pin up to 3 advancements from the advancement screen. A small list on the HUD, top right, under the minimap, showing criteria done and still to do. | Powers client |
| I | Countered's Settlement Roads | Kingdoms lays dirt and gravel paths between a realm's towns (along the road-travel routes), with milestones, slowly over game days, never in claimed or player-built ground. | Kingdoms |
| J | Progressive Bosses | Each time the same great foe (Giants, monsters of legend, dragons) is beaten in a world, the next one has more health and damage and one more move, up to 5 steps. This adds to the world-tier scaling (`WorldTiers`, `MonsterGear`); it doesn't replace it. | Kingdoms |
| K | Cat Loaf, Infinity Buttons | Idle sitting cats tuck their paws into a loaf (client pose). Hidden switches (a lever behind a bookshelf, a loose stone button) open lever doors in keeps and dungeons, and the keeps' builders place some. | Kingdoms client + Kingdoms |
| L | AstikorCarts Redux | `CartEntity` already has the supply cart, animal cart and plow. Left to check: a cart pulled by a player on foot, and the plow tilling a 3-wide row. Only those gaps get built, if any. | Kitchen |

---

## Order

1. **A**, Incendium and the demon king's start. It unblocks shipping Incendium.
2. **C** and **D**, small and separate (Powers and Kingdoms).
3. **E**, soul harvest and spawners.
4. **F**, **H**, **K** and **L**, small ports, two at a time.
5. **G**, **I** and **J**, the larger Kingdoms work.
6. **B**, Stellarity as the End and Enderscape under the Nether.

## Open questions for Andy

1. Do the three **(new)** lore paragraphs read right?
