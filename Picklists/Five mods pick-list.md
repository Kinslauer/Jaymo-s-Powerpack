---
type: picklist
source: Five mods Tiv linked
url: https://www.curseforge.com/minecraft/mc-mods/simple-difficulty-reforged · https://www.curseforge.com/minecraft/mc-mods/dynamictrees · https://www.curseforge.com/minecraft/modpacks/worldbound/gallery · https://www.curseforge.com/minecraft/mc-mods/tinker-delight · https://github.com/sskaffy/Realistic-Water/releases
licence: mixed, see each section
status: waiting-for-picks
created: 2026-11-08
tags: [inbox, picklist, mods]
---

# Five mods — fit check

Only one of the five runs on our pack today (26.3 Fabric). The other four are older versions or other mod loaders, so what's left of them is ideas. Everything below was read off the mod pages, source trees and licence files on 2026-11-08 — nothing was tested in game.

## 1. Realistic Water — the one we can try

`ship as-is` · Fabric 26.3 (1.3.2) · custom licence: unmodified in any pack, forks allowed with credit

- [x] **Decide whether the block physics can be separated from the volumetric look** `L` ^rw-split
  It cannot. The physics and the picture are one subsystem: a client-side fluid solver drawn as volumetric water, with sand and lava running through the same solver. There is no "physics only" switch in the source.
  For us: the block physics you liked only exists inside the simulation you were planning to leave out.
  Notes: we can try it.

- [x] **Test launch it against the Sodium / Iris stack** `M` ^rw-vulkan
  The mod asks you to set the game's graphics API to Vulkan and restart. Our renderer mods were built for the other path, so treat them as unknown until one launch says otherwise. This single test decides whether the mod is even possible for us.
  Notes:

- [x] **If it runs, dial it down rather than split it** `S` ^rw-perf
  The cost knobs are runtime commands, not a config file: `/water settings` lists them, `/water set resolution 3` turns the detail down, `/water pause` stops the sim, `/water stats` shows what it costs. Nothing is saved, so a tuned setup cannot ship with the pack yet.
  Notes:

- [x] **Settle who owns water** `S` ^rw-streams
  Streams Reflowing already makes our rivers and lakes. Two water systems in one world need a rule about which one wins.
  Notes: streams wins obviously, i just want the blocks. 

## 2. Dynamic Trees — right mod, wrong version

`build ourselves later` · MIT · last Fabric build 1.21.1, February 2026

- [x] **Park it until a 26.3 Fabric build exists** `M` ^dt-wait
  There is no 26.3 file. The mod is MIT, so building it ourselves is allowed, but it is a world generator mod — a real port, not an afternoon.
  Notes:

- [x] **If we do port it, settle Felled first** `M` ^dt-felled
  Both chop a whole tree from the base. Felled is the cheap answer; Dynamic Trees is the pretty one — trees grow over time, spread into forests, thin branches can be climbed.
  Notes:

- [x] **Check tree generation against our biomes** `M` ^dt-worldgen
  It takes over tree placement, which sits underneath Geophilic, Biome Blends and the structure packs.
  Notes:

## 3. Simple Difficulty Reforged — ideas for our own thirst and heat

`ideas only` · MIT · Forge 1.20.1 · an add-on for thirst and temperature mods we do not run

- [x] **Canteens with limited sips** `S` ^sd-canteen
  Leather holds 3 uses, iron 6, dragon 8 and cleans the water as it fills.
  For us: WaterskinItem — give the one item tiers instead of adding a second item.
  Notes:

- [x] **Bad water gives a slow parasite instead of poison** `S` ^sd-parasite
  Dirty water ticks damage at random rather than a straight potion hit; a "thirsty" debuff replaces nausea.
  For us: Kitchen Thirst — our own effect rather than borrowing vanilla poison, which reads as cheap.
  Notes: parasites and treatment methods should be added too.

- [x] **Blight mobs** `M` ^sd-blight
  Occasional elites with boosted stats and a visible tell so you can choose the fight.
  For us: the monster roster, not a new system.
  Notes:

- [x] **Crystal hearts found in ore** `M` ^sd-hearts
  Extra hearts mined out of the world, so health becomes something you find.
  For us: Powers health and downed state — needs a ruling on whether health can be a find, not only a level.
  Notes: finding permanent hearts is a good addition

## 4. Tinker's Delight — ideas for Kitchen and our tinkering

`ideas only` · All Rights Reserved · Forge 1.20.1 · add-on for Tinkers' Construct and Farmer's Delight

- [x] **Knives as real tinkered tools** `M` ^td-knife
  Metal knives with a per-metal effect on hit — cobalt poisons, slimesteel slows, manyullyn weakens — and they also cut straw and board items.
  For us: our knives already cut straw on the cutting board; the per-metal effect is the new part.
  Notes:

- [x] **Melt our own cookware back down** `S` ^td-melt
  Pot, pan, skillet and knives smelt back into their metal at the smeltery.
  For us: MeltingRecipes — cheap, and it fixes the "I cast the wrong pan" annoyance.
  Notes:

- [x] **Cut cakes and bricks apart** `S` ^td-cut
  Slime cakes into shareable slices, seared and scorched bricks back into singles.
  For us: cutting board recipes; splitting a block back into its parts is genuinely useful to a smeltery builder.
  Notes:

- [ ] **One dish that buffs tinkered gear** `S` ^td-skill
  A meal that gives tinkered weapons a chance of extra damage or weakness.
  For us: Kitchen already ties food to stats through Comfort and Nourishment; this would be a third buff.
  Notes:

## 5. Worldbound — a reference, not a download

`ideas only` · All Rights Reserved · NeoForge 1.21.1 · vanilla+ pack

- [x] **Read its mod list once for gaps, then drop it** `S` ^wb-list
  Best at: dense structures in all three dimensions, a bigger Illager threat, an enchantment overhaul, bosses. We already run Dungeons & Taverns, Epic Structures, Structory, It Takes a Pillage and the spell mods.
  For us: one pass to name anything we are genuinely missing.
  Notes:

- [ ] **The gallery link is only screenshots** `S` ^wb-gallery
  They show what its structure mods produce. Nothing there can be imported — it is All Rights Reserved.
  Notes:

## Notes



## Already ours

Thirst and body heat (Kitchen climate, Waterskin), our own Tinker's Construct (part builder, smeltery, modifiers, tool levels), Farmer's Delight cooking (pot, pan, cutting board, knives, feasts, Comfort and Nourishment), rivers and lakes (Streams Reflowing), tree felling (Felled), dense structures and Illagers (Dungeons & Taverns, Epic Structures, Structory, It Takes a Pillage).
