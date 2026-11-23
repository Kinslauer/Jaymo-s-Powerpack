# Heritage ways of living: plan (every blood, one at a time)

Andy, 2026-11-23: "lets plan them all first before any builds". Planned so far: **Tideborn** (settled), **Frostborn**
(waiting on answers). The rest follow, one section each, in the order Andy picks.

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

Drafted 2026-11-23 with the `draft-addition` skill. The draft line from the table: "Ice fishing through holes, traps
(their tech)."

## F1. Examine, place it, plan

**The problem.** The Frostborn are the people of the snowfields with technology nobody else has. Today you never see
them living that way: there is no Frostborn haven at all (`Havens.draw` gives island and shore seats to the Tideborn,
mountains and badlands to the Stoneborn, forests and taiga to the Sylvan, and the rest to Stoneborn, Warrior-born or
Sylvan; no branch for snow). Frostborn only turn up mixed into other villages. So the first job is to give them a place,
then a way of eating there that only they could have.

**Who it's for.** Players who go north into the harsh cold (*The middle lands and the harsh lands*: harder a step at a
time away from the middle); Frostborn players; anyone after Frostborn tech.

**What we have to build with** (seen):
- `Havens.java:110-150`: how a settlement's blood is drawn from its seat's biome; no cold branch.
- `Heritage.java:834`: a Frostborn's hunger is saved in the cold (`THRIVE_HUNGER`) and worse in the heat
  (`WILT_HUNGER`); `HeritageKits.java:242`: `FROSTBIND`, `GLACIAL` (hit harder on cold or frozen foes),
  `FIRE_FRAIL`. `HeritagePaths.java:780`: a Frostborn gift of walking speed; no Frostborn paths yet (only Heartlander,
  Stoneborn and Tideborn have them).
- Powers' `fishing/IceFishing.java`: an auger bores a hole through ice (`DRILL_TICKS = 50`), the hole stays open
  `OPEN_DAYS = 3` days, fish crowd to an open hole (`KEEN = 1.5`). Players only today.
- `wild/Muskox` (built from `docs/Tundra-Plan.md`): grazes glow lichen, sheds wool by comfort, herds, recovers from a
  loss; the tundra plan says "Frostborn villages in the cold lands may keep muskox". `FedPredators`: a fed predator
  stops hunting animals for two days.
- `FrostTech.java`: Frostborn wear scouters and made limbs; villagers who lose a limb are fitted at a smith.
- Kitchen's freshness (`fresh/`, `butchery/Spoilage.java`): food goes stale; a cold cellar ages cheese
  (`Freshness#cellar`).
- `Hunters.java` (as for the Tideborn): the one place a settlement's folk feed it.

**Where it lives in the lore.**
- The heritage list: "they came to the Overworld from far past our sun, and brought with them technology the
  Overworld has never seen ... They settled the snowfields and peaks, and the heat wears them thin." Hail, their
  frozen home world, "and they love it".
- *Emberborn and Frostborn*: "Each flourishes where the other starves."
- *The middle lands and the harsh lands*: the cold gets harder a step at a time.
- The tundra plan's line that Frostborn villages may keep muskox; its "(new) The tundra" lore paragraph isn't in
  `Lore.md` now (not found by search), so nothing in the canon says it yet.
- The placement paragraph is in F6, marked `(new)`.

**Three rules for living things.**
- *Sympathy*: they live as real Arctic peoples do: fish through the ice, run a trap line for fox and hare and check it
  each morning, keep muskox for their wool (qiviut, as Alaska's muskox farms do), and store food frozen.
- *Feedback loops*: a fishing hole gives less the more it is fished (Powers' per-spot stock) and freezes over in three
  days, so they move along the lake. A trap line empties the ground round it; they move the line. A herd that eats
  the lichen bare sheds less wool and must be walked to new ground (the muskox already works this way). Frozen food
  keeps, so a Frostborn village is never short in winter; in a warm spell their stores go stale.
- *Stewardship*: a player who leaves the traps alone, brings them glow lichen for the herd, or helps move the herd gets
  wool, traded tech and standing. Robbing a trap line is theft (like poaching); ignoring them costs nothing.

**Obstacles.**
- New havens change worldgen draws for new worlds only; old worlds keep their villages as they are.
- Villagers using an auger and fishing through a hole: new behaviour, as `SeaHunts` is for the Tideborn.
- Traps need a block (a snare or a made trap) that catches small game while nobody is near: keep it cheap (a catch is
  rolled when the trap is checked, not ticked every tick).

**Roadmap, by impact:**

| # | Step | What it achieves | Check |
|---|------|------------------|-------|
| 1 | **Frostborn havens**: a haven whose seat is in a snowy or frozen biome is Frostborn, with its own building style (snow-banked stone and dark timber, metal fittings of their own make). | There are Frostborn places to find. | New world: a village in snowy plains is a Frostborn haven with Frostborn folk. |
| 2 | **Ice fishing**: Frostborn hunters bore holes with an auger on frozen water near the haven and fish them; catch to the storehouse; holes freeze over, they move. | Their main food, seen at work. | Haven near frozen water: folk at holes, fish in the storehouse, holes along the ice. |
| 3 | **Trap line**: Frostborn set made traps round the haven; one walks the line each morning and brings back fox, hare, or nothing. The ground round a line empties; the line moves. | The tech people's way of hunting. | Traps placed, checked daily, catch falls off over days, line moves. |
| 4 | **Muskox herds**: a Frostborn haven keeps muskox near it, combs their wool, and drives them to fresh lichen. | Uses the muskox already built. | Muskox near a haven; wool in the storehouse; herd moves when lichen runs out. |
| 5 | **Ice cellar**: a Frostborn storehouse keeps food fresh as a cold cellar does; in a warm spell it goes stale. | Why they're never short in winter. | Food in their storehouse stays fresh longer than elsewhere. |
| 6 | **Helping them** (stewardship): lichen for the herd, help moving it, leaving traps alone: wool, traded tech, standing. Robbing a trap is theft. | Third rule. | Give lichen: standing; rob a trap: crime. |

## F2. The sceptic

- **Snow havens take seats from others.** Snowy taiga is "forest" today and goes to the Sylvan; snowy slopes are
  "mountain" and go to the Stoneborn. Which wins? Recommend: snow first (the cold is the Frostborn's above all), then
  the rest as now (FQ1).
- **Frostborn are a tech people, not hunter-gatherers.** Augers and made traps fit that; a whole village living off
  snares could feel primitive for them. Their traps should look made (metal, springs), not twigs.
- **Travellers from Hail** (*Travellers from Hail*) are lone arrivals, not villagers; they shouldn't live in havens.
- **The muskox step depends on muskox spawning near the havens.** If they're rare in that biome, a haven needs a
  starting herd.
- **A Frostborn haven near a Tideborn one** (frozen ocean shores are both "shore" and "snow"): which blood? Recommend
  Tideborn on the shore stays as is (FQ1).
- **Opportunity**: Frostborn smiths could be where villagers everywhere come for made limbs (`FrostTech` already sends
  them to a smith); a Frostborn haven's smith could be the best.

## F3. Keep what works

Keep: `Havens`' shore rule and island rule for the Tideborn; `IceFishing`'s holes, keen fish and refreezing (villagers
use the same holes, so a player sees and can share them); the muskox's grazing, herd pull and recovery; `FedPredators`;
`FrostTech` as it is; Kitchen's freshness rules.

Change, by impact:
1. `Havens.draw`: before the mountain/forest checks, a snowy or frozen biome (`BiomeTags` or the biome's temperature)
   is Frostborn. Shore still Tideborn first.
2. `HavenBuildings`: a Frostborn style.
3. New `IceHunts` beside `Hunters` (as `SeaHunts`), using Powers' `IceFishing` holes through the compat path.
4. New trap block and `TrapLines` for the morning walk.
5. Muskox keeping: a haven claims muskox within range as its herd; a herder drives them toward lichen.
6. Kitchen compat: a Frostborn storehouse counts as a cold cellar.

## F4. Decisions

**A. Their traps (FQ2).** Factors: fits a tech people, readable to players, cost. (a) **made spring traps of their
own design** (a metal jaw on a plate; catches small game, hurts a player who steps in, as a bear trap does)
(recommended); (b) plain snares like anyone's.

**B. Frostborn paths (FQ3).** They have no heritage paths yet; Tideborn, Stoneborn and Heartlander do. Planning their
way of living is a good moment to draft them, or leave paths to their own task. Recommend: leave them for their own
task, so this stays about living.

## F5. Self-check

**Confirmed** (seen): `Havens.draw` has no snow branch; `Heritage.java:834` hunger in cold and heat;
`HeritageKits.java:242`; `HeritagePaths.java:780` (no Frostborn paths found); `IceFishing.java`'s header and constants;
`Muskox` and `FedPredators` in the tundra plan and codemap; `FrostTech.java`'s header; Kitchen's `Spoilage`,
`FreshTooltip`, `CheeseWheelBlock` (cold cellar) in the codemap; the Frostborn lore lines quoted.

**Assumed or inferred**: that Kingdoms can reach Powers' `IceFishing` holes; that muskox spawn in the biomes the havens
would sit in; that Kitchen's cold cellar can be made to count a storehouse; that Alaska's muskox farms comb qiviut
(real-world, from memory).

**Not checked**: no game run; the muskox's spawn biomes (`jaymoskingdoms:muskox_home`); how many snowy villages a new
world gets; the tundra plan's lore paragraph (only that it isn't in `Lore.md` now).

## F6. Lore placement (new)

**How the Frostborn live** (new) (Claude's placement for Andy's heritage ways, 2026-11-23; for Andy to approve). The
Frostborn keep the cold places because they love them, as they loved Hail. Their villages stand on the snowfields and
the frozen shores away from the sea's edge, stone banked with snow and roofed in dark timber, with fittings of their
own make. They eat from under the ice: a hunter bores a hole with one of their augers, fishes it till it freezes over,
and bores another along the lake. They run trap lines of their own make, metal jaws on plates, walked every morning,
and move the line when the ground goes empty. They keep muskox for the wool and walk the herd to fresh lichen. What
they catch they store frozen, so a Frostborn village is the one place never short in winter, and the one place a warm
spell frightens. Robbing a Frostborn trap is theft; a traveller who brings lichen for their herd is welcome.

## Frostborn questions for Andy

1. **Which blood wins a snowy seat**: Frostborn over Sylvan (snowy taiga) and Stoneborn (snowy slopes), with Tideborn
   keeping frozen shores? *Recommend: yes.*
2. **Their traps**: made metal spring traps (recommended), or plain snares?
3. **Frostborn paths**: draft them now with this, or in their own task? *Recommend: their own task.*
4. **Next blood to plan** after the Frostborn? *Recommend: Emberborn, their opposite (fire and the badlands).*
