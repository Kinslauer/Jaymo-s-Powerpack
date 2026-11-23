# Heritage ways of living: plan (Tideborn first)

Drafted 2026-11-23 with the `draft-addition` skill; Andy answered the first questions the same day (in
`Decisions.md`). Nothing is built yet.

Andy: "different heritages utilizing different methods for survival, for example tide born swimming and hunting fish
with spears, as they can breathe underwater, they should hunt in packs and be able to defend even against sharks, as
they are superior swimmers. i'd also like the new large water temples to host tideborn kingdoms, they can live and
produce from inside with the glass connected halls".

## Andy's answers (2026-11-23)

- "all tideborn should now be fully amphibious, you can remove that skill or give it new functionality" (the
  Reefwalker's water-breathing end step).
- Tideborn and fishing: "reverse or change it, they need a way to eat."
- The Tideborn in the drowned courts: "the ones there are rogues and abandoned the deep mother and their people's ways".
- The large water temples are the ones he spawned on in the world "Tempt 4". That is Dungeons and Taverns' **Trident
  Trial Monument** (`nova_structures:trident_trial_monument`), next to his player at chunk (1, 5) there.
- The glass halls hold air, "so that crops can grow as well".
- One monument in three is a Tideborn court; "the rest should stay hostile and maybe be conquered by the tideborn like
  dry ground MCA do".
- Tideborn first, then one heritage at a time.

## 1. Examine, place it, plan

**The problem.** Every settlement already feeds itself by hand (`Hunters.java`, Andy 2026-11-19: "everyone should have
to do this or some way of getting food"), but every blood does it the same way: creep up on land game, run it down,
pick berries. Andy wants each people to live the way its blood is made for. Tideborn are the first: amphibious, they
fish and hunt the sea in packs with spears and stand off sharks, and rogue Tideborn hold courts in the great trident
monuments.

**Who it's for.** Anyone sailing or diving near a Tideborn island haven or a trident monument; Tideborn players.

**What we have to build with** (seen in the code or the jars):
- `Hunters.java`: hunting and gathering by a camp's band and a town's folk, only while a player is within
  `WATCHED = 128`; `GAME` is land animals; spoils go to the storehouse; poaching caps for `ForestRangers`.
- `Havens.java:116-138`: island, river and beach settlements are Tideborn havens. `HavenBuildings`: stilt houses.
- `Heritage.java:132`: `TIDE_OXYGEN = 3.0` (long breath), `TIDE_SWIM`, `TIDE_SUBMERGED_MINING`, Dolphin's Grace, hit
  harder while wet. `HeritagePaths`: Tideborn paths `TB_REEF`, `TB_SALT`, `TB_MOTHER`; the Reefwalker ends breathing
  water.
- `UnderWater.java`: spears, tridents and daggers work under water as on land; water-folk fight as always.
- `whaling/Sharks.java`, `whaling/SharkWake.java`: Naturalist's `great_white_shark`, packs of 3 to 5.
- `TideFishing.java`: today Tideborn treat fishing as an attack, and fishing in their haven as a crime.
- `DntBuildings.java`: we already read Dungeons and Taverns' (`nova_structures`) structures, so its monument can be
  handled the same way, through a check that the mod is loaded.
- **The Trident Trial Monument** (`dungeons-and-taverns` jar, `data/nova_structures/structure/trident_trials_monument/`):
  a jigsaw of 345 pieces (4,029 in Tempt 4's), in ocean-monument biomes, starting at y 41. Pieces: `main`,
  `small_monument`, `bridge`, `conduit_stage`, `drowned_stage`, `elder_guardian_stage`, `vault_stage`, `vault`,
  `spawner`. Mostly water (121,501 blocks) and prismarine; 1,082 glass, 738 of it in the `bridge` pieces (the glass
  halls joining the main monument to the small ones), and 1,575 air across `main`, `small_monument`, `conduit_stage`,
  `bridge`. Its own mob spawning is turned off (`spawn_overrides.monster` empty); the stages bring their own
  drowned, guardians and spawners.

**Where it lives in the lore** (`Lore.md`):
- *The Tideborn's paths*, *The drowned courts* ("none of them will say why"), *The sunken palace*, *Bore and brine*.
- *The bound bloods*: every Tideborn is the Deep Mother's from birth. The rogues break that, which is the point.
- The new placement is **The rogue courts** (new), appended to `Lore.md` and copied in section 6.

**Three rules for living things.**
- *Sympathy*: they hunt as real spear-fishers and dolphins do: a pack drives a shoal against the surface or a reef and
  one strikes at a time. Sharks, like real ones, leave a group that fights back and go for the lone and the bleeding.
  Crops in the air halls grow only under light (sea lanterns or glass to the surface), as they would.
- *Feedback loops*: the catch comes out of Powers' per-spot fish stock, so a haven that overfishes its reef swims
  farther and brings home less; a fed haven goes out less. Blood from a kill draws sharks (`Sharks` already comes to a
  carcass). A rogue court that takes more monuments has more mouths and more halls to light and farm.
- *Stewardship*: a player who hunts with a pack, keeps sharks off a hunter, or brings spears gets a share and standing.
  Leaving them alone costs nothing.

**Obstacles.**
- Villagers walk; a sea hunt needs its own steering under water (as `Sharks` steers sharks), and they must not float
  up.
- A court inside a monument is a settlement with no ground and no sky: storehouse, beds, work and the planner all
  assume land. The air rooms are small (1,575 air blocks across all pieces), so crops need more air dug out at
  generation or when the court takes the monument.
- The monument's own stages (elder guardian, drowned, spawners, vault) must be gone or quiet in a court, and kept in a
  hostile one.

**Roadmap, by impact** (Tideborn track; each step is checked before the next):

| # | Step | What it achieves | Check |
|---|------|------------------|-------|
| 1 | **Fully amphibious**: every Tideborn (players and villagers) breathes water; the Reefwalker's end step gets a new gift (Q1). | Andy's "fully amphibious". | Tideborn stays under for 10 minutes, no air bar loss; villager the same. |
| 2 | **Fishing changed**: Tideborn no longer treat fishing as an attack; they fish too. Only taking more than a haven's water can give back is poaching (Q2). | "they need a way to eat". | Fishing by a haven: no fight; overfishing its spot: poaching crime. |
| 3 | **Spear hunt in packs**: a Tideborn haven's `Hunters` pick sends 3 to 5 folk with spears into the water; they drive fish and strike; catch to the storehouse. New `SeaHunts` beside `Hunters`. | You see Tideborn hunting the sea. | Haven near a player sends a pack; storehouse gets fish; spot stock goes down. |
| 4 | **Pack against sharks**: a shark after a hunter is met by the pack and breaks off hurt; a lone hunter still gets bitten. | "defend even against sharks". | Shark vs pack loses; shark vs one wins. |
| 5 | **Hunting with them**: a player who swims with a hunt, or hurts a shark after a hunter, gets a share and standing; spears given to the haven send more out. | Stewardship. | Join a hunt: share and standing; ignore: no change. |
| 6 | **Rogue courts**: when a trident monument generates, one in three is a rogue Tideborn court: its stages emptied, its air rooms and glass bridges enlarged into dry halls, lit, with farm plots; a ruler and folk, storehouse, trade. | Andy's kingdoms in the temples. | `/locate` three monuments: about one is a court, with folk, crops growing in the air halls. |
| 7 | **Conquest**: the other two in three stay hostile (stages intact). A rogue court sends war parties to take the nearest hostile monument, as land realms take ground; a taken one is cleared and becomes part of the court. | "conquered by the tideborn like dry ground MCA do". | Watch a court take a hostile monument; its stages are gone and its folk move in. |
| 8 | **The other bloods**, one per track (section 4). | The general system. | Per blood. |

## 2. The sceptic

- **Amphibious makes the Reefwalker's end step empty.** Andy allows removing it or giving it something new. Recommend a
  new gift (Q1), since paths are a big part of a Tideborn player's game.
- **Dropping the fishing crime entirely** would also drop a real feedback loop. Keeping only overfishing as a crime
  keeps the loop and lets them eat (Q2).
- **Rogues vs the bound patron.** *The bound bloods* says a Tideborn is the Deep Mother's from birth. Rogues who turned
  away are the exception the canon now needs: do they keep another god, or none (Q3)? And how do the island Tideborn
  and the rogue courts treat each other: war, or cold silence (Q4)?
- **"Conquered like dry ground MCA do"** means using the realm war code under water. That code is land-built; step 7
  is the riskiest step and might start as "a court's war party clears a hostile monument's guardians" before full
  claiming.
- **Crops under water.** Crops need light and farmland with water nearby; the halls need light from sea lanterns or
  glass to the surface. The monument starts at y 41, deep: not much sunlight reaches it.
- **Performance.** Pathing hundreds of folk through 4,029 pieces is heavy. Keep courts to a few dozen folk and only
  run them near a watching player, like `Hunters`.
- **Opportunity**: the monument's `vault_stage` is a trial vault. A court could hold the vault's key as tribute or
  trade, giving players a peaceful way into it.

## 3. Keep what works

Keep: `Hunters.java`'s rules (only while watched, food from the storehouse, hunting out a place, poaching);
`UnderWater.java`'s weapon rules; `Sharks`/`SharkWake`; `Havens` picking island havens; `DntBuildings`' way of reading
Dungeons and Taverns only when it's loaded.

Change, by impact:
1. `Heritage.java` (around `:616`, where `TIDE_OXYGEN` is applied): Tideborn breathe water (vanilla
   `WATER_BREATHING`-like: no air loss under water), players and villagers (`VillagerHeritage`). Check the 26.3 source
   for how air supply is ticked before choosing an attribute or a mixin.
2. `HeritagePaths` `TB_REEF`: replace the end step's water breathing with the new gift (Q1).
3. `TideFishing.java`: drop the attack and the plain-fishing crime; keep a poaching crime when a haven's spot is fished
   past its stock.
4. New `SeaHunts` called from `Hunters`' pick for Tideborn hunters with water in `RANGE`.
5. New `RogueCourts` (compat, only with Dungeons and Taverns loaded): picks one in three monuments by seed when the
   structure generates, clears its stages, opens the halls, places the court.

## 4. Decisions

**A. The Reefwalker's new end step (Q1).** Factors: worth on the path, fits a reef-walker, not stronger than the other
two paths' ends. Options: (a) **sees clearly in dark and murky water and senses fish and foes near them, as the
Deepborn sense through stone** (recommended: helps hunting, fits "the sea is their country"); (b) swims faster than
any fish, outruns sharks; (c) remove the step and shorten the path.

**B. Fishing (Q2).** (a) **Tideborn fish too; only overfishing a haven's water is poaching** (recommended); (b) reverse
it entirely: no crime, no attack.

**C. The other bloods (Q5).** All 22, one line each from Lore.md and `HeritageKits.java:238-249`:

| Blood | Way of living (draft) | What it grows from |
|-------|----------------------|--------------------|
| Heartlander | What `Hunters` does today: land game, berries, the farms. | "plain folk of the river valleys and the farmland" |
| Earthling | Same, but they eat more, so they go out more often. | "plain human blood"; perk `HUNGRY` |
| Tideborn | This plan. | "sea folk from the coves and reefs" |
| Stoneborn | Under the ground: cave mushrooms, glow berries, fish from underground pools. | "raised under the mountains" |
| Deepborn | Hunt the deep dark by hearing: quiet, alone. | "hear the world through the stone" |
| Sylvan | Ambush from the trees: drop on game from a branch. | "They drop from branches without a sound" |
| Verdant | Hunt little; tend gardens and live on the sun. | "fed by the sun" |
| Emberborn | Hunt the badlands, cook over open flame; never into deep water. | "born of the badlands"; `HEAVY_WATER` |
| Frostborn | Ice fishing through holes, traps (their tech). | "settled the snowfields"; `GLACIAL`, `FIRE_FRAIL` |
| Nightborn | Hunt only at night. | "daylight dulls them"; `NIGHT_EYES` |
| Dawnborn | Hunt by day on the savanna, run game down in the heat. | "children of the open savanna" |
| Stormborn | Hunt the high peaks (goats); out of water in a storm. | "people of the high peaks"; `WET_GROUNDED` |
| Warrior-born | Raid game from others' grounds, fight for the kill. | "ancestry is pillage and murder" |
| Orc | Hunt big and loud as a war band. | "raised in the war camps" |
| Goblin | Steal food and trade for it rather than hunt. | "greedy, sly, never far from someone else's gold" |
| Beastfolk | Hunt as their beast does (run down, pounce, dive), by their look. | "born in a den in the wild lands" |
| Endborn | Blink onto game (Ender Step) and take it. | move `ENDER_STEP` |
| Dragonblood | Trade for food out of the hoard; hunt only big game. | "only as strong as the hoard they carry" |
| Divine-born | Live on offerings at the shrines. | "the old shrines still hum" |
| Hollow | Need little food; scavenge. | "something of the grave came back with them" |
| Rubber-folk | Buy food at fairs; fond of cake. | "born at the travelling fairs" |
| Bio-built | Absorb (`HeritageHold`). | "made to become more one fallen body at a time" |

## 5. Self-check

**Confirmed** (seen): the classes and constants named in sections 1 and 3; Tempt 4's player at (21.5, 68, 84.5) and a
`nova_structures:trident_trial_monument` start at chunk (1, 2) with 4,029 pieces; the monument's jar pieces and block
counts above; `DntBuildings` reading `nova_structures`; the lore paragraphs named.

**Assumed or inferred**: that the monument Andy spawned on is that one (it's the nearest large ocean structure to his
player; a Repurposed Structures `temple_ocean` is also nearby, at chunk (10, 3)); that villagers can be kept under
water without floating up; that Powers' fish stock can be read from Kingdoms; that the realm conquest code can be
reused under water; the drafted ways of the other bloods.

**Not checked**: no game run; the 26.3 source for air supply and villager swimming; Dungeons and Taverns' license (we
place things in its structure, we don't copy it); how the monument's stages spawn their mobs.

## 6. Lore placement (new)

**The rogue courts** (new) (Claude's placement, 2026-11-23; Andy: "the ones there are rogues and abandoned the deep
mother and their people's ways"; for Andy to approve). Not every Tideborn came up to the coves. Some houses stayed
below and took the great trident monuments of the open sea, prismarine halls joined by bridges of glass, and kept the
air in them dry so that their gardens grow. They are rogues: they turned from the Deep Mother and from their people's
ways, and that is why the island Tideborn won't say why the old courts were left, or speak of those below to
strangers. A rogue court is a realm like any on land. It trades, keeps its own law, and takes the monuments round it
that are still held by the guardians and the drowned, as land realms take ground. All Tideborn are as at home in the
water as out of it. They eat from the sea like anyone: they fish with line and net, and they hunt in packs with
spears, driving a shoal and striking one at a time. Sharks leave a hunting pack alone; it's the lone swimmer and the
bleeding one they follow.

## Questions for Andy

1. **Reefwalker's new end step**: sight and sense in dark water (recommended), faster than any fish, or remove it?
2. **Fishing**: Tideborn fish too and only overfishing a haven's water is a crime (recommended), or no crime at all?
3. **The rogues' god**: do they keep another god, or none? *Recommend: none; they keep the old court's law instead.*
4. **Island Tideborn and the rogue courts**: at war, or cold silence and no trade? *Recommend: cold silence; a
   Tideborn player is turned away at a court's door unless they've turned too.*
5. **The other bloods**: after the Tideborn, which next? *Recommend: Frostborn (ice fishing is close to this work).*
