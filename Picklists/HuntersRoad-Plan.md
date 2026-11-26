# Hunter's Road: the plan (2026-11-26)

Andy picked all 39 rows of the pick page (https://claude.ai/artifact/1rxySrYzQCbzcznbqSozee, db `picks/andy`, source
`/mnt/project-files/witcher-picks/witcher-picks.html`). Source: The Conjunction of the Spheres (Tenebris Mors,
github.com/AngelDGr/The-Conjunction-of-the-Spheres, branch 1.21.1-Multiloader, Fabric + NeoForge 1.21.1).

## Licence (checked 2026-11-26)
- Code: MIT in fabric.mod.json and on Modrinth; the repo's LICENSE file is CC0 1.0. Port freely. Add
  `licenses/conjunction-of-the-spheres/` with the MIT notice and a credit line, and name the source class in each
  ported class's javadoc ("from LungeAttackGoal in The Conjunction of the Spheres by Tenebris Mors, MIT").
- Do NOT copy: sounds (the mod page says they are edits of Witcher 1/2/3 game audio and Zapsplat), textures, GeckoLib
  models and animations (CD Projekt's designs), names (Swallow, White Honey, Drowner, Dimeritium, G'valchir, Raven's,
  Manticore...), and the Lavender book text. Our own names, vanilla-style original art (hermes-modeller for models),
  vanilla or our own sounds.

## Rules every patch keeps
- Lore first: each new monster, brew, herb, item and place gets a Lore.md paragraph, marked (new), before code.
- No screen text that states lore or announces discovery. Troll and NPC reactions are bubbles and sounds (the mod's
  `displayClientMessage` lines become bubbles).
- No free projectiles: the mire hag needs mud or water in reach and leaves a scoop; a troll's thrown rock is pried
  from the ground and leaves a hole; the anchor is the item itself; bombs and bolts are crafted and spent.
- Structures need a reason to be where they are (each patch says its reason).
- New bosses and minibosses count for boss bars and world tiers only, never the knowledge ladder.
- Iron and silver are forged through Tinkers, never grid recipes. Armour keeps the 30% durability and wear rulings.
- Mob griefing stays on. Tuning numbers are `static final` constants at the top of each class.
- New features must work with old ones: chat interactions, Awareness, hordes, unburied bodies, ragdolls, downed rules.
- Every player-visible word goes through Quill's text pass.

## Patches (one PR each; two tracks at a time; Andy merges)

### 1. Yellow tells and lunges (Powers, `combat/`) rows A1 A2 A3
- `AttackTells`: a third tier YELLOW between white and red: its own star colour (gold, #f2c230) and its own ping
  (between the white bell and the red drum). Andy's three tiers: white = block, parry or dodge; yellow = can't be
  blocked (a raised guard is knocked aside and the hit lands), parry in the window or dodge; red = can't be parried
  (existing `Guard.UNPARRYABLE_TAG`, `RED_CHANCE`), block or dodge.
- `Guard`: a new `UNBLOCKABLE_TAG`; a blow carrying it passes a guard or shield unless it lands inside the parry window
  (perfect parry still throws the attacker down).
- A1: zombies and husks (all zombie kin that use vanilla's goal) get a lunge: from 4 to 6 blocks, crouch, yellow star,
  leap (port of `LungeAttackGoal` and the `LungeMob` counters). Parried = it lands sprawled at your feet; dodged =
  it sprawls past. Their ordinary swings stay white.
- A2: the same yellow tell on every leap at the player: spider pounce, wolves, vampires' `BloodLeap` used by foes, the
  new corpse-eaters (patch 3).
- A3: husks wait buried in desert sand and dig out when a player comes near, dig back under when they lose him
  (port of the Excavator goals: emerge, wander, return to ground). Sand only, after dark, world tier Dread and up.

### 2. Monster kinds, the bestiary, silver and steel (Kingdoms) rows F4 F3 E1
- F4: item and entity tags for monster kinds: `jaymoskingdoms:kind/dead`, `corpse_eaters`, `brutes` (trolls, ogres,
  cyclops, giants), `beasts`, `spirits` (wraiths, bog lights, fog-wight), `cursed` (werewolves, vampires), `people`.
  Vanilla, our mobs and Alex's Mobs filled in; other mods join by tag. Everything below reads these.
- F3: `Bestiary` gets a page for every kind's members: a smudge until met, a "how it fights" line after a kill, a
  "what it fears" line (its oil, bomb, sign) after an herbalist, hunter or elder tells you in talk, or after you beat
  it with the right oil on. Hints only. The goblin and orc pages stay as they are.
- E1: the silver sword does its bonus to every `corpse_eaters`, `dead`, `spirits` and `cursed` kind; a steel (iron)
  sword gets a small bonus against `people` and `beasts`. Both ride on the back with worn weapons (up to three shown).

### 3. Corpse-eaters (Kingdoms `monsters/`) rows B1 B5 B6 B7 F1 F2
- B1 grave-gnawers: hunched pack corpse-eaters, out at night, drawn to unburied bodies (our ragdolls and Kitchen
  corpses) and battlefields, sometimes with a bigger spined pack leader. A badly hurt one knits back together unless
  finished, burned or dusted (patch 7 bombs). Lunge is yellow.
  **Andy's note: "the npcs know these old things and will pass the knowledge and rush to bury bodies."** Villagers
  know the gnawers of old: one who sees a body lying tells the next person (word spreads person to person, the way
  fire alarms do since 2026-11-26), and grown villagers hurry to carry and bury the dead before dark (Burials). Elders
  and herbalists can tell you what they fear (fills the F3 line).
- B5 bloated dead: swell (red tell), then burst on death in gore that hurts and sickens (blast gibs, blood decals);
  the spined variant leaves spines that bleed.
- B6 grave-brute: deep-cave miniboss walking with a band of bloated dead, charge that breaks blocks (red tell). Boss
  bar, world tier only.
- B7 bone-burrowers: a cave corpse-eater that burrows and leaps (yellow), and a heavy one whose bite gives a rotting
  sickness (eats food, slows healing). Keep it apart from Digger and the Nightfall plague in look and effect.
- F1 nests: a nest block of bones and filth that slowly breeds gnawers or burrow-imps (a slow spawner), breakable only
  by a bomb, an explosion or a well-enchanted shovel, its hoard sometimes holding an enchanted book. Reason: nests
  grow where the dead were left (old battlefields, gibbets, ruined villages, and bodies left unburied for days in
  play). Clearing one is already an incursion board job.
- F2 old bones: old skeleton and fallen-warrior blocks in caves and ruins, sometimes holding a blade (patch 8 names).

### 4. The drowned, hags and fog (Kingdoms `monsters/`) rows B2 B3 B4
- B2 river drowners: in rivers and swamp water, lunge out from the bank (yellow), swim fast, drag the fallen under,
  clumsy on land. More where people drowned (shipwrecks, sea events, capsized boats).
- B3 mire hag (swamps: stoops for mud from the bank and throws it, a scoop left behind) and grave hag (old graveyards
  and TaleAfter graves: tongue lash, stronger with each kill in a row).
- B4 fog-wight: hunts only in fog or rain in swamps and dark forests; draws fog around itself, goes unseen in it,
  sends decoy wisps of itself; lights in the fog lure travellers off the road.

### 5. Trolls, imps and the frost giant (Kingdoms `creatures/`, `monsters/`) rows C1 C2 C3 C4
- C1: port `TrollGossips` onto our troll: reputation that decays daily and spreads troll to troll; a drink (Kitchen
  ale and spirits) or food raises it; above a line it barters, follows, waits or guards a spot you show it. Some are
  rabid (only fight). Rock, ice and forest kinds.
- C2: troll holds, small homes for one to three with barrels of what they've gathered. Reason: a troll settles where
  it can take a toll, so holds sit under the stone bridges of Kingdoms roads, at mountain passes and old quarries.
- C3 burrow-imps: small burrowing packs from warrens that hit harder with a big warrior among them and run when it
  falls. Also give that leader rule to goblin bands.
- C4 frost giant: an ice-cave arena high in the mountains; swings an anchor on a chain, charges, leaps, stronger while
  snowing. Drops the anchor rarely: melee weapon that can be thrown and hauled back (heavier from a height). Look
  and moves must differ from the Arctic Giant. Reason: the cave is the giant's hold over a frozen pass. Boss bar and
  world tier only.

### 6. The hunter's alchemy (Powers `alchemy/`, Kitchen) rows D1 D2 D3 D6 D9 H1
- D1 toxicity: a body load from strong brews. Past half: a green-tinged view; past three quarters: harm every few
  seconds, faster near the top. Ordinary loads fade in minutes; a monster brew's load stays till it ends. No HUD bar:
  dark veins round the eyes on the skin and a slower heartbeat. Reads through PotionClash; sits beside Corruption's
  fleeting layer. A cleansing draught or milk clears it.
- D2 draughts, each in three grades (from brew grade): slow heal that costs hunger; grey dark-sight only in the dark
  that shows nearby living things; harder crits; more sword damage; breath and strength underwater; blood that hurts
  the dead who strike you (and bleeds them at the top grade); fewer poison ticks; a feeding draught; the cleanser.
  Crucible recipes with essences. All add toxicity.
- D3 monster brews: an organ carved from a grown monster's corpse with a butcher's knife (Kitchen butchery, no loot
  drops) makes a ten-minute brew: more harm at full health (mire hag), healing per kill in a row (grave hag), fed
  while untouched (gnawer leader), half harm in rain (fog-wight), a faster mount (imp warrior), troll healing (troll;
  trolls also trade theirs). Each holds a heavy load.
- D6 refill: used phials and spent casings stay in the pack; strong spirit (Kitchen's still) refills them at the
  alchemy bench, or while you sleep or sit at a campfire with spirit on you; sitting by a fire also lets toxicity
  fall faster.
- D9 formulas: a hunter's brew needs its formula, read from a bought or found formula (a new kind of Codex entry).
- H1 hunter's eyes: bright slit pupils on any skin after your first monster brew, for good.

### 7. Oils, bombs, herbs and the herbalist (Powers, Kitchen, Kingdoms) rows D4 D5 D7 D8 D10
- D4 blade oils: four kinds (the dead, beasts, brutes, people) by tag; three grades = +2/+4/+6 damage for 20/40/60
  swings, used up on every swing. Rendered from monster fat in Kitchen, finished in the crucible. A sheen on the held
  blade.
- D5 bombs, crafted and spent, on the GasBottle code: fire burst (breaks nests), stunning flash (stuns monsters,
  blinds players), fire spread, poison cloud, volatile gas that blows near flame, freezing blast (ices water, freezes
  foes, fliers fall), magic snuffer (stops teleporting, casting, beams, portals and, briefly, powers and spells), and
  healing-stopper dust (stops regeneration, invisibility and turning: troll, gnawers, vampires, fog-wight).
- D7 nine herbs (two meadow crop-flowers, a taiga berry bush, a cave wall/ceiling vine, a tall fern, a red swamp
  flower, a puffball and a colony mushroom that both grow huge, a rare root from sniffers, ergot from wheat as a
  Poisons poison). Each with its essences and a Lore.md line.
- D8 herbal tonics at the herbalist's table: herb + water bottle, feeds a little, one good and one bad effect,
  longer per extra herb up to five (Remedies).
- D10 a town herbalist MCA job with a stall: herbs, formulas, spirits, empty phials; buys monster parts. The order's
  herbalist (HerbalistWares) stays.

### 8. Hunter's gear (Powers `armory/`, Kingdoms stables) rows E2 E3 E4 E5 E6
- E2, **Andy's note: "remap the functionality onto our heavy crossbow."** The bolt kinds load into our
  `HeavyCrossbowItem` (and a plain crossbow): plain bolt, blunt, piercing (more with Piercing), exploding, broadhead
  (bleeds; skeletons don't, blunt chips bone). Made at the fletcher's.
- E3 read as: the heavy crossbow becomes the hunter's crossbow, dyeable, rather than a second new crossbow (it is
  already the slow, strong one). Say so in the PR so Andy can ask for a separate item.
- E4 three armour sets with piece bonuses: light hunter's leathers, the alchemist's coat (toxicity room, faster
  drinking, extra refills, longer brews), heavy hunter's mail (faster, harder on monsters, marks monsters under half
  health). Tailor and forge; our own vanilla-style designs.
- E5 horse barding: snow barding (faster in snow, walks on powder snow), knight's barding (arrows and blasts). Must
  work with Icy's Better Horses, which is in whole.
- E6, **Andy's note: "all through arsenal's weaponry."** The named blades are made in the Powers armory
  (`armory/`, WarArms): armour-piercing, bleeding, bane of magical things, and a freezing blade that puts out its
  holder's flames. Our own names and stories, found in ruins or as tale rewards.

### 9. Contracts, curses and the hunter's welcome (Kingdoms quests) rows N1 N3 N4
- N1 monster contracts: a new board work kind (BoardWork, QuestJobs). A notice (goats taken, a fisherman gone); you
  ask around, follow tracks and blood (Hunter's sense), find the victim (ragdoll), name the monster from the bestiary,
  then haggle the fee (Haggle) with an alderman who may underplay it (Liars). The lair has a reason to be there.
- N4 the monster was a person: a contract or tale kind where the beast is a cursed person; kill it for the fee, or
  lift the curse (a night in the lair, a rite, the right truth told). TaleAfter keeps the ending (a grave, or the
  person back in town). Fits werewolves and vampires.
- N3 feared and needed: folk pay a known hunter but keep away: children run, prices rise, the inn has no room. Keyed
  to hunter's eyes, hunts done and trophies carried; saving a village warms it slowly. Prejudice and Liars; attitude
  stays fixed within a talk.

### 10. Hunter's signs (Kingdoms skill tree) row N2
- A Hunter page in the skill tree, gated like the other class pages: five quick, cheap signs: a shove of force (cone),
  a jet of flame (short cone), a ward that soaks a few blows, a snaring glyph on the ground, a calming charm (beast or
  person). Weaker than a mage's spells; no projectiles. Keys per `powers-keys` (no clashes); reuse our gust, fire and
  ward effects.

## Order
Tracks two at a time: 1 with 2, then 3 with 6, then 4 with 7, then 5 with 8, then 9 with 10. Patch 3 needs 2 (tags,
bestiary); 7 needs 6 (crucible recipes, toxicity); 9 needs 2 and 3. Models for patches 3, 4 and 5 go to
hermes-modeller first so they're ready when the code lands.

## Toxony additions (Andy's picks, 2026-11-26 21:05Z)

Andy picked 29 of 31 rows on the Toxony page (https://claude.ai/artifact/TzinLRQPtkpKeDVBtok7kA, db `picks/andy`, source
`/mnt/project-files/toxony/toxony-picks.html`). Not picked: B5 redstone mortar, E3 old silver ore.

**Licence: ideas only.** Toxony (MrFrostyDev, github.com/MrFrostyDev/Toxony_Mod, NeoForge 1.21.1): metadata says LGPL-3.0,
the repo LICENSE is the full GPL-3.0 text, and the README forbids reusing any images, textures, art or models. Copy no
code, art, sound, journal text or invented name (Coldsnap, Whirlsnap, Witchfire, Eternal Plague...). Write each piece
from the behaviour below (clean-room). Real plant names (nightshade, water hemlock, bloodroot) are fine.

### Into patch 6 (the hunter's alchemy)
- T-A1 tolerance: D1's load gets a second number, tolerance. Dosing yourself raises tolerance slowly; a poison weaker
  than your tolerance no longer raises it. Past tolerance = real danger until a detox (golden apple, healing). Body
  signs stay the only display without a gauge.
- T-A3 a poison's lean: every poison ingredient leans heat, sea, decay, cold, forest or Nether (mapped onto our
  essences). Learned by taking a lot of it, or by testing a reagent made from it (redstone mix, crucible, alembic) with
  a needle on a strong creature brought near death; the Scrying Lens shows the lean only after that.
- T-A4 the toxin gauge: a held brass instrument (or worn in the baubles charm slot) showing load, tolerance, next
  threshold and your mutagens.
- T-A5 new harms beside Poisons' four: toxin (big heart losses, can kill), acid (armour weaker for a while), flammable
  (catch fire near flame), cripple (physical blows hurt more), hunt (marked: wolves and beast-mutated hit harder).
- T-B4 the paste ladder in our own blocks: poison paste (Powers mortar), toxic paste (crucible, heat), pure toxin
  (alembic), each a base for oils and brews.
- T-D1 the hunter's flask: holds several doses of one potion, doubles its length (netherite: triples); pour in, topped
  up by refill formula or D6's spirit, self-refills overnight with a Refill enchantment (behind knowledge level 3).
- T-D2 copper lab furniture: vial rack, bell jar, copper scale, loose papers.
- T-N1 sweat it out: a soak in a hot spring or sitting by a fire draws toxin out faster and lets a mutation fade sooner.

### New patch 11: mutagens (after 6) row T-A2
- Hold enough toxin long enough and you cross a threshold and mutate, three steps deep, in the direction of the leans
  you've been taking: beast (speed burst on hits, +damage, faster and marking at night; meat feeds more), water (breath,
  swim speed, regeneration in water, heavy knockback; fire hurts more), hollow (knockback resistance, less damage, slower
  hunger, night sight, wall climb slower than the symbiote's; weak in sun), necrotic (raw meat heals, no hunger effect,
  poison and wither immunity, poison your attacker, wither on hit, counted undead; weak in sun; "revive once" becomes a
  longer black-screen wake-up, never a skip of our death rulings), infernal (less burn, set attackers alight, char wood
  by hand, then fire and lava immunity and burning hits; weak in water and rain), spirit (less harm from phantoms and
  vexes, half fall damage, a chance to slip a blow and go unseen, slow falling; magic hurts more; NO summoned homing
  spirits: free projectiles, so its strike echoes a second time instead). Run out of toxin and you change back.
- Lives on the Kingdoms body side (heritage and blood), not as a Power. Feeds H1 hunter's eyes and N3 feared and needed.

### Into patch 7 (oils, bombs, herbs)
- T-B1 poison garden: jungle mint, nightshade (dark forest), water hemlock (swamp), a blue frost flower (cold taiga),
  bloodroot (crimson forest); seeds by breaking them down; they grow only on farmland poisoned with paste. Each with
  essences, a lean and a Lore.md line.
- T-B2 grafting: a known-lean ingredient on a known-lean poison crop grows a new strain (a moonlit hemlock, a sunspot,
  a war-root, a winter mint, a whirl flower); grafting blind kills the crop.
- T-B3 parts from poisoned beasts: a creature that dies poisoned, cut with a scalpel (a Kitchen butchery tool), gives a
  venom fang (wolf), green spit (llama), a double poison sac (squid), brittle poison bones (bogged), a poison jelly
  (slime). Same carve-not-loot rule as D3.
- T-B6 poisoned sweets (Kitchen): a toxic cake you can set down or throw, milk, mint and dark chocolates, a gift box.
- T-C1 effect oils from a placed pot block: poison, a slowing and weakening haze, a glowing mark, deadly toxin, a
  blinding smoke, armour-eating acid, a purifying fire; a mending oil for tools; fire resistance and healing as splashes
  for friends. Dip the blade in the pot or use from the off hand; the pot stays and refills. Same sheen and swing count
  as D4's kind oils.
- T-C2 acid: thrown on ore it dissolves the rock and leaves extra ore; the strong acid eats some blocks away.
- T-C3 oil sash (about 16 throws) and bandolier (more), filled from a pot, thrown as bombs on D5's code. The endless
  bandolier (patch 8 forge) costs toxin per throw.
- T-C5 spilled oil: a slick on the ground that bursts into flame near fire, torches or lit campfires; works with the
  Forest Fire port and the fire alarms.

### Into patch 8 (gear)
- T-C4 oiled bolts: dip bolts in a placed oil pot; works with E2's heavy crossbow bolts.
- T-D3 the alchemical forge, through the Powers armory: a two-block bench whose plans are found in ruins (F1); fuses a
  weapon with reagents into toxin-fed arms: a blade that grows keener on your load, a scalpel that injects, the endless
  bandolier. They draw from T-A1's load.
- T-E1 plague doctor's outfit (coat, hood, leggings, boots) of toxin-hardened leather: bad effects shorter per piece; a
  netherite-grade set late. Matches our existing plague doctor's mask.
- T-E2 silver steel: silver + steel alloy through Tinkers that upgrades armour, fishing rods and weapon parts, with
  silver's bonus against F4's kinds.
- T-E4 cyclebow: **Andy: "implement it if you can make a good looking fully animated model in blockbench."** A
  one-handed crossbow that fires three bolts in the order loaded, and a cartridge that loads a set order at once.
  Model first (hermes-modeller, Blockbench, fully animated: load, cycle, fire); build the item only if the model passes
  review. If it doesn't, say so in the PR.
- T-E5 twin flintlocks: a pistol in each hand, one fires while the other reloads, on our existing pistols.
- T-E6 venom club (poisons on hit), bone saw (also a Kitchen butchery tool), flail with a heavy-impact enchantment,
  through the Powers armory.

### Into patch 9 (contracts and places)
- T-F1 the expelled alchemist's house: our own scholar, thrown out of an academy, at a village's edge or in woods
  outside an academy town (reason: he was cast out); holds his journal, a book that teaches the craft page by page.
  Lore.md first.
- T-F2 hunter's watchtowers on roads that pass monster ground (nests, troll holds, swamps); their hunters tell what a
  monster fears (F3 bestiary).
- T-F3 the old hunters' guild hall in bigger towns: posts N1 monster contracts, sells formulas and silver.
- T-F4 silver mines, small and large, some abandoned, where silver ore runs in the mountains.
- T-N2 changed hunters on the road: MCA travellers carrying patch 11's mutations, marked by them; some trade, teach a
  formula or a lean, some can be hired, some have gone too far and are the contract.

### Order, updated
Waves: 1+2, then 3+6, then 4+7, then 5+8, then 9+10, then 11 (needs 6). Models for the cyclebow (T-E4), the forge and
the new gear go to hermes-modeller before patch 8.
