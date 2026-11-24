# Reference Mods picks: patch plan (2026-11-24)

Pick page: https://claude.ai/artifact/Fu1jDwywXNKSGj6mmU65fg (db picks/andy, saved 2026-11-24 17:09Z). Andy ticked 143 of 148.
Not picked: M8 Rats, and Nether rats; C1 Ship Hands-On Vanilla; C2 Craft on the table top; B9 Ship NoFigura; X1 Waterfalls foam and mist.

Rules for every patch: ideas only unless the source allows a port (MIT, Apache, LGPL: keep the notice; Mo' Creatures GPL-3 looks only inside Kingdoms, which is GPL). Our own names, code and art. No slash commands for player features, no discovery chat text, no new dimensions, no free projectiles. Build every row as written (no silent cuts; name any real cut in the PR). Change note under changes/<mod>/<branch>.md. One PR per patch. Andy merges.
Source jars, versions and licences: project memory reference-mods-fit.md and the page's manifest table.

## Patch 1: Felling: cut through the trunk, carved stump, collapse
Owns: kingdoms trees (FallingTree, ChoppedLog) + powers ground (CaveIns).
Merges with Inbox tree-chop-through-trunk.md (same ask): close that task into this one.

- **T1 Cut through the trunk to fell it** (Timber Tales, size M). The open felling task: each blow cuts a notch at the height you swing, deepening round the trunk. The tree only falls once nothing joins the top to the base, then our falling-tree physics takes over. Cheap: the check runs only when a cut finishes. We have: Felled, our TreeChop-style ChoppedLog chopping, FallingTree (#164), the hanging-tree fix (#1212). Inbox: tree-chop-through-trunk is open.
- **T2 A carved stump stays behind** (Timber Tales, size S). What's left is a stump block with the cut face and notch showing, not a full log. You can sit on it, dig it out with a shovel for a little wood, or leave it to rot into rich soil over a few seasons. We have: The stump is part of the same Inbox task.
- **T3 Cracks you can see and hear** (Timber Tales, size S). No bar on screen: cracks spread across the trunk as you cut, and the tree creaks and groans louder as it nears breaking, so you know when to step clear. We have: Logs break with the vanilla crack only.
- **T4 Choose which way it falls** (Timber Tales, size S). Cut the notch on the side you want it to fall, finish from the back, and it falls toward the notch, unless it leans badly the other way. Gets you a tree across a river as a bridge, or away from your roof. We have: Trees fall to the heavier side, or away from the cutter if even.
- **T5 The ground answers the fall** (Timber Tales, size S). A dust and leaf burst where it lands, a thump you feel (small screen shake close by), flattened grass and snow, birds bursting out of the canopy, and nearby animals bolting. We have: Falling trees damage what they hit and smash weak blocks (#1019).
- **T6 Woodcutters fell trees the same way** (Timber Tales, size S). Village woodcutters cut the notch and back-cut too, and call a warning bubble before it comes down. A tree half-cut by a woodcutter is a hazard you can walk under. We have: Woodcutters chop trees into their bags.
- **T7 Blasts bring buildings down** (TNT Logic Enhanced, size M). Dynamite, cannon shells, creepers and siege fire undercut a wall and the floors above come down as falling blocks. Our support beams hold what they prop. Siege camps can finally topple a tower. We have: CaveIns, loose wood falling (#1942), support beams.
- **T8 Chests fall with what's in them** (TNT Logic Enhanced, size S). A chest or barrel in a collapsing house rides down intact and lands in the rubble, so looting a ruined keep means digging.

## Patch 2: Druid Wild Shape: any studied beast
Owns: kingdoms WildShapes, Druids, Druid tree.

- **W1 Wear any beast you've studied** (Morph (iChun), size L). The Druid's shapes stop being a fixed four. Any animal you've studied (one foe-knowledge star, or watched a while) becomes a shape you can wear, its own body, size and walk, with the stand-in beast as now. Monsters and people stay out: a Druid's shapes are beasts. We have: Druids: wolf, bear, hawk, fish; Fox and Stag; treant and wind elemental at Elder Shape.
- **W2 A shape gives what the beast can do** (Morph (iChun), size M). Each kind of beast lends what it really has: birds and bats fly, fish and dolphins breathe and swim fast, frogs leap, cats land soft and see at night, goats climb and butt, foxes dig, horses gallop, spiders climb walls, striders walk on lava. Its weaknesses come too (fish flop on land, bats hate light). We have: Each of the four shapes has hand-made strengths and a price.
- **W3 Learning a shape shows** (Morph (iChun), size S). The first time you master a beast, a green wisp drifts from it into you and you hear its call. No chat text. We have: Studying finishes silently.
  - Andy's note: "it should be a winding vine animation instead of the black morph as well."
- **W4 A wheel of shapes and favourites** (Morph (iChun), size S). Hold the class key to open a ring of the shapes you know, pick with the mouse, star favourites so they sit at the top. Tapping still takes the selected shape. We have: Sneak and the key steps to the next shape.
- **W5 Bigger beasts as you rise** (Otherworld Origins, size M). Tie the size of beast you can wear to your Druid rank: small and quick first, middling beasts by the middle of the tree, the great ones (elephant, orca, bison) for the Moon circle near the top. We have: More Shapes ranks, Circle of the Moon, Elder Shape.
- **W6 Long and many-limbed bodies** (Otherworld Origins, size M). Snakes, crocodiles and eels worn as shapes get a long body that bends behind you and squeezes what it wraps.
- **W7 Casting in a shape, at the very top** (Otherworld Origins, size S). At the last rank of the tree, a Druid can cast nature's rites while in a shape. We have: Your call: Druids.java says a beast casts nothing.
- **W8 Circle of Spores** (Otherworld Origins, size M). A third circle beside Moon and Land: a haze of spores that rots what stands in it, mushrooms bursting from corpses you pass (Kitchen corpses), and the fungus-stitched dead rising briefly to fight for you. We have: Moon and Land circles only.
- **W9 Call a beast to your side** (Otherworld Origins, size S). Call a wild beast of a kind you've studied out of the brush to fight beside you for a while, even when none is about. We have: Pack Leader gathers beasts already nearby.

## Patch 3: Scouter: hold to scan, full readings, tracking
Owns: powers zfighter ZFighterScouter + client.
Merges with Inbox scouter-bio-scanner-tracking.md. Andy (S8): the scouter IS the item that works for anyone, so the scouter reads for everyone and there is no separate handheld.

- **S1 Hold to scan** (Wan's Bio Scanner, size M). Wearing the scouter, hold its key to focus on what you're looking at: the lens zooms a little and the reading fills in line by line. Let go early and you keep only what came through. We have: The scouter reads name and power level at once.
- **S2 A full reading of a creature** (Wan's Bio Scanner, size M). All of that under the power level, in the scouter's green readout. Horses read our breed stats (speed, stamina, courage, temper); people read their realm and mood.
- **S3 It remembers kinds** (Wan's Bio Scanner, size S). Same, and a full scan counts as studying that beast for the Druid and for foe knowledge. We have: FoeStudy stars come from fighting.
- **S4 Plants and living blocks** (Wan's Bio Scanner, size S). Crops, saplings, bee nests, eggs, coral, moss: what stage, what it needs, how long.
- **S5 Track what you've scanned** (Wan's Bio Scanner, size M). After a full scan, tap the key to mark the target. A small chevron at the edge of the lens points to it and its outline shows through walls while it's within range, for a few minutes or until you lose it. One mark at a time on the basic scouter, more on better ones. Something hiding its ki can't be marked. We have: Inbox: scouter-bio-scanner-tracking is open.
- **S6 Forms it can take** (Wan's Bio Scanner, size S). For things that change: Z Fighters' forms you've seen, xenoform growth stages, a zombie's road to the horde. Unmet stages show as blanks.
- **S7 Reading the place** (Wan's Bio Scanner, size S). Look at open sky or ground and the scouter reads the place: heat or cold, and how dangerous the land is (the harsh-land world tiers), without naming anything lore-ish. A soft chime when a scan completes.
- **S8 A handheld scanner for anyone** (Wan's Bio Scanner, size S). A future-era handheld scanner for people without ki: the same readings without a power level. We have: Only Z Fighter gear reads creatures.
  - Andy's note: "the item that works for anyone, is the scouter"

## Patch 4: Umbrellas and parasols; villagers take cover
Owns: kingdoms wardrobe/MCA outfits, Mohknee; powers item.

- **U1 Parasols for women of wealthy realms** (Umbrellas, size M). Women in wealthy realms carry a parasol in their kingdom's colours and style (lace edges, heraldic panels), opened in sun and rain, closed and hung on the arm indoors. Poorer realms don't. We have: MCA wardrobes and guard outfits; the Artifacts port left the umbrella out.
- **U2 Mohknee paper parasols** (Umbrellas, size M). Mohknee women carry oiled-paper parasols with blossom painting on bamboo ribs, their own style, carried by both villagers and barbarian-tribe women of rank. We have: Mohknee blossom kingdoms (Structures 4).
- **U3 An umbrella for you** (Umbrellas, size S). Rain doesn't wet you or rust your iron, and players who burn in daylight (vampires, the undead) can walk under one, slowly wearing it out. We have: Wet-scrub-rust, sun shelter for the undead.
- **U4 Float down** (Umbrellas, size S). Open a sturdy umbrella mid-fall and you drift down. A lace parasol turns inside out and tears.
- **U5 Ride the campfire smoke** (Umbrellas, size S). Hold an open umbrella over a campfire's smoke and it lifts you, like a hot-air seed.
- **U6 Umbrella stands** (Umbrellas, size S). A stand by doors and market stalls. An open umbrella in it keeps rain off that spot.
- **U7 Everyone opens one when it pours** (Umbrellas, size S). When heavy rain comes, villagers who own an umbrella open it on their way, and the rest pull hoods up or run for cover.
  - Andy's note: "villagers also need to run for cover at night, near fire, or monsters. only the brave and guards should ever fight"

## Patch 5: Hoverboard on the bomb glider model
Owns: powers arsenal (bomb glider model/texture), future workbench.
Andy (H1): the hoverboard uses the arsenal's bomb glider model.

- **H1 A hoverboard in our art** (Hoverboards, size M). A future-era hoverboard with our own model and texture in vanilla style. It hovers over ground and water at a steady height, climbs gentle slopes, hums louder with speed, and leaves a light trail. Built at the future workbench, never found. We have: Space pod, bomb glider, future guns and mechs.
  - Andy's note: "i mean the arsenal's bomb glider model."
- **H2 Dyed hulls and trails** (Hoverboards, size S). Dye the hull and the trail separately.
- **H3 Tricks off a jump** (Hoverboards, size S). Spin and flip on the board off a ramp, borrowing our acrobatics; land badly and you're thrown. We have: Acrobatics and backflips.
- **H4 It runs on charge** (Hoverboards, size S). A power cell charged with redstone, so it isn't free flight. Flat, it drops to the ground and you push it like a skateboard. We have: No free projectiles rule, applied to free flight.
- **H5 Crash at speed** (Hoverboards, size S). Hit a wall fast and you're flung off, ragdolled, and the board skids away. We have: Ragdolls with capped flings.

## Patch 6: Horses: Icy's Better Horses goes in, ours off
Owns: build.gradle pack mods; kingdoms equestrian (Breed, Gait, Stride, Bond, Bonding, Tack, TackMenu, Tackroom, Herd, Herding, HorseKit, HorseRevival, Horsemanship, Mounted, Riders, Discipline, Equine, Boarding, BreedGifts) and its users (Stables, Quests, Cavalry, TradeRoutes, Couriers, Emigrants, Races, Gatherers, MountClimb, PackResourcePacks).
Andy decided 2026-11-24 17:18Z: "icy has far better models and implementations, if we cant use its models then put icy's in instead." Its licence (Bare Minimum License v1.0) lets code be reused but its models and textures only as part of Icy's itself, so the mod goes in whole.
- Add Icy's Better Horses 2.1.8 (Modrinth icys-better-horses, Fabric 26.3) with Modonomicon (MIT/CC-BY-4.0, new) and GeckoLib + Fabric API (already in the pack). Keep its copyright notice and licence text in the pack licences; ship the official jar unchanged (anti-reposting clause), downloaded by id like other pack mods.
- Switch ours off where they overlap: our horse breeds and their coats, five gaits and stamina bar, bond/trust/discipline, tack screens, horse revival, the Mo' Creatures horse renderer for horse/skeleton/zombie horses, and anything Icy's does better (whistle, command wheel, free look, two riders). Keep what Icy's doesn't have: the war elephant and mammoth, the bat horse and nightmare (rewire as Icy's breeds or keep as their own mounts; say which in the PR), stables and stablehands, caravans, rickshaws, cavalry, horse quests (break-in, stud, race), hogtied captives carried on horseback.
- Check overlaps with Horseman, Free Rein (FreeRein/FreeReinClient), Kaffee's Dual Ride, Ultimate Horseshoes, Horse Expert, Craftable Horse Armor: drop or turn off whichever duplicates Icy's, list each in the PR.
- Existing worlds: Icy's converts existing horses once. Our saved breed data on old horses must not crash or duplicate.
- The ticked rows R1-R11 come from Icy's itself now; check each one works in the pack and note any that doesn't.

## Patch 7: Mo' Creatures beasts we lack
Owns: kingdoms monsters (GPL, licenses/mocreatures).

- **M1 Wyverns to raise and ride** (Mo' Creatures, size L). Wyvern nests on high crags in harsh mountains. Take an egg, keep it warm, raise the hatchling, and ride it when grown. Smaller and wilder than our dragons. Where and why: crags far from realms, because they hunt the herds of the high pastures. We have: Our dragons and saints' dragons.
- **M2 Winged and horned horses** (Mo' Creatures, size M). An alchemist's essence, given to a horse with a full bond, changes it: light makes a winged horse, a horn grows on another. Rare and costly, a reward, never found wild. We have: Bat horse and nightmare breeds.
- **M3 Zebras and their crosses** (Mo' Creatures, size S). Zebra herds on the savanna: skittish, kick hard, almost impossible to break. Cross one with a horse for a tough striped mule.
- **M4 The manticore** (Mo' Creatures, size M). A badlands and Nether hunter: flies low, stings with poison, drops its spines.
  - Andy's note: "spawns in an aether dungeon"
- **M5 Scorpions of every land** (Mo' Creatures, size S). Cave, Nether and frost scorpions to go with Naturalist's desert and jungle ones; a mother carries her brood on her back and they scatter when she's hurt. We have: Naturalist desert and jungle scorpions.
  - Andy's note: "only add the  nether, the others can be jungle and desert variants"
- **M6 The golem that throws itself** (Mo' Creatures, size M). A ruin guardian that pulls blocks from the ground around it into its body and hurls them. Strike the glowing core when its chest is bare. We have: Our mud golem, town golems, clockwork and redstone golems.
- **M7 Ents wake when the forest is cut** (Mo' Creatures, size M). Clear-cut too much of an old forest and a great old tree wakes and walks after the cutters, planting saplings as it goes. Leave the woods be and it sleeps again. Stewardship and feedback, from the living-things rules. We have: The Druid's treant shape.
- **M9 A cat that needs you** (Mo' Creatures, size M). Cats you keep get hungry, want a bed and a sand box, grow from kittens, and sulk or wander off if neglected. You can carry one on your shoulder. We have: Live animals can be picked up.
- **M10 Small life everywhere** (Mo' Creatures, size M). Crickets chirping at night, snails after rain, flies and then maggots on corpses left out, roaches in filthy homes, mice in grain stores, moles leaving hills in fields. We have: Naturalist brings butterflies, fireflies, dragonflies and ants.
- **M11 Rays and jellyfish** (Mo' Creatures, size S). Manta rays gliding in warm seas, stingrays buried in shallows that sting waders, jellyfish drifting in blooms that sting.
- **M12 Big cats you can raise** (Mo' Creatures, size M). Take an orphaned cub (its mother killed) and raise it; grown, it guards you. Lions and tigers cross into rare big hybrids. We have: Naturalist's big cats are wild only.
- **M13 Amulets that carry a beast** (Mo' Creatures, size M). A bone charm a beast-tamer makes: a bonded beast rests in it and comes out where you call it.

## Patch 8: A vampire hides among the villagers
Owns: kingdoms villagers, vampires, Senses, journal.

- **P1 A killer hides among the villagers** (Psycho Villagers, size L). A rare dark streak in a villager: they look and talk like everyone else, but at night they stalk anyone alone, drunk or hurt, never in sight of guards. Far rarer than one in ten. We have: Your call: how rare, and whether they can target players.
  - Andy's note: "this should also always be a vampire, normal villagers can attempt it but will usually be killed"
- **P2 A notebook of clues** (Psycho Villagers, size M). A journal page where you pin what you've seen (blood on someone's boots, a missing neighbour, a cellar that's always locked) and mark faces as suspect, missing or dead. It never names the killer for you.
- **P3 'It's been quiet lately'** (Psycho Villagers, size S). Gossip carries the truth: a safe village talks of the harvest, a stalked one of the baker's girl who hasn't been seen. We have: Villager memory and talk.
- **P4 Tailing a suspect** (Psycho Villagers, size S). Follow a suspect at night; they glance back now and then. Spotted, they turn innocent and go home, and remember you. We have: Senses stealth.
- **P5 The taker's cellar** (Psycho Villagers, size L). Instead of a dimension: a maze dug under a house. He drags you down; a cache with a blade and two draughts is somewhere in it; other captives are in side rooms. We have: Your call: a new scripted kidnap. Kidnapping and captives exist.
- **P6 A night when anger spills over** (Psycho Villagers, size M). When a realm's mood sinks low, one night windows get smashed, old scores get settled, and the guards look away. We have: Your call: villager violence among themselves.
- **P7 A rope to lead people** (Psycho Villagers, size S). A rope lead for a hogtied or collared captive, tied off on a fence post. We have: Hogties, collars, Stretchy Leash.

## Patch 9: Village styles and capitals (V1-V14)
Owns: kingdoms worldgen.
Every structure says where it generates and why (placement plan https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab).

- **V1 Hill-hole hamlets** (Integrated Villages, size M). Small folk homes dug into mossy hillsides, round doors, gardens on the roofs. Where and why: old meadows and woodland edges far from roads, because the gnome folk keep away from big-folk wars. We have: Gnome models.
- **V2 Stilt villages in the bog** (Integrated Villages, size M). Huts on stilts joined by boardwalks. Where and why: swamps with fish and herbs, because the bog folk trade reeds and medicine and nobody taxes a marsh.
- **V3 Mud-brick river towns** (Integrated Villages, size M). Mud-brick and thatch homes on raised plinths. Where and why: hot river plains and savanna banks, where clay and reeds are free and floods come yearly.
- **V4 Timber-and-paper villages for Mohknee** (Integrated Villages, size M). Timber frames, paper screens and heavy thatch, for the farm villages of the Mohknee blossom kingdoms. Where and why: only in Mohknee lands, around the blossom kingdoms' rice terraces. We have: Mohknee and Blossom villages (Structures 4).
- **V5 Oasis villages** (Integrated Villages, size S). Palms, a walled well and date farms. Where and why: only where water reaches the surface in harsh deserts, because life clusters at water (harsh-lands climate).
- **V6 Whitewashed harbour towns** (Integrated Villages, size M). Whitewashed stone and terracotta stepped up a warm coast. Where and why: warm coasts on sea trade routes, because ships need ports. We have: Ships and sea events.
- **V7 Pirate coves** (Integrated Villages, size S). A hidden cove of shacks, a careening beach and a fence's market. Where and why: rocky coves far from any realm's navy. We have: Pirates and sea captains exist.
- **V8 A clockwork town** (Integrated Villages, size M). Gears, chimneys and a tinkers' guild hall. Where and why: by rich ore mountains, because the tinkers came for the metal. Clockwork golems patrol it. We have: Clockwork and redstone golems.
- **V9 Crossroads inns that grew** (Integrated Villages, size M). A village grown round an inn and a well. Where and why: only where two roads meet, because travellers stop there. We have: Roads and caravans.
- **V10 Border market towns** (Integrated Villages, size M). Stalls and warehouses round a big square. Where and why: on the border between two realms at peace, because that's where they trade. We have: Economy and diplomacy.
- **V11 Log-cabin villages** (Integrated Villages, size S). Heavy log cabins with stone chimneys. Where and why: taiga and snowy forests, because timber is the trade. We have: Forestry fiefs (#1973).
- **V12 An airship mooring** (Integrated Villages, size M). A mast tower with a docked airship and sky traders. Where and why: high peaks under the cloud towns, because that's where sky folk come down to trade. We have: Cloud towns, sky whalers.
- **V13 Capitals worth the name** (Luki's Grand Capitals, size L). Bigger realm seats: walled districts, a market quarter, temples and a keep, styled by climate. We have: Sprawling kingdoms, Epic villages.
- **V14 Rebuilt illager outposts** (Luki's Grand Capitals, size M). Pillager outposts with palisades, cages and a tower. Where and why: on roads into realms they raid. We have: Illager camp picks.

## Patch 10: Outlying buildings, illager and orc camps (V15-V24)
Owns: kingdoms worldgen, greenskins, illager camps.
Same placement rule.

- **V15 Homes out by the work** (Juztsy's Civilizations, size M). Workers who live where they work: a farmhouse in its fields, a shepherd's hut on the pasture, a woodcutter's cabin in the forest. Where and why: within a walk of a village. We have: VillagersPlus job houses, forestry fiefs.
- **V16 Roadside trading posts** (Juztsy's Civilizations, size S). A lone post where a trader sells to passers-by. Where and why: on roads halfway between towns. We have: Caravans and roads.
- **V17 Watchtowers on both sides** (Juztsy's Civilizations, size S). Village towers with a guard and a signal fire that warns the village; illager towers whose lookout spots you first. Where and why: on high ground facing the other side.
- **V18 Pillager drill yards** (Juztsy's Civilizations, size S). Target dummies and crossbows going off all day. Where and why: next to illager camps, so you hear them first.
- **V19 The evoker's frozen tower** (Juztsy's Civilizations, size M). Where and why: snowy taiga, rare, because the evoker hides his studies from his own kind.
- **V20 A vindicator's lone cabin** (Juztsy's Civilizations, size S). One axe-man exiled from his camp. Where and why: deep in dark forest.
- **V21 Orc war camps with palisades** (The Orcs!, size M). Our greenskin camps with a palisade, canopy tents and a chief's hut holding the loot. Where and why: by the harsh borders orcs raid from. We have: Greenskins, orc lines, orc sightings.
- **V22 Pens where orcs train on captives** (The Orcs!, size M). A pit where captured villagers are made to fight, so the orcs learn how they move. Free them and they go home grateful. We have: Captives, kidnapping.
- **V23 Ground they claim** (The Orcs!, size S). Skull totems mark the edge of a camp. Step past them and the guards come; stay outside and they only watch.
- **V24 Four orc roles** (The Orcs!, size M). Our greenskin ranks: a chief with a long hammer, a shaman lobbing poison pots, a ranger switching bow and crossbow by range, and grunts that rush.

## Patch 11: Land: Tectonic trial and terrain features
Owns: build.gradle pack mods; kingdoms continents.
Andy (L6) wants Tectonic tried and swapped back if it is worse. Tectonic 3.0.32 Fabric 26.3 (MIT, needs Lithostitched + Fabric API, both in the pack). It rewrites the same overworld noise our continents/shape (PR #1592) rewrites, so the trial PR adds Tectonic and switches our continent shape off (keep the climate map), and is judged on a NEW world. Reverting = revert the PR; worlds made on Tectonic keep Tectonic terrain in generated chunks.

- **L1 Rivers that run under mountains** (Tectonic, size M). A river meets a range and carries on into a cave you can boat through. We have: Continental world gen (#1592).
- **L2 Old smugglers' lanterns underground** (Tectonic, size S). A few underground rivers have old lanterns hung by smugglers running goods to the speakeasies. We have: Speakeasies.
- **L3 Stone pillars in the jungle** (Tectonic, size S). Rare jungle pillars with flat tops, nests and ruins on top.
- **L4 Volcanic islands** (Tectonic, size M). Volcanic islands in warm seas with lava, ash beaches and black sand.
- **L5 Lava tunnels at the bottom** (Tectonic, size S). Same, lit and dangerous, near deep ores.
- **L6 Ship Tectonic as it is** (Tectonic, size L). Its Fabric build in the pack. We have: Your call: it would fight our continental world gen and William Wythers' Overhauled Overworld. I'd take ideas only.
  - Andy's note: "which one is more stable? we seem to have alot of world gen issues right now, can we test this then swap back if not?"

## Patch 12: Fishing extras
Owns: powers/kitchen fishing (docs/Plan-fishing.md).

- **F1 Don't stand on the hook** (Better Fishing, size S). Stand close or splash and the fish stay away; a soft warning ripple shows you're too close. We have: Fishing fight (#1114).
- **F2 A solid hook or a light one** (Better Fishing, size S). The strike window grades the hook: strike fast and the hook holds through runs; late and it slips if tension drops. We have: Strike window.
- **F3 The leap** (Better Fishing, size S). Big fish jump clear of the water mid-fight; keep reeling and the line snaps. We have: Runs left and right.
- **F4 Turn against the run** (Better Fishing, size S). Pull your rod (look) the opposite way to a run to tire it faster.
- **F5 An angler's log of bests** (Better Fishing, size S). A log book that keeps your heaviest of each kind and how cleanly you landed it, and a trophy plaque to hang the record fish. We have: Weights per catch.
- **F6 Line that hangs like rope** (Enchanted Fishing Line, size M). Slack line sags into the water and drapes over branches and rails; under tension it straightens. Snagged on a branch, it can break. We have: Line origin fix (first click).

## Patch 13: Body animation and station displays
Owns: powers client animation; C3/C4 block renderers.

- **C3 See what's cooking in the furnace** (Hands-On Vanilla, size S). The ore or food inside shows through the furnace mouth, glowing as it heats.
- **C4 Real books on lecterns and tables** (Hands-On Vanilla, size S). A lectern book opens big in the world as you read it.
- **B1 Feet turn before the body** (Punchy Moves, size M). Standing, look far left and your feet shuffle round under you. We have: Prone, sitting and crouched bodies keep facing (#1950).
- **B2 Lean into turns** (Punchy Moves, size S). Sprinting round a corner leans you in; same on horseback.
- **B3 Wading** (Punchy Moves, size S). Waist-deep water slows you and your arms come up.
- **B4 Balance on fences and beams** (Punchy Moves, size S). Arms out on fence tops and narrow beams. We have: Parkour moves.
- **B5 Hand over hand on ladders** (Punchy Moves, size S). Hands and feet on the rungs as you climb. We have: Climbing for everyone.
- **B6 Faces that move** (Punchy Moves, size M). Eyes blink, faces wince when hit and grin during emotes.
  - Andy's note: "mostly for MCA"
- **B7 The shield arm takes the hit** (Punchy Moves, size S). A blocked blow jolts the shield arm back. We have: Shield bangs.
- **B8 Hands on the reins** (Punchy Moves, size S). Riders sway with the gait and hold the reins.

## Patch 14: Sky, light and air
Owns: client particles/effects.

- **X2 Leaves drift on water** (ImmersiveVFX, size S). Fallen leaves float on ponds under trees and pile at the edges in autumn. We have: Seasons.
- **X3 Fire with smoke, embers and scorch** (ImmersiveVFX, size S). Fires throw embers and smoke lit from below, and leave scorch marks on what they touch. We have: Arson, the forest fire pack.
- **X4 Fog in the hollows** (ImmersiveVFX, size S). Morning mist lying in valleys and over rivers.
- **X5 Northern lights** (ImmersiveVFX, size S). Over the frost lands on clear nights.
- **X6 Falling stars** (ImmersiveVFX, size S). Shooting stars, and now and then a meteor shower; a rare one lands and leaves sky-iron. We have: Inbox: astronomy-scholar-journal.
- **X7 Fireworks over a far city** (ImmersiveVFX, size S). When a realm celebrates (a wedding, a tourney, a victory), fireworks over its city. We have: Tourneys, crowns.
- **X8 Sand on the wind** (ImmersiveVFX, size S). Drifting sand and sandstorms in harsh deserts. We have: Harsh lands.
- **X9 Lava crust** (ImmersiveVFX, size S). Lava edges cool to a cracked glowing crust.
- **X10 Light in its block's colour** (ImmersiveVFX, size M). Soul fire lights blue, redstone red, lanterns warm.
- **X11 Blocks settle into place** (ImmersiveVFX, size S). A placed block settles in with a tiny grow.

## Patch 15: Provenance, coins and goods
Owns: powers item component, kingdoms economy.

- **G1 Every item remembers where it came from** (Chest DNA, size M). Port it (Apache 2.0): the same stamp on every item, shown as a small line on the tooltip with more on Shift.
- **G2 Stolen goods are known** (Chest DNA, size M). With the stamp, a merchant or guard knows an item taken from their realm's chests or homes. Sell it to them and you're caught; fences in speakeasies clean the stamp for a cut. We have: Pickpocketing, speakeasies, arrests.
- **G3 Heirlooms** (Chest DNA, size S). Gear made or carried by your forebears keeps their name, and it passes to the next life in your line. We have: Lineage, old age, reincarnate.
- **G4 Famous finds fetch more** (Chest DNA, size S). Collectors pay extra for things from dragon hoards, sunken ships and old cities.
- **G5 A coin purse** (Numismatic Overhaul, size S). Culture coins sit in a belt purse instead of your slots. We have: Culture coins, toolbelt.
- **G6 Your own shop stall** (Numismatic Overhaul, size M). A stall you set prices on; villagers and players buy from it while you're gone. We have: Shifting Wares, villager trades.
- **G7 A strongbox at home** (Numismatic Overhaul, size S). A locked coin box; thieves go for it. We have: Inbox: realm-banks-finish.
- **G8 Transmutation, the alchemist's last art** (Equivalent Exchange (FTB), size L). No duplicating: at the top of Alchemy, transmute one material into another of equal worth at a heavy cost, with a philosopher's stone as the goal of a long quest. We have: Your call: it cuts across the economy and progression gates.

## Patch 16: Titles, bloods, loot, monster-born (Tensura)
Owns: kingdoms classes/heritage/skills.
Several rows here were marked Your call and Andy ticked them: that is his yes. The Tensura rows also answer Inbox tensura-neo-otherworld-picklist.md.

- **Q1 Titles you earn** (JustLeveling Fork, size S). Deeds earn titles (Wolfbane, the Ferryman, Breaker of Keeps), worn under your name; villagers use them. We have: Reputation ranks.
- **Q2 Gear that asks for skill** (JustLeveling Fork, size M). A greatsword swings badly until your Might is high enough; a longbow won't draw full without Agility. We have: Your call: knowledge levels already gate anvils, smithing and spells.
- **Q3 Dragon blood** (Otherworld Origins, size M). A dragon-blooded heritage tied to our own dragons, each giving a breath and a resistance. We have: Five human bloods, heritage.
- **Q4 The Artificer** (Otherworld Origins, size L). A tinker class: reforge gear in hand, put infusions in it, brew elixirs nobody else can. We have: Your call: a new class; Tinkering is a craft for everyone.
- **Q5 Feats the fifth edition pass skipped** (Otherworld Origins, size M). Chef (your dishes give more), Everybody's Friend (strangers warm faster), Skybound (steady in the Aether), Lucky (a rare second chance). We have: Fifth edition feats (#1387).
- **Q6 Loot affixes by region** (Otherworld Apotheosis, size M). Found gear rolls a quality from where it was found: Aether gear for air, Nether gear for fire. We have: Gem sockets (#910).
- **Q7 Break gear down** (Otherworld Apotheosis, size S). Salvage broken gear at a smith for some of its metal. We have: Melting at the forge.
- **Q8 Monsters that rise by a name** (Tensura Neo Otherworld, size L). Give a goblin or wolf a name and it grows into something more, costing you strength for days; named followers settle your town. We have: Your call: a new follower system next to companions.
- **Q9 Eat to learn** (Tensura Neo Otherworld, size L). A monster race that eats a slain creature's remains to take one of its tricks. We have: Your call: overlaps Wild Shape and remains eating.
- **Q10 Reborn as a monster** (Tensura Neo Otherworld, size L). A locked start: wake as a goblin, kobold or slime-born that grows through stages. We have: Your call: a new start and race line.
- **Q11 A demon lord's throne** (Tensura Neo Otherworld, size L). An endgame for monster-born players: unite the monster tribes and take a throne. We have: Your call. Crowns and imposter rulers exist.
- **Q12 Call back what you've slain** (Tensura Neo Otherworld, size M). A rite that calls the shade of something you killed to fight for you briefly. We have: Necromancers raise thralls.

