---
type: picklist
source: Sensible MC modpack (Modrinth)
url: https://modrinth.com/modpack/sensible-modpack
licence: mixed, see each mod's Modrinth page
status: picked
created: 2026-11-16
tags: [inbox, picklist, mods]
---

# Sensible MC — what it has that we don't

Compared Sensible MC **6.0.6** (Fabric 26.2, 385 projects) against our `build.gradle` on master. Read off Modrinth's API on 2026-11-16; nothing was tested in game.

- **48** of theirs we already run (Sodium, Iris, Lithium, Jade, Lootr, Carry On, EMF/ETF, Enderscape, Naturalist, Old Cannons…).
- **25** are libraries, which only matter if a pick below needs one.
- **44** have no 26.3 Fabric build, so they're out.
- **268** have a 26.3 Fabric build. About 100 are worth a look and are listed below. The rest are skipped at the bottom, with the reason.

Every pick below has a 26.3 Fabric file on Modrinth. Most aren't on CurseForge with a 26.3 file, so they would go in `packModsNoCf` as shipped jars. Check each licence allows that first.

Sizes: `S` drop in · `M` needs one test launch or a config pass · `L` likely clashes with something of ours

## 1. Performance

- [x] **Entity Culling** `S` ^sn-entityculling
  Skips drawing entities and block entities you can't see. Big win in villages and kingdoms full of MCA villagers. Client only.
  Notes:

- [x] **ImmediatelyFast** `M` ^sn-immediatelyfast
  Faster HUD, text and map drawing. Test beside Iris 1.11.7 and our HUD zones.
  Notes:

- [x] **ModernFix-mVUS** `M` ^sn-modernfix
  Fork of ModernFix with a 26.3 build (the handbook says ModernFix has none). Faster launch, less memory. Test with C2ME.
  Notes:

- [x] **ScalableLux** `S` ^sn-scalablelux
  Faster light updates. Helps with fire, dragon breath and Gravity's flying blocks.
  Notes: taken out again 2026-11-18: it breaks the stacked world's separate skies (the Overworld under the Aether stayed dark by day).

- [x] **Structure Layout Optimizer** `S` ^sn-slo
  Faster jigsaw structure generation. We run a lot of structure packs (Epic, Moog's, Structory, Grim, Repurposed). Server only.
  Notes:

- [x] **Krypton** `S` ^sn-krypton
  Lighter networking. Server and client.
  Notes:

- [x] **BadOptimizations / Cull Leaves / Particle Core / Optimized Block Entities** `M` ^sn-smallperf
  Small client wins, one each. Particle Core's culling may hide our power and blood particles. Check.
  Notes:

- [x] **Let Me Despawn** `M` ^sn-lmd
  Stops mobs that picked up an item from never despawning. Check it leaves downed humanoids and bodies alone.
  Notes:

- [x] **Packet Fixer / Remove Reloading Screen** `S` ^sn-packets
  Fewer "packet too big" kicks with heavy NBT. Resource packs reload in the background.
  Notes:

## 2. Looks and ambience

- [x] **Cirrus** `M` ^sn-cirrus
  Cloud and sky overhaul. Andy liked Simple Clouds' look "as long as it costs little" but couldn't use it. This is the nearest Fabric option. Check it with Complementary.
  Notes:

- [x] **Visuality** `S` ^sn-visuality
  Small particles: sparks off hit armour, slime blobs, water circles in rain, glinting ores.
  Notes:

- [x] **Cool Rain** `S` ^sn-coolrain
  Rain sounds different on leaves, metal, glass and so on. Check it doesn't stack on AmbientSounds.
  Notes:

- [x] **Windy / Effectual / Grassier Grass** `M` ^sn-wind
  Wind streaks, fireflies and dust, grass that sways. All client side. Pick one or two; they overlap with Subtle Effects.
  Grassier Grass removed 2026-11-16 (Andy: lagging the game); Windy and Effectual stay.
  Notes:

- [x] **Continuity** `S` ^sn-continuity
  Connected textures. Only worth it if a resource pack we ship uses them (check VanillaMedieval).
  Notes:

- [x] **Particle Interactions / Explosive Enhancement / Make Bubbles Pop** `S` ^sn-particles
  Particles when you strip logs or place blocks, better explosions (cannons), bubbles that pop at the surface.
  Notes:

- [x] **Visual Workbench** `S` ^sn-workbench
  Items lie on the crafting table and stay there. Very medieval.
  Notes:

- [x] **Handful / Stupid Block Animations / Telepistons** `M` ^sn-handanim
  Held stacks look like stacks; blocks pop in when placed; pistons slide. Handful may fight First-person Model.
  Notes:

- [x] **WindChime** `S` ^sn-windchime
  A decoration block that chimes in the wind. Fits porches and shrines.
  Notes:

## 3. Resource packs and shaders

- [x] **Fresh Animations (+ Details, + Creepers)** `M` ^sn-fa
  We already fetch base Fresh Animations (client/PackResourcePacks); only its Details and Creepers add-ons are new. They run on the EMF/ETF we have.
  Notes:

- [x] **Armored Legacy** `M` ^sn-armoredlegacy
  3D armour models via EMF. Check it against Fantasy Armor and our 30% durability armour.
  Notes:

- [x] **3D Ladders / Remodeled Doors 3D / Fabulous Fences / Fancy Crops** `S` ^sn-3dpacks
  Small model packs that suit a medieval look. Check they don't undo VanillaMedieval's textures.
  Notes:

- [x] **Enchanted Books: Re-covered** `S` ^sn-books
  Each enchanted book gets its own cover.
  Notes:

- [x] **Extra shaders: Photon, Solas, Bliss** `S` ^sn-shaders
  Optional extras next to Complementary Unbound. Solas and Bliss lean fantasy. Players pick one in the menu.
  Notes:

## 4. Animation (test only)

These touch the same ground as the combat-animations trial. Treat them as references.

- [x] **Punchy! / Punchy! Moves / Fluid & Foolish** `L` ^sn-punchy
  Removed 2026-11-16 (Andy: lagging the game).
  First and third person animations with a dynamic camera. Sensible just swapped Not Enough Animations for these. Likely fights Better Combat, First-person Model, Shoulder Surfing and our own poses.
  Notes:

- [x] **Take a Seat!** `M` ^sn-seat
  20+ sitting poses. Check against Kingdoms' chairs and benches, if any.
  Notes:

- [x] **Crawl / Better Climbing / Cut Through** `S` ^sn-move
  Crawl on command; ladders and vines climb better; swing through tall grass to hit mobs.
  Notes:

## 5. Creatures and mobs

- [x] **Armored Foes** `M` ^sn-armoredfoes
  Gives zombies, skeletons, illagers and others proper gear. Configure out gold and diamond gear (standing ruling).
  Notes: Gold is only realistically used in the nether, but noone else wears it seriously. It is all still available. Villagers may make fun of it

- [x] **Direbats** `S` ^sn-direbats
  Big vampire bats in caves that steal items. Medieval-horror fit.
  Notes:

- [x] **Earth & Water** `M` ^sn-earthwater
  Earth and water elementals to go with the Blaze and Breeze. Check against the lore's magic before adding.
  Notes:

- [x] **Ambient Creatures** `S` ^sn-ambient
  A few small friendly critters to fill the world. Check overlap with Naturalist and Cube Animals.
  Notes:

- [x] **VO: Better Dogs / Enhanced Cats** `S` ^sn-pets
  Wolves get personalities and smarter AI; cats get more to do.
  Notes:

- [x] **Herd Mentality** `L` ^sn-herd
  Hit one mob and its kind join in. Clashes with the ruling that herd animals flee nearby killings, unless limited to hostiles.
  Notes: Limit to hostiles

- [x] **Hostile Mobs Improve Over Time** `L` ^sn-hostile
  Mobs get stronger the longer you play. Overlaps our world tiers.
  Notes: Can we work it in with the world tiers?

- [x] **Brainier Bees / Luna Slimes** `S` ^sn-mobfix
  Better bee AI; slimes get more variety.
  Notes:

## 6. Mounts and travel

- [x] **Ultimate Horseshoes** `S` ^sn-horseshoes
  Horseshoes as horse gear. Very medieval.
  Notes:

- [x] **Seaworthy Boats** `M` ^sn-boats
  Upgrade boats into tougher tiers at a shipyard. Check with Shippy Ships and Canoe boats.
  Notes:

- [x] **Mounts Stay Still / Stretchy Leash / Craftable Horse Armor** `S` ^sn-mounts
  Horses don't wander when you get off; leashes snap less; horse armour can be crafted.
  Notes: Our horses can already do that, the other features seem helpful however.

## 7. Villages and trade

All of these touch villagers, so check each with MCA and Kingdoms first.

- [x] **Shifting Wares** `M` ^sn-wares
  Villagers sometimes reroll their trades. Check against coins and Kingdoms' shops.
  Notes:

- [x] **Doors Closed** `S` ^sn-doors
  Villagers close the doors they walk through.
  Notes:

- [x] **Room For Two** `M` ^sn-bed
  Share a bed with a villager or player. Check with MCA spouses.
  Notes:

- [x] **Village Masquerade** `L` ^sn-masq
  Dress as a villager. Clashes with "clothes only from the tailor" unless the tailor sells them.
  Notes:

- [x] **Droppable Villager Trades / Villager Converting** `S` ^sn-villmisc
  Villagers drop a trade on death; zombie attacks always convert them.
  Notes:

## 8. Building and decor

- [x] **Stoneworks** `S` ^sn-stoneworks
  Many more stone shapes and variants. Castles.
  Notes:

- [ ] **Underlay / Item Signs / Armor Statues** `S` ^sn-decor
  Carpets under anything; items shown on signs (shop signs); posable armour stands.
  Notes:

- [ ] **Reconnectible Chains / Gardener's Dream** `S` ^sn-decor2
  Hang chains between fences and walls; any plant in a pot.
  Notes:

- [x] **Scholar** `S` ^sn-scholar
  Two-page books, formatting, coloured books, mobs that read. Fits journals and the Astronomer's Journal.
  Notes:

## 9. World and survival

- [x] **Animal Weights** `M` ^sn-weights
  Animals grow heavier on good farms. Ties into butchery and Kitchen.
  Notes:

- [x] **Unloaded Activity** `M` ^sn-unloaded
  Crops, furnaces and animals catch up after their chunk was unloaded. Check with Kingdoms' storehouses.
  Notes:

- [x] **SleepWarp / NoWeatherSkip** `S` ^sn-sleep
  Sleep speeds time up instead of skipping it; sleeping keeps the rain. Check with Seasons.
  Notes:

- [x] **Comfortable Campfires** `S` ^sn-campfire
  Sitting by a lit campfire gives slow regeneration. Check against food hearts never healing back.
  Notes:

- [x] **Block Runner** `S` ^sn-runner
  Run faster on paths. Pushes players to build roads between kingdoms.
  Notes:

- [x] **Immersive Snow / Taller Forests** `S` ^sn-worldtweak
  Snow piles up in winter; dense forests get a little taller.
  Notes:

- [x] **Lithosphere** `L` ^sn-lithosphere
  Reworks overworld terrain. Would fight Geophilic, WWOO and Biome Blends. Needs a new world.
  Notes:

- [ ] **Wayback Stone** `M` ^sn-wayback
  An item that takes you back home. Check with the lore; it is magic travel.
  Notes:

- [ ] **Penchant / Tax Free Levels / Grind Enchantments** `L` ^sn-enchant
  Enchant rework and cheaper anvils. All three clash with the knowledge-level gates on anvils and enchanting.
  Notes:

- [x] **Tiered Shields / Passive Shield / The Fletching Table Mod** `M` ^sn-gear
  More shield tiers (turn off gold and diamond), shield that blocks while idle in the off hand, a working fletching table. Check against parries and Archers.
  Notes:

## 10. Inventory and HUD

- [x] **Xaero's World Map** `S` ^sn-worldmap
  We have the minimap only. Our docs already talk about the world map drawing kingdom holdings.
  Notes:

- [x] **AppleSkin** `S` ^sn-appleskin
  Shows saturation and what food restores. Check with Kitchen's food hearts.
  Notes:

- [ ] **InvMove / Smooth Swapping / Stack Refill / Status Effect Bars** `S` ^sn-inv
  Walk with a screen open; items glide between slots; hand refills from inventory; timers on effect icons.
  Notes:

- [x] **Item Highlighter / Legendary Tooltips / Polymorph+** `S` ^sn-tooltips
  Marks newly picked-up items; fancier tooltips for rare gear; pick between clashing recipes.
  Notes:

- [ ] **KleeSlabs / Accurate Block Placement / Universal Bone Meal** `S` ^sn-build
  Break half a double slab; steadier placement; bone meal works on everything.
  Notes:

- [x] **Chat Heads** `S` ^sn-chatheads
  Player heads next to chat lines.
  Notes:

## Skipped

**We built our own:**
- Serene Seasons (Seasons)
- Thief (Theft)
- Simple Blood (ported, toned down)
- FallingTree (Felled)
- Simplified Backpacks (backpack)
- Wakes (Dynamic Waters patch)
- Iron Door Key (Locks plan)
- Streams Reflowing (Dynamic Waters replaced it)

**Clashes with a ruling or a removal:**
- Distant Horizons (don't re-add)
- Camera Utils (breaks Shoulder Surfing)
- ReArm: Combat Overhaul (Better Combat and our combat)
- Damage Numbers
- Movement Plus
- Omnidirectional Movement
- Overpowered Mending
- Inventory Totem, Void Totem

**Not medieval:**
- Simple Copper Pipes, Copper Hopper, Copper Rails, Pushier Pistons
- Ziplines
- Flight Enchantment, Elytra Wind Brake, Trinkets Elytra Support
- Bucket of Nautilus
- Kiss a Friend
- GGTU
- Nvidium (Nvidia only)
- Easy Mod Sync

**We already run the same thing:**
- Ride Together (Kaffee's Dual Ride)
- Zoomify (OK Zoomer)
- REI (JEI)
- Atmosfera (AmbientSounds)
- Villager Trading Plus
- Inventory Essentials (Mouse Tweaks and Client Sort)
- Webs (Stormie's Spiders)
- Villager Names (MCA)
- Falling Leaves (vanilla has it)
- Farmer's Delight, plus Chef's Delight, More Delight and Brewin' And Chewin' (Jaymo's Kitchen; only if the Kitchen plan wants FD)

**Too small or vanilla fixes:**
- About 90 one-line tweaks, for example:
  - the "Craftable X" datapacks
  - single-enchantment mods (Poisoning, Freezing, Swiftness…)
  - menu and splash editors
  - squid and rabbit fixes
- Ask if you want the full list.

## Built (branch claude/sensible-picks)

Everything ticked went into `build.gradle` (`packModsNoCf`) or `client/PackResourcePacks`, except:

- **Lithosphere**: left for its own change. It reworks overworld terrain, so new chunks in a running world wouldn't
  match the old ones, and it would fight Geophilic, WWOO and Biome Blends.
- **Photon, Solas and Bliss shaders**: left out for now. The pack takes shaders only through the CurseForge
  import (ShaderPreset), the cloud can't look up CurseForge ids, and their licences weren't checked.
- **Already ours**, found while adding: Crawl (CrawlTaps), Visual Workbench (Kitchen keeps the grid on the crafting
  table and draws it), SleepWarp (Kingdoms' Timelapse night), Explosive Enhancement (our explosion debris, dust and
  flash), The Fletching Table Mod (our Fletching Station is the vanilla fletching table).
- **Mounts Stay Still**: out, as Andy noted.

