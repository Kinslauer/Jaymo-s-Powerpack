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
