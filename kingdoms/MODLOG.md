# Jaymo's Kingdoms: MODLOG

An original mod (own code, art and buildings) for Minecraft 26.3 (Fabric). Ideas inspired by Tale of Kingdoms, but
nothing taken from it (its code is GPL-3.0 and its assets all rights reserved).
Plan and the user's answers: `docs/Kingdoms-Plan.md` (0.3: `docs/Kingdoms-Plan-0.3.md`).

## Testing
`run/` holds an offline dev server (port 25597, RCON 25598, password `kingdomsdev`) and ops.json for the dev
client's `Tester`. Start `runServer --args="nogui"`, then `runClient --args="--username Tester --quickPlayMultiplayer
localhost:25597"`, and drive it with RCON.
With no player on, set `pause-when-empty-seconds=0` in `run/server.properties` (the server otherwise stops
ticking a minute after it empties) and `/forceload` the area under test.

## 0.1.0: stage 1
- **Kingdoms** (`Kingdom`, stored on the Overworld by `Kingdoms`): name, colour, capital (a village bell), king,
  members (rank and standing), treasury (emeralds), claimed chunks (7 x 7 round the capital), overlord, fallen,
  succession time.
- **Founding** (`KingdomEvents`): a bell within 48 blocks of a player with no capital within 160 becomes one: a name,
  a colour and its banner by the bell, its land, a king and 5 guards.
- **Folk** (`Folk`): guards (iron, sword; keep within 24 of the capital, fight monsters, anyone the kingdom counts
  hostile, and whoever hits one of them) and the king (300 health, netherite, a boss bar; rallies at 40%: strength,
  resistance, speed, a horn, his guards called in). Skins drawn by `tools/gen_folk.py`.
- **Reputation**, two systems: per player per kingdom (-1000..1000; hostile at -100: guards attack; welcome at 0)
  and per kingdom per member (standing → rank: peasant, soldier 100, knight 300, lord 700). Killing guards -40, the
  king -400 (+60 with every other kingdom), monsters on a kingdom's land +2 (+3 standing for members), deposits.
- **A fallen king** (one of three, at random): a successor in a day; vassalage to the killer's kingdom (if they have
  one); or ruin (the guards scatter, the land is lost).
- **Commands**: `/kingdom list | info [name] | pledge <name> | leave | deposit <n> | withdraw <n> (lords) |
  reputation | found (ops)`. Right-clicking one of the folk gives an audience (and the king offers a clickable pledge).
- Entering a kingdom's land shows its name and your reputation.
- Verified on the dev server: a kingdom founded at a desert village (king + 5 guards), pledge, info, reputation.

## 0.1.1: keeps
- **Every new kingdom builds a keep** (`Castle`) beside its village: the flattest spot 18-40 blocks from the bell
  with the fewest village buildings in the way, its gate facing the bell. A foundation under it, the air cleared.
  Walls 10 high (weathered: mossy and cracked), battlements, four taller corner towers, a 3 x 4 arched gate, arrow
  slits, an upper floor (ladder up).
- **The throne room**: a carpet aisle in the kingdom's colour, steps up a dais, a quartz throne with gold arms and
  back, four wall banners in its colour, torches and a hanging lantern.
- **The panic room**: under the dais, walled in polished deepslate (blackstone bricks in a desert), reached by a
  trapdoor flush with the dais beside the throne and a ladder; the treasury chest is there.
- Desert and badlands kingdoms build in sandstone.
- **Who's where**: the king on his throne (or a third of the time hiding in the panic room), keeping close to it;
  two guards in the throne room, the rest at the gate. Keeps are stored per kingdom (`Kingdoms.KEEPS`).
- Verified on the dev server: a plains kingdom's keep (throne, arms, trapdoor and ladder, banners, carpet), the king
  on his throne.

## 0.2.0: three seats, bigger kingdoms, village materials (built; not yet seen in game)
- **Three kinds of seat** (`Castle.Kind`, chosen at random; `/kingdom found keep|castle|tower` for ops):
  the **keep** (as before); the **castle** (a 31 x 33 curtain wall with walk, corner towers, a gatehouse with a raised
  portcullis, ladders to the walls, a courtyard road, barracks with beds, a training yard, a well, the keep at the
  back); the **tower** (a 12 x 12 donjon, four storeys: guard room, the throne room, the king's chamber, the roof).
- **Watchtowers**: three, 56-88 blocks from the bell round the town's edge, each with an archer on top.
- **Archers** (`Folk.ARCHER`): bows, posted on walls, towers and watchtowers (they stay put and shoot within 28).
- **Bigger kingdoms**: land 13 x 13 chunks; 8 guards (two in the hall, three at the gate, the rest about the town).
- **Village materials** (`Palette`): plains (stone brick, oak), taiga (cobble, spruce), snowy (stone brick, spruce,
  packed ice), savanna (terracotta, acacia), desert (sandstone, jungle), badlands (red sandstone, dark oak), jungle
  (mossy stone, jungle), swamp (mud brick, mangrove), cherry (stone, calcite, cherry).
- Epic Structures: Villages (all rights reserved; the author invites its use in modpacks) is loaded in the dev game:
  kingdoms rise in its towns. It goes into the pack with Kingdoms.

## 0.3.0, track 1: the foundation (factions, storehouses, production, economy, builder folk)
Built, not yet seen in game. The plan is `docs/Kingdoms-Plan-0.3.md` (sections A and the storage part of B).

### What's new
- **Factions.** `Kingdom` became `Faction` (`Kingdoms` became `Factions`), with a `FactionType`: `KINGDOM`, `FIEFDOM`,
  `VILLAGE`, `BARBARIAN`. 0.2 saves load unchanged: the attachment id and field names (`capital`, `king`) are the same,
  new fields are optional, and `Factions.migrate` (on server start) turns a vassal kingdom into a fiefdom allied to its
  overlord and gives realms a roster (8 guards plus an archer for each post).
- **Relations** (`Relation`: allied, neutral, rival, war), stored on both sides. A vassal and its overlord count as
  allied, and barbarians are implicitly at war with everyone. Folk only answer an attack on their own side (the same
  faction or an ally): `HurtByTargetGoal.alertOther` is filtered.
- **Fiefdoms.** The "vassal" fate makes a fiefdom of the victor's realm. Its next ruler is a **lord** (`Folk.LORD`):
  220 health, diamond gear, a purple boss bar, the same rally at 40%. Its folk wear the overlord's colour. A fiefdom
  sends 30% of its taxes to its overlord.
- **Villages.** A bell becomes a capital only if no realm's seat is within **320** blocks (it was 160). A bell nearer
  than that becomes a **village**, unless it lies within 64 blocks of any seat, in which case it's part of that town.
  A village gets a name (e.g. Brinbrook), 5 x 5 chunks of unclaimed land, an **elder** (its nearest grown villager,
  named "Elder of X"; a new one is chosen when he dies, and killing him costs 150 reputation), a **shed**, and a builder
  crew. Its iron golems are its guards: they attack players the village counts as hostile.
- **Storehouses.** Each realm gets a timber hall on a stone course with a gabled roof, built in its `Palette` near the
  keep's gate. Inside are 4 chests and 7 barrels. A village gets an open lean-to with 2 chests and 2 barrels. Both are
  built only on open natural ground: every replaced block is air, a plant or the land's own dirt and stone, never logs,
  paths or anything built. Their containers and the door (the **depot**) are registered in the faction's `Holdings`.
  0.2 kingdoms build one when a player comes within 96 blocks of the seat. If no site is found, it tries again a day
  later.
- **Production** happens once per day on the Overworld clock. Labour is 4 + 2 per villager + 1 per folk. It yields 2
  materials per unit of labour (at most 192 a day): the palette's logs in proportion to the woodland share of the land,
  the rest as cobblestone and stone (1 in 4 is stone). Food is 2 + 1 per villager + 1 per 4 crop blocks, at most 128,
  as bread, carrots, potatoes, beetroot and apples. Production is scaled by type: kingdom 100%, fiefdom 80%, village
  60%, camp 30%. Realms also collect taxes into the treasury: 2 + 1 per 2 villagers + 1 per member. The land is surveyed
  each time it produces with the seat loaded. A storehouse that's unloaded at the turn of the day catches up later, up
  to 3 days. Everything that doesn't fit is lost.
- **Economy health** (`Economy`), shown in `/kingdom info`:
  - **Healthy:** the next project's materials are in storage, plus 32 food.
  - **Personnel:** roster ≥ need (kingdom 12, fiefdom 10, village 2, camp 4, plus 1 per 6 villagers, capped). A dead
    folk leaves a vacancy. Every quarter day, one vacancy is filled by a villager stepping into service (in a puff; the
    village keeps at least 3 villagers). If there are no villagers at all, someone comes from the countryside. Settlements
    short of their need also take on new hands. Hiring pauses while the faction is under attack.
  - **Peace:** no stored war, and no attack on its folk, villagers, golems or leader in the last 30 s.
- **Builder folk.** `Folk.FOREMAN` and `Folk.WORKER` have 20 health and don't fight: they flee when hurt and their side
  answers. Their look depends on the faction type: a tabard in the kingdom's colour (a fiefdom uses its overlord's);
  villager smocks with leather aprons and a straw hat for the foreman; furs, hide and war paint for barbarians, with a
  wolf hood for the foreman. A crew of 3 (a foreman and 2 workers) arrives with the storehouse and grows to 6 with the
  population. Their load is visible: the real items, shown in hand, and dropped if they die.
- **Skins.** `tools/gen_folk.py` now also draws a lord in each of the 16 dye colours (`textures/entity/lord/<dye>.png`)
  and the builders (`textures/entity/builder/kingdom_{foreman,worker}_<dye>.png`, `village_*`, `barbarian_*`). Run it
  with `python -I tools/gen_folk.py lord builder`. Original art.
- **Commands.** `/faction` is an alias of `/kingdom`. `list` and `info` cover every type. `info` shows the type,
  relations, and the three health lines (+ green / - red). `pledge` only works for realms. Operator commands:
  `found village`, `storage [name]`, `produce [name]`, `storehouse`, `crew`, `hire`, and
  `relation <a> <b> allied|neutral|rival|war`.

### API for the next tracks (all in `com.jaymoskingdoms`; stay in this package)
**Factions**
- `Faction` is an immutable record. Its fields are:
  - `id`, `type`, `name`, `color`, `seat` (the bell or fire)
  - `leader` (an entity UUID: a king, lord, elder villager or chief)
  - `members`, `treasury`, `claims`, `overlord`, `fallen`, `successionAt`
  - `relations` (other id as a string → `Relation`)
  - `holdings`
- Change a faction with the `with...` methods. `styleColor(server)` gives the colour its folk wear.
- `Faction.create(id, type, name, color, seat, claims)` makes a new one. Store it with `Factions.put`.
- `Factions` reads and writes them:
  - `all(server[, type])`, `get(server, id)`, `byName`
  - `put`, and `update(server, id, f -> ...)`, which re-reads the stored copy first. **Always prefer `update`**: many
    systems write the same faction.
  - `at(server, level, pos)`: whose land this is.
  - `around(server, level, pos, radius)`: whose land, or the nearest seat.
  - `of(server, player)`: the faction they're sworn to.
  - `storageOwner(server, pos)`: which faction owns this container.
  - `keep(server, id)`, `level(server)` (the Overworld, where every faction lives), `nextId`.
  - `KingdomEvents.claim(server, center, radius)`: unclaimed chunks around a point.

**Relations**
- `Relations.between(a, b)` is the full rule (allied overlord, barbarians always at war). `set(server, a, b, rel)` sets
  both sides.
- `atWar`, `enemies(server, f)`, `vassalize(server, vassal, overlord)` (turns a kingdom into a fiefdom) and
  `overlord(server, f)`.

**Storage** (only loaded containers count; check `Storage.loaded(level, f)` first)
- Reading: `containers`, `contents`, `totals` (item → count), `count(level, f, item | predicate)`, `food`,
  `has(level, f, needs)`, `missing(level, f, needs)`, `freeSlots`.
- `itemsFor(List<BlockState>)` turns a block list into item needs.
- `take(level, f, item | predicate, max)` returns the stack taken. `put(level, f, stack)` returns what didn't fit;
  `put(level, f, item, count)` returns the count that didn't fit.
- `mayTake(player, f)`: a member of knight rank or above.
- `register(server, id, positions, depot)` replaces all of a faction's storage. `add(server, id, positions)` adds to
  it (for camps' loot piles). `Storehouse.build(level, f)` builds storage now.

**Economy**
- `healthy(level, f[, materials])`, `hasFood`, `needed(f)`, `staff(f)`, `adequatePersonnel(f)`.
- `atPeace(server, f)`, `atWar`, `underAttack`, `recentAttackers(server, f)` (entity UUIDs).
- `noteAttack(server, f, attacker)` records an attack (attacks are noted automatically through `AFTER_DAMAGE`).
- `lost(server, id, role)` records a death (`Folk.die` already calls it). `hire(level, f)` fills one vacancy now.
- **Hook:** set `Economy.nextNeeds = (level, f) -> itemMap` to tell the economy what the next building needs.

**Production**
- `produce(level, f, days)`, `survey(level, f)`, `day(server)`, `logItem(level, f)`.

**Folk**
- Roles (ints): `GUARD 0`, `KING 1`, `ARCHER 2`, `LORD 3`, `FOREMAN 4`, `WORKER 5`. `BARBARIAN 6` and `CHIEF 7` are
  reserved but not defined: the camps track defines them.
- `Folk.defineRole(new Folk.Role(id, name, combatant, leader, bossBarColour, (folk, faction) -> outfit))` adds a role.
  The outfit sets gear, health and name.
- `setup(role, faction, home, radius)`, `factionId()`, `style()` (a `FactionType`), `colorId()`, `combatant()`,
  `leader()`, `sameSide(other)`.
- Builders only:
  - `setCarried(stack)`, `carried()`, `takeCarried()`, `isCarrying()`
  - `site()` / `setSite(pos)` and `task()` / `setTask(text)`: both saved, free for the construction code to use.
- Client: `JaymosKingdomsClient.SKINS.put(roleId, state -> texture)` adds skins for new roles. `FolkState` has
  `role`, `style` and `color`.
- Spawning:
  - `KingdomEvents.spawn(level, f, role)` places a folk at its post and adds it to the roster (leaders aren't counted).
  - `spawnArcher(level, f)` places an archer on a free post.
  - `leaderRole(type)` gives the leader's role for a faction type. `crown(level, f)` seats a new leader.

**Builders**
- `Builders.setBrain((level, builder) -> ...)` sets the per-tick AI for every builder. The default is idle: they stay
  within 16 blocks of the depot.
- `crew(level, f)` lists the loaded crew, foreman first. `foreman`, `crewCount`, `crewTarget`.
- `depot(f)`: where they load.
- `spawn(level, f, role, at)` adds one builder to the roster. `spawnCrew(level, f)` spawns a whole crew.

**Barbarian hooks**
- `FactionType.BARBARIAN`, the reserved roles, and `Storage.add` for loot piles.
- Production at 30%, and `Economy.needed` (4 + villagers / 6, at most 20).
- `crown` spawns `Folk.CHIEF` through `spawn`, near the seat when there's no keep.
- The automatic storehouse skips camps. A chief's death clears only the faction's leader: no realm fates.

## 0.3.0, track 3: barbarians (camps, raiders and chiefs, raids, clearing)
Built, not yet seen in game. The plan is `docs/Kingdoms-Plan-0.3.md` section D. New classes: `Camps` (founding,
growth, clearing, rot, `/faction camp`), `CampBuildings` (designs and the growth catalogue), `Barbarians` (roles and
targeting), `Raids` (war parties); client `CampSkins`; `tools/gen_barbarians.py`. Shared files got one-line hooks:
`JaymosKingdoms` (`Camps.register()`, the `Camps.CAMPS` attachment), `Folk.registerGoals` (`Barbarians.addGoals`),
`KingdomCommands` (`.then(Camps.command())`), `JaymosKingdomsClient` (`CampSkins.register()`), and a block at the end
of `en_us.json`.

### What's new
- **Camps** rise in the wild, 150-300 blocks from a kingdom or fiefdom: at most 2 standing camps within 360 of it,
  camps 160 apart, 150 from any settlement, never on claimed land, no village bell within 96. Every minute each realm
  with room has a 20% chance; the site is tried round a player near the realm, 56-128 blocks from them (loaded, but
  out of sight), on open natural ground (no water, trees or anything built; the ground within 4 blocks of level).
  The camp gets a name (Skarfang, Gormtusk), a colour, 3 x 3 chunks of land, and is allied with every other camp
  (barbarians help barbarians). Its seat is the campfire.
- **The camp** (`CampBuildings.build`): the ground levelled and cleared in a 16 block radius; a jagged log palisade
  (radius 14) with a gate facing the kingdom between two tall posts topped with skulls; a campfire with log benches;
  the **chief's tent** at the back (a striped wool tent, skull posts at its porch, a carpet to his seat, his hoard
  chest behind it: emeralds, gold, iron, a saddle, a golden apple); three hide tents; and the **loot pile** on the
  right (2 chests and 5 barrels under a log lean-to), registered as the faction's storage with its depot in front.
  It starts with 32 logs, 16 hides (wool), 16 mutton and 3 emeralds. The chief is crowned (`KingdomEvents.crown`) and
  sent to his tent; 4 raiders and a builder crew (in furs) arrive.
- **Barbarians** (`Folk.BARBARIAN`, role 6): 30 health, stone and iron axes and swords, hide armour (dyed leather),
  sometimes a fur cap, named "Raider of X". **The chief** (`Folk.CHIEF`, role 7): 160 health, 8 damage, a diamond
  axe, chainmail and furs, leggings in the camp's colour, a red boss bar and the leaders' rally at 40%. Raiders and
  chiefs attack players (not creative), villagers, iron golems and every non-barbarian folk on sight; realms' and
  villages' fighting folk attack barbarians on sight. Alerts stay on their own side.
- **Skins** (`python -I tools/gen_barbarians.py`): a raider and a chief per dye colour
  (`textures/entity/barbarian/<dye>.png`, `chief/<dye>.png`), their war paint in the camp's colour. Original art.
- **Growth** (once a day while the camp is loaded; up to 3 missed days): the camp takes in its own hides (12 wool, 2
  in its colour), 6 fences, 8 planks, 3 ladders, a fence gate, 2 hay, a bone block, 6 mutton and 2 leather (on top of
  `Production`'s 30% share), and a new raider joins every 2 days of its age, up to 12. Its builders' catalogue
  (`Blueprint.register(BARBARIAN, ...)`, in order): `camp_totem` (1), `camp_tent` (5), `camp_watch_post` (2),
  `camp_palisade` sections (8), `camp_pen` (2). Growth blueprints use only those materials, logs and cobblestone.
- **War parties** (`Raids`): every 10 s each standing camp with a chief, off its 2-day cooldown, looks for the
  nearest kingdom, fiefdom or village within 420 blocks with a player within 80 of its seat (and not raided in the
  last day); 12% chance it raids. The party (3 + 1 per 2 raiders in the camp, at most 8) musters 48 blocks out from
  the seat on the camp's side; war horns sound and it's announced to players within 160. It marches on the seat
  (waypoints 16 blocks ahead), falls on it (announced; the target is `Economy.noteAttack`ed while they're there, and
  its golems turn on them), then each raider walks to the storage and takes up to 32 of one item (valuables first,
  half the time) with `Storage.take`, carrying it visibly. After 2 minutes (or once all carry something) they head
  home; a raider is home at the camp's fire, or as soon as no player is within 48 of him: his load goes into the loot
  pile (`Storage.put`), or is kept in the camp's data until the pile is loaded. A raider who dies drops his load; a
  player who kills one gains 5 reputation with the target. A war party is a war band mustered for the raid: it isn't
  on the camp's roster and its dead aren't replaced. Raids aren't saved; leftover raiders go home when they load.
- **Clearing a camp** (its chief killed): announced to everyone; the killer gains 100 reputation with every standing
  kingdom, fiefdom and village (players within 48 who fought beside them, 40). Its raiders flee (dropping their
  loot), its people scatter (gone in a puff), the faction falls (no land, leader, roster or storage: the loot pile
  and the chief's hoard are anyone's), and its alliances end. The tents rot: every 30 s while it's loaded (time away
  counts), 4 of its wool, carpets, hay, fences, slabs, skulls, lanterns and ladders go (the fire goes out first). The
  palisade and benches stay as ruins.
- **Commands** (ops): `/faction camp found` (a camp at your feet, its gate the way you face; no site checks),
  `/faction camp raid` (the nearest camp raids the nearest settlement now), `/faction camp grow` (a day's growth).

### Notes for the lead and later tracks
- `Camps.CAMPS` is a new persistent (not synced) attachment, class-loaded from `JaymosKingdoms.onInitialize`.
- Camp builders work from the catalogue above through the shared brain; the loot pile is ordinary `Storage` with a
  depot. Growth blueprints face north (entrance at z = 0), y = 0 the first layer on the ground.
- Killing a camp's raiders still costs reputation with the camp itself (`KingdomEvents.folkDied`), so after three
  kills the camp "counts you an enemy" in chat: harmless, but `folkDied` could skip barbarian factions. Right-clicking
  a barbarian gives the usual "audience" line.

## 0.3.0, track 2: builders, plots and theft
Built, not yet seen in game. The plan is `docs/Kingdoms-Plan-0.3.md` (section C, and the theft part of B).

### What's new
- **The building catalogue** (`Buildings`). Every design is original and drawn in code from the faction's `Palette` and
  colour. Each is drawn with its door to the north and y = 0 as the floor. The builders turn it so the door faces the
  seat.
  - **Villages:** house (7 x 7, timber on a cobble footing, gabled roof, a bed in the village's colour), farm (9 x 7:
    a log kerb, farmland round a water hole, wheat, carrots and potatoes, a fence, a gate, a composter), well (5 x 5),
    market stall (5 x 5, a striped wool awning), crossroads (9 x 9 dirt paths with lamp posts), and later a log
    palisade on a cobble footing.
  - **Kingdoms and fiefdoms:** house (a stone course, stone-stair roof), farm, smithy (blast furnace, smithing table,
    anvil, grindstone, a smoking chimney), crossroads in the realm's road stone, well, mason's yard (stonecutter),
    market square (four striped stalls), stable (two stalls, hay, troughs), barracks (four bunks, a parapet), and at
    the edge of town wall sections (9 x 3, five courses, crenellated), wall towers (7 x 7, ten courses, a ladder to the
    top) and gatehouses (a three-wide arch).
  - **Growth order with gates.** An entry may come back with a higher count and a bigger settlement needed (villagers
    plus folk), so towns grow in stages. Village: 2 houses, a farm and a well, then stalls and roads at 8, more houses
    at 12, and so on up to the palisade at 22. Realm: houses, farm, smithy, roads and well first, then the mason's yard
    at 20, the market at 24, the stable at 28, the barracks at 32, and walls, towers and gatehouses at 40.
- **What it costs** (`Materials`). Storage only holds what production makes (logs and cobblestone), so blocks are
  priced in those raw materials and the foreman works them up on site. The unit is an eighth of an item:
  - a log costs a log;
  - planks, stairs, fences and doors cost a quarter of a log, and a wooden slab an eighth;
  - stonework costs one cobblestone (stone will do too), and a stone slab half of one;
  - fittings (glass, torches, wool, beds) cost a quarter of a log;
  - soil, paths, water and plants cost nothing.
  
  A house costs about 50 logs and 25 cobble in a village, or about 30 logs and 90 cobble in a realm.
- **The planner** (`Planner`). The foreman starts a project when the faction has:
  - a healthy economy (the next building's materials in storage, and food);
  - adequate personnel;
  - peace.
  
  It runs one project at a time (`PROJECTS_AT_ONCE`). It takes the first entry in the growth order that's still short
  and allowed at the settlement's size, and looks for a site ring by ring out from the seat (walls have their own
  rings, at the edge of town). The best site in the first few rings that have one wins: the nearer the ring and the
  closer to a road (a dirt path or the realm's road stone), the better.
  
  A site must be open natural ground inside the faction's land, or within 44 blocks of its seat if it has none:
  - every column is the land's own soil or stone (no logs, no paths, nothing placed);
  - the margin of 1 around it is the same, or a road;
  - it's dry;
  - the ground is within 2 of the floor;
  - there's open air above (plants, snow and air only);
  - it's clear of the seat;
  - it keeps a gap of 2 from every building, project and plot.
  
  If no site is found, the faction looks again 2 minutes later. `Economy.nextNeeds` is set: it's what's left of the
  project under way (less what's already on site), or the whole of the next building.
- **Real-time construction** (`Construction`, the builders' brain):
  - **Staking:** the foreman walks to the site and puts a fence post at each corner and a sign on the corner nearest
    the seat ("<faction> / House / being built"). The stakes come out when the building's done.
  - **Hauling:** each worker looks at the next few blocks and works out what's short, counting what's already on site
    and what others are carrying. He walks to the depot, loads for 2 s, and takes the load out of the real containers
    (at most 16 items). He carries it in his hands to the site and drops it on the pile (the project's saved credit).
  - **Readying the ground:** column by column, he digs out natural ground and plants above the floor and fills
    hollows under the floor with cobblestone (counted as materials, up to 4 deep).
  - **Building:** he lays the blocks bottom up, each layer's materials together, about 4 a trip with a block every 1.2
    to 1.8 s. Each block has its own place sound and block chips; fences, walls, panes and stairs join up with their
    neighbours. Then he goes back for more.
  - **Pausing:** if the storage runs out of something, the crew waits 30 s and looks again. If the faction is attacked
    or at war, they stop, take their loads back, and resume once there's peace.
  - **Getting about:** paths are walked in 20-block legs. A builder who makes no progress for 10 s is moved to his goal
    in a puff of smoke. Anyone standing where a block goes is moved aside, but a player is waited for.
  - **Being careful:** outside a plot, blocks that aren't natural are never broken. Anything placed on the site since
    it was chosen is left alone.
  - **Speed:** a village house takes roughly half a day to a day for a crew of three (by the constants; untested).
  - **Saving:** progress (stage, index, materials on site, stakes) is saved with the faction (`Works`, on the
    Overworld), so half-built buildings resume after a restart. A builder carrying a load when the world was saved
    carries on.
- **Plots** (`Plots`). A lord of the faction (creative players too) marks a plot while standing in the faction's land:
  - `/kingdom plot mark` marks the chunk; `/kingdom plot mark <width> <depth>` marks a rectangle of 5 to 32 centred on
    you, at the height you stand. All of it must be in the faction's land, and plots may not overlap.
  - A **Plot Stake** marks an 11 x 11 plot round the block it's used on, or the chunk when you sneak. It's crafted from
    2 sticks and a wool (makes 2), and is in the Tools tab. Its sprite is drawn by `tools/gen_items.py`.
  - Plots come before open ground. On a plot the builders clear whatever stands in the building's way, even the lord's
    own builds; it goes into the faction's storage, containers' contents included, and what doesn't fit is dropped at
    the depot.
  - `/kingdom plot build <building>` chooses what goes up (it must be in the catalogue and fit). Otherwise the plot gets
    the next building in the growth order that fits.
  - `/kingdom plot list` lists plots and shows their edges. `/kingdom plot clear` unmarks the plot you're on and stops
    its project.
- **`/kingdom project`** shows what the builders are on: the stage, how far along, what's still to haul, and whether
  it's paused or short of materials. When there's no project, it names the next building. Operators also have
  `project start [building]` (now, ignoring the economy), `project finish` (completes it at once, for free) and
  `project cancel` (what was on site goes back to storage).
- **Theft and crime** (`Theft`, our own system; nothing from the Thief mod). Inside a faction's land, or within 48
  blocks of its seat, these are crimes:
  - **Light:** rummaging in its storage without taking anything, breaking its crops, or killing its livestock.
  - **Medium:** taking from its storage, or breaking its beds, job blocks or other chests.
  - **Heavy:** breaking its storage or its bell, or killing its villagers or golems. Taking 32 or more items at once is
    also heavy.
  - Killing its folk is medium, on top of the old reputation loss for a kill.
  - Only what moved between the container and the player's own pack counts, so a builder loading from the same chest
    isn't blamed on you.
  - Crimes against storage and its blocks don't count for members of knight rank or above, or for creative players.
    Camps keep no law.
  
  **Witnesses:** a crime counts only if one of the faction's folk, villagers or golems sees it: in line of sight within
  16 blocks (8 if you sneak), and facing you or within 4 blocks. Villagers and golems are witnesses only where that
  faction's law holds, and sleeping villagers see nothing.
  
  **When a crime is seen:**
  - The nearest witness cries out, heard within 24 blocks ("Thief! Stop, thief!", "Murder! Guards, to arms!"...).
  - Your reputation with the faction drops: -10 for light, -35 for medium, -90 for heavy.
  - A medium crime sets its guards, archers and golems on you for 2 minutes. A heavy one makes you an outlaw for 2
    days. Wanted players are hunted by guards and golems within 32 blocks. This is saved on the player (`WANTED`).
  
  **Unseen theft** is noticed 5 minutes later. It costs -3 reputation and a vague message.
  
  **Gifts:** putting things into a faction's storage gives a point of reputation (and a member's standing) per 8 items,
  up to 25 a day per faction.

### Construction API (for camps and anything else that builds)
A faction type plugs in with no change to the construction code:
1. Register its growth order: `Blueprint.register(type, new Blueprint.Entry(id, (level, faction) -> blueprint, max,
   minSize))`. `minSize` is compared with `Planner.size(f)`, its villagers plus folk.
   - A `Blueprint` lists offsets from the plot's lowest north-west corner. Draw the door to the north (z = 0), with y = 0
     as the floor laid in place of the ground.
   - Makers should be deterministic (no random): a project's layout is drawn again after a restart.
   - Two-block things need both halves listed: doors (`DOUBLE_BLOCK_HALF`) and beds (`BED_PART`). Only the first half
     is charged.
   - Same-id entries share a count, so an entry can come back with a higher `max` and gate.
2. Optionally, `Planner.setRings(id, min, max)`: how far from the seat that building goes (the default is 12 to the
   edge of the land). A faction without claims builds within 44 of its seat.
3. Optionally, `Materials.override(type, (level, faction, state) -> new Materials.Cost(item, eighths) | null)` prices
   its blocks differently, for example wool paid in loot.
4. Needs: storage registered (`Storage.register`/`add`, e.g. a loot pile), a crew (`Builders.spawnCrew`), its seat
   loaded, and `Economy` healthy, staffed and at peace.

The rest happens on its own. Useful calls: `Works.of(server, id)` (projects, built, plots), `Construction.layout`,
`Construction.cancel`, `Construction.finishNow`, `Planner.plan(level, f, force, onlyId)`, `Planner.eligible`. Plots
belong to realms and villages (they need claimed land).

**Theft API:** `Theft.wanted(player, factionId)` and `Theft.outlaw(player, factionId)`.

**Attachments:** `Works.WORKS` (Overworld) and `Theft.WANTED` (players). Both are listed in `JaymosKingdoms`.

## 0.3.0, track 4: war
Built, not yet seen in game. The plan is `docs/Kingdoms-Plan-0.3.md` section E. New classes: `Wars` (sides, declaring,
NPC kings, the score, peace and its terms, war-time goals), `WarData` (the saved state: attachment `WARS` on the
Overworld), `Banners` (planting, holding, claiming, tearing down), `WarParties` (NPC soldiers on the march) and
`WarCommands` (`/kingdom war ...`). Shared files got one-line hooks: `JaymosKingdoms` (`Wars.register()`, the
attachment), `KingdomCommands` (`.then(WarCommands.command())`), `Folk.registerGoals` (`Wars.addGoals`),
`KingdomEvents.folkDied` (`Wars.rulerFell` before the fates), `Theft` (plunder, and no crimes against a realm at war
with yours), and a block at the end of `en_us.json`. Every number is a constant at the top of its class.

### What's new
- **Sides.** A war is between two **principals**, the realms at the head of each side. Every fiefdom beneath a
  principal fights with it:
  - a war declared by a fiefdom is declared in its overlord's name;
  - a war declared on a fiefdom is against its overlord's whole side.
  
  While the war is on, every pair of realms across the two sides is stored as `WAR`, so `Economy.atPeace` stops
  construction on both sides. `Wars.syncRelations` keeps this in step: a realm that changes sides mid-war is at war with
  its old side. A stored war that no war accounts for (an operator's `/kingdom relation a b war`) becomes a war under
  way at once. Barbarians and villages take no part.
- **Declaring.** A member **lord** declares with `/kingdom war declare <name>`. NPC kings decide on their own:
  - once a day, neighbouring kingdoms (seats within 800) with nothing between them may become **rivals** (15% a pair,
    announced);
  - every minute, a kingdom at peace gets its share of a 30%-a-day chance to declare on its weakest rival within 1000,
    if its own army is at least 1.25 times the rival's. The army is the guards and archers of the whole side, with
    each leader worth 4.
  
  A declaration is announced to everyone. After a **warm-up of one day**, the war begins (announced, with war horns).
  A declaration is refused against an ally, a realm on the same side, a realm already in a war with you, or during a
  **truce** (3 days after a peace).
- **During war.** Fighting folk attack the folk of realms at war with theirs on sight, and those realms' sworn players
  too (`HUNT_ENEMY_PLAYERS`).
- **War parties** (`WarParties`, modelled on `Raids`):
  - Every 10 s, each side may send a party (15% chance, at most once a quarter day) against the nearest enemy realm
    with a player within 96 of its seat.
  - A party is 3, plus 1 per 3 of the sending realm's guards and archers, up to 9. They're dressed as its guards.
  - It musters on the enemy's land on the attacker's side, on a claimable frontier chunk if one is loaded, and is
    announced with horns.
  - The bearer plants their banner on the first claimable chunk he stands on, and 2 soldiers stay to hold it.
  - The rest march on the seat (2 minutes at most), plant a banner within 24 of it (a siege), and fight round it for
    3 minutes. Then they withdraw and vanish once out of sight.
  - Parties aren't on the roster, so their dead aren't replaced. They aren't saved: soldiers left after a restart
    vanish.
- **Unseen battles.** If nobody is near a war (no player within 128 of either principal's seat, no party out), it's
  fought out of sight. Every quarter day there are 4 blows, each a kill for one side, with odds by army size.
- **Banners** (`Banners`):
  - **Planting:** a sworn player plants a banner of their realm's colour in the land of a realm on the other side. The
    colour can be the realm's own, the colour its folk wear, or its principal's.
  - **Which chunks:** only a chunk bordering land their side holds, or on the enemy's **frontier** (bordering land
    nobody holds), can be claimed. Otherwise the player is told why.
  - **Sieges:** a banner within 24 of an enemy realm's seat **besieges** it.
  - **Holding:** every second, if none of the enemy side's fighting folk or sworn players (not creative) are within 16,
    the hold goes on. If one is, it pauses. A boss bar shows to players within 64: yellow, or red when contested.
  - **Claiming:** held for 60 s, the chunk passes to the planter's realm (+1 chunk to the score). Held for 4 minutes at
    a seat, the seat falls.
  - **Tearing down:** enemy fighting folk within 40 go to tear it down. Up to 3 go per banner; their post moves to it
    until it's down, then they're put back (also on shutdown). Anyone breaking it ends the claim.
  - NPC parties plant banners in their realm's folk colour.
  - Banners and their progress are saved. Banners of a finished war stay as plain banners.
- **The score** (`WarData.Side`):
  - 2 per enemy folk killed, by a player or a folk of the side;
  - 40 per enemy leader (a lord);
  - 10 per chunk taken (a vassal's seat counts as 3 chunks);
  - 1 per 16 items plundered.
  
  **Plunder:** when a sworn player takes from the storage of a realm at war with theirs, it counts for the score and
  isn't theft. None of their crimes against that realm count while the war lasts, though killing its folk still costs
  the usual reputation with it.
  
  `/kingdom war status` shows, for each war: the warm-up or time left, both sides' scores, armies and allies, any
  standing offer, the terms peace would bring now, and the banners being held.
- **Peace** comes in three ways:
  - when a side has 120 and leads by 50;
  - after 5 days, on the terms the score gives;
  - when a lord offers it (`/kingdom war peace offer [enemy]`) and a lord of the other side accepts
    (`... accept [enemy]`, or a clickable line sent to its lords). An offer stands for 2 minutes. A side without lords
    answers through its ruler after 10 s: he accepts when the terms favour him, when he's 30 behind, or for a white
    peace once the war is 2 days old. Otherwise he refuses, and it's announced.
- **Terms**, by the winner's lead:
  - under 15: a white peace;
  - from 15: **tribute**. That's 30% of the loser's treasury (at least 10 emeralds, as far as it goes), and 6 loads of
    real goods of up to a stack each, valuables first half the time, moved from its storage to the winner's. When
    either storage isn't loaded, each load is paid as 4 emeralds instead;
  - from 45: tribute and **4 chunks ceded**, nearest the winner, never within 2 chunks of the loser's seat;
  - from 90: **vassalage**, with tribute. `Relations.vassalize` makes the loser the winner's fiefdom.
- **Decisive ends.** Either of these ends the war at once with vassalage:
  - killing the enemy principal's ruler, by a player or folk of the other side (a player sworn to neither side takes
    the other side by doing it). The realm then skips the usual three fates, and a lord succeeds in a day;
  - holding a banner at the enemy principal's seat.
  
  Holding the seat of a *vassal* on the other side makes it the taker's fiefdom instead, so it changes sides.
- **After a peace,** the two principals are rivals (unless one is now the other's vassal) under a 3-day truce. A war
  whose principal falls, or whose sides become one, ends with nothing changing hands.
- **Reputation:**
  - killing enemy folk: +6 with every realm of the side the player helps (+50 for a leader), on top of the usual loss
    with the victim's realm;
  - planting a banner that takes a chunk: +15 with the side, and -10 with the land's owner. Sworn players holding it
    with them get +5;
  - tearing down an enemy banner: +8;
  - plunder: +1 per 16 items (at most 8).
  
  Each time, realms outside the war that are allied with the side helped gain 2, and those allied with the side hurt
  lose 2.
- **Commands:** `/kingdom war status | declare <name> | peace offer|accept [name]`. For operators:
  - `war start <a> <b>`: no warm-up;
  - `war end <name>`: a white peace;
  - `war party`: a war sends a party now, wherever the enemy's seat is loaded;
  - `war npc`: kings consider their rivals now.

### Notes for the lead and testers
- `WarData.WARS` is a new persistent (not synced) attachment, class-loaded from `JaymosKingdoms.onInitialize`.
- **Deviation:** the plan says only chunks next to land you hold can be claimed. Realms' lands are 100+ blocks apart, so
  the enemy's frontier chunks (bordering unclaimed land) count too, and a banner near the seat can besiege it from
  anywhere. From there, land is taken chunk by chunk inward.
- A realm vassalized by treaty keeps its living king (in the king's role and its old colours) until he dies. Its next
  ruler is a lord, as with the "vassal" fate.
- **Check first:**
  - a lord declaring, and the warm-up announcement;
  - `war start`, then `war party` near the enemy's seat: the frontier banner, the march, the siege banner and its bar;
  - planting a banner by hand on a frontier chunk and holding it for 60 s, and guards tearing it down;
  - plunder from the enemy's storehouse (no "Thief!");
  - `war status`;
  - a peace offer and acceptance, and each kind of terms (`war start`, then kill folk or take chunks to move the
    score).

## 0.3.0: first playtest fixes (dev server and client, with Epic Structures: Villages)
Played on a dev server with a client joined: kingdoms founded naturally at Epic towns (plains, savanna) and by command
(castle, tower, keep), production, builders, and a war party. Fixed:
- **Storehouses never built** (nor builder crews, so no projects): in 26.3 `#minecraft:dirt` no longer holds grass,
  podzol or mycelium (they moved to `#grass_blocks`), so every grassy site counted as built ground. `Castle.natural`
  and `Materials.free` now use `#substrate_overworld` (dirt, grass, mud, moss). This also changes where seats are
  sited (grass no longer counts against a spot), and helps camps, plots and the planner.
- In a big town the first ring round the gate (8-40 blocks) is all houses and roads; the storehouse now looks out to
  96 blocks when that ring has no site.
- A savanna castle's storehouse went in its courtyard (plain terracotta, its floor, counted as the land's ground).
- A seat on a slope stood on a sheer foundation with no way up to its gate: now steps run down from the gate to the
  land (three wide, in the palette's stairs), or a path is cut through when the seat is dug into a rise.
- Building a seat dropped dozens of items (flowers, crops, torches popped off), now mostly none.
- The palette came from the bell's biome, and Epic towns often have a river through the middle (a savanna town got
  plains stone): a bell on a river, beach or shore now takes the town's commonest other biome round it.

Still to look at: seats in dense towns cut through houses (their tops left floating); theft, plots and the plot stake,
and Villages were not tried.

## 0.4.0, track: faction armor and mob combat
Built, not yet seen in game. Two optional compats, each off unless its mod is loaded: `KnightsCompat` (Epic Knights)
and `MobCombat` (Better Combat for mobs; `BetterCombatBridge` is the only class touching Better Combat, client
`MobAnimations` and `PalMobAnimator` the only ones touching Player Animation Library). Shared files got one-line hooks:
`Folk.setup` (`KnightsCompat.dress` after the role's outfit), `JaymosKingdoms` (`MobCombat.register()`), the client
entrypoint (`MobAnimations.register()`), and `fabric.mod.json` (two mixin configs, `suggests`). `build.gradle` compiles
against Better Combat and Player Animation Library (`compileOnly`) and runs them in the dev game (`localRuntime`, with
Cloth Config). Every number is a constant at the top of its class. Nothing new to translate.

### Faction armour (Epic Knights)
- **Epic Knights** ("Shields, Armor and Weapons", mod id `magistuarmory`, all rights reserved: nothing copied) has no
  26.3 build yet (the newest Fabric one is 10.16 for 26.1.2, needing Architectury and Cloth Config). So there's no
  dependency: items are looked up by registry id when a folk is outfitted, and any id that's missing is skipped (the
  role's vanilla piece stays). A renamed or reworked item in the 26.3 build just falls back the same way.
- **How it decorates armour** (read from the 26.1.2 jar):
  - a surcoat (its heraldry recipe) is a banner's `base_color` and `banner_patterns` on the chestplate, as on a shield.
    Folk wear their realm's banner: a plain field of `styleColor` (a fiefdom's overlord's colour);
  - dyeable cloth (gambeson, coif, pantyhose, crusader tabard) takes `dyed_color`;
  - helmet decorations are entries `{name: "magistuarmory:<decoration>", dyeable, color}` in the `Items` list of its
    `magistuarmory:armor_decoration` component (custom data). It's only set if that component exists and accepts
    custom data.
- **Loadouts** (first id that exists wins):

  | Who | Head | Body | Hands |
  |---|---|---|---|
  | Kingdom guard | bascinet / sallet | platemail (surcoat), platemail legs and boots | steel bastard sword, steel heater shield (banner) |
  | Kingdom archer | kettle hat | gambeson, pantyhose, gambeson boots, all dyed | longbow |
  | King (kingdom or fiefdom) | armet with torse and mantle and a crown, in the realm's colour | gothic plate (surcoat) | netherite claymore |
  | Lord | grand bascinet with a big plume, dyed | Maximilian plate (surcoat) | diamond bastard sword, diamond kite shield (banner) |
  | Fiefdom guard | Norman helmet | chainmail (surcoat) | iron morgenstern, iron kite shield (banner) |
  | Fiefdom archer | quilted coif, dyed | gambeson, pantyhose, gambeson boots, dyed | longbow |
  | Fiefdom lord | greathelm with two plumes, dyed | crusader harness, tabard and boots dyed | steel bastard sword, steel heater shield (banner) |
  | Barbarian | 60%: a rusted kettle hat, Norman helmet, barbute or mail coif | 70% each: rusted mail or half-armour or lamellar, rusted mail legs, rusted or lamellar boots | club, barbed club, rusted mace or bastard sword, stone morgenstern or chain morgenstern; 30% a round shield |
  | Chief | rusted greathelm with viking horns | lamellar and lamellar boots (his war-painted leggings stay) | diamond lochaber axe |

  Builders and villages are unchanged. War parties, dressed as guards, get the guards' kit.
- **Ids relied on** (all in the 26.1.2 jar): `bascinet sallet platemail_chestplate platemail_leggings platemail_boots
  chainmail_helmet chainmail_chestplate chainmail_leggings chainmail_boots steel_bastardsword iron_bastardsword
  steel_heatershield iron_heatershield heatershield kettlehat gambeson_chestplate pantyhose gambeson_boots longbow
  armet gothic_chestplate gothic_leggings gothic_boots knight_chestplate knight_leggings knight_boots netherite_claymore
  noble_sword grand_bascinet maximilian_chestplate maximilian_leggings maximilian_boots diamond_bastardsword
  diamond_kiteshield kiteshield norman_helmet iron_morgenstern iron_shortsword iron_kiteshield coif greathelm
  crusader_chestplate crusader_leggings crusader_boots rustedkettlehat rustednorman_helmet rustedbarbute
  rustedchainmail_helmet rustedchainmail_chestplate rustedhalfarmor_chestplate lamellar_chestplate
  rustedchainmail_leggings rustedchainmail_boots lamellar_boots club barbedclub rusted_heavymace rusted_bastardsword
  stone_morgenstern iron_chainmorgenstern wood_roundshield roundshield rustedgreathelm diamond_lochaberaxe
  iron_lochaberaxe`; decorations `torse_and_mantle crown big_plume two_plumes viking_horns`.
- Epic Knights ships Better Combat attributes for its weapons, so with both mods the knights fight with their own
  weapons' combos.

### Mob combat (Better Combat for mobs)
Better Mob Combat (MIT, Fabric only to 1.20.1) let humanoid mobs fight with Better Combat. This is our own take on it.
- **Who:** humanoid melee mobs whose melee goal is vanilla's `MeleeAttackGoal`, holding a weapon Better Combat has
  attributes for. That's our fighting folk (not archers or builders), and armed zombies (husks, drowned, zombified
  piglins), vindicators and wither skeletons. Piglins and brutes (their brains attack without a goal) keep vanilla's
  blow. Unarmed mobs and other mobs are untouched. `MeleeAttackGoalMixin` hands the attack to `MobCombat.attackTick`.
- **A swing:**
  - The weapon's attributes come from Better Combat's own registry (data packs, so other weapon mods count too).
  - **Reach** is 3 plus the weapon's range bonus (or its own attack range). A swing starts within 90% of it, in sight.
  - **Length** is 20 / the weapon's attack speed (a player's 4 plus its modifiers), x1.35 for mobs, clamped to 10-40
    ticks. A sword is about 17 ticks, a claymore about 27. Then a 4-tick pause.
  - **Wind-up:** the blow lands at the attack's `upswing` share of the swing (at least 4 ticks in), so it can be seen
    coming and rolled away from.
  - **Combo:** each swing is the weapon's next attack (a sword: slash right, slash left, stab). Attacks that need a
    shield in the off hand, or an empty one, are skipped when that doesn't hold; dual-wield and mounted attacks are
    never used. The combo starts over 2 s after a swing.
  - **The blow** strikes every foe in reach and in the arc, up to 5:
    - a horizontal slash: the attack's angle;
    - an overhead: a 28 degree half-width;
    - a stab: an 18 degree half-width.
    
    The target is always struck if it's still in reach. Each hit is the mob's own attack (enchantments, knockback,
    fire) at the attack's damage multiplier. The swing sound plays.
  - **Foes:** the target, anyone fighting the mob, and those it would fight anyway: for folk, monsters (not creepers),
    folk of realms at war with theirs, and players they're hostile to or who are wanted; for monsters, players. A sweep
    never strikes the mob's own side.
  - **Moving:** the mob moves at the attack's movement multiplier while swinging.
- **Animations:** Player Animation Library only animates players' models, but its animation core isn't tied to them.
  The server sends watchers `MobSwing` (the mob, the animation id, the swing's length). Their client plays Better
  Combat's animation on a humanoid controller of its own for that mob, stretched over the swing. After vanilla poses
  the model, the animation's bones are laid onto its head, body, arms and legs. That covers the body and each armour
  piece (`ModelFeatureRenderer`) and the model as the layers see it (`LivingEntityRenderer.submit`, so held items
  follow). Humanoid models get the whole animation; an illager's limbs get it while attacking. Without the library, or
  if its controller can't be built, the mob does vanilla's arm swing. The library's Molang engine type isn't on our
  compile path (it's nested in its jar), so the controller is built by reflection.
- **Sidestep:** a fighting folk on the ground and not mid-swing may dodge a player's melee blow. The blow does nothing
  and he hops aside (0.55 sideways, 0.2 back, 0.22 up), with a whiff sound and a puff. Odds: guards 15%, archers and
  barbarians 10%, kings, lords and chiefs 25%, once every 3 s at most. Better Combat players' blows land at the end of
  their wind-up, so this is the dodge as the swing comes in. Only with Better Combat loaded.

### Notes for the lead and testers
- No new attachments or saved data. Swings and dodges are kept in memory only.
- `MobSwing` is registered whether or not Better Combat is loaded, so clients and servers always agree.
- Epic Knights can't be tested on 26.3 until it has a build. Check first when it does: a new kingdom's guards, archers
  and king; a fiefdom's (overlord's colours on the surcoats, shields and plumes); a camp's barbarians and chief.
  Then see whether the decorations show, and whether the log says pieces were skipped.
- **Check first (Better Combat):**
  - `/summon zombie ~ ~ ~ {equipment:{mainhand:{id:"iron_sword"}}}` or a vindicator. It should swing slash, slash,
    stab, animated, from about 3 blocks away, and the blow should land mid-swing. Roll out of it with Combat Roll.
  - Kingdom guards fighting a group of zombies: a slash should catch several, and never hurt their fellow guards.
  - A king's claymore combo (slam), against the wind-up.
  - Hitting a guard over and over: about one blow in seven is dodged.
  - Armour and the held sword should follow the animated arm.

## 0.4.0, track: illager camps
Built, not yet seen in game. The user's ask: "there should be illager camps as well that work like the barbarians"
(Illager Invasion as the flavour; nothing taken from it). New classes: `Illagers` (the camp illagers, their tag
attachment, hunting, the warlord's boss bar and rally), `IllagerCamps` (founding, growth, replacing a lost warlord),
`IllagerBuildings` (designs and layout). Changed (owned by this track): `Camps`, `CampBuildings`, `Raids`, `Barbarians`.
Shared files got one-line hooks: `JaymosKingdoms` (`LOGGER.debug(... Illagers.TAG)`, class-loading the attachment) and
a block at the end of `en_us.json`. No client code, art or tools script: every mob you see is vanilla's.

### What's new
- **An illager camp is a camp** (a `FactionType.BARBARIAN` faction) whose `Camps.CampData.kind` is `"illager"`
  (`"barbarian"` for the rest; old saves read as barbarian). So it rides on everything camps already do: no court, no
  theft, no builders' plans, at war with every settlement, raiding through `Raids`, cleared through
  `Camps.chiefSlain`. Camps of a kind ally with each other and **barbarian and illager camps are at war**
  (`Camps.takeSides`); barbarians fall on any illager on sight, and camp illagers hunt barbarians.
- **Real vanilla illagers**, each carrying `Illagers.TAG` (a persistent entity attachment: camp id, role, raid flag,
  rallied): pillagers (crossbows), vindicators (iron axes), evokers in older camps and a ravager. Roles (roster keys,
  defined as `Folk` roles only for their names): `PILLAGER 20`, `VINDICATOR 21`, `EVOKER 22`, `WARLORD 23`,
  `RAVAGER 24`. They're made persistent, can't pick up loot, keep within 16 of the fire (strays walk back every 5 s),
  and **never join a vanilla raid** (`setCanJoinRaid(false)`, never patrol leaders: no Bad Omen, no captain drops).
  Vanilla gives them their hatred of players, villagers and golems and their alerts (illagers call only illagers);
  every second each one with no target also picks the nearest folk it can see within 32 (kingdom, village or
  barbarian). Kingdom folk already attack them on sight (they're monsters). On peaceful they vanish like any monster:
  camps don't rise, grow or raid then, and the roster is recounted (and a lost warlord replaced) once it's back up.
- **The warlord** (`Illagers.crown`): a vindicator with 200 health, base damage 6 plus a diamond axe, armour 8,
  toughness 2, knockback resistance 0.6, the ominous banner on his head (it drops when he dies), named "Warlord of X",
  a white notched boss bar for players within 48. At 40% health he rallies once: Strength II, Resistance II and Speed
  for a minute, a raid horn, and every illager of his camp within 48 sent at his foe. A warlord missing for 2 minutes
  while his whole camp is loaded (discarded, not killed) is replaced; a second warlord who loads is dismissed.
- **Where they rise** (`Camps.spawnCamps`): next to the barbarian roll, each kingdom with no standing illager camp
  within 360 has a 15% chance a minute, on the same terms (150-300 from it, 56-128 from a player near it, out of sight,
  nobody's land, 160 from other camps), but only in pillager country (`BiomeTags.HAS_PILLAGER_OUTPOST`: plains,
  savanna, desert, snowy and mountain lands, cherry groves; plus taigas, the dark forest and the pale garden). **Trees
  are allowed**: the ground is read under trunks, leaves and huge mushrooms (`IllagerBuildings.floor`, at most 5 up and
  down) and they're felled when the camp is built. The site check now keeps 22 blocks clear for either kind.
- **The camp** (`IllagerBuildings.build`, original designs in dark oak, cobblestone, grey and white wool, ominous banners
  everywhere): the ground levelled and cleared 22 round the fire and 18 up; a dark oak fence ring (radius 20, 2 high)
  with lantern-topped log posts and a gate facing the kingdom between two posts flying the banner; a campfire with
  three benches; **the warlord's pavilion** at the back (9 x 10: a porch of bannered posts, grey walls on log posts, a
  stepped white-and-grey roof with a banner on its peak, a carpet to his seat, his hoard chest behind it: emeralds,
  iron, a crossbow, arrows, lapis, a golden apple, logs, bread); a **watchtower** (5 x 5, 13 high: a ladder up a plank
  spine, a railed lookout with a pillager sentry, a slab roof, a banner on top); an **iron cage** holding an **allay**
  (free it once the camp is cleared); the **loot pile** (barrels and chests under a dark oak lean-to: the camp's
  storage, its depot in front); three bell tents and two banner poles. Stock: 32 dark oak logs, 32 arrows, 16 bread,
  5 emeralds. People: the warlord, 3 pillagers (one on the tower), 2 vindicators.
- **Growth** (daily while loaded; up to 3 missed days): it takes in 8 dark oak logs, 8 planks, 6 white and 4 grey wool,
  4 fences, 8 cobblestone, 8 arrows, 4 bread and an iron ingot (plus `Production`'s camp share). Its age is the days
  it has grown (`CampData.grown`, so `/time add` and `/faction camp grow` age it): one more fighter every 2 days up to
  12 (2 pillagers to each vindicator); an evoker at 3 days and a second at 7; a ravager at 5. It grows **without
  builders** (folk builders would look wrong among illagers): at most one building per day grown, when no player is
  within 24 of the spot and the loot pile holds 6 logs, 8 planks and 6 white wool, it raises the next of
  `IllagerBuildings.GROWTH`: a banner pole, a practice range (targets on hay, a fletching table), a tent, a pole, a
  tent, a second watchtower behind the pavilion, two poles, a tent (9 in all; a blocked spot is skipped).
- **War parties** (`Raids`, now for either kind): the same rules (10 s checks, 12%, 2-day cooldown, 420 reach, a player
  within 80 of the target). An illager party is 3 + 1 per 2 fighters (at most 8), every third a vindicator, announced
  with "Raid horns! The illagers of X ride on Y!". Plunder is carried in the off hand (it always drops if they die)
  and goes into the loot pile when they get home.
- **Clearing** (killing the warlord): "The warlord of X has fallen to Y! The illagers scatter." The same rewards as a
  barbarian camp (100 reputation with every standing kingdom, fiefdom and village; 40 for players within 48). Its
  illagers within 160 vanish (dropping plunder); those away come back to nothing and slink off when they load. It
  rots like a barbarian camp, now also losing its banners and targets: the fence ring, wool and banners go; log posts,
  the watchtower frames, the cage and the pavilion's posts stay as a ruin.
- **Commands** (ops): `/faction camp found illager` (`found barbarian` is the same as plain `found`), `/faction camp
  info` (the nearest standing camp: kind, age, buildings raised, roster, whether its leader is loaded, whether it's
  raiding, whom it would raid now). `raid` and `grow` work on either kind (`grow` also raises the next building if
  it can, even with you standing by).

### Notes for the lead and testers
- `Illagers.TAG` is a new persistent (not synced) entity attachment, class-loaded from `JaymosKingdoms.onInitialize`.
  `Camps.CampData` gained `kind`, `built` and `grown` (optional fields: old saves are fine).
- Signatures changed: `Camps.chiefSlain(level, LivingEntity chief, int campId, source)`,
  `Raids.raiderDied(level, LivingEntity raider, int camp, source, boolean folk)`; `CampBuildings.level(...)` takes a
  radius and a height, and its `Draft` is package-visible.
- Faction lists still call an illager camp a "barbarian camp" (`faction_type.jaymoskingdoms.barbarian`): a new
  `FactionType` would have needed edits across Economy, Production, KingdomEvents, Relations, Storehouse, Theft and
  Wars. Rumours and talk can tell the kinds apart with `Camps.illager(server, id)`.
- Killing an illager on a faction's land (the camp's own included) still gives `KingdomEvents`' small monster-kill
  reputation with that land's owner: harmless.
- **Check first:**
  - `/faction camp found illager` in a dark forest: trees felled, the layout facing the way you look, banners ominous
    (not plain white), fences joined up, the allay in its cage, the sentry on the tower;
  - the warlord's boss bar, his rally at 40%, and killing him: the announcement, reputation, the scatter, the rot;
  - kingdom guards and illagers fighting on sight; barbarians and illagers fighting when the two kinds of camp meet;
  - `/faction camp raid` from an illager camp near a kingdom: the horn, the march, plunder in off hands, the loot
    pile filling;
  - a vanilla raid (Bad Omen at a village) near a camp: camp illagers must not join it;
  - `/faction camp grow` a few times and `/faction camp info`: new illagers (evokers from the third), a building each
    time; then `/time add 24000` while standing more than 24 from the camp: a day's growth on its own.

## 0.4.2: Minecraft Comes Alive
The pack now has Minecraft Comes Alive (MCA, GPL-3.0; built from its authors' 26.3 branch until a 26.3 release is
out): villagers are people with names, moods and personalities who talk, take gifts, marry you and raise your
children, and its villages have ranks up to monarch. Kingdoms ties into it through `McaCompat` (no MCA classes) and
`McaBridge` (the MCA calls, only touched while MCA is loaded), plus `mixin/mca/McaTasksMixin` (applied by
`McaMixinPlugin` only when MCA is loaded). MCA's villagers extend vanilla `Villager`, so everything that counts,
recruits or protects villagers already sees them.
- **Hearts are reputation:** a fifth of your village reputation in MCA (every villager's hearts for you, summed) round a
  faction's seat is added to your reputation with that faction, between -200 and +300 (`REPUTATION_PER_HEART`,
  `HEARTS_REPUTATION_MOST/LEAST`). Looked up every 5 seconds at most per player and faction. Barbarian and illager
  camps don't count.
- **Ranks:** your MCA rank in a village answers to the faction there (the one whose seat is nearest within 48, and
  the realm whose land it's on): a soldier counts at least as an MCA merchant, a knight as a noble, a lord as a mayor.
  If either faction is hostile to you (-100 or worse) you're an outlaw in that village whatever MCA says. Monarch
  stays MCA's own to earn (its rank tasks: reputation, population, buildings, the Grim Reaper).
- **Recruiting:** settlements draft MCA villagers into service like any other, spouses, kids and friends included
  (Andy: "builds character").
- **Elders keep their names:** an MCA villager chosen as a village's elder isn't renamed "Elder of ..." (MCA would
  rename them in its family tree too, and a dedicated server would write the untranslated key).
- **Check first:**
  - walk into an Epic Structures village: humans, not vanilla villagers; `/faction info` there still counts them;
  - gift and chat with a few villagers in a Kingdoms village, then `/kingdom reputation`: it should have risen;
  - pledge to a kingdom and climb to knight, then open MCA's village screen in its capital: rank noble or better;

## 0.4.1: ATi compounds, builders clear old structures
Built, not yet seen in game. The user's ask: "is it possible to generate multiple of the occuring structures in ATi
together, meaning if one were to spawn in a chunk, then three or four do instead per kingdom to make them larger, and
give more room for builders to expand. they should break existing structures if it means they expand on the intended
build". New classes: `CompoundStructure` (a structure type), `AtiCompounds` (registration, the built-in pack, which
ATi sets it replaces), `Ruins` (old structures as builders' ground), `mixin/ChunkGeneratorStructureStateMixin` (in its
own `jaymoskingdoms.worldgen.mixins.json`). Changed: `Planner`, `Construction`, `Works.Project` (a `ruins` flag, an
optional field: old saves read as false), `JaymosKingdoms` (one line), `fabric.mod.json` (the mixin config; ATi under
`suggests`). Nothing new to translate.

### ATi compounds (only with ATi Structures - Vanilla Edition loaded)
- **A new structure type, `jaymoskingdoms:compound`** (`CompoundStructure`): 3 or 4 other structures generated as one.
  Each member is assembled exactly as it would be alone (its own jigsaw, its own biome rule, its own height on the
  land, its rotation), so a member only goes up where it could have generated by itself. They stand in a ring round an
  open middle (room for a kingdom to grow): each member's start is 3 chunks (48 blocks) from the middle, at even
  angles (give or take 25 degrees). A member that would come within 6 blocks of another, or reach more than 128 blocks
  from the compound's chunk (the game only looks 8 chunks round for structures), is swapped for another (6 tries per
  spot), then its spot is pushed out a chunk, up to 2. A compound takes a different member for every spot while any
  are left. Constants at the top of the class; the data may set `min_count`, `max_count` and `ring` (chunks).
- **ATi's compound** (`jaymoskingdoms:ati_compound`): its members are all 32 of ATi's large and medium above-ground
  structures (the 22 of `ati_structures:aboveground_large` and the 10 of `aboveground_medium`), named by id only
  (nothing of ATi's is copied; ATi is all rights reserved). Its own biomes are `#minecraft:is_overworld`: each member
  checks its own biome tag where it stands, so a desert compound gets the desert outpost and the mosque, a forest one
  the woodland keep and the manor. ATi's small (`aboveground_small`), rare and underground sets are left as they were.
- **Where:** the set `jaymoskingdoms:ati_compounds`, `random_spread`, spacing 64 chunks, separation 32, salt
  1478103217: one compound in every 64 x 64 chunk cell (about one every 1000 blocks). Why 64: ATi alone placed a large
  structure every 70 x 70 chunks and a medium every 55 x 55, about 0.53 structures per 1000 chunks; 3-4 members per
  4096 chunks (less the spots whose biome no member likes) is about the same number of ATi buildings as before, just
  grouped. To change it, edit `resourcepacks/ati_compounds/data/jaymoskingdoms/worldgen/structure_set/ati_compounds.json`.
- **How it's loaded:** the compound's data is a built-in, always-on data pack (`resourcepacks/ati_compounds`, pack
  format 121), registered with `ResourceLoader.registerBuiltinPack(..., ALWAYS_ENABLED)` in `AtiCompounds.register`
  only when `mr_ati_structuresvanillaedition` is loaded. Without ATi none of it exists and Kingdoms runs as before
  (the structure type is registered but unused).
- **ATi's singles stop** in code, not by overriding ATi's files: Fabric adds mod data packs and built-in packs as
  required packs at the top, in the alphabetical order of their ids (vanilla's `PackRepository.rebuildSelected` walks
  a `TreeMap`), so a built-in pack `jaymoskingdoms:...` isn't sure to sit above ATi's own. Instead the mixin takes
  ATi's `aboveground_large` and `aboveground_medium` sets out of the world's structure placement
  (`ChunkGeneratorStructureState.possibleStructureSets`, which generation, `/locate` and explorer maps all read), and
  only when the compounds' set is in the game. Chunks generated before 0.4.1 keep the singles they have.

### Builders clear old structures (`Ruins`, `Planner`, `Construction`)
- **A site may now stand on an old structure** the world generated: a block counts as a structure's when it lies in
  one of its pieces, read from the chunk's structure references. Only structures whose start chunk is loaded are seen;
  anything unseen counts as built, and the site is refused. A column over a structure takes the land under it as its
  ground (looking down through the structure, at most 24 blocks), and the structure's blocks above count as open
  ground to be torn down. Everything else is as before: in the faction's land, clear of the seat and of every
  building, project and plot, level within 2, dry; any block outside every piece must still be the land's own or air.
- **Natural ground first:** a site on an old structure scores 12 worse (four rings, 24 blocks further out), and only
  natural ground ends the ring search. So the builders take open ground near town when there is some, and go through
  old structures when the open ground is much further out (the town grows through the ruins, not round them).
- **Never torn down, or the site refused**, whatever piece it's in:
  - a bell (a site with one in its way is refused), and a bed, job block or meeting point a living villager has
    claimed (`PoiManager`, occupied): a site holding one is refused, and one claimed later is left standing. So a
    living village keeps its homes and workplaces; abandoned houses (unclaimed beds) may go;
  - a village street (a site whose structure columns stand on dirt path is refused);
  - anything unbreakable;
  - anything within 10 blocks of any faction's seat (26 of a camp's fire), within 22 of a keep's throne, hall, gate
    or panic room, within 5 of an archer's post, within 10 of a storage block or the depot. Every faction's finished
    buildings, projects and plots were already kept 2 apart as taken ground;
  - player builds outside plots: a block outside every structure piece must still be natural, so they stay safe (a
    player's build inside an old structure's piece can't be told from the structure, and would go with it).
- **Tearing down** (the ground's readying, column by column, top down): in a building's columns the structure's
  blocks go as high as its pieces reach there (at most 48 above the floor), so nothing floats over the new building;
  next door, the rest of the structure stands. What's broken goes into the faction's storage: the blocks' drops (what
  doesn't fit is left: it's rubble) and **everything its chests, barrels and other containers held** (what doesn't fit
  of that is dropped at the depot). The land's own dirt and stone go nowhere, as before. `project finish` does the same
  at once.
- Saved: `Works.Project.ruins` (optional, default false), so a half-cleared site carries on after a restart.

### Check first
- With ATi in the pack, a new world: the log line "ATi Structures found: its above-ground structures generate in
  compounds (pack on)"; `/locate structure jaymoskingdoms:ati_compound`, and fly there: 3-4 ATi buildings round an
  open middle, none overlapping, each sitting on the land like ATi's own. `/locate structure ati_structures:manor`
  should find none (the singles are gone); small ones (storage shacks, mud towers) still appear alone.
- `/datapack list` shows the `jaymoskingdoms:ati_compounds` pack enabled; a world without ATi loads with no errors.
- Whether chunk generation slows where compounds go (each spot tries up to 6 members).
- A kingdom whose land holds an abandoned village house or an ATi ruin, and no open ground near: `/kingdom project`,
  then the log ("begins a house at ... (over an old structure)"); the crew tearing it down top first, its chests' loot
  in the storehouse, nothing left floating in the new building's columns.
- A kingdom at a living village: its villagers' houses, beds, job blocks and the bell stay; the keep, storehouse and
  watchtowers are never touched.

## 0.5.0, track: faction looks, skins and roads
Built, not yet seen in game. The user's asks: "the different kings and guards don't look different right now" (desert
kingdoms in Epic Knights: Slavic Armory; armour and skins differing by realm), and "make the paths load within or lead
toward kingdoms and villages" (Moog's Paths). New: `Climate`, `Outfits`, `tools/gen_paths.py`, Moog's Paths data under
`data/jaymoskingdoms/moogs_paths/` (and two biome tags), skins under `textures/entity/{guard,king}/` and
`{lord,builder}/{sunny,cold}/`. Shared files got small hooks: `Folk` (a synced `climate`, a saved `outfit` version,
`Outfits.dress` before `KnightsCompat.dress` in `setup`), `KnightsCompat` (a climate argument, Slavic Armory loadouts,
the realm's arms on surcoats and shields), the client renderer (skins by land and face).

### Faction looks (vanilla: works today)
- **Arms** (`Outfits.Heraldry`, worked out from the realm's id, so they never change): its colour for the field, a
  contrasting tincture, one of 18 charges (cross, saltire, chevron, bend, pale, fess, per pale, per fess, chief,
  lozenge, roundel, canton, stripes, a curly bordure, a flower, indented, flanks, per bend), a trim pattern, and how its
  guards are armed: plate, mail under a tabard of its colour, or half-plate. The trim's material follows the colour
  (red: redstone, blue: lapis, green: emerald, white: quartz, black: netherite...). A fiefdom bears its overlord's arms
  within a border. Epic Knights' surcoats and shields now bear these arms too (they were a plain field).
- **Rank:** guards in trimmed iron or mail with a shield of the arms (30% carry a spear; 20% wear the other helmet of
  their harness); archers in leather of the realm's colour (legs in its tincture); a lord in trimmed diamond,
  bareheaded (his circlet shows), with his shield; **the king** in a glinting gold crown set with the realm's gem,
  netherite trimmed in gold in the realm's pattern, a glinting netherite sword and a glinting shield of the arms.
  Barbarians' hides (half of them) and the chief's mail get rough trims of their camp.
- **Land** (`Climate`, read as `Palette.at` reads the seat): desert and badlands: linen turbans (dyed leather caps with
  gold trim), sand or dust trousers and boots, gold boots for the king; snow: white hoods and grey boots; taiga: fur
  boots; jungle: green leather and mostly spears; swamp: murky leggings; savanna: straw caps and copper boots. A realm's
  builders get the land's hat.
- **Old saves:** folk dressed before this are dressed again on their first tick (`outfit` < `Outfits.VERSION`); a
  builder's or raider's hands are kept.
- Tuning at the top of `Outfits` (chances, cloth colours). Gear changes armour values a little (a turban is leather).

### Skins
- `tools/gen_folk.py variants` draws faces by land (our own art, the same generator): `guard/<land>_<0-3>`,
  `king/<land>_<0-1>` (grey or dark beard), and lords and kingdom builders for the sunny and cold lands
  (`lord/<land>/<dye>`, `builder/<land>/kingdom_<role>_<dye>`; temperate ones are the originals). Lands: temperate,
  sunny (desert, badlands, savanna, jungle: darker skin, light linen under the armour), cold (snow: pale skin, fair or
  red hair, dark wool). The client picks the land from the folk's synced climate and the face from its UUID.
- `guard.png` and `king.png` are no longer used by the renderer (the generator's defaults still draw them).

### Slavic Armory for desert realms (waits on 26.3 builds)
- Epic Knights: Slavic Armory (mod id `slavicarmory`, all rights reserved: items named by id only) is an add-on to Epic
  Knights. Neither has a 26.3 build (Slavic Armory's newest Fabric build is 2.3 for 1.21.1; Epic Knights is at 26.1.2),
  so neither is in the pack, and this applies only when both are loaded.
- Desert and badlands realms then wear its eastern harness, Epic Knights' piece as each slot's fallback:

  | Who | Head | Body | Hands |
  |---|---|---|---|
  | Kingdom guard | erikhonka / bulgar helmet | bahteretz or Byzantine lamellar (surcoat), Rus mail legs, bahteretz boots | steel Rus sword, heater shield (arms) |
  | Archer | eastern kettle hat (fiefdom: shapka) | kaftan and kaftan boots, dyed; pantyhose | longbow |
  | King | gilded shater helmet / gilded erikhonka | gilded yushman or gilded Byzantine scale (surcoat), gilded scale boots | steel Rus sword |
  | Kingdom lord | gilded erikhonka | gilded bahteretz (surcoat), gilded Byzantine lamellar boots | steel bardiche, kite shield |
  | Fiefdom guard | bulgar helmet | Byzantine scale (surcoat), Rus mail, scale boots | steel rogatina, iron kite shield |
  | Fiefdom lord | gilded tagancha | gilded Rus scale (surcoat) and boots | steel winged rogatina, heater shield |

- Ids relied on (its 2.3 jar): `erikhonka bulgar_helmet bahteretz_chestplate byzantine_lamellar_chestplate
  rus_chainmail_leggings bahteretz_boots byzantine_lamellar_boots steel_rus_sword eastern_kettlehat kaftan kaftan_boots
  gilded_shater_helmet gilded_erikhonka gilded_yushman_chestplate gilded_byzantine_scale_chestplate
  gilded_byzantine_scale_boots gilded_rus_scale_boots gilded_eastern_kettlehat gilded_bahteretz_chestplate
  gilded_lamellar_mail_chestplate gilded_byzantine_lamellar_boots steel_bardiche byzantine_scale_chestplate
  lamellar_mail_chestplate byzantine_scale_boots lamellar_mail_boots steel_rogatina shapka gilded_tagancha_helmet
  tagancha_helmet gilded_rus_scale_chestplate rus_scale_chestplate rus_scale_boots steel_winged_rogatina`. Whether the
  kaftan takes `dyed_color` is a guess (harmless if not).

### Roads (Moog's Paths)
- Moog's Paths (CC0, in the pack) lays two kinds of path: regional networks (trails wandering across biomes) and
  structure-anchored ones, which start at a structure (a village: at the ends of its streets) and lead away from it.
  It doesn't route between structures, so "toward" means: every road added here starts at a settlement, and following
  one leads you to it. Its path types and networks are its own data registries, so ours are new entries in our
  namespace (`data/jaymoskingdoms/moogs_paths/`), not overrides of its files: no load-order problem, and without Moog's
  Paths they're never read. Structure sets that aren't in the game (a mod not installed) are skipped by it.
- **Kingdom roads** (`path_type`): paved cobble and gravel (stone bricks, mossy cobble), 2-3 wide, 250-700 long;
  sandstone in the desert; terracotta and packed mud on the savanna; cobble, gravel and planks in the snow. Plank
  bridges over water.
- **Networks:** two more roads out of every vanilla village (plains, taiga, snowy, desert, savanna; Epic Structures:
  Villages keeps vanilla's street pools, so its towns count), besides Moog's own two trails, so a kingdom seated in a
  village has four; two from each Grim Kingdoms castle, keep, citadel, fort and town (31 structures); two from each ATi
  compound (`jaymoskingdoms:ati_compounds`: paved off the sand, sandstone on it, by the biome tags
  `jaymoskingdoms:paved_roads` and `sand_roads`); one from ATi's lone homesteads, storehouses and camps. ATi's absorbed
  large and medium sets are deliberately not named: they never place, and Moog's Paths would lay roads from buildings
  that never generate.
- Regenerate with `python -I tools/gen_paths.py` from `kingdoms/`. Roads only appear in newly generated chunks.

### Pack
- Epic Structures: Dungeons (all rights reserved; "feel free to add to any modpack"; Modrinth
  `epic-structures-dungeons:NK6Uptyi`, 1.2.6 for 26.3; CurseForge 1501318 / 8984101; structure ids `epic:*`) joined the
  root `packMods`. Nothing in Kingdoms keys off it.

### Check first
- Two realms side by side: different shields, trims and harness; the king's crown, sword and shield glint.
- A desert realm (turbans, sand trousers, gold trims, darker faces) and a snowy one (white hoods, pale faces).
- An old world: its folk re-dressed once after loading, builders still holding their loads.
- `/paths debug` (Moog's) near a village: `jaymoskingdoms:village_road_*` listed, roads leaving the village's street
  ends in new chunks; a Grim castle and an ATi compound with roads leading off.

## 0.5.0, track: kingdoms in their villages, Grim holds
Built, not yet seen in game. The user's ask: "the structure that it loads does not fit the epic villages it loads within,
can you make the kingdoms spawn strictly within villages, designating the church or largest building to the king, and
another building to the storehouse, so the builders can expand. the barbarians and fiefdoms should spawn in grim
kingdom structures near kingdoms, but please make sure they also get a storehouse to start clearing debris and
expanding on the structures. possibly erecting another grim structure nearby." New classes: `Seats` (reading a
structure's pieces, rooms, a seat made from a building), `Holds` (a new attachment, `jaymoskingdoms:holds`, listed in
`JaymosKingdoms`), `GrimSites` (Grim Kingdoms compat), `Debris` (rubble clearing). Changed: `KingdomEvents` (founding),
`Storehouse` (`adopt`), `Ruins` (held buildings are never torn down), `Planner.ready` (waits for the rubble), `Builders`
(a `brain()` getter), `Camps` (`stock`, `name` opened up; the Grim preference; a Grim camp doesn't rot), the lang file.

### Kingdoms only in villages (`KingdomEvents.tryFound`, `Seats`)
- **A bell founds anything (kingdom or village) only inside a village the world generated**: a structure in
  `#minecraft:village` (or any `minecraft:village_*`; Epic Structures: Villages keeps those ids) with a piece within 2
  blocks of the bell. Read from loaded chunks only: while the village's start chunk, or its ground within 112 of the
  bell, isn't loaded, it waits (no castle fallback). A bell outside every village is looked at again after 5 minutes.
- **No more castle beside the village.** The seat is the village's **church** (a piece whose template path has
  `temple`), else its **largest building** (streets, town centres, farms, pens, decorations, lamps, wells and trees
  aren't buildings). Its main floor is the storey with the most open cells (two high, a sturdy floor, a roof within 12).
  - **The throne**: a stair in the village's materials at the open cell furthest from the door with a wall behind it
    and floor in front; the king stands in front of it; the kingdom's wall banner above it (if there's air).
  - **The panic room**: a 3 x 3 x 3 cell of fortified stone dug under the floor beside the throne, a trapdoor laid in
    the floor, a ladder down, the treasury chest (weaponsmith loot) and a lantern. Only dug through the land's own
    ground (nothing built, no block entities, nothing wet); otherwise the king has no hiding place.
  - **The hall** (two guards): the open cell nearest the building's middle. **The gate** (three guards): just outside
    the door nearest the bell (or, with no door, the ground outside on the bell's side).
  - **Archers' posts** (up to 3, 6 apart): rooftops round the seat and round the bell at least 4 above the seat's
    floor, topped up with open ground near the bell. No watchtowers are built.
  - Only air is ever replaced in the building: beds and job blocks stay, so its villagers stay.
- **The storehouse** (`Storehouse.adopt`): another building, one nobody sleeps in first (library, smiths, mason,
  tannery, stable, butcher..., or a large/big/medium house), else the next largest; else the seat's own building, away
  from the throne. Up to 10 chests and barrels (barrels two high) along its walls at the end away from the door, only
  where there's air, never boxing in a spot; the depot just inside the door; fewer than 3 and it isn't used. Open
  ground (the old storehouse) is the fallback. The builder crew comes as before.
- **Both buildings are held** (`Holds`): `Ruins` never tears them down (a site within 2 of one is refused). The village's
  other empty houses are ground for the builders as in 0.4.1, so the town grows on from the bell.
- `/kingdom found` (operators) with no kind takes the village's buildings if you stand in one; `keep|castle|tower`
  build the old castles as before. Villages (bells near a capital) keep their shed.

### Grim Kingdoms: fiefdoms and camps in its ruins (`GrimSites`, only with `mr_grim_kingdomsloststructuresruinsvanillaedition`)
- Every 30 s the Grim structures referenced in the chunks within 5 of each player are looked over (by id; nothing of
  Grim's is copied). One whose middle is within 600 of a standing kingdom's seat, on nobody's land, 96 from every
  settlement, wholly loaded and with nobody within 40 is taken:
  - **big holds** (the fortresses, citadels, keeps, the grain sack keep/fort/hold, bastion, onion forts, Zweiberg,
    Eldermoor, the hall manor, the colosseum) become a **fiefdom** of the nearest kingdom (vassal, allied, in its
    colours): its largest roofed building the seat, made like a village kingdom's (throne, banner, panic room, posts), a
    lord crowned, 5 guards, archers; land 4 chunks out. "The lord of X raises the banner of Y over an old ruin..."
  - **small and wild ones** (camps 1-3, the outposts, the watches, Tor tower, the spur towers, the meadow ruins,
    Bramblepath, the travelers' lodge) become a **barbarian camp** (the usual camp data, a chief and 4 raiders, allied
    with the other barbarian camps): the chief at the back of its largest roofed building (or the structure's middle),
    the raiders in it; land 2 chunks out; a camp's starting stock. "Barbarians have taken an old ruin near Y..."
  - at most **2 fiefdoms and 2 camps per kingdom**. A structure passed over is looked at again in 5 minutes.
- **Its storehouse**: another of its buildings, else its seat's building away from the throne, else a shed on open
  ground; then its builder crew.
- **Rubble first** (`Debris`): the builders walk the structure's pieces (not its bare terrain) pulling out cobwebs,
  vines, dead bushes and lichen, and loose heaps on floors (gravel, cobble, mossy or cracked stone, andesite, tuff,
  coarse dirt, cobble or stone slabs lying on a floor, open above and on three sides: never part of a wall, a merlon or
  a column); gravel and cobble go into storage. They patch one-block holes in walls (wall left and right, above and
  below, open on both faces) with the wall's own block, 48 at most. Up to 4 passes; the planner waits until it's done
  (or 5 days on). "The builders of X have cleared the rubble from their hold." Then they grow it through the planner
  like anyone else; the Grim structure itself is held (never torn down).
- **A second Grim building**: 3 days after the hold was taken, its rubble cleared and no project under way, the
  builders begin one more nearby **block by block** as an ordinary project (the planner's site rules: its own land,
  level natural ground, nothing built in the way): a fiefdom a grain sack hold, thatch outpost, travelers' lodge or
  Zweiberg; a camp one of the camps, the old timber watch or the thatch outpost. Its template (the first of the
  structure's start pool, from the game's template manager) becomes a `Blueprint` (no air, voids or jigsaws; its
  lowest layer laid in place of the ground; containers come empty), at most 26 across and 5000 blocks. Once per hold
  (`erected`, saved); with no site it looks again a day later. "The builders of X begin raising a new hall..."
- **Wild barbarian camps** still rise, but with Grim loaded a kingdom with a free Grim camp site seen in its reach
  waits for that instead. Illager camps are unchanged. Without Grim none of this runs (the rubble brain idles).

### Notes for the lead and testers
- `Holds` is a new persistent attachment on the Overworld (logged in `JaymosKingdoms` so it loads). Old saves: no holds.
- The Grim catalogue entries (`grim_<structure>`, at most 0) show in the build command's suggestions; the planner
  never picks them by itself.
- **Check first:**
  - a new world with Epic Structures, walking into a village: the king on a throne in the church (or largest house),
    the banner above, the trapdoor beside it and the panic room below, guards at the door and in the hall, archers on
    the roofs; chests and barrels in another building, the builders there; a bell in the wild founds nothing;
  - the builders' first projects: nothing of the church or the storehouse torn down;
  - with Grim: 150-500 blocks from a kingdom near a Grim keep: the fiefdom's message, its lord on his throne, guards;
    a Grim camp's barbarians; builders pulling cobwebs and heaps; wall holes patched (arrow slits may be filled too);
    `/time add 72000` near one: the second building staked out and raised.

## 0.5.1: nights slept through, kings' days out, farmers, the citadel (built; not yet seen in game)
Andy's ask (2026-10-11): sleeping should move the kingdoms on; the storehouse should hold basic blocks (stone, wood,
not dirt) and food; kings should leave their halls by day with 3 to 8 guards, greet their people and come home at
night; the king, a builder and a farmer should take the village's own buildings (the largest for the king, a farm for
the farmer, a building with room for chests for the builders), with one built in the village's style where there's
none; a seat built from nothing should be a castle of several floors, defended, at least 60 blocks across. Gatherers,
mines, tree farms and villagers keeping houses are the next stages. New classes: `Nights`, `Royals`, `Farmers`,
`Citadel`. Changed: `Construction` (`advance`, `leg`), `Folk` (the farmer role, escorts, doors), `Production` (wheat,
meat), `Economy` (farmers hired), `KingdomEvents` (founding), `Seats` (`farms`), `Castle` (`Kind.CITADEL`, the site
prefers loaded ground), the client (farmers wear workers' clothes), the lang file.

### What the storehouse holds
Unchanged in kind: once a day (`Production`), logs of the land's wood, cobblestone and smooth stone (never dirt; the
builders dig soil on site for nothing), and food: bread, now **wheat**, carrots, potatoes, beetroot, apples, and now
**raw beef, pork, mutton or chicken** for each kind of livestock grazing within 64 of the seat. What farmers bring
(below) comes on top.

### Nights slept through (`Nights`)
- The Overworld's clock jumping ahead of the game's ticks (sleeping, or `/time add`) by 1200 or more is time that
  passed in a moment; up to a day of it is made up at once by every settlement whose seat is loaded:
  - its builders lay what they would have laid (`Construction.advance`): a block per 60 ticks per builder about (at
    most 1500), readying columns and laying blocks in order, paid from the pile on site and then storage a load at a
    time, silently; it stops where storage runs short or a player stands in the way; a staked-out site starts without
    its stakes; a finished building is announced as ever. "While you slept, the builders of X laid N blocks."
  - its crops (within 48 of the seat) grow a stage per 2500 ticks.
  - production already counts days by the clock, so the new day's goods come too (up to 3 missed days, as before).

### A ruler's day (`Royals`)
- A king or a fiefdom's lord with a seat: from 1000 ticks into the day (just after sunrise) until 11000 he walks a
  round: his gate, up to four of his villagers (picked at random, nearest first), the bell, his gate again; then
  another round. At each stop the nearest villager within 7 stops, looks at him (its brain's look and interaction
  targets), says yes, and green sparkles show; he raises a hand. Out, he may roam 160 from the seat.
- **His escort**: at dawn, up to 8 of the faction's guards (within 96 of the seat, not fighting) fall in behind him in
  pairs, the hall's and the town's first; the gate's guards keep the gate unless they're needed to make up 3. With
  fewer than 3 guards about he stays in (see Wardens). An escort fallen 40 behind is
  brought to him. At dusk he heads for his throne; when he's there (or at night, his way blocked for 12 s: taken
  there) his escort goes back to their posts (each guard keeps its old post, saved).
- A ruler who slept in his panic room comes up to his throne at dawn. A fight comes first, for him and his guards.
- Every folk now opens (and closes) wooden doors on its way.

### Wardens: a ruler short of guards (`Wardens`)
Andy's follow-up: with fewer than 3 guards the king dares not leave and says so, and may offer you a place.
- A ruler with fewer than 3 of his guards within 96 of the seat keeps to his hall (or heads back to it), unless one
  of his wardens is within 24 of him. (He no longer raises guards for himself; the usual hiring replaces the dead.)
- At an audience he says so ("With but N guards left to me, I dare not leave this hall."). Once a day for each player
  (welcome, not sworn to another realm) he may offer a place: the chance is 10%, +20% per guard short of 3, +15% with
  fewer than 10 villagers, +25% at a reputation of 1000 (scaled). The place: a royal guardsman (welcome; soldier's
  standing), a sworn vassal (200; knight's) or a lord of the realm (500; lord's). A clickable line runs
  `/kingdom serve <name>`, which stands for 2 minutes.
- Accepting swears them to the realm at that standing (or keeps theirs if higher) and makes them a **warden**
  (`jaymoskingdoms:wardens` on the Overworld, faction id → players, logged in `JaymosKingdoms`). Announced to all.
- **If he falls** while a warden is in the world (any cause, a war included), the warden loses all their good name
  with his realm and every realm allied with it (positive reputation to 0; a bad name stays), and every warden's charge
  ends. Leaving the realm (`/kingdom leave`) ends the charge.

### Farmers (`Farmers`, `Folk.FARMER`)
- A new folk role (id 8, `farmer`): a hoe, a leather cap, a worker's clothes; doesn't fight (flees when hurt).
- He keeps one farm: a village's farm piece (`village_farm`, recorded among the faction's buildings and held from
  the builders) or a farm the builders put up. By day he harvests ripe crops in his farm's reach and sows them again
  (one seed kept back from what it gave), sows bare farmland with wheat, and at a pen keeps the herd at 6 head a kind:
  he butchers one when there are more (its meat, and leather, wool or feathers), pairs two off when there are fewer than
  5. What he gathers shows in his hands; at 32 items (or when the work runs out) he takes it to the storehouse's depot.
- A farm with nobody keeping it is a reason to take on a new hand (`Economy.hire`: a farmer before another guard,
  after the builders' crew is full).

### Founding in a village (`KingdomEvents.foundIn`, `settle`)
- The seat is now the village's **largest** building (a church no longer comes first). The storehouse as before (a
  building nobody sleeps in first: room for 3 to 10 chests and barrels).
- **The farmer**: the village's farm piece nearest the bell (`Seats.farms`: pieces named `farm`, then `animal_pen`),
  held from the builders; with none, the builders' `farm` goes up at once in the village's style (the planner's site
  rules); then the farmer.
- **No building fit for a king**: the kingdom builds a **citadel** beside the village (before, the bell only founded
  a village). `/kingdom found` with no kind does the same outside a village; `keep|castle|tower` still build the old
  kinds.

### The citadel (`Citadel`, `Castle.Kind.CITADEL`)
61 x 61 in the village's materials, 52-100 blocks from the bell on the flattest loaded ground, its gate toward it:
- the **curtain wall**, 3 thick and 10 high, a wall walk on top behind battlements, lanterns along it; a flight of
  steps (two wide) up to it inside each side wall;
- **towers** (7 x 7, 16 high) at the corners, half way along the sides and at the back: a lower room, a floor at the
  wall walk (doorways where the walk meets them), a battlemented roof, arrow slits, a ladder through all three;
- the **gatehouse**: two towers either side of a 5-wide, 5-high gate, the portcullis raised, a gallery over the
  passage (murder holes) reached from the towers' lower rooms;
- the **courtyard**: a road to the keep, barracks, a smithy (blast furnace, anvil, smithing table, grindstone), a
  well, a training yard, a stable;
- the **keep** (25 x 23) at the back: three storeys of 6 and a battlemented roof, turrets at the corners, a straight
  flight of stairs inside its right wall on every storey: the throne room (dais, throne, banners, the panic room
  beneath), the king's chamber (a bed in the kingdom's colour, books, a chest) and bunks for the household guard, the
  armoury (barrels, anvil, grindstone, smithing and fletching tables).
- Archers on the four corner towers, both gatehouse towers and the keep's roof (8). No outlying watchtowers.

### Notes for testers
- Only compiled. **Check first**: sleep in a kingdom with a project under way (the message, the blocks); a king
  leaving his hall after sunrise with guards behind him and villagers stopping to look at him, home at dusk; the
  farmer harvesting and carrying to the storehouse; a village with no roofed building (or `/kingdom found` in the
  open) raising the citadel: walk the wall walk, the towers' doorways and ladders, the keep's stairs to the roof.

## 0.5.2: mines, miners and woodcutters (built; not yet seen in game)
Stage 2 of Andy's living-economy ask: the storehouse filled by people seen gathering, and the builders' order of work.
- **The mine comes first** (`Mines`, kept on the Overworld by faction id). A settlement with a crew looks for a rock
  or cliff face 14-72 blocks from its seat (39 or more for a realm, clear of its castle), on natural ground, clear of
  its buildings, plots and held buildings; failing one, open level ground for a shaft. Into a cliff they tunnel 6
  blocks straight in, then down a block a step; a shaft goes down at once. The tunnel is 3 wide and 3 high, its mouth
  framed in logs, walled and roofed in planks over its first 3 steps with a lantern; fence-and-plank posts every 4
  steps and a wall torch between; a cobblestone floor where there's a gap. It stops when it breaks into a cave (open
  air round it that isn't open sky), or short of water or lava, at bedrock, at 40 steps, or 10 above the world's
  floor. Each block takes a builder about 1.5 s; they carry what they dig (stone, ore; dirt thrown away) to storage
  48 items at a time. The mouth is kept among the buildings (`mine`), so nothing is built over it.
- **While it's dug nothing else starts**, unless the crew is 5 or more: then the last 2 workers dig and the rest
  build (`Planner.ready` asks `Mines.mayBuild`). Mines wraps the builders' brain after Construction; Debris (rubble)
  still wraps both.
- **Miners** (`Folk.MINER`, id 9, iron pickaxe, leather cap; `Gatherers`): 2 are taken on when the mine is done
  (villagers turned to it if the settlement can spare them, `Economy.recruit`, else new hands). By day each takes ore
  he can see from the tunnel (3 either side) or in the cave it found (8 round its end), follows any vein he uncovers,
  and otherwise drives side tunnels (2 high, 16 long, both ways off every 4th step, between the posts), leaving
  anything beside water or lava. Loads of 48 to storage; at night he goes home to the mine's mouth.
- **Woodcutters** (`Folk.WOODCUTTER`, id 10, iron axe): one per tree farm. He fells the nearest tree (his tree farm's
  first, then wild ones within 28): a trunk standing on soil with grown (not placed) leaves, so no building's logs; the
  trunk comes down a log at a time from the foot up, and he plants the tree's own sapling where it stood.
- **New buildings, in this order** after the mine: a **tree farm** (11 x 11: grass, a log kerb and fence with a gate,
  five saplings of the land's wood, corner torches), an **animal pen** (11 x 9: grass, a fence with a gate, a water
  trough, hay; 2 cows, 2 sheep, 2 pigs and 2 chickens bought in when it's done), the field, then houses and the rest.
  A finished tree farm takes on its woodcutter, a pen or field its farmer (`Gatherers.built`). Pens count as farms, so
  their farmer keeps the herd and does the butchering.
- **Hiring**: a short crew first, then a farmer, miner or woodcutter where one is wanted, then guards (villages:
  workers). Vacancies for the new roles are filled like the others.
- **Daily production** now only makes up what nobody gathers by hand: no logs with a woodcutter, no rock with a
  miner, half the food with a farmer.
- **Sleep** digs the mine on (a block per 45 ticks per digger) and brings in what the miners (stone with some coal,
  raw iron and copper) and woodcutters (logs) would have; tree farm saplings grow a stage per 5000 ticks. Players near
  the seat are told.
- Only compiled: none of it has been seen in game yet.

## 0.5.3: first in-game test of 0.5.1-0.5.2, and its fixes
Tested on the dev server with a client (plains village, seed 8675309): the kingdom founded in the village, the mine
dug 7 steps into a hill until it met a cave, miners took it over (ore, a side tunnel), stone and raw iron reached the
storehouse, then the tree farm and the pen went up in that order, the woodcutter felled and replanted, the pen got
its beasts, and a night skipped with `/time add` laid the rest of the tree farm and brought in the gatherers' goods.
Fixed along the way:
- A mine stopped at the first pocket of air beside it. Now only a real cave (48+ blocks of open air joined to the
  tunnel, not under the sky) stops it, and not before the tunnel has turned down.
- A cliff site needs ground over the whole level stretch of the tunnel and past it (5+ blocks up), not just a rise.
- Miners with nothing in reach (ore, side tunnels) quarry the rock round the mine's end instead of standing idle.
- Miners, woodcutters, farmers and the king stood still when they had no path (shut in a house, a farmer put on top
  of a tower, the king penned by a fence gate). Like builders, they now turn up where they were going in a puff when
  they get no nearer for a while.
- A farmer's and a woodcutter's home is on the farm's own ground, not on whatever stands over its middle.
- Gatherers reach the depot from a floor above or below it (3 blocks of slack).

## 0.5.4: living trees (our own take on Dynamic Trees)
Dynamic Trees has no Fabric build for 26.3, so Kingdoms grows its own (original code, nothing copied):
- **Saplings grow up in stages.** In the Overworld a plain sapling (not dark oak, pale oak or a 2x2) sprouts a sapling
  tree (2 logs and a tuft), then a young tree (4 logs and a crown), then the full tree of its kind, a stage every
  6000-10000 ticks in daylight with room above. Bone meal still grows a sapling at once.
- **Trees fall whole.** Chop a natural tree's log (not sneaking, not in creative) and everything above it comes down
  a few blocks a tick from the foot up, with the leaves no other tree holds; the axe wears a point per log. Placed
  logs and leaves (buildings) aren't trees.
- **The woods seed themselves.** Near players, natural trees now and then drop a sapling 3-7 blocks away on open,
  lit ground that isn't crowded, inside a hold, or on a building site.
- **Woodcutters** fell whole trees the same way (into their bag) and leave young trees to grow.
- **Sleep** grows the trees on by the time slept.
Tested on the dev server: oak, birch, spruce and cherry saplings sprouted, grew young, and grew full over two skipped
days, and the woodcutter felled a whole tree in his tree farm and carried the wood to the storehouse. Not yet seen in
game: a player felling a tree, and saplings seeding by themselves.

## 0.5.5: stage 3, homes and jobs
- **The working folk sleep at night.** From dusk (12500) to just before dawn, builders, farmers, miners and
  woodcutters walk home and sleep in a bed: one in the settlement's own houses or barracks first, else any bed near
  the seat that no villager has claimed. With no bed they wait out the night at home. They get up at dawn, or if a
  fight starts. A player who uses a bed one of them is sleeping in turfs them out. Guards and archers keep the night
  watch, and the king keeps his own hours. Builders no longer build through the night (sleep still makes up the
  work).
- **New houses bring villagers.** Each house the builders finish brings a villager to live in it, while the
  settlement has more beds than villagers.
- **Villagers take a trade and work it.** A villager without a trade is sent to a free job site in one of the
  settlement's own buildings (the smithy's forge, the mason's stonecutter, the farm's composter, the stall's barrel,
  ...). In working hours (2000-9000), a villager at its job site is seen working (its trade's sound, smoke at a forge
  or smoker), and every 1200 ticks turns storehouse goods into products: smiths smelt 8 raw iron, copper or gold
  with a coal; the butcher cooks 8 raw meat or fish; the mason cuts 8 cobblestone into stone bricks while there are
  more than 256; the fletcher makes 4 arrows from a stick, a flint and a feather; the fisherman brings in 1-2 fish; a
  farmer hands over the crops it carries beyond 12 of each.
Tested on the dev server: at night two builders, three farmers and a miner went to bed (sleeping in house beds), the
rest walked home; all were back at work at dawn. By day an unemployed villager took a trade, the butchers cooked all
38 raw porkchops, and the farmers handed their spare carrots to the storehouse. Not yet seen in game: a smith (the
test village has none), a villager moving into a new house.

## 0.5.6: barbarians settle into Grim ruins
- **Barbarians live in the ruins they take.** When a camp takes a small Grim Kingdoms ruin (`GrimSites.foundCamp`),
  they pitch a camp beside it (`CampBuildings.settle`): a fire with log benches, their loot pile (the camp's storage,
  in place of the plank shed) and two tents, on even natural ground just clear of the ruin's pieces (trees there are
  felled). With no building fit for a chief he keeps by the fire. The raiders are spread out, each keeping within
  `RAIDER_ROAM` (8) of his own spot: the fire, the ruin's middle, its hall, its gate. Before, they all stood in one
  spot at the ruin's middle.
- **Ruins far from any kingdom are taken too.** A small Grim ruin with no kingdom within `RANGE` (600) used to be
  passed over; now barbarians take it if no other camp stands within `Camps.CAMP_SPACING` (160). These camps are
  announced as "in the wild". Fiefdoms still need a kingdom nearby.
- Seen on the dev server (seed 8675309, Grim Kingdoms 2.0.0 in `run/mods`): the tor_tower at -773,-8 taken near a
  kingdom (camp pitched 49 blocks south, raiders at the fire, the tower's base, its top and its gate), and the
  stonyfell_outpost_plains at -777,952 taken in the wild, a jungle clearing cut for its camp.


## 0.5.7: no two buildings alike, each taking after its neighbours
- **Every building is its own variant.** A project now keeps a variant seed (`Works.Project.variant`, saved; picked by
  `Planner.variant` from the world, the faction, the building and how many it has built, and up to
  `Planner.VARIANT_TRIES` (3) are tried for a site). The same seed always redraws the same building; variant 0 is each
  design exactly as before, so projects saved before 0.5.7 finish as they were staked.
- **Materials come from the buildings around them** (`Townscape`): the blocks of the structure the settlement was
  founded in (its village's houses, its ruin's halls; with no structure, the made blocks round the seat) are counted
  once a few days: the woods (planks), the roofing (stairs), the stonework, the colours (wool, beds, terracotta,
  concrete, glass). Each building picks its wood, stone, roofing and colours from these, weighted by how common they
  are, with the land's own palette in the mix (`Kit.HOME_SHARE`).
- **Houses:** 5-9 wide and 7-9 deep; maybe a porch with posts, a rail and a lamp, maybe a second storey with a ladder
  and beds upstairs; timber, plank, stone, timber-framed (with a light infill the town uses) or two-toned walls; a
  gabled roof either way, a hipped roof or a flat one with a parapet (flat roofs are common in the desert, savanna and
  badlands); maybe a smoking chimney; one or two beds, a rug, a chest, books, a furnace or a lamp by the walls, a
  potted flower; windows single, paired or tall, sometimes stained glass in its colour.
- **The rest:** fields of 2-3 crops (beetroots too) in halves, rows or quarters, some with a water channel or a
  scarecrow; tree farms planted in a dice, square or cross; pens with a lean-to; wells flat-roofed, peaked or open with
  lamps, some with a chain; stalls and market stalls with checked, striped or bordered awnings in the town's colours,
  their own wares, a basin or a lamp post in the middle; crossroads with lamp posts in different corners, patchy
  paving, benches; smithies of stone or wood with the forge at either end and a flat or gabled roof; masons, stables
  and barracks with flat, gabled or hipped roofs.
- **Defences match each other:** walls, towers, gatehouses and the palisade take one look for the whole settlement
  (`Buildings.townSeed`), so they join up, and vary only in their details: weathering, lamps, towers crenellated,
  lamp-lit or under a pointed roof, palisade logs of uneven height.
- **Finished buildings keep their full height** (`Works.Built`, its `top()` for older ones), so beds and job sites
  above the floor count as the building's own: house beds were only found through the fallback, and job sites in the
  settlement's own buildings (the smithy's forge, the mason's stonecutter) were never matched before.
- Not yet seen in game.

## 0.5.8: fruit baskets and tool racks (Cooking for Blockheads)
- **Displays** (`Displays`, looked up by name and filled through their `getContainer()`; nothing of Cooking for
  Blockheads is compiled against, and without it nothing changes). Market stalls and village stalls set out produce
  in fruit baskets, up on a counter (a top slab); village stalls also line their counter with baskets. Smithies
  (two on the back wall), barracks (two inside the front wall), masons and some houses (on a side wall) hang tool
  racks. When a building is finished its displays are stocked: from the faction's storage where it has the goods
  (apples, carrots, potatoes, beetroot, melon, berries, bread, pies, cookies; tools and weapons by the building),
  else a few made on the spot, so a display is never bare.
- **`/kingdom project preview <building> [count]`** (ops): sets down a row of a building's variants beside you at
  once, stocked, to see how they differ. Nothing is recorded or paid for.
- **Dev games only** (`DevCommands`, the `jaymoskingdoms.devCommands` property the Gradle runs set): the lines of
  `commands.txt` in the run folder are run as server commands once a second, so a test can drive a singleplayer game.
- Seen in the dev client (seed 8675309, Ravnreach): six house variants (porches, two storeys, chimneys, hipped,
  gabled and cross-gabled roofs, stone, plank and framed walls), markets with checked, striped and bordered awnings,
  smithies (flat and gabled) with racks holding a sword, an axe and hoes, barracks with racks of a crossbow, shields
  and a sword, a market basket of apples and a pie. Baskets up on counters: built, not yet seen.

## 0.5.9: trees topple over; leaders by name
- **Felled trees fall over whole** (`FallingTree`, our own take on Dynamic Trees' falling trees): the moment a tree is
  cut, its logs and the leaves they held leave the world and become one entity hinged at the cut. It tips away from
  whoever cut it, slowly at first and faster as it leans (`WEIGHT` scaled by how far over it is, over its height), and
  stops when its wood strikes solid ground or it lies flat (`FLAT`, at most `LONGEST` ticks). It breaks apart where it
  lands with a crash: every log and leaf gives its drops there (a woodcutter's go into his bag), with chips, smoke and
  sounds, and anything under it is struck (`CRUSH_PER_LOG`, at most `CRUSH_MOST`). Drawn block by block, turned about
  the cut (`FallingTreeRenderer`). Not saved: a tree mid-fall when the server stops is lost. The old way (a few blocks
  a tick vanishing from the foot up) is gone.
- **Leaders by name on their boss bars.** Only kingdoms have kings. A fiefdom's ruler is its Baron, Vassal or Lord
  (`Names.fiefTitle`, the same every time for its fiefdom); a kingdom's lords stay lords. A barbarian chief goes by
  his own Slavic or Norse name (`Names.chief`, from his UUID, so it never changes), one in `BYNAME_ONE_IN` (8) with a
  byname: "the Terrible", "Bloodaxe", "Skullsplitter"...
- Seen in the dev client: an acacia and a dark oak toppled away from the cutter, lay a moment and broke apart with
  their drops; the boss-bar names not yet seen.

## 0.5.10: trees grown like Dynamic Trees'; slower falls
- **Grown trees** (`GrownTrees`, `TreeFeatureMixin`; our own code, vanilla logs and leaves): every oak, birch, spruce,
  jungle, acacia, dark (and pale) oak and cherry the world generates, or a sapling grows into, is drawn branch by
  branch in place of the game's shape: a trunk that leans a little, branches reaching out and up, rounded clumps of
  leaves at their tips, roots spreading at the foot of old trees. Oaks round-crowned (big ones forking), birches
  slender, spruces in tiers that shorten toward the top (two by two for the giant ones), jungle trees tall with a crown
  only at the top, acacias forked into diagonal limbs under flat crowns, dark oaks stout (two by two) under a broad
  crown held high, cherries arching out. Leaves only within `LEAF_REACH` (6) of the wood, each knowing its distance,
  so none decay. Bushes, azaleas, mangroves and modded trees are left to the game; a tree with no room is too.
- **Felling leaves a stump:** roots and the rest of a two-by-two trunk at the cut's own height stay put.
- **Falls slower and settle:** a tree takes about twice as long to come over (`START_SPEED` 0.3, `WEIGHT` 4), lands
  with a thud and the crush, lies `LIES` (14) ticks, then breaks apart.
- **`/kingdomsdev fell`** (dev games only): fells the nearest natural tree as if the tester cut it, and moves the
  camera to watch. The test client is held to 2.5 GB (`-Xmx2560M`).
- Seen in the dev client: every kind placed side by side on a test platform (screenshots), a jungle and dark forest
  generated fresh, and a dark oak felled at half speed (it tipped, lay, broke, its stump and roots stayed).

## 0.5.11: fires, rebuilding, walls round the whole town
- **Fires answered** (`Firefighting`, `BaseFireBlockMixin`): a fire lit within `WATCHED` (96) of a settlement's seat is
  watched until it's out. Each grown villager near it chooses once: fight it (`FIGHT` 40%: runs to a spot two short of
  it, then pours water on it, taken up again after `WATER_TICKS`, or beats it out, half the time knocking out the
  burning block under it), look after the nearest child and lead it away (`GUARD` 30%), or run (`RUN` blocks off).
  Children run. Folk not already fighting put it out. Seen in the dev client: eight villagers and a child round three
  fires on a plank floor; the fires were out within seconds (water poured, then taken up), the rest scattered.
- **Lost buildings put up again** (`Rebuilds`): finished buildings now keep how they were turned and their variant
  (`Works.Built.rotation`, `variant`, `ruined`). A settlement looks over one building every `CHECK_TICKS`; with
  `RUINED_SHARE` (35%) of its paid-for blocks gone (crops, plants, water and soil don't count) it lies in ruins
  (announced), and after `REBUILD_AFTER` (half a day) it's put up again where it stood, clearing what's left first.
  Mended in the meantime, it's forgotten. Only buildings finished from 0.5.11 on, and not the village it grew from.
  Seen: a gatehouse cleared away was found in ruins and announced; the rebuild itself not yet seen (it waited on the
  settlement being "ready", now dropped).
- **The wall round everything** (`Enclosure`): once every kind of building in a settlement's growth order but houses
  is up to its full count (`Planner.utilitiesDone`), its builders wall it in: a rectangle round its buildings, the
  village it grew from and a `MARGIN` (12) for houses to come, squared to whole sections (`Works.Site.wall`). Kingdoms
  and fiefdoms: stone sections, a tower on each corner, a gatehouse in the middle of each side; villages: a palisade
  with a gap in the middle of each side. Gatehouses first, then towers, then the sections. Later buildings stay inside
  (`Planner.site`). Wall pieces are no longer ordinary buildings (`Planner.eligible`). `/kingdom project start wall`
  puts up the next piece at once for ops. Seen: Ravnreach walled round its whole village (86 pieces, across a river).
- The Kingdoms dev game loads Minecraft Comes Alive too (`localRuntime` from `../libs`).

## 0.5.12: war that isn't won by archers alone, rulers' tempers, villagers afraid of monsters
- **Javelins.** Guards, barbarians and chiefs throw javelins (tridents that can't be picked up) at a foe they can't
  close with: one 2.5+ blocks above them (on a wall or tower), a bowman more than 7 blocks off, or one they have no
  path to. Every 70-100 ticks, from 4 to 22 blocks, with line of sight. A javelin never hurts the thrower's own side,
  and a defender's never hurts villagers or golems. War parties were all guards against a town's archers on its
  walls; now they can hit back.
- **Attackers fight harder and destroy.** War-party soldiers and raiders fight with Strength I and carry a torch.
  One that gets no nearer its foe or goal for 3 seconds breaks the door, gate, fence, trapdoor, bars, glass, wool or
  leaves in its way, or sets fire to a wooden wall it can't get past and backs off. On enemy land they now and then
  set fire to wood, wool and hay of anything built within 6 blocks (never trees, never their own side's land).
  Villagers fight the fires and builders put lost buildings up again.
- **Rulers' tempers.** Every king or lord has a temper drawn from who he is (his heir may differ), shown in
  `/kingdom info`:
  - *Tyrant*: slaps the villagers he meets, takes food from stalls he passes, and tithes 10% of the stores' food and
    ingots each day for his coffers. Buildings get 75% of the materials, taxes are doubled, and good deeds count half.
  - *Noble*: greets his people, talks with them and gives them bread. Buildings get 125% of the materials and food,
    taxes are 75%, and good deeds count half again.
  - *Warlord*: inspects rather than greets. His guards fight with Strength, and his realm sends war parties twice as
    often with 3 more soldiers. Taxes are 125%.
  - *Paranoid*: cuts his rounds short (home by 5000), greets no one, and his guard shoves back any stranger within 5
    blocks. Good deeds count half and bad ones double.
  - *Reveler*: greets with cookies and bread, and throws a feast at dusk: villagers gather at the seat, bread for
    all (so more children), music and fireworks. It eats 24 food, and guests win good name.
  Each says a line to nearby players as he meets someone.
- **Villagers fear monsters.** Villagers (MCA's too) who see a monster within 12 blocks run home if home is near
  and not toward it, else 14 blocks away, some fetching the nearest child first, and stay hidden for 10 seconds.
  Endermen, zombies, skeletons, creepers, spiders, raiders and barbarians all count.
Tested on the dev server: a war party of 7 against Garhaven threw javelins (and Garhaven's guards threw back at
soldiers below them), broke trapdoors in its way and set a dozen fires in the town; the paranoid king of Garhaven
shoved a player back; a vanilla and an MCA villager ran from an enderman (4 to 11-13 blocks). Not seen in game: the
other tempers' rounds, the tyrant's tithe, the reveler's feast.

## 0.5.14: fiefdoms that feed their kingdom, caravans, mounted guards and archers, the king's carriage
- **Fiefdoms have a focus** (`Focus`), drawn from their land: *mining* in hills, mountains and badlands, *farming* on
  plains, savanna and by rivers (otherwise by id). Either way a fiefdom builds no wall, towers, gatehouses or
  barracks, and keeps at most 4 guards (the rest of its hands work).
  - *Mining*: 5 miners a mine, 160% stone and logs, one ore (coal, iron, copper, gold, redstone, lapis) for every 6
    stone, and only 1 field and 1 pen (it's fed by its overlord).
  - *Farming*: up to 6 fields and 3 pens, 200% food (256% at most a day).
  Shown in `/kingdom info`.
- **Caravans** (`Caravans`, saved as `jaymoskingdoms:caravans`): by day a fiefdom sends its surplus (all but 16 of
  each good; at least 32 items, up to 3 carts of 576) to its overlord, at most three times a day. Each cart is a
  horse-drawn wagon (`Wagon`) driven by a carter, its load showing what it carries (stone and ore, hay and barrels,
  or a bit of everything), with a guard plus one more for every 2 carts (mounted if the overlord is prosperous).
  The kingdom unloads into its storehouse (kept for later if the storehouse isn't loaded) and sends the carts back
  with up to 60% as much of what the fiefdom lacks (food to a mining fiefdom; logs, coal, iron and cobblestone to a
  farming one), keeping 64 of each. Out of every player's range a caravan goes on in the record at 3.2 blocks a
  second and comes back into the world where it's got to. Stuck (a river, a cliff, a wall) it's set 8 blocks on;
  riders get back in the saddle after a river.
  - Players can strike a cart: its guards turn on them, and a cart broken open (24 damage) spills its load, -60
    reputation with the fiefdom and its overlord. A cart whose carter or horse dies spills too.
  - Right-click a cart to see its load.
- **Wealth** (`Wealth`): a kingdom is *prosperous* at worth 120 and *wealthy* at 260 (worth = treasury + 4 a villager
  + 60 a fiefdom). Shown in `/kingdom info`.
- **Mounted guards and archers** (`Cavalry`): a prosperous kingdom buys horses (6 emeralds each, keeping 40) for 2 of
  its town guards (never those posted at the gate or the throne), a wealthy one for 4, and keeps 2 mounted archers
  riding round the town. Horses are saddled, in leather barding in the realm's colour, or iron for a wealthy realm.
  A horse whose rider fell is taken by the next guard. A prosperous realm's war parties put one soldier in 3 on a
  horse.
- **The king's carriage** (`Carriages`): a wealthy kingdom buys its king a carriage (40 emeralds; another a day
  after one is lost): a cabin in his colours with gold trim and lanterns, a coachman on a horse in iron barding. It
  waits at the gate; on his round the king gets in and is driven from stop to stop, his escort behind, and is driven
  back to the gate at dusk. Attacked, he gets down to fight.
- Riders and caravan folk don't smother in walls (a horse's head brushes leaves and eaves), and neither do the
  realm's horses.
- Op commands: `/kingdom caravan [fief]` sends one now, `/kingdom carriage` buys the king's carriage now,
  `/kingdom treasury <n>`, `/kingdom vassal <fief> <overlord>`. Commands that act on "the realm here" now work from
  the console and command blocks too (at the command's position).
Tested on the dev server: farming fiefdom Tormoor sent caravans to wealthy Hedvale (about 190 blocks): out across a
river, unloaded, back home and gone, both in sight and out of every player's range; Hedvale bought its carriage and
the king rode his round in it; it has 4 mounted guards and 2 mounted archers. The carts' and carriage's models
seen in the client. Not seen in game: a player robbing a cart, a mining fiefdom's caravan, mounted war parties.

## 0.5.17: villager talk over their heads, not in chat
- **Speech** (`Speech`, client `SpeechBubbles`): words spoken aloud float over the speaker's head for players within
  range (newest at the bottom, 3 lines at most, fading after 3-9 s by length) instead of going to chat. A ruler's
  temper lines and a witness's cry (theft, vandalism, murder, arson) use it; a client without the mod gets chat.
- **MCA villagers' talk** (to each other, to you, screams and gossip) goes the same way (client mixin on MCA's
  `ClientHandlerImpl`, only with MCA). It still goes to chat while you're in a villager's talk screen, or when the
  speaker isn't loaded on your client.
Tested on the dev client with MCA: hurt villagers' lines and a witness's "Murder!" floated over them, nothing in chat.

## 0.5.19: boss bars only once you've hit the boss
- **A boss's bar shows only to the players who've hit it** (`BossBars`): kings, lords, barbarian chiefs and illager
  warlords. A hit by hand, with an arrow or javelin, or with a power that names its player all count. The bar stays
  while that player is within 48 blocks; once they leave (or die, or change dimension) it's gone until they hit the
  boss again. Its death or unloading clears it. Banner capture bars are unchanged (they're not a boss's).

## 0.5.20: arson counts as theft, and the townsfolk mob you
- **Lighting a fire in a settlement** (flint and steel or a fire charge, once it catches) is arson (`Theft`), a medium
  crime like stealing: seen, it costs reputation and the guards hunt you. A fire on netherrack (a hearth), relighting
  a campfire, creative players and members allowed to take from storage don't count.
- **The townsfolk turn on an arsonist they saw**: every grown villager (vanilla or MCA) and civilian folk within 24
  blocks runs at you and punches (1 damage; one blow per 30 ticks each, at most one per 10 ticks from the whole
  crowd). Hit any of them and the crowd scatters; it also ends after 60 s, on death, or 48 blocks away. The guards
  keep hunting. Rioters leave the fire to the others (`Firefighting`).
Tested on the dev client: the fire was seen, the crowd closed in and punched, one hit back stopped it.

## 0.5.21: the workers are MCA's villagers; monsters hunt the folk; miners light their way
Andy: "kingdom miners builders and farmers dont seem to be targetable by hostile mobs, please fix that, and integrate
them as much as you can with the MCA mobs, i want builders to be interactable in the same way, and promoted to those
jobs (builder farmer guard) in the same way"; then "miners should place torches as they go".
- **Why they weren't hunted**: workers were `Folk` (a `PathfinderMob`), and zombies, illagers and ravagers only target
  `AbstractVillager`. `Prey` now sets zombies (husks, drowned, zombie villagers) on any folk, illagers and ravagers on a
  realm's; non-combatant folk keep 8 blocks from monsters (`AvoidEntityGoal`, their work waits meanwhile).
- **Workers are villagers (with MCA)**: the work code (`Construction`, `Mines`, `Debris`, `Farmers`, `Gatherers`)
  now drives a `Worker` (an interface `Folk` implements, and `McaWorkerMixin` gives MCA's villagers). A realm's new
  builder, farmer, miner or woodcutter is one of its own MCA villagers promoted where they stand (`Workers.body`,
  `McaBridge.enlist`: the jobless first, then the unskilled, then anyone grown who isn't its ruler or a guard; nobody
  is taken away any more), or someone new who moves in. They keep their name, family and hearts and get MCA's menu.
  New villager professions (`Professions`: `jaymoskingdoms:builder|farmer|miner|woodcutter`) show the job in MCA's
  UI and dress them (`McaSkinMixin`: mason's, farmer's, MCA miner's and lumberjack's clothes); MCA counts them
  important, so it never makes them guards or lets the job lapse. By day `VillagerWorkMixin` runs their work in
  place of their brain (`Workers.step`); at night, frightened, hurt, talked to, or idle, they live as villagers. A
  worker `Folk` in an old save becomes a villager with the same job on its next tick. Camps keep `Folk`.
- **MCA's Professions menu** (adult, monarch rank, MCA's own gate) gets Builder, Farmer, Miner and Woodcutter
  (`McaScreensMixin`, `McaCommandMixin` → `McaCompat.promote`): they join the settlement's crew (its foreman if it has
  none), or are told why not (no settlement here, no farm, no mine dug, no tree farm). Setting one back to villager
  or guard frees the place.
- **Guards** (Andy's card, recommended option while unanswered): a realm's guard vacancies are filled by making a
  villager an MCA guard (or archer, 1 in 3); MCA's own guards in a realm's village are counted on its roster as
  `GUARD`. `McaGuardFoesMixin` sets them on its foes too (players it counts hostile, barbarians, enemies' folk in a
  war). Archers on posts, the starting garrison, escorts and war parties are still `Folk` soldiers.
- **Miners' torches**: where a miner digs out a dark spot underground (block light under 7), a torch goes on its floor
  or a wall (`Gatherers.light`).
- `/kingdomsdev stock`: 256 each of common building stuff into the nearest faction's storage (dev runs only).
- Seen in game (dev server, MCA loaded, Caelwold at -320,-327): the founding crew were MCA villagers (foreman and two
  workers, `jaymoskingdoms:builder`, shovels) who fetched, hauled oak logs and built once stocked; MCA guards counted
  on the roster; a zombie went straight for a faction-less farmer folk, which ran; MCA's Professions page showed the
  four new rows and Builder made Nikoll "Caelwold's Builder" in new clothes with a shovel. Not seen: miners' torches,
  guards attacking a hostile player, farmer/miner/woodcutter promotion.

## 0.5.23: guards clear away dead monsters
- **`Undertakers`** (with Jaymo's Kitchen 0.4.28+, reached by reflection in `KitchenCompat`, so Kingdoms still builds and
  runs without it): every 2 s each standing town or kingdom (not barbarian camps) hands up to 2 monster corpses lying
  within 64 blocks of its seat to its nearest free guard (not fighting, asleep or on another duty). A kingdom's guards
  are its guard folk or, with MCA, its MCA guards; a village's are its iron golems, who only throw corpses into a fire
  and never light one (with no fire near, a golem leaves it for a guard, or for a player).
  - With a **fire pit** (fire on netherrack, soul sand or magma: `infiniburn_overworld`), a **lit campfire** or open
    **lava** within 16 blocks of the corpse, the guard carries it there (Kitchen's `carryBy`) and throws it in from
    3.5 blocks (`throwAt`); Kitchen lights a corpse lying in flames.
  - Otherwise they set it alight where it lies (flint-and-steel sound), unless something that catches (`ignitedByLava`)
    is within 2 blocks: then they first carry it to the nearest bare, solid ground within 12 blocks. With nowhere safe it's
    left for 60 s and tried again.
  - A guard called to a fight puts the corpse down at their feet; a duty not done in 60 s is given up.
- **Fire pit** (`Buildings.firePit`, "fire_pit"): builders put one up in kingdoms and fiefdoms (from 20 people) and
  villages (from 10). A campfire (a soul campfire a quarter of the time) sunk in a 3x3 stone ring, a path round it,
  and now and then stone posts with lanterns at the corners. It's what the guards throw corpses on.
- Tuning is at the top of `Undertakers` (`SWEEP`, `PYRE_REACH`, `SPOT_REACH`, `CLEAR`, `MOST_PER_ROUND`...).

## 0.5.25: trees take chops to fell (HT's TreeChop, our own code)
Andy asked for TreeChop's feel in our trees. TreeChop (MIT) has no 26.3 build (its newest Fabric file is 1.21.4), so it's
rebuilt here; none of its code or art is used.
- **Chopping** (`Trees.chop`, from `PlayerBlockBreakEvents.BEFORE`): breaking a log of a tree (a trunk under living
  leaves) in survival is a chop, not a break. The log stays and narrows: it becomes a **`ChoppedLog`**
  (`jaymoskingdoms:chopped_log`, 1-6 `chops`, an `axis`), drawn by `ChoppedLogRenderer` as the log it was, two pixels
  thinner a side per chop, with a matching hitbox. Once a log is as thin as it goes (6), further chops go into the nearest
  log up the trunk. Each chop shows the log's break particles and sound, wears the axe a point and costs a little hunger.
- **Felling**: a tree comes down on the chop that makes up `Trees.needed(logs)`, TreeChop's logarithmic count
  `round(1 + 10 ln(1 + (n-1)/10))`: 1 log 1 chop, 5 logs 4, 17 logs 11, 20 logs 12, 100 logs 25. It then topples as
  before (`FallingTree`, chopped logs falling as the logs they were) from the block being chopped. Chops in the stump
  under the cut count toward the same tree. Tuning: `Trees.CHOP_SCALE` (0 = one chop per tree; very large = one per log)
  and `ChoppedLog.MOST`.
- **Sneak** to break a single log as ever; creative breaks as ever. A chopped log broken on its own (no tree left round it,
  or sneaking) gives the log it was (`ChoppedLog.getDrops`). Chopped logs are in `minecraft:logs` (leaves keep to them,
  a tree is still a tree) and `mineable/axe`.
- **Woodcutters** chop too: a chop every `Gatherers.CHOP_TICKS` (12) at the foot, the trunk narrowing as they go, until
  it comes down into their bag. Bigger trees take them longer.
- Seen in game (dev server + client): a 17-log oak narrowed at its foot chop by chop, the chops moved up the trunk, and it
  fell and broke into wood on the 11th. Not yet seen: woodcutters chopping.

## 0.5.26: rickshaws (pullers carry goods and people)
Andy asked for villagers who rickshaw goods and people about the villages and kingdoms. A rickshaw is a third kind of
`Wagon` (`Wagon.RICKSHAW`), hitched to a person instead of a horse; the work is `Rickshaws`.
- **Pullers** (`Folk.PULLER`, the `jaymoskingdoms:puller` profession, a "Rickshaw puller" button on MCA's Professions
  page): a settlement takes its first once it has a road, a storehouse and `Rickshaws.FIRST_AT` (12) people, its second
  at `SECOND_AT` (30). With MCA it's one of its villagers promoted in place, like the other workers. They wait at the
  rank by the bell, park there at dusk and go to bed.
- **Goods**: a miner, woodcutter or farmer walking a load in from more than `MEET_FROM` (28) blocks from storage is met;
  the load goes into the rickshaw (shown on its rack) and the gatherer goes straight back to work. The rickshaw hauls
  to storage at `HAUL_AT` (96) items or when there's nobody else to meet.
- **Errands**: at most every `ERRAND_TICKS` (3600) a settlement sends up to 64 items to a sister settlement within
  `ERRAND_RANGE` (220) blocks (its overlord, a vassal, a fellow vassal or an ally): food if it has `FOOD_GAP` more,
  else whatever it has `ERRAND_GAP` more of.
- **People**: now and then a waiting puller fetches one of the town's villagers (a child or a grown-up) and drives them
  to one of its buildings. A ruler with no carriage is fetched on his round and rides it from stop to stop
  (`Carriages` drives a rickshaw the way it drives his carriage).
- **Players**: right-click a waiting rickshaw for up to 6 settlements within `RIDE_RANGE` (700) blocks with fares
  (`FARE_BASE` + 1 per `FARE_PER_BLOCKS`, at most `FARE_MOST` emeralds); clicking one runs `/kingdom ride` and the fare
  goes to the settlement's treasury. Sneak to get down on the way (no refund). Hostile players and enemies at war are
  refused. Out of sight of any player after a trip, the puller is back at the rank in a puff.
- Stuck, a puller hops `HOP` (8) blocks ahead in a puff, never the whole way. A rickshaw whose puller is gone (or given
  other work) for `ABANDON_TICKS` spills its load and is taken apart.
- **Worn out** (Andy's follow-up): pullers tire with every block pulled (more laden), spent after `TIRED_BLOCKS`
  (1800), losing up to `SLOWEST` (half) their speed. They get a little back waiting at the rank and all of it after a
  night's sleep; spent past `WINDED` they stop now and then to catch their breath (sweat drops, a gasp). A player
  hailing a worn-out puller is told it'll be a slow ride. The rickshaw syncs its puller and their tiredness (`Wagon.PULLER`,
  `TIRED`) for the client.
- **Pose** (`PullerPose`, client, through the same model hooks as `MobAnimations`): bent into the shafts like a crouch,
  hands down at the hips gripping them, a tug back with each stride; the more tired, the lower the lean, the heavier
  the tug and the more the head hangs; stood still and spent, their shoulders heave. MCA villagers' clothes layers and
  breasts follow (`McaPullerPose`).
- **Cabs** (`Cabs`, `Wagon.CAB`): a realm whose treasury holds more than `Cabs.RICH` (300) emeralds keeps a hired
  carriage (a driver on a horse, a plain coach in its colours) across the bell from the rickshaw rank. It, not the
  rickshaws, takes players to other settlements (same fares and offer, faster); a rickshaw there points players to it.
  The pullers do everything else. Below 300 the cab is let go once it's back and empty.
- Not yet seen in game: all of it (built only).

## 0.5.27: stables and taverns
- **Three stable designs** (`Buildings.stable`), mocked up on the dev server and picked from screenshots:
  - **Row stable** (`stableRow`, 13 x 7), the village one: four open-front stalls (gate, hay manger, water trough) under
    a sloped slab roof, with a covered walk along the front.
  - **Coaching yard** (`stableYard`, 13 x 13): four stalls under a gabled roof behind a fenced yard with a trough, a hay
    stack, lamps on the posts and a 3-wide gate for carts.
  - **Aisle barn** (`stableBarn`, 11 x 15): a stone-footed barn with a cart-wide aisle end to end, six stalls, a hayloft
    over them and lanterns over the aisle.
- Villages now build a stable (from 18 people); kingdoms and fiefdoms still build theirs from 28, now the coaching yard
  or (half the time) the aisle barn in place of the old two-stall stable.
- A finished stable gets its horses bought in (`Gatherers.STABLE_HORSES`, 2).
- **Three tavern designs** (`Buildings.tavern`), mocked up the same way and picked by size (Andy's "mix by size"): the
  alehouse below `TAVERN_GRAND` (48) people, then the coaching inn or (half the time) the beer-garden tavern:
  - **Alehouse** (`tavernAlehouse`, 13 x 11): a long hall under a hipped roof, the bar and barrels down one side, a
    smoker hearth and chimney, three tables, and a covered porch of tables out front.
  - **Coaching inn** (`tavernInn`, 11 x 9): a stone taproom (bar, barrels, three tables, a smoker hearth and chimney)
    and a timber floor of three beds above, up a ladder.
  - **Tavern with a beer garden** (`tavernGarden`, 15 x 11): a two-storey tavern and a fenced garden beside it with
    tables, lanterns on posts and its own gate.
- **Only a wealthy kingdom with room to spare builds a tavern** (Andy's rule; `Planner.eligible`): a kingdom (never a
  fiefdom or village) of 26 or more people, `Wealth` tier WEALTHY, with nothing else in its growth order left to
  build. The wall doesn't wait for it (`utilitiesDone` skips it). Tables are a fence and a pressure plate of the land's
  wood (`Kit.plate`).
- **Sittable stools** (`Cushions`): every stool (a wooden slab beside a table) gets one of 26.3's cushions in the
  realm's colour, facing the table, when the building is finished (or previewed). Right-click to sit.

## 0.5.28: what guards throw depends on their kingdom's purse; treasuries that rise and fall; march with a war party
- **Javelins are Balkon's.** Guards of ordinary kingdoms now throw Balkon's WeaponMod javelins
  (`WeaponModJavelins`), which can't be picked up and hit for about 5 instead of a trident's 8. When and at whom they
  throw is unchanged.
- **Tridents for the elite.** Only the guards of the 2 wealthiest kingdoms holding at least 300 emeralds still throw
  tridents (`Javelins.elite`, tuned by `ELITE_REALMS` and `ELITE_TREASURY`).
- **Rocks for the poor.** Guards of a kingdom with under 50 emeralds (`POOR_TREASURY`), barbarians and their chiefs
  throw rocks: a cobblestone thrown like a snowball that hits for 3 (`ROCK_DAMAGE`). Without WeaponMod loaded, everyone
  but the elite throws rocks.
- **Treasuries rise and fall** (`Treasuries`). A new kingdom starts with 10 to 700 emeralds instead of none.
  Trade earns: a player's trade with a villager on a realm's land pays the realm a toll of a quarter of the emeralds in
  it (at least 1; `VillagerTradeMixin`), and a fiefdom's caravan pays its kingdom 1 emerald per 32 goods delivered.
  War costs and pays: a war party costs 2 emeralds per soldier when it marches, and a soldier who kills an enemy's
  folk plunders 3 emeralds from that realm for his own (40 for a leader). The loser's tribute at a war's end, the
  daily taxes and the tyrant's tithe are unchanged. Existing kingdoms keep their treasuries
  (an op standing in one can set it with `/kingdom treasury <emeralds>`).
- **March with a war party** (`/kingdom war join`, `WarParties.join`). A player sworn to a realm at war joins their
  side's party in the field (told where it is), or, with none out and standing within 64 blocks of their realm's
  seat, calls one up right there: it marches the whole way to the nearest enemy seat with them (up to 8 minutes'
  march, 40 for the whole outing).
- **Spoils carts.** Every war party now takes a horse-drawn storage cart with a carter (a caravan `Wagon`), or it
  doesn't march. The cart keeps behind the column on the march and back from the fighting at the siege. When a
  party's siege is over, it sacks the seat: 30% of the enemy's treasury and up to 9 stacks from its storehouse go
  into the cart, which hauls them home into the storehouse (what won't fit is left at the seat) and treasury. Players
  who marched with the party share 25% of the emeralds and gain 15 reputation. Break the cart open, or kill its horse
  or carter, and the spoils spill on the ground for whoever gets there first. If the soldiers fall, a laden cart goes
  on home alone. A cart that loses its way (stuck on a roof, a wall or a ravine for 12 seconds) is helped over it.
  A party whose cart is lost can still sack the seat, but takes no goods and only the emeralds its soldiers can carry
  (8 each, `SOLDIER_CARRIES`). Right-click a cart to see what it carries.
- **Fix:** a rock hitting an MCA villager crashed the server (the rock's blow was dealt again and again).
- The Kingdoms dev game loads WeaponMod (and Architectury) too, and Kingdoms compiles against it (`compileOnly`).

## 0.5.29: quests from villagers and kings
Andy asked for Quest Giver's kind of quests. Quest Giver is Forge/NeoForge 1.21.1 only, so this is our own code in
Kingdoms (nothing taken from it). Only villagers of a kingdom, fiefdom or village (not barbarians, outlaws or
cultists) and kings give quests.
- **Who has work**: each giver rolls once a day (`OFFER_CHANCE` 35%) when a player is near. A gold **!** floats over
  one with work; a **?** over the giver of one of your quests (grey, then gold once it's ready). MCA villagers have a
  **Quests** button on their main page; plain villagers: sneak and use; kings: talk to them (after the audience).
  A giver stands still and faces you while their quest screen is open (`HOLD_TICKS`), and a king pauses his round.
- **Villagers' quests fit their job** (`QuestBoard`): farmers want crops or rabbits killed, butchers and
  leatherworkers want animal parts, fishermen fish, fletchers feathers and flint, smiths iron and coal, clerics undead slain,
  librarians and cartographers want a keepsake fetched from a real ruin (trail ruins, ruined portals, shipwrecks,
  Grim ruins, ATi structures, never one a settlement has taken over), guards want monsters slain. Anyone may ask for
  **mail** to be taken to a villager in town, or to the king, lord or elder of a settlement nearby (a sealed letter).
  A fetch quest sets out a chest with the keepsake and 3 guards when you get within 48 blocks; it glints.
- **Kings' decrees are hard** (one at a time): slay a **champion** in a real lair (trial chambers, outposts,
  pyramids, strongholds, mansions, ancient cities...; each lair has its foe: warchief, tomb lord, stalker, frost
  witch, bone lord, brute, beast, plague knight) or the chief of a barbarian camp or illager warlord. A champion has
  500 health (beast 700), +10 damage, 20 armour, 10 toughness, no knockback, 5 escorts, 4 more at 60% health, rage
  at 30%, a shockwave, heals when nobody's near, and a red boss bar (once you've hit it).
- **Rewards suit the giver**: emeralds, the giver's own goods (a farmer gives hay, a mason stone, a woodcutter
  logs, a smith a weapon or armour piece, a librarian always an enchanted book), MCA hearts with that villager, reputation with their realm and standing.
  Smiths often give a **relic**: a weapon or armour piece with a passive (weapons: Bloodthirst, Frostbite,
  Emberbrand, Stormcaller, Executioner, Venomfang; armour: Second Wind, Retribution, Bulwark, Fleetfoot, Flameward;
  `Relics`). Kings pay 64 emeralds, diamonds, a high book, a royal netherite relic and +150 reputation.
- **Regicide** (Andy's ask): any king but a noble may decree another king's death, each for his own reasons (the
  tyrant's pride, the warlord's contempt for a king who won't fight, the paranoid's fear of plots, the reveler's
  grudge), how often by temper (`REGICIDE_CHANCE`: tyrant and paranoid 40%, reveler 25%, warlord 10%). The target is
  his enemy at war first, then a rival, then the nearest crowned realm that isn't his ally (within 2500 blocks). How
  to get close is in the telling:
  - **A false oath** (any king but a noble one): swear to his realm (`/kingdom pledge`), walk up to his throne
    with his guards' leave, and strike. Dying sworn to, he pays the assassin +50% emeralds and +50 good name with
    the giver; the oath is broken with the blow (you're struck from his realm, and it counts you an enemy).
  - **The noble's round**: a noble king walks out among his people by day. Come within 5 blocks of him out there and
    he stops for you, gives you bread and stands still for 8 seconds. Slain out on his round, the same +50%.
  - Killing a king keeps its usual cost: -400 with his realm, +60 with its rivals, and his realm's fate.
- **A warlord's host**: a warlord would rather wage the war himself, so half his decrees (`WARBAND_CHANCE`) are to
  lead his war party against that same rival and sack its seat. Not at war with them yet, he declares it when you
  say yes. Once the war is on, stand at his seat and `/kingdom war join` (sworn to him or not): the party marches with
  you, and the quest is done when it sacks the enemy seat (`WarParties.seize`).
- Ops: `/kingdom quests offer regicide` and `/kingdom quests offer warband` give the nearest king that decree.
- Up to 5 quests and 1 decree at once. `/kingdom quests` lists them (click to give one up); a tracker on the right
  of the screen shows 3 with distance and direction. Ops: `/kingdom quests offer` (nearest giver has work now) and
  `/kingdom quests finish` (counts made up).
- Tuning: tops of `Quests`, `QuestBoard`, `QuestFoes`, `Relics`, `QuestClient` (mark size and height).
- Seen in game (dev server, MCA loaded, Dunhaven): the gold ! and MCA's Quests button; a farmer's gather quest taken,
  the ? turning gold, turned in for emeralds and a cake (+reputation, +hearts); the king's decree "The Bone Lord" at
  trial chambers taken; the champion and its escort set out at 48 blocks, boss bar on hitting it, its death made the
  quest ready; turned in for 64 emeralds, 5 diamonds, Mending, a Retribution netherite helm (+75 reputation after
  the king's temper). The helm sent back a quarter of an 8-damage hit. Not seen: mail, fetch caches, slay counts,
  the other passives. Regicide: Dunhaven's tyrant sent for the reveler king of Falshire; pledged to Falshire, struck
  him down, and the decree was ready with 51 → 76 emeralds and 150 → 200 reputation, the oath broken. Taking the warlord's
  host declared war on Ravngard; once it began, `/kingdom war join` called up Dunhaven's party for the unsworn
  Tester, which marched, planted its banner, besieged and sacked Ravngard's seat, and the decree was ready. Not seen: the
  noble's round and his bread.

## 0.5.30: the realm's armouries (original 3D armour sets)
Andy asked for Jet and Elia's Armors (Forge only, all rights reserved, no source), then chose "make our own sets".
Nothing of that mod is used: shapes, art and names are our own, drawn by `tools/gen_armory.py`.
- **Four sets** (`Armory`), each helmet, chestplate, leggings, boots, crafted like vanilla armour from two ingredients:
  **Paladin** (iron + gold; great helm with a gilt comb and plume, round pauldrons, a tabard with a gilt cross; 18 armour,
  toughness 1), **Gladiator** (copper + leather; broad-brimmed grille helm with a crest, banded sword arm, studded
  belt, greaves; 14 armour, enchantability 14), **Ronin** (iron + string; horned kabuto with a neck guard, hanging
  shoulder plates, plated skirt; 15 armour, toughness 0.5), **Warlord** (leather + bone; horned iron cap, fur mantle and
  pelt, bone pauldron, hide kilt; 16 armour, knockback resistance 0.1 a piece). Numbers at the top of `Armory`.
- **Dye**: the cloth (plume, tabard, crest, lacquered lames and lacing, hide kilt) takes dye like leather (a
  `crafting_dye` recipe per piece with cloth); undyed it's the set's own colour (blue, red, red, brown).
- **Raised pieces** (client `ArmoryLayer`): each set's 3D pieces are in `assets/jaymoskingdoms/armory/<set>.json`
  (slot, bone, box, UV, optional pivot/rotation/grow), baked into one model per slot ("main" in its colours, "cloth"
  greyscale and tinted by the piece's dye) and drawn on every humanoid renderer (players, mannequins, folk, zombies...),
  following the wearer's head, body and limbs by copying the parent model's pose. The flat part of each set is a normal
  equipment asset (`equipment/<set>.json`), so trims and enchantment glint work as on vanilla armour.
- **Kingdoms' folk** (`Outfits`, VERSION 2 so folk already out are dressed again): one realm in `ARMOURY_REALMS` (2)
  arms its guards from its land's set in the realm's colour (desert, badlands, savanna: gladiator; jungle, cherry:
  ronin; elsewhere: paladin), the realm's trim on the chest. A barbarian chief wears the whole warlord set, kilt in
  his camp's colour; `RAIDER_HORNS_CHANCE` (0.3) of raiders wear the horned cap.
- **Second pass** (Andy: "emulating Epic Knights more, cleaner details, depth"): metal shades top-lit with a sheen and
  a crisp rim, leather has stitched edges, flat layers get rimmed faces; and every set has raised 3D rivets and studs
  (`N` in the generator: half-size cubes on a plate's surface, all sharing one UV per material) and ridges, combs and
  bands (`ridge`): the paladin's helm, gorget, pauldron comb, belt and knee bosses; the gladiator's studded brim,
  belt, sword arm and greaves; the ronin's ribbed, gilt-riveted kabuto and shin splints; the warlord's banded cap and
  studded bracers.
- **Third pass** (Andy: smaller paladin helm rivets, more layers): the paladin's great helm narrows in two steps to the
  comb and has a faceplate standing proud of the bowl (brow plate and bevor with breaths, the eye slit between them
  split by a gilt nasal bar); the ronin's do is four lacquered lames stepping out down the chest, laced with raised
  cords and a gilt mon, the sode's lames raised and corded, the kusazuri three stepped lames; the warlord's cuirass is
  layered: a cloth tunic hem, three rows of overlapping hide scales front and back, a dyed sash across; the gladiator
  has leather pteruges tipped with bronze studs. Warlord chestplates now take dye (the sash and tunic).
- **Helmet holes cut through**: the paladin's eye slit and the gladiator's grille holes are transparent, so the
  wearer's face shows through them.
- Seen in game (dev server + client, on mannequins): all four sets, dyed paladins in red, green and white. Not yet
  seen: guards and chiefs dressed from the armoury.

## 0.5.31: goblin villages, and a mask to walk in among them
Andy liked Goblins Tyranny's idea of infiltrating goblin villages. That mod is Forge 1.20.1 only and all rights reserved,
so goblin villages are rebuilt here as a third kind of camp (`Camps.CampData.kind` "goblin"); none of its code or art is used.
- **Villages** (`GoblinVillages`, `GoblinBuildings`): up to one per kingdom (`VILLAGE_CHANCE` 0.12 a roll) in forest, taiga,
  jungle, plains, swamp or meadow, inside a palisade fort: spruce logs 6 high topped with sharpened stakes (placed
  without shape updates so they stand as lone spikes), skull posts, a walkway with ladders up, and two 10-high gate towers
  with a lintel and lanterns between them; slingers keep watch from the tower tops. Mud-brick and mossy-cobble huts under mushroom
  domes, the great hut (the king's throne, a hoard chest of gold, emeralds, golden apples and a spare mask), a tavern, a
  lookout, a junk heap (storage), a mushroom patch, totems and a stewpot. It grows a goblin every 2 days and a slinger every
  4 (most 16) and raises more huts out of its takings. Goblin villages never raid. `/faction camp found goblin` for ops.
- **Goblins** (`Goblins`, roles 30-32): Folk at 0.7 scale. Goblins fight in melee, slingers shoot, the **goblin king**
  (140 health, a boss bar, golden crown and axe) sits on his throne; killing him clears the village like a chief. They
  attack players, villagers, realm folk and wild iron golems. Skins and the ears and nose (`GoblinSkins.Features`, a
  layer over the folk model) are drawn by `tools/gen_goblins.py`.
- **The goblin mask** (`Disguise`, green dye + leather + string, or a 1 in 6 drop from a goblin a player kills): worn with
  nothing but leather elsewhere, goblins take you for one of them. Right-click one for a line of goblin talk.
- **Suspicion** (actionbar meter, 0-100): close to the king (+6 per half second) or sprinting where they see you (+4) raises
  it; calm lowers it. Full, or caught opening a chest or breaking a block where one sees you, and you're **exposed**: a cry,
  every goblin within 40 on you, and that village sees through the mask for 5 minutes.
- **Backstab**: a melee hit on a goblin that isn't after you does triple damage. If it lives, or another sees it die, you're
  exposed.
- Seen in game (dev server + client): a village built in forest with its king on his throne, small green goblins with ears and
  noses, a masked player walked in unharmed, and took the mask off and was killed within seconds. Not yet seen: suspicion
  filling, backstabs, the mask on the player.

### Loot goblins (from Loot Goblins, LGPL-3.0)
Andy asked for the goblins from SkylorBeck's Loot Goblins (Fabric 1.19.2, GeckoLib). It's LGPL-3.0, so its model and skins
are reused and its behaviour ported to 26.3 (package `lootgoblins`, `client.lootgoblins`); the licence and a
notice are in `licenses/lootgoblins/`. Its code here stays LGPL-3.0: keep that source available if the pack is shared.
- **Loot goblins** (`LootGoblin`): a little goblin with a treasure chest, eight looks (plain, nether (fireproof), ender,
  armourer, cake, plant, Easter, witch). Wanders, flees players, sparks. A player kill bursts it into 1-5 of its own treasure,
  a chance of enchanted books, a disc and enchanted gear, and 5-10 generic loot; 100 xp. The GeckoLib model is rebuilt as a
  vanilla model with its idle and run animations.
- **Spawning** (`NaturalSpawnerMixin`, `LootGoblins.CHANCE` 0.0001): about 1 in 10,000 natural monster or animal spawns
  is a loot goblin instead.
- Left out: the minor goblins (ore mobs) and their treasure items (Andy didn't want them), the helmet cosmetics and loot
  beams.
- Seen in game: all eight looks in the original's idle pose and a kill's loot burst.

## 0.5.32: riders sit in the saddle
- **Riders no longer float** (Andy's clip: mounted folk hovered high over their horses). The folk entity had no vehicle
  attachment, so a rider's feet were set on the saddle point and the sitting model hung in the air. It now takes the
  zombie's `ridingOffset(-0.7F)`, which lowers every folk rider: mounted guards and archers, caravan riders, carriage
  and cab coachmen, and a ruler in a carriage or rickshaw seat.

## 0.5.33: quest bosses fight in the dungeon; trail ruins become dig quests
Andy's clip: a king's "The Beast" decree at trail ruins set its ravager down inside the buried ruin, walled in by
earth and roots, and only spectator mode could find it. Andy's ruling: the boss belongs inside the dungeon, in a room
with space, and buried trail ruins should get quests that suit them (more variety).
- **Why**: foes were placed where a 1-block column was free, so a ravager (nearly 2 wide) fit into walls; and trail
  ruins are buried by design, so their "rooms" are pockets in the earth.
- **Room to fight** (`QuestFoes`): a spot must fit the mob's whole body (`EntityType.getDimensions()`, `noCollision`,
  no liquid) on a sturdy floor, and open onto at least `OPEN_SPACE` (40) free blocks, so nothing goes in a sealed
  pocket. Champions and caches go only in a structure's own pieces, never on the ground over it: with no room yet
  loaded, the search waits `RETRY_TICKS` and tries again. Escorts and cache guards check their own size too.
- **Kings** no longer send champions to trail ruins (`LAIRS`); every other lair has rooms.
- **Dig quests**: a villager's fetch at trail ruins (`QuestFoes.DIGS`) buries the keepsake in the suspicious gravel
  nearest the ruin's middle (no guards). The giver lends a brush if you have none; the objective reads "Brush ... out
  of the suspicious gravel"; near the ruin you're told to dig down. `BrushableBlockEntityAccessor` sets the gravel's
  item (and clears its loot table).
- **Testing** (op): `/kingdom quests offer champion <lair>` (the nearest king, a champion at that lair) and
  `/kingdom quests offer dig` (the nearest villager, a fetch at trail ruins).
- Seen in game: a guard's "A Comrade's Blade" at trail ruins buried the sword at 124, 54, 318; brushing it out
  turned the quest to "Return to Prokopios". A Bone Lord decree at trial chambers set the Bone Lord and escort in a
  chamber room at -475, -19, -396.
