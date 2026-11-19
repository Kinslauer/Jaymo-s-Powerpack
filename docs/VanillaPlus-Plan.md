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
(tag `incendium:structure/has_castle`), and our heartland is literally called the Ash. So:
- Append `jaymospowers:demon_ash` to `incendium:structure/has_castle`. That adds a tag value; nothing in Incendium's
  files is changed.
- `ZFighterStarts.demon` looks for the nearest castle with `ChunkGenerator.findNearestMapStructure`, with a small radius
  (Andy's log showed long chunk-generation waits on wide searches). It falls back to today's demon hall when no castle
  is found.
- The start puts you in the castle's throne hall, and the **Master of the Ash** stands there as the "demon king" (an
  MCA villager, like the other demon masters). The WARD and the old portal nearby stay as they are. On a castle another
  player already started in, the start passes it over, as it does for halls.

I read "our demon king" as the Master of the Ash, who rules the heartland. If Andy meant a new figure above the three
masters, that changes the lore paragraph below.

**(new) The Forbidden Castle.** *In the heart of the Ash stands a black castle older than the demons' villages, and the
Master of the Ash keeps his court in it. The demons call it the Forbidden Castle because no one goes in uncalled. A
child he takes as his ward is raised in its halls, and walks out of its gate into the Nether when they are grown.*

---

## Patch B: Stellarity as a band under the Nether

**Andy:** "what if we shipped this as a layer underneath the nether, and the enderscape end is still accessible through
the portal as a proper end dimension?"

This is Andy's own exception to the no-new-realms rule, like the high sky and the Cloud Sea. No new portal is needed:
you dig through the Nether band's floor (One Dimension turns its bedrock to obsidian) and drop into it.

**What Stellarity is.** Stellarity 6.0.1 (26.3 Fabric, Modrinth `WBiwFsvZ`, no other mods needed) is a datapack in a
jar, with no Java code. It replaces `minecraft:dimension/the_end.json`, `dimension_type/the_end.json`,
`worldgen/noise_settings/end.json`, the four vanilla End biomes, `end_city` and `stronghold`. It adds its own biomes and
structures, and 1,990 functions. Enderscape also replaces `the_end.json`, so today the two fight over the End.

**How the band would work.**
1. Add our own dimension, `jaymospowers:the_beneath`, with Stellarity's biome list and End-style noise. Then put the
   End's own files (dimension, dimension type, noise settings) back to Enderscape's, so the real End through the portal
   is Enderscape's.
2. Add `jaymospowers:the_beneath` to the One Dimension order under `minecraft:the_nether`. A new dimension added to a
   live world's order goes at the bottom anyway, which is where this one goes, but new worlds are still the safe case.
3. Stacked helpers (Powers, Kingdoms, Kitchen) learn the new band, the same way the Cloud Sea's was added.

**What breaks.** Stellarity's mechanics are written for `minecraft:the_end`: 12 functions run `in minecraft:the_end`,
and 28 predicates test that dimension. Its dragon fight, exit portal, gateways, altars and void fishing would stay in
(or look for) the real End, and would not work in the band. The biomes, terrain, structures, blocks, items and loot
would. Before building, list exactly which features survive, using a plain 26.3 server with the files as a datapack.

**The blocker is the licence.** Stellarity's licence lets modpacks use it, but says that "using modified versions of
Stellarity in modpacks" needs **kohara's explicit permission**. Moving its biomes into another dimension, and taking the
End back from it, is a modified use. So Andy (or whoever speaks for the pack) should ask kohara on the Prismatic Shards
Discord before this patch is built. The other choices are to ship Stellarity unchanged as the End in place of
Enderscape, or to drop it.

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

I read "the tree" as the K skill tree, because the Horseman skill already lives there (`Skills.HORSEMAN`, rank 1 the
horse whistle, rank 2 tricks). So:
- **Horseman rank 1** reads a mount's stats with no monocle needed. This is a compat mixin on
  `AttributeOverlayHandler.getInspectableEntity`: when the monocle check fails, it asks `Skills` instead.
- The monocle still works for anyone, so nobody loses it.
- A line on the Horseman tile says so.

If Andy meant the advancement tree (the Jaymo's Powerpack tab), the change is one advancement for the first time you read
a horse, instead of the skill gate.

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
6. **B**, Stellarity, after kohara answers.

## Open questions for Andy

1. Stellarity: ask kohara for permission (recommended), ship it unchanged as the End instead of Enderscape, or drop it?
2. Is the "demon king" the Master of the Ash, or someone new above the three masters?
3. "Add to the tree" for Horse Expert: the skill tree (my reading) or the advancement tree?
4. Do the three **(new)** lore paragraphs read right?
