# Kingdoms (after 0.7.0)

Inspired by *Tale of Kingdoms: A New Conquest* (Fabric, 1.21.1). **Licence check (from its repository):** the code
is GPL-3.0 (not LGPL as Modrinth lists), and its README says everything else (textures, structures, sounds) is All
Rights Reserved by Convallyria. So its art and buildings can't be reused, and copying its code would make our mod
GPL-3.0. Recommended: an original mod (our own code, art and buildings) that only takes ideas from it. A separate mod, like Jaymo's Kitchen, in
`Desktop\Kingdoms Mod` (the upstream source, untouched, is in `Kingdoms Mod\upstream\tale-of-kingdoms`).

## Wanted (from the user)
- **Several kingdoms**, each with a banner and a capital, possibly built on the villages Towns and Towers spawns.
- **Allegiance:** players pledge to a kingdom: shared ranks, a treasury and territory.
- **War:** kingdoms declare war on each other, with rules for claiming land.
- **Barbarians:** camps and raids that every kingdom has to fight.
- **Rulers are bosses:** every king or fiefdom leader is a boss fight, behind real defences (walls, guards, armies,
  the best weapons and armour). Defeating one raises or lowers your reputation with the other kingdoms and groups
  (their allies resent it, their rivals reward it).

## What upstream is
- 266 Java files, about 15,800 lines; 41 MB of data (88 schematics: the guild castle, the player kingdom's buildings
  and their upgrade tiers), 6 MB of assets (entity textures, GUIs).
- Single-player at heart: one guild, one player's kingdom (built tier by tier with the city builder), the guild's
  quests and its worthiness, shops, workers (foremen, farmers, miners, lumberjacks), the Reficule mages, bandits.
- Written against 1.21.1 with **Yarn** names. 26.3 uses Mojang's own names and has changed rendering (render
  states), GUIs (extractRenderState), entities, networking and items since then. Every file needs touching: in
  practice a rewrite on 26.3 that keeps upstream's designs, schematics, textures and data.

## How to build it (proposed)
1. **Foundation:** a new Fabric 26.3 project (Kitchen's setup). Bring over upstream's assets and schematics, and
   rebuild its core on 26.3: the guild and its entities (guildmaster, guards, archers, banker, shopkeepers,
   blacksmith), coins and shops, the schematic placer, worthiness.
2. **Kingdoms for a server:** several kingdoms instead of one player's. Each has a capital (upstream's kingdom
   buildings, or a Towns and Towers village claimed as one), a banner, a treasury, ranks and claimed land (chunks).
   Players pledge; the king (player or NPC) sets ranks.
3. **Rulers as bosses:** every NPC king or fiefdom lord holds a keep with walls, guard posts and armies, wearing the
   best gear, with a boss bar and phases. Reputation per player (and per kingdom), shifted by kills, raids, trade
   and quests; allies and rivals react.
4. **War and land:** declarations (with a warm-up), rules for what can be claimed (adjacent land, a banner planted
   and held), war score, peace terms.
5. **Barbarians:** camps that spawn in the wild and grow, and raids on kingdoms (bigger as kingdoms grow); clearing
   a camp is a deed every kingdom rewards.
6. Upstream's single-player features that still fit (quests, workers, the city builder) come back one by one.

## Answers
1. **A fallen ruler's kingdom:** any of the three, chosen at random: a successor takes the throne, it becomes the
   victor's vassal, or it falls to ruin.
2. **Reputation is two systems working together:** every player has a reputation with each faction and kingdom, and
   every kingdom you join keeps its own standing for you as well (rank, trust).
3. **NPC kingdoms fight on their own:** autonomous battles between kingdoms; intervening wins or loses you
   reputation with each side.

## Rulers dress for the day (RoyalDress, Kingdoms 0.7.184)
Andy (2026-10-24): "Give kings and queens the ability to wear armor or not as they please/ change activities throughout the day (they change before bed, etc)".

- `RoyalDress` (kingdoms): every ruler (king, queen, lord, lady; folk or MCA villager) picks one of three outfits and is seen to
  change (armour sound, a puff, a word, held still a moment; hurried in danger). The set not worn is kept on the ruler
  (`royal_wear` attachment), generated on first use, never copied or deleted; non-regalia pieces drop with a fallen ruler.
  - **Plate**: danger (foe, hostile stranger, fresh wound, war, war party coming; lingers a minute), and by temper: warlord all
    day, paranoid through round and court and from 40 blocks for any stranger, tyrant and purist on their guard-review round,
    noble and reveler only for danger or war (never the reveler's feast).
  - **Robes**: crown and royal attire (MCA royal clothes if any) otherwise.
  - **Nightwear**: crown and armour off from time 11200 until dawn (they dress again when they wake).
- The day: morning court on the throne (to 2500), the round and guard review (to 5000), the midday meal (5000-7000, the
  villager routine `LUNCH`), afternoon audience at the throne (to 8000), then leisure and evening among the people
  (`Routines`, with rulers' own weights: garden, stroll, animals, chat, reading, fire, dance), bed.
- Tuning constants sit at the top of `RoyalDress` and `Rulers`. Without MCA only folk rulers exist; they start in plate (their
  outfit) and generate finery on first need.

## Ranged tactics and the camps' MCA villagers (Kingdoms 0.7.184)
Andy: "Villager and other ranged ai should not keep firing from the same position if their attacks fail, instead they
should reposition or try other tactics. Also rework the bombardier and other humanoid mobs that arent mca villagers
back into the system. I want full integration."
- `RangedTactics` is the one shared helper. Every projectile a ranged mob looses is followed; one that doesn't hit
  the mob's target (short, wide, a wall, a raised shield, a friend in the way), or a shooter that wants to shoot but
  can't see its target for 2 s, is a failed shot. 3 in a row and it changes tactics, one more each time that doesn't
  help: step sideways, find a spot with a clear line, close in or back off, flank, wait in cover; at the end of the
  list a folk or villager puts the bow away and fights hand to hand for 8 s. Tuning is in the constants at its top.
  Covers folk archers, musketeers, javelin throwers, pistols, bombardiers, Hornkin hurlers, MCA guards and camp
  villagers, skeletons and pillagers. Not covered: cannons (fixed), spells, piglins.
- The camps' "other humanoids" come back into the MCA system: a cove's gunners and bombardiers ashore become MCA
  villagers like deckhands and raiders (gunner: cutlass in hand, pistol off hand; bombardier: dynamite off hand), keep
  their pistols, dynamite, shouts and drops; camp raiders as villagers throw rocks again; downed camp members are tended
  by their own camp's villagers (never by a realm's, nor a realm's by a camp's). Left as folk: chiefs, captains, orcs,
  goblins, ship crews and raid war bands (boss, species or ship-station AI that MCA's body cannot host).
