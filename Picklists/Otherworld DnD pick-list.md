---
type: picklist
source: Otherworld [Dungeons & Dragons] modpack (SHXRKIE, CurseForge 1418133)
url: https://www.curseforge.com/minecraft/modpacks/otherworld-dnd
licence: modpack ARR · Tectonic MIT · Ice and Fire Dragons LGPL-3.0 · Oh The Biomes We've Gone ARR · Luki's Grand Capitals ARR · Integrated Villages ARR · Otherworld Origins ARR · JustLeveling Fork Apache-2.0 · Otherworld Apotheosis MIT
status: waiting-for-picks
created: 2026-11-26
tags: [inbox, picklist, otherworld-dnd]
---

# Otherworld DnD — differences from our pack

A Forge 1.20.1 DnD-style modpack (435 mods). The pack itself is All Rights Reserved, so nothing ships as-is. The rows below are the mods that do what we do not already have, or that do it differently. Tectonic, Ice and Fire, JustLeveling Fork and Otherworld Apotheosis are openly licensed and could be ported; the rest are ideas only. We already have our own class/heritage/feat system, goblins, orcs, coins, banks, bounties and cantrips — those are not listed here.

## Tectonic — fantasy worldgen

`portable` · MIT · Fabric/Neoforge, builds for 1.21.1 and 26.1 (no 26.3 build yet)

- [ ] **Deeper, layered overworld terrain** `L` ^tw-terrain
  Tectonic reshapes the overworld: deeper oceans, a new crust layer, varied stone bands, and new ore distribution tied to depth.
  For us: a worldgen overhaul underneath our existing biomes and structures. Would need a 26.3 port or a datapack fallback (Tectonic ships both). Sits under Geophilic, Biome Blends and the structure packs — a real integration project, not a drop-in.
  Notes:

- [ ] **New ore veins by depth band** `M` ^tw-ore
  Iron, copper, gold, diamond and netherite each get a depth range instead of vanilla's scatter.
  For us: could be done as a datapack (no mod needed) or folded into a worldgen port.
  Notes:

- [ ] **Tectonic as a datapack only** `M` ^tw-datapack
  Tectonic ships a datapack version that works on vanilla. No mod dependency.
  For us: the safest way to get its terrain shape without a 26.3 mod port. Test against our structure packs first.
  Notes:

## Ice and Fire: Dragons — dragons

`portable` · LGPL-3.0 · Forge (by sbom_xela, who also makes Alex's Mobs which we already run)

- [ ] **Dragon species and lairs** `L` ^if-species
  Ice, fire, lightning, frost and storm dragons, each with a lair structure, a dragon egg, and a taming path (feed it, it follows you, it becomes a mount).
  For us: a new mob family with lair structures and a taming system. Big scope: five dragon types, lair generation, mount controls, and the dragon's breath attack. Could also be ideas-only if the scope is too large.
  Notes:

- [ ] **Dragon eggs and baby dragons** `M` ^if-eggs
  Each lair has a nest with an egg; a broken egg hatches a baby dragon that grows.
  For us: a growth stage on the dragon mob, plus lair loot.
  Notes:

- [ ] **Dragon gear and armour** `M` ^if-gear
  Dragon-scaled armour and weapons, each tier per species, with a unique texture.
  For us: a new armour set in our Armory, or a new loot table on the dragon drops.
  Notes:

- [ ] **Dragon lair structures** `M` ^if-lairs
  Underground lairs with hoards, nests, and a boss-level adult dragon.
  For us: a new structure type in our worldgen, with a boss fight.
  Notes:

## JustLeveling [Fork] — character progression

`portable` · Apache-2.0 · Forge 1.20.1 (the fork is by Alzhen; the original JustLeveling is ARR)

- [ ] **XP-based skill levels per attribute** `L` ^jl-skill
  Each attribute (Strength, Dexterity, Intelligence, etc.) has its own XP bar and level, separate from the player's overall level. Gaining a skill level gives a bonus and can unlock abilities.
  For us: we already have class trees (Kingdoms' ClassTrees) and feats. This would be a parallel leveling track — a skill XP bar per attribute alongside the class tree. Could replace or supplement the existing class tree.
  Notes:

- [ ] **Titles earned by deeds** `S` ^jl-titles
  A title unlocks when you complete a specific feat (kill a boss, build a structure, reach a level). The title shows under your name.
  For us: we already have DeedTitles (Kingdoms, from JustLeveling's idea). This is already ours.
  Notes: already ours

- [ ] **Item level requirements** `S` ^jl-requirements
  Some items require a minimum skill level to use (a +3 sword needs Strength 10).
  For us: we already have GearDemands (Kingdoms, from JustLeveling's idea). This is already ours.
  Notes: already ours

- [ ] **Magic schools with spell points** `M` ^jl-magic
  Six magic schools (Abjuration, Conjuration, Divination, Enchantment, Evocation, Illusion), each with its own spell points and spell list. Spells are learned by level, not by item.
  For us: we have Cantrips (Kingdoms) and Spell Engine (a mod we ship). A full magic school system would be a new layer on top — spell points per school, a spell list per school, and a way to learn spells without a wand or staff.
  Notes:

## Otherworld Apotheosis — legendary gear

`portable` · MIT · Forge 1.20.1 (by MuonR, an addon for the MIT-licensed Apotheosis)

- [ ] **Random affixes on found gear** `M` ^oa-affix
  Gear found in the world (dungeons, loot chests, mob drops) rolls random affixes: +damage, +speed, +resistance, etc. Affixes are rolled from a pool, with a rarity tier (common, uncommon, rare, epic, legendary).
  For us: we already have FoundQuality (Kingdoms, from Apotheosis's idea) which rolls a quality from where gear was found. This would extend that to full random affixes with a rarity system.
  Notes:

- [ ] **Reforge bench: reroll affixes** `M` ^oa-reforge
  A workbench that lets you reroll a piece of gear's affixes, spending an ingot. Each reroll is random; you can lock one affix.
  For us: a new workbench in the Armory or a Kingdoms smithy. We have BandForge for the xeno band reforge — this would be a general-purpose reforge for any gear.
  Notes:

- [ ] **Salvage gear for materials** `S` ^oa-salvage
  Breaking a piece of gear down at a smith returns its base materials plus a small bonus.
  For us: we already have GearSalvage (Kingdoms, from Apotheosis's idea). This is already ours.
  Notes: already ours

- [ ] **World tiers: mob scaling by distance** `M` ^oa-tiers
  Mobs further from the world spawn point are stronger and drop better loot. The tier is based on distance from the origin.
  For us: we already have WorldTiers (Kingdoms, from Apotheosis's idea). This is already ours.
  Notes: already ours

## Luki's Grand Capitals — capital village structures

`ideas only` · All Rights Reserved · Forge/Fabric 1.19–1.21 (by lukidon)

- [ ] **Grand capital villages with district layout** `L` ^lc-capitals
  Large village structures with a clear district layout: market, temple, barracks, homes, a well, and a central square. Each district has its own style and a unique set of buildings.
  For us: we already have Epic Structures: Villages (ARR, modpacks allowed) and our own village mods in Kingdoms. This would be a new, larger village structure type — a "capital" that's bigger and more detailed than a regular village. Ideas only; we'd build our own models and textures.
  Notes:

- [ ] **Village heroes and guardians** `M` ^lc-heroes
  A capital has a named hero (a strong villager) and a set of guardians (iron golems or custom villagers) that defend it.
  For us: we already have Kingdoms' village guards and town golems. This would add a named hero with a unique model and a larger guard roster.
  Notes:

## Integrated Villages — village overhaul

`ideas only` · All Rights Reserved · Forge/Fabric/NeoForge 1.20.1, 1.21 (by CraisinLord)

- [ ] **Villages that integrate with modded content** `M` ^iv-integrate
  Villages spawn with modded buildings and jobs: a librarian with a bookshelf mod, a farmer with Farmer's Delight crops, a smith with Create machinery.
  For us: we already have Epic Structures: Villages and our own Kingdoms village system. This would be a layer that makes villages aware of the mods we ship — new jobs, new buildings, new trades for each mod.
  Notes:

- [ ] **Village growth: villagers build new buildings** `M` ^iv-growth
  As a village grows (more villagers), new buildings appear: a school, a hospital, a brewery, a forge.
  For us: a new system in Kingdoms where villages expand over time based on population.
  Notes:

- [ ] **Village defense: walls and gates** `M` ^iv-defense
  Larger villages get walls, gates, and a watchtower. The village's golems patrol the wall.
  For us: a new structure component for Kingdoms villages — walls and gates that scale with village size.
  Notes:

## Oh The Biomes We've Gone — fantasy biomes

`ideas only` · All Rights Reserved · Forge 1.20.1+ (by AOCAWOL, 50+ biomes)

- [ ] **New fantasy biome families** `L` ^otb-biomes
  Over 50 new biomes in families: Ethereal (floating islands, purple flora), Savage (jungle variants with new mobs), Frostbite (extreme cold), Volcanic (lava biomes), and more. Each family has its own flora, fauna, structures and loot.
  For us: we already have our own biome mods (Geophilic, Biome Blends, Naturalist, Cube). This would be a large addition — new biome families with their own mobs, flora and structures. Ideas only; we'd pick the families we want and build our own versions.
  Notes:

- [ ] **Biome-specific mobs** `M` ^otb-mobs
  Each biome family has its own unique mobs: Ethereal has ethereal spirits, Savage has new predators, Volcanic has fire-resistant creatures.
  For us: new mob families tied to the new biomes. Would need models, textures, AI and loot.
  Notes:

- [ ] **Biome structures and loot** `M` ^otb-structures
  Each biome family has its own structures: Ethereal has floating temples, Volcanic has lava fortresses, Savage has tribal huts.
  For us: new structure types tied to the new biomes. Would need models, textures and loot tables.
  Notes:

## Otherworld Origins — DnD races and classes

`ideas only` · All Rights Reserved · Forge 1.20.1 (by MuonR, addon for Origins Forge)

- [ ] **DnD 5e races with racial traits** `M` ^oo-races
  DnD 5e races (Human, Elf, Dwarf, Halfling, Tiefling, Dragonborn, Gnome, Half-Elf, Half-Orc) with their 5e racial traits: darkvision, fire resistance, fey ancestry, etc.
  For us: we already have our own heritage system (Kingdoms' HeritageScreen) with its own races and traits. This would be a new set of races modeled on DnD 5e, with their specific traits. Ideas only; we'd build our own versions with our own names and art.
  Notes:

- [ ] **DnD 5e classes with class features** `M` ^oo-classes
  DnD 5e classes (Fighter, Wizard, Rogue, Cleric, Ranger, Paladin, Warlock, Druid, Barbarian, Monk, Artificer, Bard) with their 5e class features: spellcasting, sneak attack, divine smite, etc.
  For us: we already have our own class system (Kingdoms' Classes, ClassTrees, Heritage). This would be a parallel class system modeled on DnD 5e. A large scope: 12 classes, each with a feature list and a progression path.
  Notes:

- [ ] **Feats from the 5e Player's Handbook** `M` ^oo-feats
  DnD 5e feats (Alert, Lucky, Resilient, Keen, etc.) that a player picks at level-up in place of an ability score increase.
  For us: we already have a feat system (Kingdoms' GrowthScreen, Feats, Paths). This would add DnD 5e's specific feats to our existing system.
  Notes:

## Already ours

Our class/heritage/feat system (Kingdoms' Classes, Heritage, Feats, Paths, Cantrips, ClassTrees), our goblins (GoblinBody, GoblinSkins, GreenskinLooks), our orcs (OrcSkins, GreenskinLooks), our coin system (PurseIcon, Banks, Strongbox), our bounties (BoardTerms, Decrees), our legendary gear pieces (FoundQuality, GearSalvage, WorldTiers, DeedTitles, GearDemands), our Artificer class (from Otherworld Origins' idea), our village mods (Epic Structures: Villages, Kingdoms' village system), our biome mods (Geophilic, Biome Blends, Naturalist, Cube), and our structure packs (ATi Structures, Epic Structures: Dungeons, Moog's Voyager Structures).
