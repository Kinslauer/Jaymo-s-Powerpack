# Heritage ways of living: plan (every blood, one at a time)

Andy, 2026-11-23: "lets plan them all first before any builds". Planned so far: **Tideborn** (settled), **Frostborn** (settled:
Overworld hunting, and Hail with its city Vein), **Emberborn** (waiting on answers). The rest follow, one section each, in the order Andy picks.

# Tideborn

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
ways, keep no god at all, and live by their court's law alone. That is why the island Tideborn won't say why the old
courts were left, or speak of those below to strangers. The two don't speak, and where they meet, on land or at sea,
they fight. A rogue court is a realm like any on land. It trades, keeps its own law, and takes the monuments round it
that are still held by the guardians and the drowned, as land realms take ground. All Tideborn are as at home in the
water as out of it. They eat from the sea like anyone: they fish with line and net, and they hunt in packs with
spears, driving a shoal and striking one at a time. Sharks leave a hunting pack alone; it's the lone swimmer and the
bleeding one they follow.

## Tideborn: Andy's second answers (2026-11-23)

1. Reefwalker's end step: sight in dark water and sensing nearby fish and foes ("works for me").
2. Overfishing a haven's waters stays a crime ("keep").
3. The rogue courts keep no god: "none, court law".
4. Island Tideborn and rogue courts are "not speaking, hostile if ever they encounter on land or at sea". So step 6
   adds: rogue court folk and haven Tideborn fight on sight, at sea and ashore; a Tideborn player loyal to the Deep
   Mother is turned away at a court's door.
5. "lets plan them all first before any builds, next can be frostborn": no building until every heritage is planned.

The Tideborn plan is settled.

---

# Frostborn

Drafted 2026-11-23 with the `draft-addition` skill; Andy's answers the same day are below and in `Decisions.md`. Two
parts: how the Frostborn on the Overworld hunt (small), and **Hail**, their home planet, with its city **Vein**
(large).

## Andy's answers (2026-11-23)

- Overworld Frostborn "will stay as they are, only adopting their futiristic hunting techniques and ice fishing". So:
  no Frostborn havens, no Frostborn building style, no muskox keeping, no ice cellar. They stay mixed in other
  villages, as now.
- "is it possible to add their planet? ... a new planet mega city using los perrito's map since that has been changed.
  is it possible to make them inhabit the entire planet and live in a high tech enviornment?" Yes: see Part 2.
- "The planet is Hail, the city's name is Vein."
- Players reach Hail on a Frostborn ship boarded at their Overworld settlements, after earning their trust ("correct").
- Non-Frostborn freeze outside heated places on Hail ("yes").
- This lifts the 2026-11-11 decision "Hail is lore only for now".

Second answers, the same day:
- The muskox herds and the ice cellar "are still viable": back in Part 1 (steps 5 and 6), kept by the Frostborn living
  in other peoples' villages.
- Vein only on worlds whose Chrono future is Mosslorn ("yes").
- The ship lands at a pad by any village with Frostborn folk, calling every few days, "but only after the second world
  tier": read as from **Wildlands** on (Hearth, Borderlands, then Wildlands; `WorldTiers.Tier`).
- Trust is standing with the Frostborn you live among, from trade and help ("yup").
- Vein is safe; creatures of the ice roam the wilds, "if they wander in they get dealt with by patrolmen".
- Hail's place (dimension, map, cold) in Powers; its people, shops and trust in Kingdoms ("sounds good").
- Frostborn heritage paths "later on".
- Next blood: Emberborn ("lets go!").

The Frostborn plan is settled.

## Part 1: the Overworld Frostborn hunt

### F1. Examine, place it, plan

**The problem.** Frostborn live among other peoples' villages (no haven is ever drawn Frostborn: `Havens.draw` has no
branch for snow). When one of them goes out for food (`Hunters.java`), they do it the same way as everyone. Andy wants
them to hunt the way a tech people would, and to fish through the ice.

**What we have to build with** (seen):
- `Hunters.java`: the one place a settlement's folk feed it (only near a watching player).
- Powers' `fishing/IceFishing.java`: an auger bores a hole through ice (`DRILL_TICKS = 50`), it stays open
  `OPEN_DAYS = 3` days, fish crowd to an open hole (`KEEN = 1.5`). Players only today.
- `FrostTech.java`: Frostborn wear scouters (about one in ten of those not raised here) and made limbs.
- `Heritage.java:834`: a Frostborn's hunger is saved in the cold and worse in the heat; `HeritageKits.java:242`:
  `FROSTBIND`, `GLACIAL`, `FIRE_FRAIL`. No Frostborn heritage paths yet.

**Lore.** "They brought with them technology the Overworld has never seen ... They settled the snowfields and peaks."
The placement paragraph is in F6.

**Three rules.**
- *Sympathy*: ice fishing as real Arctic fishers do: drill, fish, move on when it's frozen or fished out. A trap line
  walked each morning, as real trappers do; Frostborn traps are made, not twigs.
- *Feedback loops*: a hole gives less the more it's fished (Powers' per-spot stock) and freezes over in three days, so
  they move along the ice. A trap line empties the ground round it, so the line moves. A hunter with a scouter finds
  game farther off, so their ground empties faster and they range wider.
- *Stewardship*: a player who leaves their traps alone may share their ice holes; robbing a trap is theft. Ignoring
  them costs nothing.

**Roadmap:**

| # | Step | What it achieves | Check |
|---|------|------------------|-------|
| 1 | **Ice fishing**: a Frostborn villager who goes out for food, with frozen water in range, bores holes with an auger and fishes them (the same holes players use); catch to the storehouse; they move along the ice. | Their way of fishing, seen at work. | Village with Frostborn near frozen water: holes along the ice, fish in the storehouse. |
| 2 | **Made traps**: Frostborn set spring traps of their own make (a metal jaw on a plate) out on the hunting ground; one walks the line each morning and brings back rabbit, fox or nothing; the line moves when the ground empties. A player who steps in one is caught and hurt, as by a bear trap. | Their futuristic hunting. | Traps placed, checked daily, catch falls off, line moves. |
| 3 | **Scouter hunting**: a Frostborn hunter wearing a scouter spots game twice as far off and goes straight to it, no creeping. | Tech you can see in use. | Scouter hunter picks game from farther than others. |
| 4 | **Stewardship**: robbing a Frostborn trap is theft; sharing a hole is welcome; bringing glow lichen for the herd or helping move it earns wool, traded tech and standing. | Third rule. | Take from a trap: crime; give lichen: standing. |
| 5 | **Muskox herds**: a village with Frostborn folk in a cold biome keeps the muskox near it as its herd (the `Muskox` already built); a Frostborn herder combs their wool into the storehouse and walks them to fresh glow lichen when it runs out. | Andy: "still viable". | Muskox near such a village; wool in the storehouse; the herd moves when the lichen's gone. |
| 6 | **Ice cellar**: a village with Frostborn folk digs an ice cellar by its storehouse; food in it keeps as in Kitchen's cold cellar (`Freshness#cellar`); in a warm spell it goes stale. | Never short in winter. | Food in the cellar stays fresh longer than in the storehouse. |

### F2. The sceptic

- **No Frostborn villages means the ship has nowhere obvious to land** (Andy: "boarded at their Overworld
  settlements"). Frostborn live scattered in other peoples' villages. See question 2.
- **Ice is seasonal and northern.** Most villages with Frostborn may have no frozen water near; they fall back to traps
  and scouter hunting. That's fine.
- **Traps hurting players** in a shared village could annoy friends: they sit out on the hunting ground, not in the
  streets.

### F3. Keep what works

Keep `Hunters`' rules; `IceFishing`'s holes and refreezing (villagers use the same holes); `FrostTech` as it is.
Change: a Frostborn branch in `Hunters`' pick (`IceHunts`, beside the Tideborn's `SeaHunts`); a new trap block and
`TrapLines`; scouter range in the hunter's search.

### F4. Decisions

- Traps: made metal spring traps (Andy's "futuristic", taken as yes).
- Frostborn heritage paths: still open, their own task (question 6).

### F5. Self-check (Part 1)

**Confirmed**: `Havens.draw` has no snow branch; `IceFishing.java` header and constants; `FrostTech.java` header;
`HeritageKits.java:242`; `Heritage.java:834`. **Assumed**: Kingdoms can reach Powers' ice holes; a hunter's villager
data says whether it wears a scouter. **Not checked**: no game run.

### F6. Lore placement (new)

**How the Frostborn hunt** (new) (Claude's placement, 2026-11-23; for Andy to approve). Frostborn living among other
peoples hunt as their own people do. They bore the ice with augers of their own make and fish the hole until it
freezes over, then bore another along the lake. They set made traps, metal jaws on plates, out on the hunting ground
and walk the line every morning, and move it when the ground goes empty. One with a scouter sees game a long way off
and walks straight to it. Where Frostborn live in the cold they keep muskox for the wool and walk the herd to fresh
lichen, and dig an ice cellar by the storehouse, so their village is never short in winter and a warm spell frightens
it. Robbing a Frostborn trap is theft; a traveller who brings lichen for their herd is welcome.

## Part 2: Hail and Vein

### H1. Examine, place it, plan

**The problem.** The Frostborn's home, Hail, is spoken of everywhere and seen nowhere. Andy wants it as a planet you
can travel to, the whole of it theirs, with **Vein**, a high-tech city, built from the Los Perrito City map.

**What we have to build with** (seen):
- `LosPerrito.java` (Powers): `PERRITO`, Uospag's **Los Perrito City** 2.0.1, CC BY 4.0 (credit kept in
  `message.jaymospowers.future.credit`). About 800 MB, so it is fetched from CurseForge the first time it's needed (or
  taken from a zip dropped in the server or world folder) and unpacked into a dimension's folder; map art is copied in
  above the world's map ids. Its region files run r.-7.-7 to r.10.6: the city spans about **9,200 x 7,200 blocks**
  (x -3584 to 5631, z -3584 to 3583). New futures use `MOSSLORN` instead, so Los Perrito is free; a world whose
  future was built from Los Perrito keeps it until `/future swap`.
- `FutureRuins.java`: our pass that changes each chunk of the city the first time it loads (ruins, overgrowth, dead
  lights, biome, loot). The same kind of pass can freeze Vein and dress it in Frostborn tech.
- `Future.java`: how a dimension of ours is declared (`ResourceKey<Level> FUTURE`) and how a player is sent there and
  back (`Return`).
- Powers' `frost/Freezing.java` (the freezing effect) and vanilla freezing (`getTicksFrozen`).
- `FrostTech.java` (scouters, made limbs), Powers' rigs and mechs, Galactic Patrolmen (`core/brawl/Brawl.java`).

**Where it lives in the lore.**
- The heritage list: "**Hail** ... is their home world, constantly frozen and unwelcoming, and they love it. (It is a
  place in the lore only, not a world you can travel to.)" That bracket goes (Andy, 2026-11-23).
- *Travellers from Hail*: some "come down from Hail alone, on one of their people's ships". *The pods*: the Mad
  Tinkerer worked "from the Frostborn's own void-ships". The ship fits the canon.
- Crossing the void "was theirs first: the Warrior-born learned it from them".
- *What happened to Los Perrito*: "Los Perrito was the largest city on Earth". Vein is built from the same map: see
  H2.
- The placement paragraph is in H6.

**Three rules.** Hail is a people's world, but Vein lives by them too:
- *Sympathy*: a city on a frozen world lives as one would: sealed and heated inside, food grown in lit greenhouse
  towers and fished from under the ice.
- *Feedback loops*: Vein's districts are warm only where their heat runs; a district whose heat fails freezes, and its
  people move inward. Players who bring fuel or parts keep a district warm.
- *Stewardship*: a visitor who helps (fuel, parts, trade) is trusted further into the city; one who doesn't is left in
  the cold, never punished.

**Obstacles.**
- **An Earth city on another planet**: Los Perrito has roads, cars, shop signs and billboards in English. Vein must not
  read as Earth. The freeze-and-dress pass has to turn the Earth things (cars into frozen hulks or transports,
  billboards into Frostborn panels).
- **The same map twice**: worlds whose Chrono future is still Los Perrito would have the same streets in the future
  and on Hail. See H2.
- **Size and cost**: an 800 MB download per server (once), and a dimension that size. People only spawn and act near
  players, as `Hunters` does.
- **Powers or Kingdoms?** The map loader and dimensions live in Powers; the Frostborn, villagers, trade and standing
  live in Kingdoms. See H4.
- **Beyond the city**: the rest of the planet must be generated (frozen terrain, ice seas), since the map ends at its
  edges.

**Roadmap, by impact:**

| # | Step | What it achieves | Check |
|---|------|------------------|-------|
| 1 | **The Hail dimension**: frozen sky (a small, cold sun; aurora), snow and ice terrain round a space for the city. | A planet to go to. | Teleport in: snow under a dim sun. |
| 2 | **Vein**: the Los Perrito map fetched and unpacked into Hail's middle (the `LosPerrito` loader), credit to Uospag. | The city exists. | Arrive in Vein's streets; credit shown. |
| 3 | **Frozen and dressed**: the first-load pass covers Vein in snow and ice outside, keeps its lights on, turns cars, signs and billboards into Frostborn tech and frozen hulks, and seals the buildings warm inside. | Reads as a Frostborn city, not Earth. | Walk five streets: nothing reads as Earth. |
| 4 | **The cold**: outside a heated place, a non-Frostborn freezes (Powers' `Freezing`, then vanilla frost damage); inside a building, near a heat source, or in warm Frostborn gear, they don't. Frostborn never freeze there. | The trip means something. | Non-Frostborn outside: freezing within a minute; inside: fine; Frostborn: fine. |
| 5 | **The ship**: a Frostborn ship takes trusted travellers to Vein's port and back. | How you get there. | With trust: board, arrive; without: refused. |
| 6 | **Vein's people**: Frostborn citizens, patrolmen with scouters, workers in rigs, spawned near players; tech everywhere. | A living city. | Busy streets near you, empty far from players. |
| 7 | **Vein's trade**: shops selling what the Overworld only scavenges (scouters, made limbs, rig parts, warm gear). | A reason to go. | Buy a scouter in Vein. |
| 8 | **The rest of Hail**: Frostborn outposts, mines and stations across the frozen planet. | "inhabit the entire planet". | Travel out from Vein: outposts with Frostborn. |
| 9 | **Heat and districts** (the feedback loop above). | Vein reacts to what happens to it. | A district's heat fails: it freezes, people move. |

### H2. The sceptic

- **Same streets as the future.** On worlds still on Los Perrito for the Chrono future, a player could stand in the
  same street a lifetime on (ruined, Earth) and on Hail (Vein). Options: Vein only on worlds whose future is Mosslorn
  (all new worlds), an older world keeping Vein off until `/future swap`; or accept it and explain it in the lore.
  Recommend the first (question 1).
- **"Constantly frozen" vs a city of glass and lights.** The pass seals and heats the inside; outside stays deadly
  cold. That keeps both.
- **The map isn't ours.** CC BY 4.0 lets us change and share it with credit; show the credit on arrival in Vein as the
  future does.
- **Too big for one PR.** Steps 1 to 4 are a track (the place), 5 to 7 another (getting there and the people), 8 and 9
  later.
- **Danger on Hail** besides the cold is unasked (question 4).

### H3. Keep what works

Keep `LosPerrito`'s fetch-and-unpack, credit and map-id offset; `FutureRuins`' way of changing a chunk once on first
load (a new pass of its own for Vein, never mixed into the future's); `Future`'s way of sending a player somewhere and
back; `Freezing`; `FrostTech`'s looks.

### H4. Decisions

**A. Which mod holds Hail** (question 5). Factors: where the loader is, where the people are, each mod must run without
the other. (a) **Hail's place (dimension, map, the cold) in Powers, beside the future; its people, trade and the
ship's trust in Kingdoms, which fills Vein when it's loaded** (recommended); (b) all in Kingdoms, copying the loader.

**B. The same-map problem** (question 1). (a) **Vein only where the future is Mosslorn** (recommended); (b) both,
explained in lore.

### H5. Self-check (Part 2)

**Confirmed**: `LosPerrito.java` lines 41-51 (the `CityMap` record, `PERRITO`'s bounds, download, credit) and its
header (fetch, unpack, map art, `/future swap`); `Future.java:67` dimension key; `FutureRuins` and `Freezing` in the
codemap; the lore lines quoted; Decisions 2026-11-11 and 2026-11-15 on Hail and Mosslorn.

**Assumed**: that the loader can unpack into a second dimension with small changes; that the city can be offset into
Hail's middle; that most of the map's buildings can be entered (so "inside" works for the cold).

**Not checked**: no game run; how Earth-like the map's streets look up close; the map's height range; how a dimension
this size performs.

### H6. Lore placement (new)

**Hail and Vein** (new) (Claude's placement, 2026-11-23; Andy: "The planet is Hail, the city's name is Vein"; for Andy
to approve; it replaces the bracket in the heritage list that says Hail is lore only). Hail lies far past our sun,
under a small cold one of its own, frozen from pole to pole, and the Frostborn hold all of it. Their capital is
**Vein**, a city of towers and lit streets under snow that never melts, sealed and warm inside, killing cold outside.
Their ships cross the void between Hail and the Overworld, as they did before anyone else knew how. They carry only
those the Frostborn trust; a traveller who goes must earn it first among the Frostborn living here. On Hail anyone not
of the blood freezes in the open, and only a warm room, warm gear or a Frostborn's help keeps them alive. In Vein the
scouters, made limbs and rigs the Overworld treasures are everyday things, sold in shops.

## Frostborn questions for Andy (answered 2026-11-23: see "Second answers" at the top of this section)

1. **The same map twice**: build Vein only on worlds whose Chrono future is Mosslorn (all new ones), or allow both and
   explain it in the lore? *Recommend: Mosslorn worlds only.*
2. **Where the ship lands**: there are no Frostborn villages to board at. A landing pad by any village where Frostborn
   live, or a ship that comes now and then to the snowfields? *Recommend: a pad by any village with Frostborn folk,
   the ship calling every few days.*
3. **How trust is earned**: trade with Frostborn, help them, or a quest from a Frostborn elder? *Recommend: standing
   with the Frostborn you live among, from trade and help.*
4. **Danger on Hail** besides the cold: none, raiders, or creatures of the ice? *Recommend: Vein safe, the wilds
   beyond with creatures of the ice.*
5. **Which mod holds Hail**: split as H4 A recommends?
6. **Frostborn heritage paths**: their own task later? *Recommend: yes.*
7. **Next blood to plan**? *Recommend: Emberborn.*

---

# Emberborn

Drafted 2026-11-23 with the `draft-addition` skill. The draft line from the table: "Hunt the badlands and burning
places, cook over open flame; never into deep water."

## E1. Examine, place it, plan

**The problem.** The Emberborn are the fire people: "born of the badlands and the burning places. Flame cannot touch
them; the frost saps them." Like the Frostborn, they have no haven of their own (`Havens.draw` gives badlands to the
Stoneborn) and live in other peoples' villages, where they hunt like everyone. Andy wants each blood to live its own
way. Theirs should use the one thing nobody else can: fire, which can't hurt them.

**Who it's for.** Players in the hot lands (savanna, badlands, desert: *The middle lands and the harsh lands*);
Emberborn players; anyone who meets a grass fire on the plains and wonders who lit it.

**What we have to build with** (seen):
- `VillagerHeritage.java:131-133`: an Emberborn villager is never hurt by fire, lava or burning; `:217` they lean to
  hot lands (`LEAN`) and shun cold ones (`SHUN`); `W_EMBERBORN = 35` of every thousand-ish villagers.
- `Heritage.java:832`: an Emberborn's hunger is saved in the heat and worse in the cold; `:153` frost and freezing
  weaken and slow them. `HeritageKits`: `FORGE_BLESSING`, flaw `HEAVY_WATER` (they sink).
- Powers' `power/ember/EmberCook.java`: an Emberborn player sneaking with raw food in the off hand cooks it in the
  hand, no fire, no fuel. Players only.
- Peterwolf's Forest Fire, our 26.3 port, is in the pack (`peterwolfs-forest-fire-1.1.11-26.3-jaymo.3.jar`): fire
  throws embers, climbs, runs uphill and with the wind, smoulders and relights (*The fire crews*). A fire set on
  purpose behaves like a real one.
- `Hunters.java`: the one place a settlement's folk feed it; game is the `camp_game` tag (cow, pig, sheep, chicken,
  rabbit, goat, Naturalist deer, boar, duck and more).
- Kitchen's smoker (`SmokerView`: a heat gauge with a curing band) and drying racks: smoked, cured meat.
- *Water that comes back* (lore): desert travellers press cactus for "the Mother's thin milk", which turns the stomach
  unless boiled.
- `nether/AvatarCamps.java`, `nether/CinderForge.java`: the Emberborn the fire consumed (Flame Avatars) camp in the
  Nether; the oldest Emberborn smith keeps a forge there.

**Where it lives in the lore.**
- The heritage list (quoted above). *Emberborn and Verdant*: "The Emberborn are the people of the burning places.
  Nothing is forgiven there." *Emberborn and Frostborn*: each flourishes where the other starves.
- *The Flame Avatar*: an Emberborn who holds fire too long is taken by it. Living by fire has a cost in the canon
  already.
- *The fire crews*: villagers fight wildfires with bottles and beaters, and Frostborn pumps.
- *Notus*, the south wind dragon (fire, the badlands and basalt), takes most kindly to the Emberborn.
- The Emberborn expedition at the Nether's edge paid the Hornkin off in gold.
- The placement paragraph is in E6.

**Three rules for living things.**
- *Sympathy*: real peoples of dry grass country have hunted with fire for as long as there have been people: a line
  of fire set downwind drives game into waiting hunters (fire-drive hunting), and burning the old grass brings up a
  green flush that the grazers come back to (Aboriginal fire-stick farming, the Plains peoples' burns). Meat is
  smoked and dried to keep in the heat, as in every hot country.
- *Feedback loops*: a burnt patch regrows as fresh grass within days and draws grazers back, so a burn feeds the next
  hunt; burn the same ground too often and it stays bare and the game leaves. Fire on a windy day jumps the line and
  runs, and the fire crews and the Emberborn have to beat it out; set too near trees, it takes the woods.
- *Stewardship*: a player who helps beat out a burn's edges, or brings flint and dry meat racks, gets a share and
  standing. Nobody is punished for staying away. An Emberborn who lets a burn reach a farm or a house answers for it
  (arson), so they burn carefully.

**Obstacles.**
- **Fire is dangerous on a shared server.** A villager setting grass fires near friends' builds is griefing. Burns
  must stay in open grass and badlands scrub, far from any building, never in forest, never on a windy day, and the
  hunters stamp out the edges after.
- **Cost**: Forest Fire's spread is already ticking in the world; a burn adds one fire, not a new system.
- **Hot lands aren't everywhere**: Emberborn in a cold or forested village can't burn; they fall back to ordinary
  hunting, with meat cooked in the hand.

**Roadmap, by impact:**

| # | Step | What it achieves | Check |
|---|------|------------------|-------|
| 1 | **Cooked in the hand**: an Emberborn villager cooks raw food in their hands, as an Emberborn player already can (`EmberCook`); a village with Emberborn sends its raw meat to them, no fuel burned. | Their gift used daily; easy, first. | Raw meat in the storehouse turns cooked when an Emberborn is home. |
| 2 | **The fire drive**: on a calm day, in open grass or savanna in range, two to four Emberborn hunters set a line of fire downwind of a herd and wait where it runs; they walk through the flames to strike. Then they beat out the edges. | You see the fire people hunt with fire. | Herd in grassland, calm: a burn, game driven, kills; burn put out after; no building within the safe distance. |
| 3 | **The green flush**: burnt grass regrows as fresh grass after a few days, and grazers in range drift to it; burning the same patch again within the rest time leaves it bare. | Feedback loop: a burn feeds the next hunt. | Burnt patch: fresh grass in days, deer/cows come; reburnt too soon: bare. |
| 4 | **Smoked and dried**: Emberborn villagers cure meat in a smoker without fuel (they are the heat) and hang it on drying racks; cured meat keeps longer in the storehouse. | Food that keeps in the heat. | Cured meat in the storehouse; it outlasts fresh. |
| 5 | **The desert's water**: Emberborn boil cactus water in their hands, so it's safe to drink; a village with Emberborn in the desert has water. | Uses *Water that comes back*. | Emberborn hands boiled cactus water to the storehouse. |
| 6 | **Stewardship**: helping beat out a burn's edges earns a share and standing; an Emberborn whose burn reaches a farm or house is charged with arson. | Third rule. | Help: share and standing; burn reaches a house: crime. |

## E2. The sceptic

- **Setting fires is griefing waiting to happen.** Even with rules, Forest Fire is realistic: embers jump. Keep a
  wide safe distance (EQ2), only calm days, only grass and badlands scrub, and the hunters always put it out.
- **No Emberborn villages**: same question as the Frostborn. Andy kept the Frostborn mixed; recommend the same here
  (EQ1).
- **The Flame Avatar's warning**: an Emberborn who plays with fire too much is taken by it. A village burner who
  burns too often could be the one who goes over (an Avatar walking out of the smoke). That's a hook, not a must
  (EQ3).
- **Nether hunting** (hoglins, striders) at the Nether's edge would fit the expedition lore, but it's a different place
  and a big step. Leave for later (EQ4).
- **Opportunity**: Notus, the south wind, "takes most kindly to the Emberborn": a burn on a day Notus blows could run
  faster and further, a risk they read the sky for.

## E3. Keep what works

Keep: `VillagerHeritage`'s fire immunity and hot-land lean; `EmberCook` as the players' version (the villagers' is
the same idea, in Kingdoms); Forest Fire's spread as it is (a burn is just a fire); `Hunters`' rules; Kitchen's smoker
and racks.

Change, by impact:
1. Kingdoms: Emberborn villagers cook the storehouse's raw food in hand (a small tick beside `Hunters`).
2. New `FireDrives` beside `Hunters` (as `SeaHunts`, `IceHunts`): the burn, the drive, the putting out, the safe
   distance.
3. The green flush: a burnt grass block remembers when it burnt and regrows (check whether Forest Fire already
   regrows burnt ground before adding anything).
4. Kitchen compat: an Emberborn at a smoker is its heat.
5. Cactus water boiled in hand: Kitchen compat with the cactus water item.

## E4. Decisions

**A. Havens (EQ1).** (a) **Stay mixed in other villages, as the Frostborn** (recommended: consistent, and Emberborn
are rare, 35 in roughly a thousand); (b) badlands Emberborn havens, taking badlands from the Stoneborn.

**B. Safe distance for a burn (EQ2).** Factors: friends' builds, Forest Fire's ember jumps. (a) **64 blocks from any
building or farm, never in forest, never when it rains or the wind is up** (recommended); (b) 32 blocks; (c) no
burns at all, only cooking and smoking.

## E5. Self-check

**Confirmed** (seen): `VillagerHeritage.java:131-133`, `:217`, `W_EMBERBORN = 35`; `Heritage.java:832`, `:153`;
`EmberCook.java`'s header; the Forest Fire jar in the pack's mods; `camp_game` tag contents; Kitchen's `SmokerView`
and drying racks in the codemap; `AvatarCamps`, `CinderForge`; the lore lines quoted.

**Assumed or inferred**: that Forest Fire's spread can be read for "is the wind up" and whether it regrows burnt
ground (not opened); that a villager can stand in as a smoker's heat; fire-drive hunting and fire-stick farming as
described (real-world, from memory).

**Not checked**: no game run; Forest Fire's own code; how often grass country lies within a village's hunting range;
the cactus water item's id.

## E6. Lore placement (new)

**How the Emberborn hunt** (new) (Claude's placement for Andy's heritage ways, 2026-11-23; for Andy to approve).
Emberborn living among other peoples hunt the way their people always have in the burning places: with fire. On a
still day in open grass they set a line of flame downwind of a herd and wait where it runs, and walk through the burn
to strike, because it cannot touch them. Then they beat out its edges. The burnt ground comes up green in a few days
and the grazers come back to it, so a good burn feeds the next hunt; burn it too often and it stays bare. They cook in
their hands and cure meat without a fire, and in the desert they boil the Mother's thin milk in their palms. An
Emberborn whose burn reaches a farm or a roof answers for it, and their elders say the one who burns too often is the
one the fire takes.

## Emberborn questions for Andy

1. **Their own villages**: stay mixed in other peoples' villages like the Frostborn, or badlands Emberborn villages?
   *Recommend: stay mixed.*
2. **Fire drives**: how far from buildings and farms must a burn be? *Recommend: 64 blocks, never in forest, never in
   rain or wind.* Or no burns at all?
3. **The Flame Avatar hook**: should a villager who burns too often be at risk of going over and walking out of the
   smoke as a Flame Avatar? *Recommend: yes, rarely.*
4. **Nether hunting** (hoglins at the Nether's edge): plan it now or leave it? *Recommend: leave it for later.*
5. **Next blood to plan**? *Recommend: Stoneborn (they're in the most villages after Heartlanders).*
