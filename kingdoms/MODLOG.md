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

## 0.5.34: Jaymo's Equestrian, stage 1 (riding)
Andy liked SWEM (Forge/NeoForge only) and asked for our own take, built into Kingdoms; the plan doc and Andy's answers
are in project memory (`equestrian-plan`). Nothing is taken from SWEM: original code, model and art (the elephant's look is Mo' Creatures', credited below). Package
`com.jaymoskingdoms.equestrian` (client: `client.equestrian`); tuning numbers at the top of `Equestrian`, `Breed`, `Gait`.
- **Every horse, skeleton horse and zombie horse is upgraded in place** the first time it loads (`ENTITY_LOAD`), so the
  stables', cavalry's, caravans', carriages' and cabs' horses get it too. A horse from before keeps its old speed and
  jump as its stats. Donkeys, mules, llamas and camels are left alone.
- **Breeds** (`Breed`): destrier, courser, palfrey, draft, steppe pony, desert horse (and the war elephant and woolly mammoth), each with
  its stat ranges, size (scale attribute) and build (drawn wider/taller/longer), courage, stamina and top gait. Wild
  horses by biome (`Equestrian.breedAt`): desert/badlands desert horses, savanna ponies and desert horses, cold lands
  drafts and ponies, plains coursers/palfreys/drafts. Undead horses take any horse breed (size and stats), never
  spook, tire at half the rate.
- **Hidden stats** (`Equine`, an attachment saved and synced): speed, stamina, jump, courage, temper (0-1; three dice).
  Foals take one parent's breed, mostly a parent's coat, each stat between the parents' plus a little luck
  (`FOAL_LUCK`). Temper sets how long a horse takes to break in. A tamed horse's inventory screen shows them as bars.
- **Gaits** (`Gait`): halt, walk, trot, canter, gallop on the scroll wheel while riding (the hotbar keeps its number
  keys); sprint is a burst at the gallop. Drafts top out at the canter.
- **Stamina** (`Stride`, saved and synced): galloping drains it, walking and standing refill it (also while unridden);
  run dry, a horse is blown and drops to a trot until it's back to 35%. Coursers tire fast, ponies hardly at all, a
  palfrey's amble (trot and canter) costs nothing, desert horses don't tire in the heat but do in the cold.
- **HUD**: bottom right, above the powers' chip row in Wind's style: breed, gait (or Sprint!/Charge!/Blown), five gait
  pips, the stamina bar with the wind-back mark.
- **Courage**: fire close by, monsters at night, a blow in a fight and explosions (`ServerExplosionMixin`) test a
  ridden horse's nerve; a timid one rears, drops to a walk, and may throw its rider. Destriers and brave horses hold
  steady; steppe ponies never spook.
- **Coats**: 6 per breed (the last rare, 4%), 3 skeleton, 3 zombie, adult and foal, drawn by `tools/gen_equestrian.py`;
  vanilla's markings layer is left off (the markings are painted in).
- **War elephant** (`WarElephant`, `war_elephant_spawn_egg`): tamed, fed, saddled and bred like a horse. It wears the
  elephant of DrZhark's **Mo' Creatures** (GPL-3.0): its model and animation ported to 26.3 (`WarElephantModel`), and
  its Indian, African (big ears) and royal (painted, rare) hides. Saddled, it wears Mo' Creatures' harness and wooden
  war platform (the howdah) with our banner. At the canter and faster it tramples monsters (and whatever targets its
  rider); sprinting is a trumpeted charge. Desert realms' stables keep one, and a wealthy desert realm puts a guard on
  one (`Cavalry.ELEPHANTS_ARID`, 20 emeralds).
- **Woolly mammoth** (breed `woolly_mammoth`, the same entity): bigger and tougher, tires at half the rate in the cold
  and much faster in the heat (`MAMMOTH_COLD_DRAIN`, `MAMMOTH_HEAT_DRAIN`). Mo' Creatures' mammoth hide (domed head,
  shaggy skirt), plus russet and a rare frost coat tinted from it. Snowy realms keep and ride mammoths where desert
  realms keep elephants; a spawn egg used in the snow hatches a mammoth. The hides are built by
  `tools/gen_elephant_hides.py` from Mo' Creatures' originals in `tools/mocreatures/`; the licence and a notice are in
  `licenses/mocreatures/`, and `WarElephantModel` stays GPL-3.0.
- **Kingdoms**: carters, coachmen and cab drivers drive drafts; guards ride destriers, archers coursers (desert horses
  in desert realms). New stables: a village's palfreys and drafts, a realm's destriers and coursers (desert horses,
  steppe ponies or a draft by climate).
- **Death**: a tamed horse that dies leaves a lock of its mane (`mane_lock`, named for it, with its breed, coat, stats
  and owner in its data, for a necromancer later). No revival yet.
- **Testing** (op): `/kingdom horse <breed>` gives a tamed, saddled mount of that breed.
- Seen in game (cloud playtest): elephants and mammoths render; riding not yet tried.

## 0.5.36: Mobs work themselves loose
Andy: mobs too often get stuck pathing. AI Improvements (suggested) is NeoForge-only and a performance mod, so this is
our own. `Unstick` (tuning at the top), run from `PathNavigationMixin` after every navigation tick, covers every mob:
vanilla, MCA villagers and our folk.
- Vanilla's stuck check resets whenever a goal asks for its path again, and chasing or wandering goals ask every few
  ticks, so a mob snagged on a corner stayed snagged. `Unstick` watches how far the mob has moved across instead.
- No headway for 1.5 s: a hop toward its next step (frees it from slabs, carpets, fence-post corners); again every
  1.5 s while it stays stuck. 3.5 s: a nudge back to the middle of its block and a fresh path. 7 s: the walk is
  dropped so its AI picks something else.
- Our folk don't give up: out of a fight, not breaking in on a raid and not on a caravan, after 12 s they turn up
  where they were going in a puff (as builders, farmers and gatherers already did), which now also covers guards,
  escorts, banner defenders, firefighters and undertakers.

## 0.5.37: sorcery (necromancers, shamans, court wizards)
Andy asked for Iron's Spells 'n Spellbooks to fuel necromancy and wizards for the kingdoms and (more so) the barbarians.
Iron's Spells is Forge/NeoForge 1.21.1 only and all rights reserved (no ports, no reuse of its art), so this is our own
take: original spells, art and names (`Sorcery`, `Thralls`, `Arcana`, client `SorcerySkins`, `tools/gen_sorcery.py`).
- **Camp shaman** (`Sorcery.SHAMAN`, every barbarian camp, a second from day 5): fire bolts; a ring of fire when cornered;
  a hex on a player (weakness, slowness); a war chant (strength and speed for the raiders fighting near him, a drum and a
  shout); mends the worst hurt. War paint and a wolf-skull headdress in the camp's colour, a totem staff.
- **Camp necromancer** (`Sorcery.NECROMANCER`, from day 2; at once in a Grim ruin): life drain; raises the dead (Kitchen
  corpses lying near first, not players'; else a zombie and a skeleton of his land's kind out of the ground); bone spikes
  at anyone close; and whatever dies near him in a fight rises as his thrall (villagers as zombie villagers, horses as
  zombie horses, men and folk as zombies). Up to 5 thralls; they wear a grey rag (no sunburn) and crumble after 90-120 s
  or when he falls. Hood, black robe, skull clasp, bone staff.
- **Court wizard** (`Sorcery.WIZARD`; a second over 300 emeralds). Not every kingdom believes in magic: about 60%
  (`MAGIC_BELIEF`, fixed per realm) keep a court mage; the rest keep none and prosecute magic: reading a spell scroll
  inside their walls is a crime like arson (`Theft.Crime.SORCERY`: "Witchcraft! Not within these walls!", the guards
  after you), for their own people too, short of the ruler. Arcane bolts (double on the undead), a
  ward (absorption) on the guards fighting near him, mending, a blast that throws back anyone close, and he banishes
  enemy thralls. Robe in the realm's colour, a beard, a pointed hat (its own model), a crystal staff. A **tyrant** king
  keeps a court necromancer instead (his dead spare the realm's villagers and players it doesn't hold hostile).
- **Thralls** (`Thralls`, attachment `thrall`): never target their own side, and their side's folk leave them be
  (`ThrallTargetMixin` on `LivingEntity.canAttack`).
- **Scrolls** (`Arcana`): a player who kills a caster takes a scroll: Raising (two thralls for a minute that fight only
  monsters and whoever fights you), Draining, Fire, Warding, the Arcane Bolt. Read once (right-click).
- Casters are hired like any other hand: `Sorcery.staff` opens places for them, `Economy.hire` fills them.
- Testing (op): `/kingdom caster <necromancer|shaman|wizard>` (one for the nearest faction, at your feet).
- Seen in game: the three side by side; a shaman's fire bolts set a court wizard alight while a necromancer's drain and
  his raised zombie and skeleton closed in; staffs held upright.

## 0.5.38: skin tones by climate
Andy: villagers in hot, desert climates should be darker-skinned, and the opposite in cold biomes. MCA already leaned
melanin on the biome's temperature, but one villager in five was an "immigrant" whose skin ignored the land. Andy then
asked to keep both far-off immigrants and nearby newcomers, and have them mention it in conversation. `SkinTones`
(tuning at the top), applied by `mixin/mca/McaGeneticsMixin` after MCA randomizes a newly spawned villager, re-rolls the
melanin gene from where the villager's family comes from:
- **Natives** (about two in three): a lean on the biome's heat: about 0-0.2 in the snow, 0.25-0.55 on the plains, 0.75-1
  in the desert, savanna and badlands (0 pale, 1 dark), a little paler high up.
- **Newcomers** (15%): from a neighbouring climate, up to 0.9 biome temperature away.
- **Immigrants** (20%, MCA's own share): from a far land, at least 1.0 away (a desert family in a snow town).
- Newcomers and immigrants remember their homeland (`jaymoskingdoms:origin` attachment: desert, jungle, plains, taiga or
  snow). Choosing Chat with them, 30% of the time they talk about it instead of MCA's small talk
  (`mixin/mca/McaDialogueMixin` on `Dialogues.selectAnswer` for `chat.topic`; lines `dialogue.chat.jaymoskingdoms.*`).
- Children still inherit from their parents (MCA doesn't randomize them), and villagers already in the world keep their
  skin. Our own folk (guards, lords, kings, builders) already wear climate faces (`Climate.skinGroup`).

## 0.5.39: the townsfolk come to you
Andy: villagers should be able to confront you with questions, quests and start all kinds of interaction.
`Approaches` (tuning at the top; packets `TalkNet`, client `TalkScreen`): every so often one of a settlement's people
who can see you (an MCA or plain villager, a child, a realm's guard) walks over and says something over their head
(`Speech`). When they're asking, right-click them to answer; walk off and they take it as they will.
- Paced: one approach per player every 1.5-4 minutes, the same villager once a day, a guard halts a stranger once a day
  per settlement; never mid-fight, from workers at their work, or from people who hate you (their guards hunt you).
  By night only guards come over.
- What they come about suits who they are and what they know of you:
  - Quests: a giver with work (the gold !) offers it; one whose errand you carry asks how it goes. Right-click opens
    their quests.
  - A crime of yours they saw or heard of (`Theft` tells `Approaches.suspect`; held two days): confess and pay a
    5-emerald fine (reputation back), deny it (believed about half the time, more if you're sworn), or threaten them
    (they run off crying for the guards). Walking off counts against you.
  - A guard halts a stranger: trading (fine), looking for work (sent to the nearest giver with a !), or none of their
    business (reputation down). A guard warns you indoors at nightfall.
  - A stranger's questions (8: where you're bound, what you make of the place, can you read...), answered warmly
    (hearts up), plainly, or rudely (hearts and reputation down), each with its own reply.
  - A hungry villager asks for food (any plain food in your pack); a craftsman offers a bargain from their trade
    (bread, cod, pork, bookshelves, bottles o' enchanting, iron, a sword, a pickaxe, a compass, arrows, wool, leather,
    bricks; a friend gets an emerald off); a friend (40+ MCA hearts) brings a small gift.
  - A realm at war asks if you'll stand with it; gossip; a friend greets you by name.
  - Children: tag (they run off laughing), "are you a knight?" (you're armoured), "can I see your sword?".
- Testing (op): `/kingdom talk [topic]`: the nearest who could comes over now (topics: quest_offer, quest_check,
  accuse, halt, question, hungry, bargain, gift, war, gossip, greet, child, warn).

## 0.5.42: golems keep quiet and fight fires
Andy: iron golems should not be able to speak, and they should beat the fires out instead of watching them burn.
- `Speech.say` never lets an iron golem speak. A crime seen only by a golem gets its groan and no words; if a villager
  or folk saw it too, they cry out instead (`Theft.commit`).
- `Firefighting`: iron golems near a watched fire (and not fighting anyone) stride over and beat it out with an arm
  swing (`GOLEM_SPEED`), no water and nothing knocked out.

## 0.5.43: no rainbow hair, no Homosexual trait
Andy: turn off rainbow hair, and the Homosexual trait. Two MCA sources, both off pack-wide in code (no config file to ship or forget):
- **Rainbow Hair trait** (MCA rolls it for about one villager in twenty): `mixin/mca/McaTraitsMixin` makes
  `Traits.hasTrait` answer no for `Traits.RAINBOW` and drops it from `getTraits`, so villagers already in the world lose
  it too and show their natural hair colour. Rainbow Eyes is untouched.
- **Homosexual trait**: hidden the same way by `McaTraitsMixin` (it never counts and leaves trait lists), for villagers
  and players alike. Bisexual and Asexual are untouched.
- **Dyed hair at spawn** (MCA's `coloredHairChance`, 2% of new villagers with pink/blue/green heads):
  `mixin/mca/McaConfigMixin` forces it to 0 whenever MCA's config is read. Villagers already dyed this way keep it,
  since a random dye can't be told from one a player chose.

## 0.5.44: Jaymo's Equestrian, stage 2 (bond and tack)
Stage 2 of the equestrian plan (project memory `equestrian-plan`): a horse comes to know its riders, learns by being
ridden, needs looking after, and wears real tack. Package `com.jaymoskingdoms.equestrian` (`Bonding`, tuning numbers at
the top; `Bond`, `Tack`, `Discipline`, `Tackroom`, `TackMenu`), client `TackScreen`, `TackLayer`; art by
`tools/gen_tack.py`.
- **Trust** (0-100, per player, saved and synced in `Bond`): a new owner starts at 30 (an owner from before 0.5.44 at
  50). Brushing (vanilla brush, once every 2 minutes; the coat shines for a day), feeding, a water bucket, picking its
  hooves and easy riding raise it; sprinting (the spurs), going hungry or thirsty, standing in the rain without a
  blanket and being hit by its own people lower it. Under 20 it bucks now and then, so a stranger on your war horse
  soon ends up on the ground. From 60 it comes when you whistle (N): your nearest horse within 96 blocks.
  From 40 its stats show on its inventory screen; before that it's "wary".
- **Disciplines** (`Discipline`, levels 1-5 at 0/100/300/700/1500 xp): trail (blocks walked or trotted: +8% stamina a
  level), racing (blocks cantered or galloped: +2.5% top speed), jumping (jumps: +0.04 jump strength), war (blows struck
  and taken while ridden: +6% courage). A hungry horse stops learning. Level-ups are announced with a chime.
- **Tricks** (J; hold back for the kick): the rear at level 2 in anything (monsters in front are knocked back and back
  off), the sliding stop at racing 3 with a bridle (from the canter or gallop straight to a halt), the side-pass at
  trail 3 (full sideways speed at the walk), the war kick at war 4 (both hind hooves, 7 damage, at whatever's behind),
  and kneeling: a rider in 20+ armour can only mount a horse at war 3, trail 4 or 80 trust, and it kneels for them.
- **Needs** (only for player-owned living horses; kingdoms' horses are left alone): hunger and thirst fall slowly
  standing and faster with every gait (a destrier, draft, elephant or mammoth eats more, a pony less; cold without a
  blanket makes it eat more, heat makes it drink more, desert horses hardly drink). Left alone it grazes on grass,
  eats from a hay bale next to it and drinks from water or a full cauldron next to it; rain waters it a little. Feeds:
  wheat, sugar, apples, carrots, hay bales, oats (8% from grass; also a quarter of its stamina back), golden food
  (golden food still goes to vanilla too, for breeding). Under 20 it's hungry (stops learning) or thirsty (tires twice
  as fast); empty, it runs 15% slower and holds it against you. Two small bars over the stamina bar while riding, and
  under the crosshair when you look at a horse of yours.
- **Hooves**: unshod hooves wear on stone, gravel, paths and the like (8% slower there; under 25 it's lame, 20% slower
  anywhere) and grow back slowly at rest; a hoof pick (96 uses) restores them. Horseshoes (4 from 4 iron) put on with a
  right-click: shod, it runs 5% faster on dirt paths and wears its shoes instead.
- **Tack screen** (the Tack button on the horse's own screen): saddle, bridle, blanket and barding slots, the horse,
  and a panel with your trust, its four disciplines and its hunger, thirst and hooves (or shoes).
  - War saddle (leather and iron): its high cantle keeps you on through blows (no throws from being hit).
  - Racing saddle (leather and string): 10% faster, but a fall of 3.5+ blocks at the canter or faster may pitch you off.
  - Bridle (leather, iron nugget, string): without one a horse at the canter or faster turns at most 12 degrees a tick;
    the sliding stop needs one.
  - Horse blanket (wool and leather, dyeable): warmth in the cold, keeps the rain off.
  - Chain barding (armour 4) and plate barding (9, toughness 1); leather is vanilla's horse armour. Saddlebags stay
    Jaymo's Kitchen's (body slot).
  - Drawn in 3D on horses, skeleton and zombie horses: the war saddle (deep seat, high cantle, red cloth, stirrups), the
    racing saddle, the black-and-brass bridle, the blanket in its dye, shoes.
- Death keeps the mane lock; the bridle and blanket drop.
- **Mo' Creatures horses** (Andy: "these horse models are not gonna cut it"): every horse, skeleton horse and zombie
  horse is now DrZhark's Mo' Creatures horse (GPL-3, credited in `licenses/mocreatures`), ported to 26.3 as
  `MocHorseModel` and drawn by `MocHorseRenderer`: jointed legs (the cannon bends as the hoof comes forward), the head,
  neck and mane moving together, the mouth opening to eat, the tail lifting with speed; vanilla's rear and graze blend
  in. Foals are the same horse at 55% size.
  - Coats: our 36 breed coats are painted onto Mo' Creatures' white horse and shaded with it, so every coat keeps its
    eyes, nostrils, mouth and hooves (`tools/gen_moc_horses.py`, specs still in `tools/gen_equestrian.py`). Skeleton
    horses wear its skeleton (bone, sooty, mossy), zombie horses its undead horse (rotten, pale, mottled).
  - A vanilla saddle is its saddle, stirrups, headstall, bit rings and reins (reins while ridden); the war and racing
    saddles, the bridle, blanket and shoes are ours on its bones. Its headstall shows with any saddle unless the horse
    wears a bridle.
  - Horse armour: its metal (iron), gold and diamond armour as an overlay over any coat; copper, netherite, leather
    (in its dye), chain and plate barding recoloured from the metal one. Other mods' horse armour shows as iron.
  - The old vanilla-layout coats (`textures/entity/equine/`) are gone.

## 0.5.45: orc war camps
Andy linked The Orcs! (CurseForge 976768). It stops at 1.20.4 and is all rights reserved with no source, so this is our
own take, built on the camps' machinery like the goblin villages: nothing of it is used (looks, names, camps original).
- **Orc camps** (`OrcCamps`, camp kind `orc`): one may rise near a kingdom in rough country (savanna, badlands, taiga,
  dark forest, hills, plains, meadow, snowy plains), not on peaceful. A dark oak stockade on blackstone, sharpened stakes,
  bone posts crowned with skulls, gate towers with red war banners; inside (`OrcBuildings`) the warchief's longhouse
  (black hide roof, a red aisle to his blackstone throne between bone pillars, his hoard behind it), hide tents, a forge
  (blast furnaces, anvil, a lava trough behind bars), a watchtower, the plunder pile (its storage), a fighting pit,
  trophy racks, spike totems, a roasting spit and war drums by the fire. It grows daily: more orcs (every fourth a
  ranger, up to 15) and the next of 7 tents, racks and totems.
- **Orcs** (`Orcs`, roles 50-53), a man's height but drawn 16% broader, grey-green with tusks, a heavy brow and swept
  ears (`OrcSkins`, skins by `tools/gen_orcs.py`):
  - Grunts (34 health, axes and cleavers, hide or mail; they throw javelins): below 35% health a blood rage (strength
    and speed); war cries in a fight.
  - Rangers (bows) on the watchtower and the gate towers.
  - A shaman (a second once the camp is 5 days old): `Sorcery`'s shaman spells (war chant, mending, fire bolts, a ring
    of fire, hexes), and a fire scroll now and then when slain.
  - The warchief (220 health, 1.35x size, netherite axe, a red boss bar, the usual rally): a ground slam every 8 s at
    anyone within 3.5 blocks (8 damage, thrown back).
- They hunt players, villagers, golems and every settlement's folk (other camps' folk only if they start it); realms'
  fighting folk hunt them back, and villagers flee them.
- **Raids**: orc war bands (grunts, every third a ranger) raid the nearest kingdom like a barbarian camp's, to drums
  ("Drums in the hills! The orcs of X march on Y!"). Raiders' deaths are now undone on the roster by their own role.
- Killing the warchief clears the camp (every settlement thanks you); its hides, fences, bones and skulls rot, the
  stockade stays as a ruin. A lost warchief is replaced after 2 minutes. `/faction camp found orc` for ops.

## 0.5.46: your own blocks are no crime
- Andy: breaking your own items in a village made the villagers take you for a criminal. Now every block a player
  places from an item is remembered as theirs (`Placed`, a per-chunk attachment keyed by position, with the block it
  was, written by `BlockItemMixin` on `BlockItem.place`'s `setPlacedBy`). `Theft.broke` lets that player break it
  (a bed's head counts as its foot's), and `Theft.opened` lets them open a chest they placed, without a crime. The
  mark is dropped when the block breaks, and only matches while the same block stands there, so a block the
  settlement later puts in that spot is the settlement's again. Other players' blocks and settlement-built ones still
  count as before.
## 0.5.47: Jaymo's Equestrian, stage 3 (the stables at work)
- **Stablehand and farrier** (`Stables`, `Folk.STABLEHAND`/`FARRIER`, MCA professions `jaymoskingdoms:stablehand` and
  `jaymoskingdoms:farrier`): any settlement with a standing stable takes on a stablehand (the day the stable's finished),
  and a farrier once it has 8 people. They work about the stable going from horse to horse, brushing them or picking
  their hooves, and go home at night. Someone the settlement hates gets nothing.
- **Stable screen** (`StableNet`, client `StableScreen`): right-click a stablehand or farrier (sneak and right-click
  an MCA one, whose plain click is MCA's own screen), or use `/kingdom stable` / `/kingdom farrier` beside them. The
  stablehand's screen lists your horses nearby (Board, Sell), those boarded here (Take out, with the fee owed) and
  today's horses for sale (breed, coat, stats, Buy); the farrier's lists your horses' hooves and shoes (Shoe) and the
  whole tack shop, the wares your kingdom is too poor for greyed out. What a button did shows under the list; long
  lists scroll; a Quests button opens their quests. Walking up to one, they greet you with a click that opens it.
- **Boarding:** the stablehand stalls your horse by one of the stable's hay blocks (the one you ride, lead, or your
  nearest). It's fed, watered and brushed while it's there, kept in its stall, and can't be ridden or whistled off.
  The fee is 4 emeralds for each week begun, into the treasury when you take it back out ("Take ... back out", which
  sets it down beside you). A boarded horse whose stable is ruined walks free.
- **Horse market:** the stablehand sells 2, 3 or 4 horses a day (poor, prosperous, wealthy), the same for everyone till
  tomorrow. A poor stable (every village and fief) sells palfreys, steppe ponies and drafts with middling stats at best;
  richer kingdoms sell their land's horses (desert horses in the desert, ponies on the savanna, drafts in the snow) and
  coursers, a wealthy one destriers too, each stat the better of two rolls. Price: the breed's (destrier 24, courser 22,
  desert 18, palfrey and draft 12, pony 10) from half to one and a half times by quality. Bought horses come tamed to
  you. The stable buys yours back for half its price (a foal 60% of that) if the treasury has it; its tack comes back
  to you and it becomes one of the realm's horses (a spare for its cavalry).
- **Farrier:** shoes your horse for 3 emeralds (fresh shoes, hooves trimmed), boarded or not. Tack shop: horseshoes, hoof pick, oats,
  lead, saddle and bridle anywhere; a brush, a blanket in the realm's colour, the racing saddle and chain barding in
  prosperous kingdoms; the war saddle and plate barding only in wealthy ones.
- **Cavalry:** a realm with stables mounts its guards and archers on war saddles with a blanket in its colour; for each
  standing stable (up to 3) the horse is better (each stat the better of two rolls), a little faster, and more trained
  in war (level 2, 3, 4); with two or more stables they wear a bridle and chain barding (plate in a wealthy realm).
- **Knights' destriers:** a mounted guard, archer or ruler cut down by a player leaves his horse wild where it stands,
  tack and war training kept, for whoever breaks it in. Caravans' horses still go home with the caravan.
- **Raiders:** every other barbarian raider rides a steppe pony (saddled, no barding); kill him and the pony bolts free.
- **Saddlebags:** sneak-use a caravan horse with an empty hand to rifle its saddlebags (a few emeralds and goods, once a
  horse). It's theft (`Theft.Crime.SADDLEBAG`): seen, the caravan's realm cries thief.
- **Horse quests** (`Quest.BREAK_IN`, `STUD`, `RACE`): a stablehand mostly asks for a wild horse you broke in yourself
  (not one bought at a stable) or a stud, a horse of a given breed at 45-65% quality or better; either is handed over
  to the stable when you turn it in. A king with a stable sometimes wants a horse broken in for him, or sends you on a
  race to another settlement 150-900 blocks off (allowed: distance at 6.5 blocks a second plus 40 seconds): ride in
  mounted before time's up, then go back to him. Stablehands and farriers also ask for feed, iron, coal and leather.
  Operators: `/kingdom quests offer horse` beside a stablehand or king.

## 0.5.48: iron golems drop corpses into the fire
- Andy: "iron golems should pick up and drop the corpses into flame". Since 0.5.23 only a registered village's golems
  did it, only for monsters, and only with a fire pit, lit campfire or lava within 16 blocks of the corpse, so in
  practice it hardly ever happened. Now every iron golem does it, wherever it is (a village's, a kingdom's, one a
  player built at home): a golem that isn't fighting picks up a corpse within 24 blocks of it, carries it in its arms
  to the nearest fire pit, lit campfire or lava within 24 blocks of the corpse, walks right up to it and drops it in
  (`Undertakers.golems`). Monsters' corpses at once; any other (an animal, a villager) once it has lain untouched 5
  minutes (`SETTLE_TICKS`), so a kill you mean to butcher is yours first. Never a player's corpse, nor a heavy golem
  corpse. Golems still never light a fire: with none near, the corpse is left (tried again a minute later).
- Folk guards keep clearing monster corpses as before (they may still light one where it lies).
- Firefighting no longer beats out a fire kept on netherrack (anything that burns forever), so a fire pit stays lit
  for the golems; and anyone sent to a fire puts down the corpse they were carrying first and is left to the fire for
  10 seconds (`Undertakers.calledAway`).
- Needs Jaymo's Kitchen 0.4.31 (golems hold the corpse up at their arms, not at their knees; older Kitchen works but
  looks wrong, and before 0.4.27 golems leave corpses alone).
## 0.5.49: Quests are rare

- **One villager in twenty:** only about one villager in twenty is ever a quest giver (fixed by who they are, so the
  same few in each settlement are the ones with work), and they have work on about half their days
  (`Quests.GIVER_SHARE`, `OFFER_CHANCE`). Everyone else never wears the gold !.
- **Kings seldom:** a king has work on about one day in twelve (`Quests.KING_CHANCE`), not every day.
- **A good name helps a little:** reputation with the settlement raises both, up to one villager in ten and a king's
  work on one day in five at the best reputation (`GIVER_SHARE_REPUTED`, `KING_CHANCE_REPUTED`). The day's roll is made
  for whoever is near when it's first made. Operators can still `/kingdom quests offer` anyone.
- **Giving up costs you:** giving up a quest (the giver's Give up button, or `/kingdom quests abandon`) costs 10
  reputation with the giver's settlement, 40 for a king's decree (`Quests.ABANDON_REPUTATION`, `ABANDON_ROYAL_REPUTATION`).
- **Traveller's Journal** (`Journal`, `JournalClient`, `JournalScreen`): craft a book with a map (empty or filled).
  With it anywhere in your inventory, M (rebindable, "Open Traveller's Journal") or using it opens three pages:
  **Quests** (each quest, who gave it, its story, what's left and which way it lies), **Map** (the land about you
  drawn like a map from the chunks you have loaded, blank parchment beyond, zoom 1-32 blocks a cell with +/- or the
  mouse wheel; you as an arrow, your quests numbered (gold, red for decrees, green when ready to turn in, then
  pointing home), settlements in their colours; hover for names and reputation) and **Reputation** (each settlement
  within 3000 blocks or that knows you: its word and a bar from -1000 to 1000). Without a journal, M only says how to
  make one.

## 0.5.50: pickpocketing
- Andy asked for pickpocketing, then for it to be held: "shift right clicking and holding, entering an animation where
  you get close attaching your hand for a random duration up to 3s, and if you are able to match speed with the target
  or otherwise manage to stay behind them for the duration the pickpocket is successful. it increases in duration
  dependent on the light level, armor the target is wearing, and target's mood. crying villagers wont be as perceptive".
- `Pickpocket`: sneak up within 2 blocks behind a grown villager (MCA's included) or any of a settlement's folk short
  of its ruler (their body facing away: over ~100° from the line to you; a sleeping mark from any side), empty handed,
  and hold use on them. It runs before `Quests`, so sneak-using a plain villager from the front still opens its quests.
- The hand stays in for `holdTicks`: a random 1-3 s, times light (x0.6 pitch dark to x1.4 full light), armour (+3% a
  point), an MCA villager's mood (-15..15, 2.5% a point: a depressed, crying one notices far less), x0.6 if they're
  panicking, x1.25 for a guard, x0.5 asleep. Holding use repeats every 4 ticks; no repeat for 8 ticks and you've let
  go (no crime). A progress bar shows on the action bar.
- Stay behind them (follow them as they walk) and within 2.75 blocks till it's done: you lift half a stack of something
  a villager carries, else a pocket's worth (emeralds 1-3, gold or iron nuggets, bread, an apple, string, paper,
  flint) or nothing. A picked pocket is empty for a day. Fall out of reach and they slip away (no crime).
- If they turn toward you (body more than ~84° round) while your hand's in, you're caught: they cry thief; where a
  faction's law holds that's `Theft.Crime.PICKPOCKET` (medium) seen: reputation -35 and its guards after you. Done clean,
  anyone else who sees you is still a witness (the mark doesn't count); unseen, it may be noticed later like any other
  theft. Outside any faction's law the mark only shouts.
- The reach is seen by everyone nearby: `Pickpocket.Reach` tells them, and the client's `PickpocketPose` puts the
  player's main arm forward and down into the pocket, fingers working, the other hand drawn in.

## 0.5.51: gossip about butchers
- With Jaymo's Kitchen 0.4.32, half the gossip townsfolk walk up with is about a butcher (`BUTCHER_GOSSIP_CHANCE`):
  trash talk or praise for their own village's butcher, or word of a master far off, or one to avoid, with how far
  and which way. Hearing it tells you that butcher's skill on Kitchen's butcher screen from then on. Reached through
  `KitchenCompat.butcherGossip` (reflection on Kitchen's `Butchers.gossip`); without Kitchen, gossip is as before.

## 0.5.52: the skill tree (Survival, Stealth, Strength)
- Andy asked for stealth that improves the more you steal successfully, as a skill tree with three pillars:
  Survival, Stealth and Strength; then for all three pillars ("we can fix later"), a respec at a court wizard or by
  drinking a bottle o' enchanting, and an alignment other players can see that villagers gossip about.
  Plan: `docs/Skills-Plan.md`.
- `Skills`: a player attachment (`jaymoskingdoms:skills`; saved, kept through death, synced to its own player) with
  experience per pillar and ranks per perk. Levels 0-15; level l → l+1 takes 30 + 15·l XP (2025 XP to 15). Each level
  is one point to spend in that pillar, and each pillar's six perks cost 15 points, so a master has all of them.
  **K** opens the tree (`SkillScreen`); a perk button spends a point (`Skills.Spend`, checked on the server). Perks
  that are attributes (`Skills.modifier`, ids `jaymoskingdoms:skill_*`) are put back on join, respawn and every change.
- **Stealth** (`Skills`, `Pickpocket`, `Theft`). XP: a pocket picked clean +10 (+2 empty; a guard's ×2, a sleeper's
  ×½; one already picked today gives nothing), a theft nobody saw +4 at most every 30 s. Practice: the hold
  (`Pickpocket.holdTicks`) 3% shorter per level. Perks: Light Fingers (5: hold 8% shorter each), Deep Pockets (3: +15%
  of a carried stack and a reroll on an empty pocket each), Soft Step (3: sneaking witnesses see 10% of their range
  less each, 50% → 20%), Quick Hands (lvl 5: reach in from the side, ~70° round, and the mark must turn to ~53° of
  facing you instead of ~84° to catch you), Slip Away (lvl 5: half the time you're caught, they can't tell who: a cry,
  no crime), Ghost (2 points, lvl 10: guards take no longer than anyone, ×1.25 gone; unseen thefts never noticed).
  Fully practised with everything the hold is about a third as long.
- **Survival** (`SurvivalSkill`). XP: a ripe crop or nether wart broken +1, every 2 hunger points eaten back +1, a
  night lived through in the Overworld without dying +15, taking damage and living +1 per 4 damage (at most every
  5 s). Practice: hunger drains 2% slower per level (`PlayerExhaustionMixin` on `Player.causeFoodExhaustion`; this
  also lightens powers' hunger costs). Perks: Forager (5: 8% chance each of an extra harvest), Second Wind (3: under
  30% health, Regeneration 2.5 s per rank, every 20 s at most), Weathered (3: burning 20% shorter each, `BURNING_TIME`;
  powder snow freezes 25% slower each), Iron Stomach (lvl 5: Hunger never takes hold), Hardy Landing (lvl 5: fall
  damage ×0.7 via `FALL_DAMAGE_MULTIPLIER`, `ADD_MULTIPLIED_TOTAL`, so a power that takes it to nothing keeps it at
  nothing, and Gravity's own modifiers are untouched), Last Stand (2 points, lvl 10: once a day a killing blow leaves
  you on half a heart with a moment of Regeneration II; not against /kill or the void).
- **Strength** (`StrengthSkill`). XP: a melee blow landed +1 (at most every half second), a monster killed +4, stone,
  ore or wood broken (hardness ≥ 1.5 or logs) +1 a quarter of the time. Practice: +1% melee damage per level
  (`ATTACK_DAMAGE`). Perks: Heavy Blows (5: +4% each), Brace (3: +0.1 knockback resistance each), Bulldozer (3: +0.25
  attack knockback each), Quarryman (lvl 5: break blocks 25% faster), Packhorse (lvl 5: hunger drains 25% slower),
  Titan's Grip (2 points, lvl 10: a full-strength blow staggers its target 1 time in 5: Slowness III and Weakness, 2 s).
- **Respec** (`Skills.respec`): every point comes back, levels and XP stay. Sneak and use a bottle o' enchanting to
  drink it, or right-click a kingdom's court wizard, who offers it for 5 emeralds + 1 per rank learned
  (`Skills.wizard`; the answer comes through the talk screen, `Approaches.answer` hands it over).
- **Alignment** (`Renown`): your best pillar once it's level 5 and ahead of the others (Survivor, Rogue, Warrior;
  "Master" at 15). It shows over other players' name tags (`SkillTitles`, in the pillar's colour; hidden while they
  sneak), on the skill screen, and is synced as `jaymoskingdoms:alignment`. A settlement whose people see you (as
  `Theft.witnesses`) learns it (`jaymoskingdoms:renown`, saved); each day word may travel from each settlement that
  knows you to one more (50%). Their gossips talk about you half the time (`talk.jaymoskingdoms.gossip.you.*`), and
  where you're known for a Rogue pockets take 20% longer to pick.
- Butchering: any rank of Forager, the first Survival perk, makes you experienced with butchering (Kitchen 0.4.32's
  `Butchers.setExperienced`, set by reflection in `KitchenCompat.skills`): you judge a butcher's skill from their cuts.
- A gossip who's heard of you talks about you first; otherwise half of gossip is about butchers, as before.
- Tuning constants are at the top of `Skills`, `SurvivalSkill`, `StrengthSkill` and `Renown`. Not playtested in game.

## 0.5.53: court wizards are MCA villagers, taken from the elders
- Andy: "court wizards should be integrated as MCA villagers, maybe once they grow old". With MCA loaded, a kingdom's
  court wizard (`Sorcery.WIZARD`) is one of its own villagers, promoted in place as workers are (`CourtWizards`,
  `McaBridge.enlistWizard`): they keep their name, family and hearts, take the profession `jaymoskingdoms:wizard`
  ("Court Wizard"; MCA dresses them in the cleric's robe, `McaSkinMixin`), hold the wizard's staff and wear the
  wizard's hat (`SorcerySkins.VillagerHat`, a layer on any humanoid renderer, keyed off the synced profession; blue,
  since the client doesn't know the realm's colour).
- MCA has no old age, so a villager's age is counted from when they came of age (`Enlisted.grownSince`, saved as
  `jaymoskingdoms_grown_since`; noted every second in `Workers.step`). Adults first seen are backdated 0-16 days so
  villages have elders from the start; children are stamped when they grow up. Grown 8 days (`ELDER_DAYS`) is an
  elder, and the eldest spare one (not the ruler, not at work, not a guard or anything MCA keeps) takes the office.
  No elder to spare: the place stays open and is tried again with the roster check (`Sorcery.STAFF_TICKS`).
- The wizard lives as an MCA villager (talks, sleeps, keeps home) until a foe of the realm is in reach and sight:
  then their fight replaces their brain (`CourtWizards.step`): they close in or back off to 7 blocks and cast every
  2 s (repulse up close, banish thralls, else arcane bolts); they ward their side's fighters and, idle or not, mend
  the worst hurt of the townsfolk, folk and golems near. Foes: players the realm counts hostile (or who just hit them),
  barbarians and folk of realms at war, monsters, anything going for their side. A player who kills one takes a
  scroll. Their spells are `Sorcery`'s, generalised from `Folk` to any caster.
- A kingdom no longer hires the standalone wizard while MCA is loaded; a standing one retires in a puff of sparks
  (`CourtWizards.retire`) and an elder takes the place. Without MCA nothing changes. Court necromancers stay folk.
- `/kingdom caster wizard` with MCA makes the grown villager nearest you (8 blocks) the nearest kingdom's wizard, old or
  not. A wizard's profession on anyone a kingdom didn't make one is taken off again.
- `Thralls.refuses` treats a realm's MCA villagers in its service (wizards, guards) as its folk, so they spare the
  thralls of their own side and of players the realm doesn't count hostile.
- The skill tree's respec works with either kind of wizard (`CourtWizards.is`; `Skills.wizard` and its answer take any
  living entity): right-click a folk wizard as before, or sneak and right-click an MCA wizard (plain right-click still
  opens MCA's menu; from behind with an empty hand it's still a pickpocket).
- Not playtested in game.

## 0.5.54: people grow old and die
- Andy: "npcs grow old and die". Built on 0.5.53's age count (`CourtWizards.grownDays`, now on an `Aging` interface
  both MCA villagers and `Folk` implement). Everyone ages: every MCA villager (kingdom or not, spouses and friends
  too), and the folk of realms (kings, lords, guards, archers, standalone casters; saved as `grown_since`, first seen
  backdated 0-16 days like villagers). Camps' barbarians, orcs and goblins don't.
- `OldAge` (tuning at its top): grown 8 days (`CourtWizards.ELDER_DAYS`) is an elder: MCA hair dyed grey (left alone
  if someone dyed it themselves) and 10% slower. Each has their own lifespan, 24-36 days grown (`OLD_AGE_DAYS` +
  up to `LIFESPAN_SPREAD`, from their UUID); their last 6 days their hair is white and they're 20% slower.
- When their time comes they die asleep in bed; one who never lies down dies at night (wherever they are) once 2 days
  past it. Never while fighting, riding, just hurt, or talking to a player. The death is quiet (no scream, a few
  rising sparks and a soft chime) through the damage type `jaymoskingdoms:old_age` ("X died peacefully of old age",
  in MCA's village records too). Jaymo's Kitchen leaves their corpse; MCA does the rest as for any death (grave,
  family and neighbours mourn, a widow or widower).
- A king or lord who dies so is succeeded: no fate roll, no war, no wardens' blame; "The King of X has died peacefully
  in his sleep, at a great age" and the heir is crowned a day later (`KingdomEvents.folkDied`). A court wizard's office
  goes to the next eldest (0.5.53's vacancy). Other folk leave a vacancy that is hired as usual.
- Population: MCA's own village procreation (couples have a baby while a village is under its bed count) refills
  the beds the dead leave; children grow up and are stamped as come-of-age then. One raised from their grave with
  MCA's staff comes back an elder with a full stretch ahead.
- Not playtested in game.

## 0.5.55: wilderness encounters (Naturalist)
- `Encounters` (tuning at its top; only with Naturalist loaded, everything by id): Naturalist's bear, lion, tiger,
  alligator, komodo dragon, rhino and desert/jungle scorpions are taken out of natural spawning (a Fabric biome
  modification in the REMOVALS phase), and its elephant and mammoth too (Kingdoms has its own). `WILDLIFE = false`
  would take every Naturalist mob out and leave only the encounters.
- Every 20 s each player in the Overworld who is out of creative/spectator, on the surface (within 6 blocks of the
  ground's top) and 256+ blocks from every settlement's seat and off every settlement's land has a 1 in 45 chance
  (about once per 15 minutes in the wild) of an encounter, then at least 20 minutes' quiet (also after joining).
- What comes is picked by weight from the beasts whose Naturalist biome tag (`naturalist:has_<mob>`) fits the spot:
  a bear, a pride of 2-3 lions, a tiger, 1-2 alligators or komodo dragons, a rhino, 2-3 scorpions, 2-4 boars, a
  rattlesnake or coral snake. They appear 20-32 blocks off on dry ground (never in a settlement's reach), grown, and
  go for that player (neutral ones angered at them) for 2 minutes, with a vague line on the action bar ("Tawny shapes
  are slinking through the grass..."). After that they're ordinary animals, and vanish if no player is within 96.
- **Kingdoms' own wild beasts** (`wild/`; models and textures from Better Animals Plus 11.0.10, CC BY-NC-SA 4.0, by
  cybercat5555, itsmeowdev and Wolverbyte, credited in `textures/entity/wild/CREDITS.txt`; the models carried over to
  26.3 names by a script, the AI our own): feral wolves (14 health, packs of 3-5 in forests, taiga and snow; six coats,
  arctic and snowy ones in the cold), coyotes (plains, savanna, badlands, desert; hunt players only at night, when their
  fangs show and eyes glow; encounters only at night), tarantulas (deserts, badlands, jungles; climb walls, bite, and
  every 8 s flick stinging hairs at you from up to 12 blocks: blindness and nausea for a moment) and moose (swamp and
  taiga; 52 health, charges anyone within 15 blocks; drawn 1.5x). They never spawn on their own; they're in the
  encounter table with biome tags `jaymoskingdoms:has_<beast>`, so they come even without Naturalist. Wolves, coyotes
  and moose are animals (Kitchen butchers them; the moose is a big carcass); the tarantula is a monster. Vanilla sounds.
- Not playtested in game.

## 0.5.56: monsters of legend, beast gossip and quests, bat horse and nightmare
- **Monsters** (`monsters/`, Fangs 'n Claws' line-up done as our own code; models converted from Mo' Creatures and
  textures redrawn from its art, GPL-3, in `licenses/mocreatures/NOTICE.txt`; tuning at the top of each class):
  - **Werewolf** (40 health): a frightened villager-looking human by day, at night it changes over 3 s (smoke, howl)
    into a beast that hunts players and villagers. Blows hurt the beast at a quarter unless the weapon is gold (full)
    or silver (double). Four faces, four coats; the rare fire coat (1 in 10) sets you alight and doesn't burn.
  - **Ogres** (green, cave, fire; 50 health, cave 70; half have two heads): every ~7 s they raise both fists and
    smash the ground, hurting and throwing everyone within 5 blocks. Fire ogres burn what they hit, and spawn in the
    Nether as well.
  - **Wraith / flame wraith**: drift through walls, swoop at you, undead and burn in daylight (wraith); flame wraiths
    set you alight and spawn in the Nether too.
  - **Silver skeleton**: fast undead swordsman that sprints at you, burns in daylight.
  - **Mud golem**: hits slow you (Slowness II, 3 s), heals in water or rain, takes double fire damage when dry.
  - **Mimic**: when you open a wild loot chest (one with a loot table, 256+ blocks from any settlement) there's a 6%
    chance it's a mimic instead: it keeps the loot and drops it when killed. It sleeps until you walk up unsneaking,
    touch it or hit it, and shuts again after 30 s of quiet.
  - They come as wilderness encounters (`Encounters`, biome tags `jaymoskingdoms:has_<beast>`; most at night only).
  - **Silver sword**: like iron, +4 damage against undead; 8% drop from monsters of legend killed by a player.
- **Sightings and gossip** (`Sightings`): encounters, and rumours placed 300-800 blocks from faction seats, are
  remembered for an hour. Villagers within 1200 blocks of one gossip about it (what, how far, which way).
- **Beast quests**: a villager near a sighting may give a quest (10-16 emeralds): **hunt** it (go to the spot; it
  comes for you, and comes again if it got away) or **survive** a night there (at night, 2-3 of its kind come for
  you; alive at dawn wins, dying fails it).
- **Mounts of legend** (`equestrian/MythicMounts`, new `Breed`s at the end so old saves keep theirs): the **bat
  horse** (Mo' Creatures' wings; forests and taiga at night) flies while ridden: hold jump to climb, release to glide
  down, it keeps cruising forward. The **nightmare** (badlands and deserts at night, and the Nether) has a flickering
  fiery coat and gives its rider fire resistance. Tame and saddle them like any of our horses; untamed ones leave at
  dawn.
- Not playtested in game.

## 0.5.57: silver for the silver sword, rarer mythic mounts
- The silver sword is crafted from two silver ingots and a stick (the `c:ingots/silver` tag; Jaymo's Powers 0.8.111
  adds silver ore and ingots) and mended with silver ingots instead of iron.
- Werewolves: a Jaymo's Tinkering tool counts as what its head is made of (read from the `jaymospowers:tool_materials`
  component by id), so a silver-bladed tinker sword hits for double, and a rose gold one for full.
- `MythicMounts`: bat horses and nightmares are exceedingly rare: `CHANCE` 1 in 6000 per 30 s check (was 1 in 80),
  about once in 50 hours a player spends at night in the wild or in the Nether, and none while another wild one is out.

## 0.5.61: assassins
- **Contracts** (`Assassins`, tuning at the top): each day a realm's ruler (king or lord) has a 1.5% chance of a price
  on his head, times his temper (tyrant 2.5, noble 0.5, warlord 1.5, paranoid 0.75, reveler 1) and doubled if a realm
  is his rival or at war with him. Who paid: that rival (70% when there is one), his own people (a tyrant), an
  impatient heir, or the guild. Players get marked too: a realm that hates them (2%/day), a Rogue (2%), one known in 4+
  settlements (1%). At most 3 contracts at once; the blade comes 1-2 days later; a contract lapses after 6 days.
- **Whispers**: while a contract waits, villagers near a player in that realm's land whisper of it (or warn a marked
  player). Heard it? Right-click the ruler to warn him (+15 good name): the first blow is parried. A paranoid ruler
  parries half the time anyway.
- **The attempt** only happens with a player within 40 blocks of the ruler (a marked player: at night). A "Stranger" in
  plain clothes walks in and makes for his mark; at reach he throws off the cloak (black leathers, iron sword) and
  stabs: a quarter of the ruler's health (half if asleep) and a poison that takes 3.5% a second for 30 s; his guards
  come running. Milk, honey or a golden apple (right-click the ruler) cures it (+40 good name, 16 emeralds from the
  treasury); his physician may (4% a second). The assassin fights 12 s or until half dead, then smoke, invisibility,
  and he runs. A marked player feels eyes on them at 9 blocks (Stealth 3+: 22 blocks, "one of your own trade"), then a
  6-damage stab and Poison II.
- **After**: killed, he drops his purse (8-20 emeralds) and a Sealed Contract (paper, its lore names the mark and who
  paid). For a ruler: +50 good name to his killer, news to everyone, and a rival realm's seal makes the two rivals. A
  murdered ruler is mourned and his heir crowned a day later (no ruin or conquest fate); word may get out of who paid.
- Right-clicking a stranger: an evasive line (Stealth 3+: you recognise an assassin). `/kingdom assassin [realm]` (op):
  a blade sent now for that realm's ruler, or for you.

## 0.5.62: hermit wizards on floating islands
- The villager Moog's Voyager Structures puts on its large floating island is no longer a stray: `Hermits` makes them
  a hermit wizard (an MCA villager found standing in a structure tagged `jaymoskingdoms:sky_hermitage` is checked once
  a minute; `Hermits.FACTION` -2 marks them, role `Sorcery.WIZARD`). They wear the court wizard's robe, hat and staff,
  keep within 10 blocks of where they were found, and cast as a court wizard when something comes for them (monsters,
  barbarians, whoever strikes them; never a player otherwise).
- Each has a story (from their UUID): a scholar stranded by their own spell, a mage who left the courts for quiet, the
  warden whose spell holds the island up, or an exiled court wizard. The first time a player comes near each day they
  greet them aloud with it and hand over a court wizard's spell scroll (warding or arcane bolt) for the climb. MCA's
  chat brings the story up 70% of the time (`dialogue.chat.jaymoskingdoms.hermit_<story>`). Sneak-use them to unmake
  skills, as with any court wizard.
- No kingdom claims them: they're never promoted, drafted, made court wizard, or sent over by `Approaches`.
- Falling more than 2.5 blocks, they cast slow falling on themselves.
- MCA's profession label for `jaymoskingdoms:wizard` is now "Wizard" (was "Court Wizard").

## 0.5.63: every king's guards have his temper
- `Retinue` (was the paranoid-only `Paranoia`): how a ruler's guards take players about him, by his temper. Each
  stranger builds heat a second close (within 5 blocks) or lingering (within 12), more for right-clicking him, and
  it cools 1 a second once they give him room. Tuning is the `RULES` table at the top of `Retinue`.
  - **Paranoid** (3/s close, 1/s lingering, +10 a click): too close, shoved back. At 25, up to 2 town guards dog
    you, shoving you away from him and threatening you. At 60 they walk you out of the city (80 blocks from the
    seat) and keep you out for 5 minutes.
  - **Tyrant**: kneel (sneak) near him or it's insolence (2/s close, +15 a click). Too close standing, a guard
    demands you kneel. At 20 a guard slaps you (1 damage, only above 5 hearts) and fines you an emerald for his
    treasury every 5 s; at 50 you're thrown out (5 minutes). Kneeling cools it twice as fast, and once a day wins a
    nod (+1 good name).
  - **Warlord**: a weapon in hand near him (sword, axe, bow, crossbow, trident, mace) is a challenge (3/s close, 1/s
    lingering, +8 a click). Too close armed, shoved and told to put it away; at 15 guards dog you; at 50 you're
    marched out. Unarmed with 15+ armour, a salute once a day (+1 good name).
  - **Noble**: greets you once a day, with 2 bread if you have 20+ good name with his realm. Standing right by him
    (1/s) or pestering him (+6 a click) gets a polite word and a gentle push at 20. Never throws you out.
  - **Reveler**: a cheer and 3 cookies or a honey bottle once a day. Pester him (+8 a click) and at 20 his guards
    toss you up and away, laughing, and it's forgotten. Never throws you out.
  - Guards never draw on you; only hitting the king or a guard makes them hostile. Realm members, sworn wardens and
    creative players are left alone. Heat and bans live in memory (a restart clears them).
- Not playtested in game.

## 0.5.64: muskets and cannons for the richest kingdoms
- `Muskets`: a kingdom with 1000+ emeralds arms about one guard in 3 (picked by UUID, so always the same men) with
  Balkon's WeaponMod musket with an iron bayonet (a crossbow without WeaponMod). A musketeer fires at a foe in sight
  from 3 to 26 blocks (a ball hits for 9; WeaponMod's own 20 was too much for a garrison), reloads for 6-8 s, and
  meanwhile closes in with the bayonet; he throws no javelins. Shots spare his own side like javelins
  (`Javelins.harms`). Below 800 emeralds the muskets are sold and the guards take their swords and shields back.
- `Cannons`/`Cannon`/`Cannonball`: such a kingdom sets 2 to 4 cannons (fixed per kingdom) on its archers' wall and
  tower posts first, then beside the gate (round the seat with no keep), facing out. An archer at the post sits on the
  trail as gunner (a gate cannon with no archer near gets one hired). The cannon swings to his foe, raises its barrel
  for the arc, and fires every 8-11 s at 7-32 blocks: a ball that bursts for 14 (falling off over 2.5 blocks) and
  knocks back, never hurting its own side nor breaking blocks; a foe closer than 7 gets the gunner's arrows. Players
  break a cannon with 40 worth of blows. Below 800 emeralds the cannons are sold. Drawn from blocks (`CannonRenderer`):
  dark oak carriage, spoked wheels, cast-iron barrel with copper bands, the realm's colour on the gunner's cushion; the
  barrel kicks back when it fires. Tuning numbers at the top of each class.
- Not playtested in game.
## 0.5.65: no punching trees
- Wood needs an axe (Andy): `Trees.needsAxe` is true for logs and chopped logs when the player isn't in creative and
  holds nothing that cuts oak faster than a hand (axes, the Armory flint hand axe, tinker hand axes and mattocks).
  `mixin/PlayerLogToolMixin` makes `Player.hasCorrectToolForDrops` false then, so a bare-handed log breaks as slowly as
  stone without a pickaxe and drops nothing; the chop (`Trees.breaking`) is refused, and starting to punch wood shows
  "Wood needs an axe. Knap a hand axe from 3 flint and a stick (leaves drop sticks)."
- Natural leaves broken by an empty hand drop a stick `LEAF_STICK_CHANCE` (12%) of the time, for that first stick.

## 0.6.0: player classes (pack 0.9.0)
- The first time a player comes into a world the class screen opens (`Classes`, client `ClassScreen`): Thief, Warlord,
  Mage, Archer, Survivalist, Brute, Marksman or Sorcerer, for keeps. A Mage picks a first spell (Fire Bolt, Arcane
  Bolt, Warding) and a Sorcerer an element (water, fire, earth, air, lightning, ice) on a second page; a Mage gets a
  wizard's staff. The **Writ of Rebirth** (rare in stronghold libraries, ancient cities and woodland mansions; a court
  wizard sells one a week for 30 emeralds when you've no perks to unmake) unmakes the pick and asks again. Ops:
  `/kingdom class set|clear|get`. The K screen shows your class.
- What each class is good with is attribute modifiers from the class, the held weapon and the armour worn
  (`ClassPerks`, every number at its top). Gear is found by item tags under `data/jaymoskingdoms/tags/item/`
  (`light_blades`, `two_handed`, `bludgeons`, `guns`, `light_armor`, `heavy_armor`; optional entries for Armory,
  Balkon's and Tinkering ids). Thief: light blades +25% damage, +20% swing speed, +1 armour per light piece. Warlord:
  two-handed +25%, heavy armour weighs half. Mage: -2 hearts. Archer: arrows +25% (`ENTITY_LOAD`), +10% speed, and
  with a bow drawn a two-block jump (`JUMP_STRENGTH` +0.12) and half fall damage. Survivalist: hunger -30%, air x2
  (`OXYGEN_BONUS`), +1 heart, fall damage -30%, +5% speed. Brute: bludgeons +30%, +2 hearts, -8% speed, heavy armour
  weighs double, a raised shield halves what gets past it (`PlayerBlockingMixin`) and comes back in 50 ticks when
  knocked aside. Marksman: -1 heart, +1 armour per light piece (its guns are stage 3). Sorcerer: its blade and element
  are stage 2. Heavy armour now weighs on everyone: -1.5% speed a piece. Fall-damage changes are
  `ADD_MULTIPLIED_TOTAL`, so a power that takes it to nothing keeps it there.
- The ninth class, the **Rider** (Andy asked for one more): in the saddle the horse runs 15% faster (a transient
  modifier on the mount's speed) and tires 25% slower (`Equestrian.drain`), horses trust them 50% sooner
  (`Bonding.addTrust`), blows from horseback +20%; on foot 5% slower. Nobody starts with gear (Andy's word): the
  cards show what each class is good with (`Classes.gear`, other mods' items by id with vanilla stand-ins). A Mage
  gets a wizard's staff by having a court wizard teach the recipe (sneak and speak to him: gold and amethyst on two
  blaze rods, `recipe/wizard_staff.json`, put in the recipe book) or by finding one (rare chests, a slain court wizard
  half the time).
- Stage 2, the casters (`Spellcraft`): a Mage's wizard's staff casts the spell on it (right-click; sneak and
  right-click turns to the next), at 1.5x a scroll's power with its own cooldown per spell (`MAGE_COOLDOWN`); a Mage
  reading a scroll of a spell they don't know learns it (`Chosen.spells`). `Arcana.cast(spell, level, player, power)`
  casts any of the five by name. A Sorcerer's `J` key imbues the blade in hand with their element for 2 minutes (every
  blow carries it: fire burns, water douses and slows and hurts what fears water, earth staggers, air launches, lightning
  chains to 2 neighbours with a visual-only bolt, ice freezes); sneak and `J` throws a bolt of it (5 damage plus the
  element, 2 s cooldown). The great scrolls (Raising, Warding) are beyond a Sorcerer.
- Stage 3, the Marksman's guns (`WeaponModGuns`, only with Balkon's WeaponMod): a shot from a gun in
  `#jaymoskingdoms:guns` flies half again as fast and far, hits 30% harder (`extraDamage`) and bends up to 3 degrees
  toward the nearest living thing within 48 blocks of its line (`ENTITY_LOAD`); the gun reloads and winds up 35%
  faster (`RangedComponentMixin` on `onUsingTick`/`releaseUsing`, applied by `WeaponModMixinPlugin` only when
  WeaponMod is there).
- Not playtested in game. Plan: `docs/Classes-Plan.md`.

## 0.6.2: beg their pardon (pack 0.9.2)

- Missed what someone who walked up to you said? For 45 s after they've spoken (`PARDON_TICKS`), and while they're still
  telling you their news, right-click them (not sneaking) for a small screen: "Beg your pardon?" (they say it again,
  over their head, and the screen shows it), "Could you tell me more?" (each opening has a `.more` follow-up line) or
  "Never mind". A question you walked off on is asked again after a pardon (walking off a second time costs no more
  reputation); a quest giver opens their quests. The answer screen has "Could you tell me more?" too, which adds the
  follow-up to the question shown. A grey hint ("Missed that? Right-click ...") follows news, gifts and greetings.
- Guards halt a stranger once in three days per settlement (was daily, `HALT_TICKS`), and any guard comes over to a
  player at most once in 8 minutes (`GUARD_GAP`, except to accuse). Four new guard encounters, weighted and worded by
  the king's temper (a fief takes its liege's; a village with no ruler has its own plain lines), each with three answers:
  - search your pack: allow (stolen goods you're suspected of turn up: a fine; clean: good name, though a tyrant's man
    takes an emerald for his trouble), refuse (tyrant and paranoid guards arrest you), or bribe 2 emeralds (tyrant,
    reveler and village guards take it and forget what they suspected; noble and warlord guards are insulted; a
    paranoid one arrests you).
  - a toll (tyrant 3, warlord and paranoid 2, others 1 emerald, to the treasury): pay (the reveler's feast toll gives
    sweets back), haggle (to half, odds by temper; a tyrant's man takes it all and slaps you), or refuse (a tyrant's
    men arrest you).
  - a bout for 2 emeralds: odds from armour and attack damage (15-85%); a warlord's men think less of a refusal, and
    insulting a tyrant's, warlord's or paranoid king's guard makes him draw on you.
  - a loyalty test: praise (a paranoid king's guard may take it for flattery), keep quiet, or speak ill (tyrant: slap
    and arrest; paranoid: arrest; warlord: a lot of good name lost).
  Arrest (`Theft.arrest`) makes you wanted for a minute and the guards nearby come for you. Walking off on a search or
  a toll counts as refusing it.
## 0.6.4: townsfolk and the Xeno-Band's aliens (pack 0.9.7)
- Jaymo's Powers' Xenoform (the Xeno-Band) turns a player into an alien. To a kingdom's people that is a monster:
  villagers run from one as from a zombie (`Frights`), and guards hunt it (`Folk`'s target goal, `MobCombat.foe`),
  unless they know better. A realm's folk or villagers who see you change (within 16 blocks, line of sight) know the
  alien is you from then on (`Aliens.KNOWN`, kept across deaths): that kingdom then treats the alien as it treats you,
  and one of them says so. Read by entity tag (`jaymospowers_xenoform`, set by Powers while you're changed), so
  Kingdoms still needs no Powers classes.
- Not playtested in game.

## 0.6.3: no floating buildings (pack 0.9.3)

- When any mod's surface structure is placed in a chunk, a gap of up to 6 blocks of air under a piece's lowest full
  block, down to natural ground, is filled with that ground (dirt under grass, sand on a beach): `Foundations`, run from
  `StructureStartMixin` at the end of `StructureStart.placeInChunk`. Columns over water (docks), deeper drops (bridges,
  sky structures), unnatural ground and terrain-following street pieces are left alone. New chunks only.
- (0.5.58, PR #96) Villages generate with `beard_box` terrain adaptation, and one builder of each crew levels the
  settlement's ground (`Groundwork`: pits, ramps on 2-5 high ledges, plank bridges where roads meet gaps).

## 0.6.5: Combat Arts, Strength's second page (pack 0.9.10)
- Strength goes to level 25 and its tree gets a second page of 8 perks (`CombatArts`, `docs/Skills-Plan.md`): combo
  chains (blows within 2 s), Momentum and Cleave; finishers on every 4th full-strength link by weapon (Earthsplitter,
  Rend, Executioner); Charge and War Cry on the new Y key; Bladestorm every 8th link. Area blows only hit foes.
- Alignment counts experience only up to level 15, so Strength's longer road doesn't decide it.
- Not playtested in game.

## 0.6.11: townsfolk react to the strange (pack 0.9.24)
- `Oddities` reads the tags Jaymo's Powers puts on strange things (`jaymospowers_odd:<kind>`; Powers' `core/Oddities`)
  and the world's people (vanilla and MCA villagers, a realm's folk who aren't attacking) react:
  - A Gravity meteor or a falling star in the sky: everyone out of doors within 112 blocks (across the ground) stares
    up at it and two cry out (`SKY_SEEN`, `SKY_CRIERS`). Where it lands, villagers within 36 run from the spot
    (`Frights.scare`) and someone within 72 says so.
  - A power used in sight within 24 blocks (any ability, mode or ultimate key, for 3 s after): they stare, and one
    says a line for that power (8 powers, 3 lines each; a child cheers instead), at most every 20 s per player.
  - The symbiote's blob, a mob it inhabits, a behemoth: monsters (`monstrous`): villagers run and cry out, and guards
    hunt them (`hunted`; not an inhabited villager, whom they only shout about by name).
  - A player in the symbiote's suit or big body, or walking as a Darktide shade, is treated like an unknown alien
    (`Aliens.KINDS`): villagers run, guards hunt, unless the kingdom saw that change (each kind learned on its own;
    the alien's saved entries are unchanged).
  - Under a blood, super blood, harvest or blue moon those out at night now and then remark on it.
  - Folk who know a changed player (they saw the change) now and then remark on it, unafraid (`KNOWN_TALK`).
  - Twelve lines for every sight, fright, moon and bit of gossip, dealt like a shuffled deck (`Oddities.line`): each
    is said once before any comes round again.
  - It's all talked about: a gossip's line is often of the nearest strange thing within 1000 blocks in the last three
    days (a star fell 300 blocks north; Andy called the wind down on the square). No one says something strange more
    than once in 30 s (`TALK_GAP`). Tuning at the top of `Oddities`.

## 0.6.9: villagers' days (pack 0.9.20)
- **More routines** (`Routines`, 11 more). Grown: **chores** (a barrel, chest, oven, crafting table, cauldron or loom within 6 of
  their bed: hands busy, the block's sound), **read** (lectern or bookshelf: page turns), **sweep** (their door's
  step: dust), **lunch** (5000-7000, at home else by the bell: bread or apple crumbs, eating sound), **shop**
  (follow a tradesperson at their work and haggle), **animals** (follow and fuss over a farm animal or pet: hearts),
  **watch** (eye a stranger, under 50 reputation, from 5 blocks off), **dance** (evenings round a campfire or the
  bell: jumps, spins, notes). Children: **hide** (hide and seek in pairs: the seeker counts 5 s, finds, they swap),
  **stones** (skip stones at the water), **tagalong** (trail after a grown-up). Lines `routine.jaymoskingdoms.*`.

- **Tag** (`Tag`): a child's "wanna play tag?" is a real game now, not one run-off. You're it: the child dodges away
  whenever you're near (`LandRandomPos.getPosAway`), taunting; catch up, punch (no harm) or right-click to tag them.
  Then they count to three and chase you until they tag you back. No tag-backs for 1.5 s. 90 s a game, ending at
  nightfall or if you wander off past 28 blocks; hearts by how many tags (up to 6).
- **Toddlers** (`Families`, `McaBridge.families`): MCA only has couples conceive while a village has beds to spare,
  so full villages never had children. Now a realm's settlement with fewer than one young one (or one on the way) per
  4 grown has a couple conceive now and then (25% a minute); MCA's babies are born toddlers. With no young at all the
  first is due within ~2.5 minutes, and with no couple to have it a toddler is taken in (once a day at most).
- **Routines** (`Routines`): townsfolk free of work (anyone with a trade only before 2000 / after 9000) take up
  something fitting the hour, walk there and stay 15-40 s: dawn water fetching and gardening (flowers, composters);
  by day the market round the bell, calling on a neighbour to chat (both stop and face each other), strolls round
  the outskirts; evening campfires (else the bell), going home early. Children play chase in pairs. Landmarks are
  found on the surface within 40 blocks of the seat every 5 minutes. Short lines (`routine.jaymoskingdoms.*`).
- **Suspicion at home** (`Trespass`): indoors within 6 blocks of a villager's bed (MCA home, else vanilla HOME) with
  them seeing you, suspicion builds a point a second (x1.5 at night, x0.5 sneaking): at 2 they stop and stare ("can
  I help you?"), at 8 they come over and ask what you're doing (-2 hearts; their guards will want to search you), at
  16 it's trespass, a light crime (`Theft.Crime.TRESPASS`), and again after that the guards are called (medium).
  Friends (40+ MCA hearts), people of rank and 300+ reputation are welcome.

## 0.6.8: imbued blades proc (pack 0.9.17)
- A Sorcerer's imbued blade no longer bursts on every blow: one blow in twenty does (`Spellcraft.PROC_ONE_IN`), the
  rest only spark with the element's colour. A lightning burst also blinds for 0.3 s (`LIGHTNING_BLIND`). Air
  instead lifts on every second blow (`AIR_EVERY`), so the next lands in the air: air combos. Thrown bolts (sneak + J)
  always burst.

## 0.6.7: thread-safe structure palettes (pack 0.9.16)
- Structure template palettes cache their blocks in a `ConcurrentHashMap` (`StructurePaletteMixin`): two worldgen
  threads opening the same piece at once crashed the server with a ConcurrentModificationException.

## 0.6.6: lunar events and Bad Omen grudges (pack 0.9.15)
- Our own lunar events (`Moons`; ideas from Lunar and Enhanced Celestials, nothing of theirs used). At dusk the
  Overworld rolls for the night (none in a world's first 4 nights); the roll is saved. Sky, fog and night light take
  the moon's colour through a layer added to the game's environment attributes (`EnvironmentAttributeSystemBuilderMixin`),
  and the moon itself is tinted and resized on the client (`MoonSkyMixin`), fading in and out over 10 seconds.
  - Blood Moon (5% of nights): extra zombies and skeletons claw up out of the ground round each player (up to 22
    monsters near; they start 2 blocks down, frozen, and climb out over 2.5 s through a spray of the ground's block,
    and the game's own zombies and skeletons that spawn that night rise the same way; never saved half-buried), monsters out that night get Strength and Speed, no one can sleep, barbarian camps raid
    twice as readily.
  - Super Blood Moon (8% of full moons): a huge deep red moon; up to 36 monsters near, two tries a draw, Strength II.
  - Harvest Moon (30% of full moons): a big golden moon; crops, stems, berries, cocoa and saplings round each player
    grow all night; at dawn every loaded settlement's fields grow a stage.
  - Blue Moon (22% of full moons): Luck for everyone under the open sky; monsters slain by a player give 6 extra
    experience and a 15% chance of an emerald, diamond, amethyst shard or lapis.
  - Ops: `/kingdom moon <blood|super_blood|harvest|blue|clear>` sets tonight's moon.
- Bad Omen (`Omens`): walk into a kingdom, fiefdom or village (its land, or 96 blocks of its seat) carrying Bad Omen or
  Raid Omen and every good word you had there is gone, then 400 more (once a day per settlement). If the raid you
  brought comes and the settlement wins it, it holds a grudge (saved): reputation gains there count a tenth and never
  climb over -50 (no pledging again), with a 1% chance a day it's finally forgiven. A raid that wipes it out leaves
  no one to remember.

## 0.6.12: natural disasters, stage 1 (storm fronts and tornadoes, pack 0.9.26)
Our own take on Weather, Storms & Tornadoes (Corosus; Forge/NeoForge only and all rights reserved, so ideas only).
Plan: `/mnt/project-files/disasters/Disasters-Plan.md` (stages 2-3: sandstorms, blizzards, wildfires, floods, hail;
earthquakes, relief quests, forecasts).
- Storm cells (`disasters/Storms`): a cell forms ~300 blocks upwind of someone and drifts across with the wind (which
  slowly veers). Rain falls on the ground only under it (a `Level.precipitationAt` mixin, both sides); on the client
  the sky darkens and thunder rolls as you come under one (the camera's rain/thunder level, eased); its lightning
  strikes only under it (a lightning rod nearby takes it). Ordinary cells about every 1.5 days, from 70 to 130 blocks
  across. They live in memory only.
- Supercells, every `jaymoskingdoms:disaster_days` days on average (game rule, default 6, 0 for none): a black cell with
  a green cast, announced to everyone within 450 blocks. Within 170 blocks of someone it puts down a tornado (one in
  five a monster). In a dry biome (no rain) the disaster is a dust devil near the player instead.
- Tornadoes (`disasters/Tornadoes`): dust devil, tornado, monster (cores 2.5/6/11, winds reach 7/20/34, funnels 22/75/115
  high). They weave under their storm, form and rope out over 10 s, and die when their storm breaks up. Everything in
  reach is pulled in and spun; in the core it's lifted and thrown out at the top (players carried 4/22/38 blocks up,
  then fall: fall damage applies), and the living in a tornado's core take a little damage. Unless
  `jaymoskingdoms:disaster_damage` is off they tear up the land in their path (5/12 blocks a tick): plants, leaves,
  glass, wool, axe/hoe blocks up to hardness 2.5, and for a monster stone up to hardness 2. Up to 40 blocks per
  tornado fly as falling blocks, riding the winds for 2.5 seconds and then falling where they're flung (they no longer
  hang in the air); the rest are smashed (a quarter drop). Block entities are never touched. Settlement
  buildings it ruins are rebuilt by `Rebuilds` as usual. Touchdowns are announced within 320 blocks; the roar carries.
- Whole trees (`Trees.uproot`, `FallingTree.fling`): a tornado that strikes a living tree rooted in dirt may tear it
  out whole (12% a strike for a tornado, trees up to 12 logs, 3 in the air at once; 45% for a monster, up to 64 logs,
  8 at once). The tree flies off round the winds, tumbling end over end, falls, and breaks apart where its wood hits
  the ground (logs and saplings drop; anything under it is struck).
- Villagers (MCA's too) within 110/150 blocks of a tornado run home to bed, or away from it.
- Jaymo's Powers' Wind (0.9.26): Wind users (tag `jaymospowers_wind`) ride a tornado's winds up to near its top
  without damage or fall build-up, and leave it gently; a Cyclone Lance fired at one within 48 blocks shoves it the
  way you aimed (tag `jaymospowers_wind_lance:<time>`). Under a storm cell's thunder Wind counts as in a storm.
- The client draws the storms as particles: dark cloud decks over each cell within 640 blocks (lower and greener under
  a supercell), each tornado's whirling funnel and the debris at its foot within 420.
- Instruments: the **storm bell** (planks, bell, redstone, smooth stone slabs) rings and gives a full redstone signal
  while a tornado is within 180 blocks; the **weathervane** (copper over iron) tells the wind and the nearest storm on
  right click; the **barometer** (copper, glass pane, redstone) held shows the sky's mood and the nearest storm or
  tornado on the action bar. Art: `tools/gen_disasters.py` (models from vanilla's block textures).
- Ops: `/kingdom disaster <storm|thunder|supercell|dust_devil|tornado|monster|clear>` (a cell overhead, or a
  tornado 60 blocks ahead coming your way).

## 0.7.0: vampires, stage 1 of the 0.10.0 drop (pack 0.10.3)
Plan and open questions: `docs/Vampires-Plan.md`. All in `Vampires` (tuning at the top).
- **The Elder.** One per world (`elder_vampire` on the Overworld). From day 6, by day, a grown villager 20-64 blocks
  from a player becomes him (+20 max health). By day he's himself; at night (12800-23200) he hunts the nearest grown
  person within 24 blocks (villagers, MCA's, any realm's folk, rulers included; never children), holds them (Slowness
  VI) and bites every second for 5 (healing 3/4 of it) until they die, rests 2 minutes, and hunts again. Slain (or
  cured), another may come 30 days later.
- **The bite** (`vampire_bitten`): a person bitten who lives turns at the next nightfall, rolled: feeder 50% (hunts
  each night until it's killed once), abstainer 30% (sleeps; after 3 nights unfed it bites someone once and lets go),
  hunter 20% (goes for any vampire that's shown itself, with claws). Turned vampires get +8 max health. Killed
  outright, the victim stays dead. Players bitten only get Weakness and Hunger for now (they turn in stage 3).
- **Shown.** A vampire hunting or fighting shows red eyes (dust particles) and drains with blood and a drinking sound;
  till dawn guards go for it (`Folk` targets, `MobCombat.foe`), villagers run from it crying who it is
  (`talk.jaymoskingdoms.vampire.cry.*`), and whoever it attacks fights back. Hurt by someone, it fights them with its
  claws; under 30% health it runs. At night it heals 0.5 a second when left alone. Fire hurts it twice over. By day it
  has Weakness and passes for the living. Villagers are driven by their brain's walk target, folk by their target.
- **Bat form** (Andy): the Elder, his prey 12-48 blocks off, bursts into a bat (smoke, a no-AI vanilla bat tagged
  `jaymoskingdoms_elder_bat`; he's hidden: invisible, no gravity, no physics, carried along under it), flies at 0.7
  blocks a tick 5 blocks over the higher end, drops onto his prey and changes back beside them. Under 30% health he
  flies 32 blocks away from whoever hurt him. In flight he can't be hurt; a blow to the bat lands him and he takes
  twice it. 30 s between changes, 20 s at most a flight, and he lands at dawn. A flight cut by a restart ends on load.
- Ops: `/kingdom vampire elder | turn <feeder|abstainer|hunter> | cure | where`.

### Stage 2: lycanthropy (still Kingdoms 0.7.0)
All in `Lycans` (tuning at the top) and the legend `Werewolf` (Mo' Creatures port), now able to be someone.
- **The curse** (`lycanthropy` attachment: coat, size 0.8-1.4, build lean/middling/heavy, last night changed) lies in
  villagers (MCA's too) and realms' folk. Each dusk from day 4, 15% that it turns up in someone 20-64 blocks from a
  player. A cursed beast's bite that someone survives passes it on 40% of the time. Vampires can't carry it; the cursed
  can't be turned by a vampire's bite. Coat by where they live: timber in the cold, brown in the heat, any elsewhere;
  1 in 20 fiery.
- **The change**, every full moon (`EnvironmentAttributes.MOON_PHASE`; an op's `/kingdom werewolf moon` calls one
  tonight), each within 10 s of moonrise: they stop, freeze and convulse for 2.5 s (jerking head and body, bones
  cracking, cries, bits of fur), then the beast tears out (smoke, blood, a crack) and goes on changing for 3.5 s,
  frozen: `Werewolf.beastness` drives the model's muzzle, ears, sideburns, claws and tail growing out of nothing, the
  head and arms thrashing and the body convulsing hardest at first, and the body swelling from 65% to full size; a
  growl halfway, a howl-whine at the end. Its size is the SCALE attribute, its build widens or narrows it, its health
  grows with both.
- The person rides hidden inside (invisible, no AI, no gravity, no physics, carried each tick, can't be hurt). The
  beast hunts players and villagers (not hidden ones) and any vampire showing itself. Kill it and the person dies
  with it, where it fell. At dawn it shrinks back over 3 s (the same animation backwards) and the person is left on
  the ground: Slowness II and Weakness II for a minute, Nausea. Nobody near (not entity-ticking): they're simply
  back. A restart mid-night puts them back at once; the leftover beast is discarded when it loads.
- Ops: `/kingdom werewolf curse | change | cure | moon` (nearest within 16 blocks).

### Stage 3: players (still Kingdoms 0.7.0)
- **Vampire by bite** (`VampirePlayers`): the Elder and feeders now hunt survival players too, and bite them (claws
  only for vampires, and for a non-person who attacks them). A bitten player turns at the next nightfall (dying first
  cures it; the wolf-cursed can't turn). At night: Strength I, Speed I, Night Vision. By day under open sky: Weakness;
  3000-9000 with a bare head they catch fire. Sneak + right-click a grown villager or folk with an empty hand (from the
  front: Pickpocket has the back) to drink every second: 4 damage, +4 food, +1.5 saturation, +3 health, and the
  bitten turn if they live. Two nights without blood: Hunger and Weakness till they drink. Seen by that realm's
  people it's a heavy crime (`Theft.Crime.BLOODLETTING`); at night they show themselves and guards hunt them till dawn
  (`Folk` player targets, `MobCombat.foe`).
- **Werewolf by ritual** (`WerewolfPlayers`): full moon, night, Overworld, under the sky, 4+ lit campfires within 5
  blocks, sneak and use a bone: the curse (`lycanthropy`, kept through death) and the change at once. Every full
  moon the change is forced and lasts till dawn; after 3 moons `U` changes at will (and back), never against the
  moon. The change: 2 s doubled over (Nausea, held still, bones cracking), then the beast tears out: a no-AI
  `Werewolf` puppet (their own size, build, coat; updated every tick, `updateInterval(1)`) stands in for them for
  everyone else, while they're invisible; the puppet's change animation plays as for villagers. As the beast:
  Strength II, Speed I, Jump Boost II, Resistance I, Night Vision, Hunger, +10 max health; the change tears 30% of
  each armour piece's max durability (Andy): if that finishes it, it breaks, else it falls at their feet; armour put on
  as the beast just drops; held items go to the inventory (claws); a kill (not a monster) feeds them 6 + 0.8. Blows to the puppet land on them;
  guards hunt it, villagers flee it. At dawn it shrinks back (Slowness, Weakness, Nausea after). Their own puppet
  isn't drawn for them in first person.
- **The cure** for both (Andy): a rare quest from a priest (`Cures`). A quarter of clerics know the old rite (fixed by
  who they are); ask one for work while bitten, turned or cursed and they set the hunt instead: slay 2 vampires among
  the living, or 1 cursed werewolf in its beast shape (players don't count). Turn it in and the rite lifts the curse.

## 0.7.1: the cursed ones' coats (pack 0.10.4)
- Andy: the cursed werewolves get coats of their own. Each of the three ordinary coats (black, brown, timber) comes worn
  three ways: **mangy** (bald, sore patches, thinning fur), **scarred** (old pale wounds, fur never grown back round
  them) or **patchy** (piebald, uneven, bald spots). Over it, what's left of their clothes: a split shirt and trousers,
  trousers and a waist strip, or shirt shreds with a sleeve and one leg, torn, frayed and bloodied, dyed the colour of
  their trade (librarian red, cleric purple, farmer straw, fisherman blue and so on; folk and players a plain cloth).
  Wear and rags are fixed by who they are, so the same villager is the same beast every moon. The fiery coat keeps its
  flames (rags still on). `Werewolf` syncs `look`/`rags`; `WerewolfRenderer` picks the coat and draws a `RagsLayer`.
  Textures painted from our own coats by `tools/gen_cursed_werewolf.py`.

## 0.7.2: rarer tornadoes (pack 0.10.9)
- Andy: tornadoes should be far rarer. A supercell that comes on its own (every `disaster_days`, 6 by default) can put
  down a tornado only one time in ten (`Storms.TORNADO_SUPERCELLS`): about one tornado every 60 days, down from every
  6. The others still storm, with lightning. Dust devils, and the op commands (`/kingdom disaster supercell|tornado`),
  are unchanged.

## 0.7.5: the timelapse night and bed rest (pack 0.10.37)
- Andy asked for Sleeping Overhaul 2, which is Forge/NeoForge only (up to 1.21.1), so this is our own take on its ideas
  (its code is LGPL-3.0 but nothing is taken from it).
- **Timelapse night (`Timelapse`):** once enough players are fast asleep, the clock doesn't jump to morning: the whole
  world runs faster. The server ticks at 100 a second (`TICK_RATE`, vanilla's `/tick rate`; 5 times as fast), so
  villagers walk, build and farm at speed, and the clock runs 6 times as fast again (`SPEED`): 30 times in all, about
  20 s for a night if the server keeps up (slower if it can't). Both eased in over 2 s and sent to every player, so it
  all moves smoothly. Andy asked to be able to watch the villagers work. The screen fades to black on falling asleep as
  ever, then clears while the night runs, and dragging the mouse on the bed screen looks around from the pillow. Dawn doesn't turn sleepers out; at vanilla's wake-up time it stops exactly there and vanilla wakes
  everyone and clears the rain. Leave Bed (or Escape) works at any time; once too few are asleep the world goes back to
  its own pace wherever it got to. The hours the clock ran on ahead of the ticks are made up by the settlements at once (`Nights.pass`), as for a
  skipped night. A server stopped mid-night saves the clock at its own pace and its tick rate is put back.
- **Bed rest (`BedRest`):** using a bed by day (or wherever sleep isn't possible, a blood moon too), or sneaking at
  any time, lays you down awake: the screen dims a little, you don't count as a sleeper, and poison and withering
  can't hurt you while you rest (anything else gets you up). At night a **Sleep** button over Leave Bed puts you to
  sleep. Resting needs what sleeping does (reach, room, no monsters near) and sets your spawn.

## 0.7.7: sailing, stage 1: ships built from blocks (pack 0.10.42)
- Andy: "i want sailing" (Valkyrien Skies, Eureka and Valkyrien Pirates stop at 1.20.1; Haubna's are ARR), so our own.
  Plan: `/mnt/project-files/sailing/Sailing-Plan.md` (stage 2 naval combat, stage 3 pirates and navies, later).
- **Helm** (`HelmBlock`; planks, a compass and iron) and **sail** (`SailBlock`; wool and a stick, makes 4). Build a hull
  of any blocks on water, put a helm on it and right-click: everything joined to the helm (not the sea, not the land)
  casts off as one `Ship` entity, up to 3000 blocks and 64 long. Joined to the land, or aground, it won't go.
- **Buoyancy** (`Hull`): every block weighs by its material (cloth, wood, glass, stone, metal, gold) and the shape it
  fills; dry rooms enclosed by the hull, layer by layer, float it too. It settles to the depth where it displaces its
  own weight, and too heavy a hull won't cast off at all. The sea inside the hold is masked out (no water drawn there,
  no swimming in it).
- **Sailing**: W/S set and furl the sails, A/D the rudder; S with sails furled backs water. Top speed grows with sails
  and length (8 blocks/s at most); long ships turn slower, and slower at low speed. It stops against land and blocks,
  heels in turns and rolls on the swell (more in storms). Everyone and everything on deck rides along and can walk it.
- **Anchoring**: sneak at the helm of a ship lying still and it swings to the nearest straight heading and goes back
  into the world as blocks, a block or two off if it must, chests and their contents with it; under way, sneaking leaves
  the helm and it drifts with its sails furling. With no one aboard it anchors itself after 5 s.
- Chests (any 1-6 row container) open aboard while sailing; other block entities wait for the anchor.
- **Sails** (Andy: "the sails kind of float"): sails side by side and above one another join into one sheet of our own
  canvas (seamed panels), hanging from a yard across its top just in front of the mast (a fence, wall or upright log
  beside it is crossed by the sheet and the yard, lashed to it), bellied out toward the bow, most in the middle and less
  at its free edges, with sheets (ropes) from its lower corners. `SailBlock` knows its neighbours (`left`/`right`: none,
  sail or mast; `up`/`down`); 36 shapes of sheet from `tools/gen_sailing.py`. Hung facing aft, a sail is turned to
  face forward as the ship casts off. While the ship's sails are furled (W/S) they're drawn rolled up on their yards
  (`furled`, the ship's sail setting synced to clients).

## 0.7.8: musketeers carry Jaymo's flintlocks (pack 0.10.43)
- **`TaczGuns`:** with TaCZ installed, our TaCZ guns (musket, blunderbuss, flintlock pistol; the gunpack ships in
  Jaymo's Powers) are made by item id with their gun id in custom data. Musketeers are handed the TaCZ musket
  (`Muskets.musketStack`) and their shots stay our own; the Marksman's class card shows it with musket balls.
  Without TaCZ nothing changes (WeaponMod's musket, or a crossbow). The Marksman perks still apply to WeaponMod's guns
  only.

## 0.7.9: King's Roads (pack 0.10.44)
- Andy asked whether Via Romana and Countered's Settlement Roads could work with our carriages. Neither runs on 26.3
  (Via Romana stops at 1.21.1 and its Responsive Source License bars hosting a port; Settlement Roads is MIT but stops
  at 1.21.10 and only joins vanilla villages at worldgen), so this is our own take, nothing copied.
- **Planned** (`Roads`): every kingdom, fiefdom and village is linked to its 2 nearest within 700 blocks (a fiefdom to
  its overlord too). The route is an A* search on 8-block cells over the world generator's own terrain heights (so it
  plans through unexplored land), off the server thread, disliking climbs, cliffs and water; then rounded off, walked
  out in 4-block points and graded. Saved on the Overworld (`jaymoskingdoms:roads`).
- **Laid** a chunk at a time as chunks load: 3 wide in the ground's style (dirt path, coarse dirt and gravel in the
  green, sandstone on sand, cobble and andesite on rock, packed mud in swamps, cobble/mossy/stone bricks within 120 of a
  town), graded up to 3 blocks, trees in the way felled whole, 5-wide spruce bridges with rails over water, a lantern
  post every ~48 blocks, and a milestone (post and waxed sign) at each end: "King's Road / To <town> / <n> blocks",
  "Welcome to <town>" on the back. Only natural ground is touched, never builds; towns' own streets (within 56 of a
  seat) are left alone. Each chunk is paved once.
- **Followed** (`Roads.ahead`): `Construction.leg` keeps long walks to the road that leads to the goal, so cabs,
  rickshaws and rulers on their rounds take it; caravans make for points along it, seen and unseen.
- Ops: `/kingdom roads` (the roads, their length, chunks paved), `/kingdom roads plan` (plan the next missing one now).
- **Milestone travel** (`Milestones`; Andy chose "both" a coach and a teleport): right-click a linked sign (sneak to
  edit your own) for where its roads go, each with **[Coach]** (1 emerald + 1 per 160 blocks, at most 12, to the
  realm: a coachman, a horse and a carriage, `Wagon.COACH`, appear and gallop you along the road itself, helped on
  when stuck; gone in a puff after) and **[Teleport]** (4 + 1 per 48 blocks, at most 40, lost to the road; a blackout,
  hunger as if you'd walked it, a 30 s rest; a ridden horse comes too). A King's Road's milestone is linked to the
  town at its other end from the start.
- **Road Charter** (map + feather + ink sac; art by `tools/gen_roads.py`), Via Romana's idea in our own way: mark a
  sign, follow your own road (sampled every half second; 80% of it on road blocks: paths, gravel, cobble, bricks,
  tiles, planks, slabs, stairs, smooth/cut stone), mark another sign at least 48 blocks along: they're linked (saved
  on the Overworld, `jaymoskingdoms:charted_roads`). Jumping (a pearl, a fall) loses the chart; a link whose far sign is
  broken goes when someone tries it. Coaches on charted roads come from the nearest realm.
- Not yet seen in game.
- **0.7.10, tail fear (pack 0.10.48):** townsfolk who see a Z Fighter's tail out (`jaymospowers_tail` tag) take them for
  a werewolf and run (`Tails`, `Frights`, 12 cries), until told: at 25 MCA hearts the chat menu gains "Explain your tail"
  (`data/jaymoskingdoms/dialogues/main.json`, `McaAnswersMixin`), and that villager fears it no more.
  `ClassPerks.trainedWith` tells Powers which weapons a class is trained with.

## 0.7.11: physical falling trees (pack 0.10.50)
- Andy asked to combine the Physical Falling Trees datapack with our felling. It's CC BY-NC-ND 4.0 (no derivatives)
  and stops at 26.2, so it's remade in our own code in `FallingTree` (none of its files used):
  - A chopped-down tree falls to its **heavier side** (a log weighs 3, a leaf 1; `heavier`, `LOG_WEIGHT`, `TIPS`),
    straight along a compass line; only an even tree falls away from its cutter, as before.
  - It **strikes what's in its path** as it goes over, the cutter too (not a woodcutter), as hard as the wood there
    is moving (`hit`: 7 per block a tick, at most 14), knocking them the way it falls; once each.
  - A **bee nest or hive** on its logs comes down with it, and its bees come out angry at the cutter (the game's own
    release; smoked hives stay calm; `hives`).
  - Down, it lies half a second and becomes **real blocks** where it rests (`settle`): logs on their side along the
    fall, nests too, leaves left to wither (non-persistent, distance 7, so they decay and drop saplings as usual).
    It settles flat along the ground, resting up on what it hit and sinking up to 3 blocks onto the ground below; a
    piece with no room gives its drops. Felled in under half a second, it settles at once.
  - A **fallen log** (on its side, resting on the ground or another fallen log) isn't a tree: it's broken log by log,
    not chopped (`Trees.fallen`).
  - Woodcutters' trees still break up into their bags. Trees a tornado throws (Andy) strike by their speed and land
    the same way: on their side along the compass line nearest their flight, wherever they come down (rising up to 4
    blocks out of what they hit, sinking up to 8 onto the ground).
- Not yet seen in game.

## 0.7.13: roaming caravans, pillager and merchant (pack 0.10.55)
- Andy asked to work Pillager Caravans into our caravan system so there are hostile and neutral caravans to meet. Its
  latest build is for 26.1 and it carries the Obscuria Ecosystem licence, so its ideas are remade in our own code
  (`RoamingCaravans`, reusing `Caravans`' ground, steering and `Drive` goal); none of its files are used.
- **When:** each minute (`SPAWN_CHECK`), a 3% chance (Andy: rarer; about half an hour on average once one is due) that a due caravan comes near a random player: 64-96 blocks off
  on dry land outside every realm's claims, crossing past him to a point 420 blocks beyond. Cooldowns per kind:
  minor pillager 3 days, major pillager 7 days (a major also resets the minor's), merchant 2 days (Andy: rarer). Never on peaceful
  for pillagers.
- **Where it goes:** a pillager caravan with an illager camp ahead of it (within 700 blocks, within about 50 degrees of
  its way) is bound there; a merchant caravan with a realm ahead (900) is bound for its storehouse door. It keeps to a
  King's Road when one runs that way (`Roads.ahead`). Stuck for 20 s with nobody within 32 blocks, it hops 8 blocks on.
- **Pillager caravans (hostile):** a caravan master (pillager, patrol leader) with 2 pillagers and a vindicator and 1-2
  pack animals; a major one has a captain under the ominous banner (it drops), 3 pillagers, 2 vindicators, a ravager
  with a rider and 4 animals, the first carrying a strongbox (emeralds, gold, a saddle, golden apples; 1 in 4 a
  diamond or two, 1 in 10 a totem). They walk as a vanilla patrol and fight whoever comes near, merchants included.
  Animals are donkeys and mules, llamas in snowy and taiga land; each is leashed to one of the raiders and brought
  back if it strays (the leash snaps at 12). Kill its leader and it is free (tame it to open the chest, or kill it
  for its load). Kill every raider and the animals go free, and every realm and village within 320 blocks gives each
  player who fought it +15 reputation.
- **Merchant caravans (neutral):** a Caravan Merchant (wandering trader: his usual trades plus 3 of the land's goods
  for 1-3 emeralds), 2 trader llamas and 2 pack mules on his leads, and, when a realm stands within 640 blocks, 2 of
  its guards as escort (mounted when it's prosperous), steered with the caravan folk's `Drive` goal. Strike the
  merchant or one of his animals and the escort turns on you, and that realm takes -40 reputation (once per caravan).
  If the merchant dies, his animals go free and the escort goes home once it has no one left to fight.
- **Cargo** by the land (`Climate`): jungle cocoa, bamboo, melons; desert and badlands sugar cane, cactus, gold,
  golden carrots; snowy and taiga fish, berries, mutton, spruce; savanna, swamp, cherry and plains goods of their own;
  pillagers add village plunder, merchants paper, books, dyes, candles, lanterns.
- **Gone** when no player is within 144 blocks, after a day on the road, or on reaching its goal; nothing of it is
  saved but the cooldowns (`roaming_caravans` attachment), and its leftovers in unloaded chunks are cleared when they
  load. Animals set free are ordinary animals from then on.
- Ops: `/kingdom roaming` lists them; `/kingdom roaming pillager | pillager_major | merchant` brings one past you.
- `ChestedHorseInvoker` (villagers mixins) remakes a pack animal's inventory after it's given a chest.
- Tuning is in the constants at the top of `RoamingCaravans`. Not yet seen in game.

## 0.7.16: layered storm clouds (pack 0.10.63)
- Andy liked Simple Clouds' look (Forge/NeoForge only, PolyForm Perimeter, so ideas only) as long as it costs little.
  `StormsClient.clouds` splits each cell's puffs (no more of them than before) into layers: 55% a flat dark base,
  30% a tower over the middle 45% of the cell that climbs 45 (thunderstorm) or 85 (supercell) blocks as the cell
  gathers, paler toward its top, and for a supercell the rest an anvil: a pale sheet over the top, 1.15 times the
  cell's width and leaning downwind. A rain cell stays all base.
- A tornado's wall cloud: a lowered, turning ring of dark puffs round the top of its funnel (3 a tick for a tornado,
  5 for a monster), so the funnel hangs out of the cloud.

## 0.7.17: torches burn down, moonless nights go dark
- **Torches burn down** (`Torches`, `mixin/TorchBlockMixin`; Realistic Torches' idea, none of its code or art): every
  torch, floor or wall, whoever put it there, burns about two days on average (a chance on each random tick), then
  goes out with a hiss and is left a smouldering **Burnt-out Torch** (no light, drops a stick). Rain on a torch open to
  the sky puts it out within a couple of minutes. Flint and steel, a fire charge or a lit torch in hand lights it
  again. **Villagers relight them** (Andy): every 2 s the nearest awake grown villager within 24 blocks of a burnt
  torch walks over and lights it, so a village stays lit while its folk are up.
- **Dark nights** (`client/DarkNights`; Total Darkness / True Darkness's idea, LGPL, remade): moonlight follows the
  moon's phase. Full moon is a normal night; a new moon gives no sky light at all, so anything without a torch near it
  is pitch black. Lunar events always shine full. Overworld-type dimensions only. Shader packs light the world
  themselves and would undo it, so on any night darker than full moon an Iris shader pack switches off at dusk (with a
  note on screen) and back on at dawn (Andy).
- **Spider kinds** (`wild/SpiderKinds`, own skins from `tools/gen_spider_kinds.py` via `client/mixin/SpiderSkinMixin`; Spider Overhaul's ideas, all rights
  reserved, remade on the game's own spiders with our own art, no new mobs): a spider takes its kind from its biome the first time it
  loads. Birch: lies still in wait and pounces. Desert: tougher, bursts its spines (4 damage, 3.5 blocks) as it dies.
  Swamp: floats still like a lily pad, never drowns, swims at prey; its bite bogs you (pulled under in water, 6 s).
  Cavern (below y 0): drops amethyst, emerald or (rarely) diamond; spits web that holds its prey in a cobweb for 5 s.
  Jungle: 30% faster, pounces, poison bite, a poison pollen cloud as it dies. Ice (snowy biomes): bite freezes and
  slows. Cave spiders and spiders elsewhere are unchanged. Spider Overhaul's "Icy" slipping is freezing here instead.
- **Spider legs** (`client/mixin/SpiderLegsMixin`, Mo' Bends' idea; Mo' Bends itself fights Fresh Animations/EMF): every
  spider and cave spider leg is a thigh and a shin with a knee. Knees rise above the body, shins bend down to the ground
  and fold further on the step; legs twitch at rest, the abdomen breathes and sways as it walks. Tuning in KNEE_LIFT,
  SHIN_BEND, STEP_BEND.
- Spider kinds and legs seen in a cloud playtest (screenshots in spider-kinds/); torches and dark nights not yet.

## 0.7.18: sailing, stage 2: wind, oars, cannons and build kits (pack 0.10.67)
- Andy: "I want wind, oars, cannons, and the in world build kits" (after linking AlekiShips: MIT, but Forge/NeoForge
  1.20.1 only, so its ideas remade; none of its code or art).
- **Wind** (`sailing/Winds`): sails take the world's own wind (`Storms.windAngle()/windStrength()`, the one that drives
  storm cells), harder in rain and hardest under thunder. How much they catch goes by the angle off the wind: running
  0.85, broad and close reach 1.0, beam 0.8, close-hauled down to 0.3, in irons (within 40 degrees of head to wind)
  nothing. The helm readout shows sails, speed, wind strength, where it blows from and the point of sail; lying still
  it takes turns with the keys. Tuning in `Winds.ANGLES/CATCH`, `LEAST/MOST/RAIN/THUNDER`.
- **Oars** (`OarBlock`; planks, a stick and an iron nugget): set on the gunwale facing outboard. A ship with no sails
  rows on W/S; one with sails rows ahead on Space and backs oars on S once her sails are furled. Speed 0.12 x the
  square root of her oars, slowed by her weight (`Ship.ROW_*`), at most 4.4 blocks/s; rowing tires the one at the helm a
  little. Shipped, an oar lies along the gunwale; rowing, the ship draws them run out and sweeping through the water
  (`ShipRenderer.OAR_*`).
- **Ship's cannon** (`ShipCannonBlock`; iron, an iron block and planks) and **round shot** (iron nuggets round an ingot,
  makes 4; WeaponMod's cannonball works too). Right-click with gunpowder, then shot, then flint and steel or a fire
  charge: it fires as high as you look (-6 to 15 degrees). Works ashore and aboard. At the helm a left click fires a
  **broadside**: every loaded gun on the side you look to, 3 ticks apart. `ShipShot` (speed 1.6, gravity 0.015) holes a
  ship it hits (blocks within 1.3 smashed out, a quarter wash up, chests spill, her helm never), tears sails it passes
  through, hurts everyone within 2 (16 at the heart) and splashes into the sea. Andy: "cannons should break blocks. no
  mercy": shot that hits land smashes every block within 1.6 of where it strikes (`ShipShot.breach`; unbreakable and
  obsidian-tough blocks, blast resistance 1200+, hold; 30% drop as items). Keep cannons (`Cannon`) don't. A ship holed
  at the waterline (half her hold there flooded) or too heavy to float founders: she goes down slowly with bubbles and
  is laid on the bottom as a wreck, chests and all.
- **Build kits** (`ShipKits`; paper, string and a stick, the sloop adds a second paper and wool, the brig a third and
  an iron ingot): used looking at open water, a kit lays her whole frame (`ShipFrameBlock`) on the water, stern just
  beyond where you look, bow the way you face. Each frame shows what it wants (planks, fence rail, fence or log mast,
  sail, helm, oar, cannon, chest); right-click it with that. The kit's tooltip counts what she takes. When the last
  frame is filled her timbers are dried, her hold bailed, and she can cast off like any ship of blocks (casting off
  refuses while frames remain). Rowboat: 7 long, two oars. Sloop: 13 long, a mast and sail, 4 oars, 2 guns, a chest.
  Brig: 19 long, two masts, 4 oars, 6 guns, 2 chests in a two-high hold.
- **Streams in the sails** (`Jets`): a Wind user's Gale Stream or a Smoke user's Smoke Jet (Powers 0.10.67, read by the
  `jaymospowers_jet:wind/smoke` tag) adds way to a ship along the stream for each sail in it: 0.1 b/t per square root
  of sails filled from dead astern for a gale, 0.05 for smoke, less across her, backing her from ahead; furled sails
  catch 30%. Works with no one at the helm.
- Tested in a cloud dev client: all three kits laid, filled and bailed dry; sailing on a beam reach and in irons;
  rowing; loading a gun by hand; a broadside holing a target ship that then sank and lay as a wreck; a gale moving a
  furled sloop 6 blocks in 8 s; a world gun holing a brick wall.

## 0.7.19: neighbours: villagers' regard for one another (pack 0.10.68)
Andy: "make villagers have more interactions with each other dependent on their relationship with each other ...
negative interactions at lower reputation, positive interactions, higher reputation".
- **Regard** (`Neighbours`, attachment `neighbour_regard` on each villager, both sides kept): every pair of grown
  villagers has a regard of -100..100. Where it starts is the pair's own draw (-40..60, always the same for the two),
  plus 10 per step of each one's MCA personality (friendly, upbeat, peaceful, extroverted, relaxed +1; greedy, gloomy,
  anxious -1; crabby -2); MCA partners start at 70 or more, kin at 35 or more. Each remembers their 48 strongest.
- **Meetings:** each second a realm's free grown villagers (not at work, on guard, in a routine, afraid, talking to a
  player; daytime) have a 12% chance that one walks over to another within 14 blocks; at most 3 at once per realm,
  then each rests 45-120 s. What they do hangs on the regard (an MCA villager's low or high mood counts as 15 less or
  more): below -50 fights, arguments, shoves, snubs and the odd apology; to -15 arguments, snubs, shoves, spats;
  middling greetings, small talk, jokes, spats; from 15 gifts, shared meals (likelier at lunch), hugs; from 50 hugs
  and, for unattached MCA man-and-woman pairs who aren't kin, courting; partners are affectionate. At 88+ a courting
  pair may propose (35%) and, with MCA, marry (both sides of the family tree).
- Every meeting moves the regard (fight -8, argument -4, shove -3, snub/spat -2; greeting +1, chat/joke +2, meal/hug
  +3, gift/courting +4, apology/proposal +10) and an MCA villager's mood (fight -3, sour -1, warm +1/+2). Below -50 an
  argument becomes a shove and a shove a fight 40% of the time. A routine chat (`Routines`) adds +1, and nobody
  calls on someone they're below -15 with.
- **Fights** are scuffles: blows trade with knockback, sounds and crits but no damage; onlookers stop to watch and call
  out (children too); a realm guard within 16 who can see it breaks it up; the one who took more blows has the sore
  last word.
- Lines: `neighbours.jaymoskingdoms.<kind>.<open|answer>.0-7` (8 each, dealt as a shuffled deck), plus `watch`,
  `watch.child`, `guard`, `fight.lost`, `wed`; lines may use `%1$s`, the other's name. Talk floats over their heads.
- Ops: `/kingdom neighbours [kind]` makes the two grown villagers nearest you meet now and tells you their regard.
- Built; not yet seen in game.

## 0.7.21: grudges: villagers set each other up (pack 0.10.70)
Andy: "Villagers will set each other up at low rep, maybe giving you a quest to beat them up or lure them into a
beating". (0.7.20 / 0.10.69 are the Kingdom languages PR's.)
- **A grudge comes to a head** (`Grudges`): after any sour meeting (`Neighbours`) that leaves the pair at or below
  -50, there's a 30% chance one of them mutters about it and gets a `Quest.GRUDGE` on offer until tomorrow (gold
  mark; at most one a day each, and not over a quest they already offer). They walk up to players like any giver
  (`Approaches` QUEST_OFFER). Pays 6-12 emeralds and 6 MCA hearts with the giver; no reputation.
- **A beating:** land 3 blows on their enemy (or kill them, which counts, and is murder if seen). On the third they
  beg off and run (`Frights.scare`). MCA, the guards and golems react to a villager being hit as they always do.
- **A lure:** ask the enemy to follow you (MCA's Follow in their talk menu; Andy: "you should have to use the follow
  dialogue option instead of getting near") and they come along ("You've something to show me?"); lead them to the
  giver's hiding spot on the outskirts (26-36 blocks from the seat, shown on the tracker). The giver goes there and
  waits; when both are there the giver springs out and lays into them (`Neighbours.ambush`: a fight where only the
  giver lands blows), and the quest is done. Get more than 18 blocks ahead or take over 4 minutes and they give up
  (you can draw them again).
- Each grudge settled drops the two's regard by 10 more.
- Stories: `quest.jaymoskingdoms.grudge.<beat|lure>.0-3.title|text`; lines `neighbours.jaymoskingdoms.grudge|lure|
  lure.lost|ambush|mercy.0-7`.
- Ops: `/kingdom neighbours grudge_beat|grudge_lure`: the nearest grown villager comes to hate the next nearest and
  offers you the quest.
- Built; not yet seen in game.

## 0.7.24: vampire looks and sight (pack 0.10.83)
Andy: "vampirism should give you the sirben visual modifier" (Sirben is an MCA trait that changes the screen), then
"do both of those": blood vision and the vampire looks as well.
- **Sirben:** a vampire player's screen always wears MCA's own `mca:sirben` post effect (blur growing toward the
  edges, tinted red), asked for by name each frame (`mixin/vampires/SirbenMixin`, `BloodVision.SIRBEN`); MCA is
  GPL-3.0, so nothing of it is copied, and without MCA there's no Sirben.
- **Blood vision** (`client/BloodVision`, tuning at the top): only at night (fades in at dusk, out at dawn over 2.5 s).
  The screen's edges darken blood red (vanilla's vignette texture and pipeline, darkening green and blue only) and
  beat like a heart (double thump every 24 ticks). Every living creature within 24 blocks, except undead, golems and
  armour stands, glows through walls with a red outline that beats in time (`BloodGlowMixin`, `BloodGlowColorMixin`);
  only the vampire sees it, and a creature glowing for another reason keeps its own colour. Starving
  (`VampirePlayers.starving`, two nights without blood): the beat comes every 14 ticks, the edges go darker and blood
  shows from 40 blocks.
- **Looks** (`client/VampireLooks`, a layer on the player renderer; textures `tools/gen_vampire_looks.py`): everyone
  sees a vampire player's face and hands washed ashen; at night, two fangs under the mouth and red eyes that glow in
  the dark. The red eyes go where each skin's own eyes are (Andy: "try and match it on unusual skins"):
  `VampireLooks.findEyes` reads the skin once (face plus hat layer), takes the most common colour as skin tone and
  picks the row from 9 to 13 with the most mirrored pixel pairs that stand out from it (x 9-10 / 13-14 with plain
  skin between; or x 11-12 alone for close-set eyes; ties go to row 12), plus a second row when the eyes are tall.
  Eyes under a mask or glasses on the hat layer glow there too. A blank face, or a skin still downloading, uses the
  stock spots (row 12). Checked against all nine default skins: each lands on its eyes.
- `Vampires.VAMPIRE` on players now syncs to every client (villagers' never does).
- Built; not yet seen in game.

## 0.7.25: no shader message (pack 0.10.87)
Andy: "no shader disable message". Dark nights still switch Iris shaders off past a third of the night on a waning
moon and back on at dawn (`DarkNights`), but without the "A dark night: shaders are off until dawn" overlay line.

## 0.7.28: creative kits build the whole ship (pack 0.10.97)
- Andy: "is there a way to autocomplete a boat in creative?" A kit used in creative now fills every frame at once
  (`ShipKits.complete`: oak planks and fence rails, spruce fence masts, sails, helm, oars, unloaded guns, chests), joins
  the fences and sails up and bails her hold, as the last frame filled by hand would. Sneak-use still lays the bare
  frames. Survival is unchanged.
- Andy: "activating the ship breaks any attached light sources, cant we wimulate them with our lighting?" Torches,
  wall torches, lanterns (hanging too) and any other glowing block stay aboard when she casts off, and now light the
  water and deck around her as she sails: `Ship.shine` keeps an invisible `minecraft:light` block (waterlogged under the
  sea line) in each world cell a light piece is in, at its brightness, every `LIGHT_EVERY` ticks, and takes them up as
  she moves on, anchors, wrecks or is removed (`Lit` is saved so a reload takes up stale ones). A light someone else
  takes up is put back. `Hull.lights` lists the glowing pieces.
- Andy: "can we make our own ship kits?" **Ship's Plans** (`ShipPlans`, 3 paper + an ink sac): sneak + right-click the
  helm of a ship you've built (before launch or at anchor) and the plans draw her, block for block, with how deep she
  sat. Used on open water they lay her again, bow the way you face: in survival as frames (any full block, stair or slab
  becomes a hull frame wanting planks; fences and logs stacked 3+ high are masts, other fences rails; sails, helm,
  oars, guns and chests/barrels as themselves; lanterns, doors and other fittings are left for you, and the tooltip
  counts them); in creative she's built exactly as drawn (sneak for the bare frames). The plans are kept, so one design
  can be laid again and again; rename them on an anvil. `ShipKits.lay`/`room` now take any design.
- A wrecked ship's blocks go down solid ones first, so the torches and lanterns hung on them don't pop off.

## 0.7.42: sailing, stage 3: pirates and navies (pack 0.10.129)
Andy: "resume stage work". Stage 3 of `/mnt/project-files/sailing/Sailing-Plan.md`, all our own designs and art.
- **Crewed ships.** A `Crew` (`sailing/Crew`, saved on the ship) is who sails her: pirate, navy or merchant, her
  faction, name, captain, home, where she's bound, which gunner serves which gun, and each player's share of her hold.
  Her crew are `Folk` riding her at their stations (`Ship.SEAT`, synced; up to 24); her captain at the helm drives the
  same input a player would (`Ship.Captaincy`, `Corsairs`). Ships are built from our own designs (`Fleet`: sloop,
  brigantine, a three-masted 8-gun frigate, a gunless cog) out of sight and cast off at once.
- **Captains** (`Corsairs`): keep a lookout, chase, come about to bring a broadside to bear, sheer off when close,
  steer round land (probes ahead, swings, backs off when embayed), row when head to wind. Pirates attack any ship
  that isn't a pirate's (players' too); warships attack pirates and players their realm counts hostile; merchantmen
  run. Gunners lay their own guns with lead and elevation for range, reload (faster with a powder monkey), and shoot
  pistols at boarders. Kill the captain and she drifts (and a player can take her wheel); kill the gunners too and the
  hold opens, Lootr-style, a share per player. A ship that founders takes her crew down; sinking a realm's ship
  costs reputation with it.
- **Pirates** (`Pirates`, roles 70-75): captain (boss, cutlass and pistol, tricorn), gunner, deckhand, powder monkey,
  plus the kingdoms' shipwright and sea captain. Skins by `tools/gen_pirates.py`. Pistols use WeaponMod bullets when
  loaded, else arrows.
- **Pirate coves** (`PirateCoves`, camp kind `pirate`): on a coast near a coastal kingdom; a tarred shack over the
  hoard, a jetty, a black flag, deckhands round the fire; their ship's captain is the cove's leader, so killing him
  (or sinking her with him aboard) clears the cove. A lost ship is rebuilt after two days. Landing parties raid only
  coastal settlements, and the ship stands off the landing while they're ashore.
- **Navies** (`Navies`): a coastal realm with 1000+ emeralds builds a pier from its nearest shore (only over open
  ground and water), a shipwright, and a warship crewed by its guards under a sea captain, rebuilt two days after a
  loss. Merchantmen sail to the nearest friendly harbour (one out at a time); arriving, they pay both realms and
  carry goods into the other's storage (caravans by sea).
- **Quests:** "The Pirates of X" (a king's camp quest on the cove's captain) and **escort** (`Quest.ESCORT`): go to
  the king's harbour and a merchantman sails with you; she arrives, it's done; she sinks, it fails.
- **Treasure wrecks** (`TreasureWrecks`): pirate holds may carry a Pirate's Chart (a real map with an X on ocean); the
  wreck is laid on the seabed when you come near, chests full of treasure (a chest buried if the X is on land).
  Rarely, sailing the open ocean, you happen on one nobody charted.
- Ops: `/kingdom camp found pirate`, `/kingdom fleet harbour | navy | merchant | pirate | wreck | chart | info`.
## 0.7.43: natural disasters, stages 2 and 3: the land's own, earthquakes, kingdoms and forecasts (pack 0.10.130)
- Andy: "okay resume stage work while we test" (plan `/mnt/project-files/disasters/Disasters-Plan.md`, stage 2). All
  in `disasters/Calamities`; stage 1's `Storms`, `Tornadoes`, `Storm` and `Tornado` are untouched. Every
  `disaster_days` (6) on average, as often as stage 1's supercells, the land under someone brews what suits it:
  - **Sandstorm** on sand: a 150-230 block patch drifting with the wind for 5-8 minutes. Out in it, the fog closes to
    20 blocks of orange dust (`client/mixin/disasters/CalamityFogMixin`, dimmed at night), sand blows past, the wind
    howls and pushes you, and **Sand Drifts** / **Red Sand Drifts** (`SandLayerBlock`, layered like snow, up to 3 deep,
    don't melt, dig for a sand) pile up round you, paths included.
  - **Blizzard** in snowy land: a 14-block white-out, snow layers up to 4 deep, and anyone in the open freezes (the
    game's own freezing: frost on the screen, slowed, then hurt), unless wearing leather or within 4 blocks of a fire,
    campfire or lava.
  - **Wildfire** in dry land (savanna, badlands): dry lightning strikes 45-75 blocks upwind and a fire line runs
    downwind over grass and wood for 2-4 minutes (90 blocks at most), with tall signal smoke; rain puts it out. A
    settlement within 120 blocks of one throws 80% of its grown villagers at the fires (else 40%), every one with a
    bucket (`Firefighting`). Not a crime: only player arson is.
  - **Hail** anywhere else: a thunderstorm drifts in carrying it. Besides, any thunderstorm over someone may drop hail
    (12%, a supercell 40%), for 1.5-3 minutes under the middle of the storm: hailstones bounce and clatter, sting
    anyone bareheaded for half a heart now and then (a helmet just rings), crack glass and beat down crops.
  - **Flood** after long rain: 7.5 minutes of rain on someone, then a small chance each 10 s that the river or lake
    near them rises 3 blocks, a layer at a time, over the low ground beside it (up to 3000 blocks, 44 round), stands 3
    minutes and drains. Every block it put down is taken back out, at once if the server stops or skies are cleared.
- The `disaster_damage` rule off: no drifts, no broken glass or crops, no wildfires, and floods fill only open air.
- `/kingdom disaster sandstorm|blizzard|wildfire|flood|hail` (ops) calls one in by you; `clear` ends them too.
- Kept cheap (Andy asked that of the clouds): sandstorms, blizzards and hail are a few numbers sent to the clients 2x a
  second (`Calamities.Sync`), which draw the fog and a few dozen specks a tick round the camera (`CalamitiesClient`,
  `GustParticle`); the server only works round each player under one.
- Stage 3 (Andy: "next stage?"):
  - **Earthquakes** (`disasters/Earthquakes`), a land disaster: half of those near mountains (biome tag or ground 50
    above the sea), 5% elsewhere; magnitude 1-3 (felt 60/100/150 round, 15-30 s; a Blood Moon adds one). Clients rattle
    the view (`CalamityShakeMixin` on the hurt bob, so aim never moves) and rumble; anyone standing staggers; plain
    whole blocks of hardness 3 or less hanging over air round each player (cave ceilings, overhangs, roofs) shake loose
    and fall, hurting what they land on; magnitude 2-3 open jagged cracks 3-6 deep in natural ground; the strongest may
    swallow a 3-5 round, 6-10 deep sinkhole once. All block damage waits on `disaster_damage`.
  - **Kingdoms react** (`DisasterRelief`): a settlement within a disaster's reach is struck: news (across the land for
    a realm, round about for a village); its treasury pays for repairs (storms 10, fire 25, flood 20, quake 25 a
    magnitude; a NOBLE king pays half again and says so); a TYRANT levies a storm tax of twice that on top; a PARANOID
    king blames anyone near with strange powers (Powers' oddity tags), -40 reputation. Two of its villagers ask for
    help: bring food (bread, baked potatoes, steak or cod, a GATHER quest) and, after sandstorms, fires, floods and
    quakes, clear the rubble (new `Quest.RELIEF`: break stone, earth, sand, drifts, timber within 96 of the seat).
    Rebuilds puts the buildings back as ever.
  - **Forecasts**: land disasters (not floods, which come of the rain, nor stage 1's storms) are foretold half a day
    ahead and come about whoever is within 400 of the spot then. A court wizard, sneak-used, tells you what's coming
    near him; the Traveller's Journal shows "The Weather Ahead" first; the new **Almanac** (book + clock + lapis) reads
    them out (what, which way, how far, how many hours), or promises fair days. `/kingdom disaster forecast` foretells
    one where you stand, a minute off; `/kingdom disaster quake` shakes now.
  - **Moons**: a Harvest Moon brings no disaster (stage 1's supercells wait too; one foretold for it never comes); a
    Blood Moon twice as many, and quakes one stronger.

## 0.7.45: character creation can't be skipped (pack 0.11.1)
- Andy: "force character creation on spawn". In order, on first joining:
  - **MCA's destiny screen** (your look, then where you start). MCA already opens it to everyone whose look isn't
    set (its `launchIntoDestiny`, on by default) and won't let it shut. The class screen now waits for it:
    `Classes` JOIN skips asking while `McaCompat.destinyPending` (MCA's `PlayerSaveData.isEntityDataSet`), and
    `mixin/mca/McaDestinyDoneMixin` asks (`Classes.afterDestiny`) when MCA's empty `DestinyMessage` says it's done.
    Without MCA the class screen opens on joining as before.
  - **The class screen**, forced as before (no Later, Escape does nothing), and now it comes back on the next tick if
    anything else (another mod's screen) puts it away (`ClassScreen.owed`; 3 s grace after Choose while the class
    syncs). The same holds after a Writ of Rebirth or a court wizard's respec.
- **Protected until done** (`Classes.unmade`: no class yet and the client has this mod): no damage (except what
  bypasses invulnerability, like the void), and mobs within 32 that go for them are called off every 10 ticks. MCA
  makes them invisible during its screen too.

## 0.7.46: village life (pack 0.11.5)
- Andy: add Village Life's features (CurseForge `villagelife`, VillageLife+, Forge 1.20.1) to our systems. It's All
  Rights Reserved, so these are our own remakes of the ideas. Already covered and skipped: names and family surnames,
  partners and children, moods, per-player reputation, profession/time-of-day talk (MCA); routines, children trailing
  grown-ups, neighbours' regard (Routines, Neighbours); a mayor (our rulers, lords and ranks).
- **Festivals** (`Festivals`): a realm at peace with food in store keeps its own festival about once in 8 days (the
  same days each time, `OWN_EVERY_DAYS`). At dawn the bell rings and players near or sworn to it are told. All day its
  free townsfolk (tradespeople get the day off) gather at the square: dancing, feasting (up to 24 food from the
  storehouse, then bread of their own), toasts, one musician playing the flute; children dash about. Each lifts their
  MCA mood. A player at the square is welcomed once a festival (+5 reputation, unless held in ill repute). From dusk
  fireworks go up in the realm's colours. **`/kingdom festival`**: a lord of the realm (or its player ruler) calls one,
  32 emeralds from the treasury, held today (tomorrow if past noon), not within 3 days of the last called one, not at
  war. Ops: `/kingdom festival now`. Called festivals are saved (level attachment `festivals`).
- **Village pets** (`Pets`): a settlement wants a cat or dog per 5 grown (4 at most). Every 2 minutes it may take in a
  stray cat or wild wolf about the place (or an orphaned pet whose villager died); with none about, a stray turns up
  by someone at most once a day (60% a dog). Pets are tamed with the villager as owner (vanilla's taming: they trail
  after them, a dog goes for whatever hurts its villager, players can't order them about), wear the realm's colour on
  their collar and are marked with attachment `village_pet`. Villagers fuss over their pets; children throw sticks for
  dogs, who bound off after them. A pet's death sinks its villager's mood (-15).
- **Couriers** (`Couriers`): **`/kingdom deliver <player>`** in a realm (seat within 128) whose reputation for you
  isn't negative: the nearest free grown villager within 24 takes what's in your hand and walks it to that player
  (within 256, same world), hands it over and says who sent it (+1 MCA heart with you). Not reached within 2 minutes:
  they bring it back; nor you: they leave it where they stand. Killed on the way: it drops. The server stopping hands
  everything still out back. Couriers are busy for `Routines.free`.
- **Hunger and weariness** (`Spirits`): a realm whose (loaded) storehouse has no food at all has hungry townsfolk:
  rumbling bellies, grumbles, MCA mood -2 each time, and no lunch routine until it's stocked. From time 10500 to
  nightfall they yawn and say they're for bed; at dawn they stretch.
- `Routines.free` now also excludes festival-goers and couriers (`Routines.freeOf` is the rest, for Festivals).

## 0.7.47: everyone is a villager (pack 0.11.12)
- Andy: "lock everyone to make a villager instead of vanilla or player". MCA stores each player's body as
  Villager, Player (player-shaped with the villager skin) or Vanilla; `mixin/mca/McaVillagerBodyMixin` makes
  `VillagerLike.PlayerModel.byId` always answer VILLAGER, so every stored choice (old saves too) reads back as a
  villager, for drawing and for size. `client/mixin/mca/McaVillagerBodyChoiceMixin` drops the three body buttons from
  MCA's character screen (`VillagerEditorScreen.addModelSelectionWidgets`).

## 0.7.49: imbued blades keep their element
- Andy: "sorcerer imbuements carry over onto dropped weapons, because of this sorcerers can only imbue one weapon at a
  time". The Imbue used to sit on the Sorcerer (any blade they swung burned, the dropped one didn't). Now
  `Spellcraft.Charge` (sorcerer, name, element, until, stamp) lives in the blade's custom data: dropped, handed over or
  looted, it bursts in anyone's hand (mobs too) until it wears off (`IMBUE_TICKS`). A Sorcerer keeps one imbued blade:
  `IMBUED` holds the stamp of their current one, and a blade with another stamp has gone cold (taken off when a player
  holds or carries it, swept every `SWEEP_EVERY` ticks). After a restart the blade's own clock decides. Tooltip:
  "Imbued with Fire by Andy (95s)". The Wind Cyclone's imbued element now comes from a blade the player holds or carries.
- **0.7.51, emeralds in bundles; private conversations:** every price the kingdoms ask (a villager's bargain, tolls,
  fines and bribes, the wizard's writ and respec, stables, rickshaws, a retinue, a lost coach fare) now counts and
  takes emeralds carried in a bundle too, loose ones first (`Purse`). Before, emeralds in a bundle didn't count, so a
  bargain you could afford was taken as a refusal and ended. What's said to one player in their own conversation (an
  approach and its replies, a quest given, refused or thanked, the wizard's work) is shown only to that player
  (`Speech.tell`); ambient talk, cries and festivals are still heard by everyone near.
- **0.7.52, every bubble for everyone:** what's said to one player is seen by everyone near again, and more of it: 0.7.51's
  private conversations are undone, and what was only ever shown to the player spoken to (an MCA villager's side of a
  conversation, a remark on your accent, the wizard's disaster relief) now floats over the speaker's head for the
  players around too, each in their own understanding of the tongue (`Speech.overheard`, `McaMessageMixin`). The
  client shows a line once if it arrives twice within 1.5 s (`SpeechBubbles.REPEAT_MS`).
- **0.7.54, hearths and chimneys:** villages burned down: golems and guards carried corpses to the nearest lit campfire,
  often a fireplace inside a wooden house, and a burning corpse lights what it lies by (Kitchen, like lava). Now every
  campfire, fire or lava source inside a village piece (vanilla's, Epic's, any structure with "village" in its id)
  gets a stone hearth as the village generates (`Hearths`, from `StructureStartMixin`): what catches within 1 block,
  and the floor within 2, turns to stone brick of the same shape (stairs, slabs, walls; logs to cobblestone), carpets
  near it come up; beds, doors, chests and other block entities are left. Under a roof it gets a chimney: a 3 x 3 stone
  brick hood from 2 blocks over the fire up through the roof (wood over the fire turned to stone), standing 2 blocks
  above it, open at the top. Chimney-top campfires (open sky) only get the hearth. New chunks only. A pyre
  (`Undertakers.isPyre`) must now be under open sky with nothing that catches within 2 blocks, so no corpse is
  dropped into an indoor hearth again; builders' fire pits pass.
- **0.7.55, tailors:** Andy: "a way to change clothes", then "only possible at a tailor villager, and for varying
  price". A grown shepherd (the villager at the loom) is the tailor: MCA's interact page has a Change Clothes button on
  them alone (`McaTailorButtonMixin`). They name their fee and MCA's character editor opens held to its clothing page
  (`Tailors`, `WardrobeScreen`: MCA's own Needle and Thread screen without the needle). Body, hair, genes and the
  destiny can't be reached from it; picking an outfit pays the fee in emeralds (bundles count), saves it and closes;
  Escape changes nothing and costs nothing. The fee: 3-7 emeralds by tailor, one off per 25 hearts with them (up to 3),
  one on per 25 below zero (up to 2), half for family, at least 1; free in creative (`Tailors` constants).
- **0.7.56, farsighted monsters and stalking creepers:** two mods remade (neither has a 26.3 Fabric build).
  Farsighted Mobs (LGPL-3): every monster now notices you from at least 24 blocks (vanilla skeletons, creepers and
  spiders: 16), 8 more on Hard and up to 4 more at the highest local difficulty; monsters that already see farther
  (zombies, our legends) keep theirs, and illagers and witches keep their own (`Farsight`, a transient follow-range
  modifier set as each monster loads). Stalker Creepers (Forge/NeoForge, no licence given): a creeper hunting you
  freezes and stares while you look at it (within about 53 degrees of your view, in line of sight) and creeps on as
  soon as you look away; within 3.5 blocks, or once it hisses, looking no longer stops it (`Stalkers`, a priority-1
  goal holding its legs).
- **0.7.57, attributes and heritage:** PlayerEx and Origins remade (neither has a 26.3 build; Andy: "build em both").
  **Attributes** (`Stats`, the K screen's fourth tab): your character level is Survival + Stealth + Strength levels
  added up; every 2 levels is an attribute point for Might (+2.5% damage), Agility (+1% speed), Finesse (+2.5% attack
  speed), Endurance (2.5% less hunger), Grit (+0.5 armour, +2% knockback resistance) or Wits (+3% skill experience,
  +0.1 luck), each to 10. Your class gives a free point in its favoured attribute and your heritage one or two more.
  No health: hearts stay Hearty Meals'. The points sit in the tree's ranks (`stat_<name>`), so a respec gives them back.
  **Heritage** (`Heritage`, `HeritageScreen`): a forced step between MCA's destiny and the class, picked for life
  (ops: `/kingdom heritage set|clear|get`). Eight, passive only: Heartlander (2 free points), Stoneborn (mines faster,
  slower), Tideborn (water breath/swim/mining, hungrier dry), Sylvan (soft landing, quieter thieving, lighter blows),
  Verdant (green skin and antennae, sun-fed, burns longer), Emberborn (burns briefly, thrives hot, starves cold),
  Frostborn (never freezes, thrives cold, starves hot), Nightborn (stronger in the dark, weaker in the sun). Players
  with a class already get the heritage screen once on their next join.
- **0.7.59, skill commands:** `/kingdom skills set <players> <survival|stealth|strength> <level>` (perks refunded if the
  new level can't pay for them), `add <players> <pillar> <xp>`, `get <player>`, `respec <players>` (perks back, levels
  kept) and `reset <players>` (all gone), for ops (`SkillCommands`).
- **0.7.60, fire spreads, ice bites:** an imbued (or thrown) fire burst now also catches up to 3 more creatures within
  4 blocks (players spared), each lit for 4 s and burnt for 1, like lightning's chain. Ice's burst freezes far longer
  (300 ticks, long enough for the cold's own damage), slows for 4 s, does 2 damage, and chills up to 4 creatures within
  3 blocks (frozen a while, slowed 3 s). (`Spellcraft.strike`)
- **0.7.61, villagers react to your deeds:** (`Deeds`) put out a fire in a settlement (a watched fire that goes out
  within 6 blocks of a player who just used their hands or an item), kill whatever struck (or was going for) a villager,
  or bring a captive home, and they react: the one saved thanks you (30% hand you a gift), witnesses cheer, MCA hearts
  and mood rise, reputation with the settlement (fires +1, saves +6, capped at 60 a day; freed +10, brought home +40).
  For 7 days folk there call out to you about it as you pass and gossips talk of it; each day word may reach the
  nearest settlement that hasn't heard ("they say in X you..."). 12 lines per family, dealt as a deck.
  (`Captives`) villagers in chains in camps (80%, 1-2 when a player first comes within 64; a camp left without takes
  another after 3 days, 50%) and in 35% of dungeons (spawner on cobblestone): taken from the nearest settlement within
  1500, bound (NoAI, invulnerable, unseen by monsters), they cry for help. Right-click breaks the chains; they follow you
  home (lost after 30 s more than 48 blocks behind, or 3 days), and give 3-8 emeralds there. Ops: `/kingdom captive`.
- **0.7.61, foreign voices grunt:** MCA's spoken voice lines (greet, yes, no, celebrate, angry) are sent per player:
  those who don't understand the villager's tongue (`Languages.understands`: fluent, family or sworn) hear the vanilla
  villager grunt instead (`EntityVoiceMixin` on `Entity.playSound`, `Languages.voice`).
- **0.7.62, raiders leave fires burning:** (`Firefighting.ours`) raiders and war parties' soldiers (`Assault.attacker`)
  never fight fires, theirs or the town's, and no folk fights a fire on enemy land (`Assault.enemyLand`). The town's own
  villagers, folk and golems still do.
- **0.7.65, the whole realm on alert in a war:** (`WarAlert`) from the day war is declared until peace, every realm on
  either side (vassals and villages under them too) is on alert: the seat's bell peals, then tolls 3 strokes every
  2 minutes; once a war party musters against it (`WarParties.incoming`) the bell rings the alarm every 2 s (the real
  bell swung, so vanilla villagers hide too). Its villagers carry sandbags out (up to 4 at a time) and lay 5 walls of
  5 facing the nearest enemy seat (16-22 out) and 3 of 3 behind (13-18 out), on open ground only (no roofs, roads,
  fields, doors). With a party coming, civilians run home and stay in (`Frights.shelter`) until 10 s after it's gone,
  and idle guards and archers stand behind the front sandbags facing the enemy. Bags laid are saved (`war_sandbags`)
  and taken down at peace, 2 a second. New block `Sandbags` (sacks along an axis; 4 from string + sand; the realm's
  own are `raised` and drop nothing).
- **0.7.66, the night's gifts** (ideas from the Vampires Strike Back modpack's origins, remade; nothing of theirs used):
  vampire and werewolf players get two keys and a sense (`NightGifts`). Vampires: `O` Blood Dash (look up for a great
  leap, one more dash in the air, no fall damage till they land); `P` in the air Groundpound (down like a stone, throws
  back and hurts everyone within 5 blocks, no fall damage, every person within 32 shows through walls for 10 s); bat
  wings (Space in the air glides with no elytra; 2 minutes of gliding, mended by experience; spread on the back while
  gliding); Lifeleech (kills heal and lend Absorption that builds with quick kills); quick below y 60 out of the sky;
  the sun drains their hunger. Werewolves: `O` Leap (look up to jump straight up; stronger as the beast); `P` Howl
  (shows people through walls; as the beast it throws everyone back and sends villagers running; other werewolves hear
  where it came from); Frenzy after a kill; the beast climbs any wall it runs into and falls far better; quick at
  night in forests. Both: a kin-sense compass top left (arrows and distances to the 3 nearest of their kind).
  Dhampirs: a golden apple eaten between the bite and nightfall half-saves you (no sun, starving or double fire, no
  night strength, all the gifts). Wolfbloods: the bone ritual on any night but a full moon half-curses you (never
  change, human-form gifts; finish the rite under a full moon). Newly turned vampires wake in a crypt dug beneath them
  (coffin bed = respawn, candles, ladder up); werewolves wake from their first moon in a den. Natural ground only.
  `/kingdom nightgift <vampire|dhampir|werewolf|wolfblood|crypt|den|none>` for ops. Villagers' gossip hints at all of
  it as old wives' talk (`NightGifts.gossip`, 18 lines; 12% of gossip, 40% to the cursed or half-cursed).

## 0.7.67: two-handed weapons reach further
Mobs swinging a two-handed weapon reach `MobCombat.TWO_HANDED_REACH` (1.25x) further and sweep `TWO_HANDED_ARC` (1.25x)
wider, matching players (Powers 0.11.75, `core/TwoHanded`).
- **0.7.68, secret identities** (`Identities`, with Jaymo's Powers 0.11.76 costumes): a player masked by their costume
  (Powers' tag `jaymospowers_masked:<key>`) is a stranger. Done masked, a deed or crime goes on the costume's own
  reputation and wanted time with that settlement (`Reputation.get/change`, `Theft.wanted/outlaw/commit/arrest` ask
  `Identities.persona`); guards hunt the costume when they see it, not the player bare-faced. Members are strangers
  in a mask (no storehouse rights, no greetings, no MCA hearts, no remembered deeds). Putting the mask on or off in
  sight of a settlement's people (16 blocks, looking), lifting it for a guard, or being seen using the same power
  3 times masked and 3 times bare-faced (24 blocks, sightings 30 s apart) tells that settlement who wears it: the
  costume's reputation is added to the player's and its wanted time carried over, and from then on it treats the
  costume as the player. Word spreads each day (40%) to the rest of that realm. A paranoid king's guards stop masked
  strangers (Approaches topic `unmask`): lift the mask, be searched (stolen goods: fine and arrest), or refuse (the
  costume's name suffers; 30 s to leave the settlement or the guards come). Ops: `/kingdom identity [list|link
  <settlement>|unlink [<settlement>]|forget]`, `/kingdom talk unmask`.
  With MCA a masked player is a stranger to its villagers: their memories (hearts) of you are kept under the
  costume's own stand-in id (`McaMemoriesMixin` on `VillagerBrain.getMemoriesForPlayer`, by the synced `masked_as`),
  so the costume starts at 0 hearts and your real hearts are untouched; your names in what they say to you become the
  costume's (`McaMessageMixin`, `Identities.withoutName`). Tyrants and paranoid kings (their own or their liege's)
  exile the vigilantes they know: when a settlement of theirs learns who wears a costume, and whenever that player is
  on its land, its guards see them out of the city (`Retinue.exile`, 3 days, where the realm has a ruler of its own),
  and after 60 s still there they're taken by force (`Theft.arrest`, 2 minutes).

## 0.7.69: tailors sell Costume Tables and sew costumes
- Andy: "Village tailors (the ones who already change MCA clothes) sell the table and can make a costume for you for
  emeralds" (`TailorCostumes`, with Jaymo's Powers 0.11.81). A tailor's MCA page has two more buttons under Change
  Clothes (only on a tailor, only with Powers): **Sew a Costume** names a price and opens Powers' designer in its
  paid mode by running `/costume tailor <player> <price> <tailor>` as the server (you design it; the emeralds are
  taken when it's sewn, near the tailor, within 5 minutes); **Costume Table** sells `jaymospowers:costume_table`.
  Priced like the outfits (`Tailors.price`): table from 10, costume from 8, plus the tailor's own rate, less for
  friends, more for people they dislike, half for family.

## 0.7.72: vassal lords set apart from kings
- Andy: "vassals should be differentiated from kings". A kingdom made a fiefdom (war terms, a taken seat, `/kingdom
  vassal`) kept its living king: crown, netherite, yellow bar, "King of X", king's quests, its own colours. Now every
  Folk checks its realm's type each second (`Folk.reform`): its folk take the overlord's bordered arms and colours, and
  the king sets his crown aside and becomes the fiefdom's lord in place (circlet, trimmed diamond, purple bar, "Baron/
  Count/Lord of X", 220 health, kept in proportion); players within 48 blocks are told. A lord leading a free kingdom
  is crowned king the same way.
- A lord walks his round with at most 4 guards (`Royals.LORD_ESCORT_MOST`; a king up to 8).
- A fiefdom's audience says whose land it is: "X is a fiefdom: its lord holds it for Y and pays him tribute."
- The "Vassal of X" title (read as if he served his own fiefdom) is now "Count of X" for new lords.
## 0.7.73: guards are downed, not killed
- Andy: "guards, when killed dont drop as a corpse, instead they get downed, and either crawl or otherwise fall into a
  downed animation, they can be disarmed but not looted" (`Downed`, with Jaymo's Powers 0.11.90). The blow that would
  kill a realm's guard or archer (Folk, a villager in its service, or any villager in MCA's guard/archer job) downs
  them instead (saved, synced `downed` attachment): half crawl face down away from whoever felled them, the rest lie
  on their backs (client `DownedPose`). Their AI stops (`DownedMobMixin`) and they drop
  nothing (`DownedMixin`), and they can't be pickpocketed; right-click takes the weapons from their hands. Andy: "they
  should get back up unless they are hit by a player, they can be hit repeatedly until they bleed out, up to 10 times":
  left alone, they get up after 1 minute (`RECOVER_TICKS`) with 30% health; a player's hit is a wound (saved
  `downed_wounds`), after which they never get up and bleed out 2 minutes after the last hit (`BLEED_OUT_TICKS`), and
  the 10th hit kills (`MAX_WOUNDS`). Andy: "Mobs fire and falls can kill them too and you can throw them": downed,
  they have 6 health (`DOWNED_HEALTH`) that mobs (who can target them again), fire, falls and the void take as usual;
  sneak + right-click carries one over your shoulder (a passenger, placed by `DownedMixin`), then right-click throws
  them (`THROW_SPEED`) and sneak + right-click sets them down. Any death is a real one (wars, quests, crimes), with no
  corpse and no loot.

## 0.7.75: Emberborn fireproof, sapped by the cold; Frostborn ice prison; heritage cards scroll (pack 0.11.102)
- Andy: "emberborn should be immune to fire and weaker in the cold / winter". Fire, lava, hot floors and burning
  never hurt an Emberborn (`DamageTypeTags.IS_FIRE` cancelled), and flame never stays lit on them (burn time -100%,
  put out each second).
- In the cold (biome base temperature 0.2 or less: snowy and frozen lands) their blows are 15% weaker and they walk
  10% slower (`EMBER_COLD_WEAK`, `EMBER_COLD_SLOW`); freezing in powder snow doubles both (`EMBER_FREEZING_*`).
  The old 25% hunger in the cold stays, and now counts freezing too. The pack has no seasons mod, so cold lands are
  its winter.
- Andy: "frostborn should be able to freeze an enemy in a block of ice, once a day". `FrostPrison`: a Frostborn presses
  `;` (rebindable) at a living thing they look at (`Arcana.sighted`) and it's locked in a shell of packed ice for 10 s
  (`HOLD_TICKS`), held still, numb and frozen through; the ice placed only in air, removed when it ends (or on
  shutdown). Once per 24000 ticks of game time (`COOLDOWN_TICKS`, kept through death). Things over 2 wide or 3.2 tall
  don't fit.
- Andy: "heritage screen text is cut off, it should scroll". The heritage pane's rows wrap instead of cutting off;
  the pane scrolls with the wheel, and pointing at it while it overflows scrolls it slowly on its own.
- Andy: "add all zfighter lineages to the heritage screen ... so anyone can be any of those races, not just z
  fighters". New **Earthling** heritage (Wits + Endurance; skill tree learns 25% faster, `EARTHLING_XP`); with
  Warrior-born, Frostborn and Verdant all four lineages are heritages. Jaymo's Powers now gives anyone of that blood its
  race (see the pack MODLOG). The `;` key is now the heritage gift: a Frostborn's ice prison, a Warrior-born's tail
  (`Heritage.gift`, tag `jaymoskingdoms_tail_toggle`). Heritage list rows 19 high so ten fit.
- Andy: the ice prison "should be frostborns left mouse hold": Jaymo's Powers' race hold (left mouse held bare-handed,
  not at a block) tags the player `jaymoskingdoms_frost_prison` and `FrostPrison.tick` casts it. `;` is only the
  Warrior-born tail now. Verdant card mentions the stretch punch (Powers' `RaceMoves`).
## 0.7.77: the Emberborn glow, captives skinned (pack 0.11.104)
- Andy: "add some of the fire effects to ember born as well, they should glow in the dark". An Emberborn player
  (client `EmberLook`) has embers under the skin of the hands and forearms (a glowing hand, cracks of fire climbing
  toward the elbow) and eyes of ember gold, both drawn emissive over their own skin so they shine in the dark; the
  eyes are found on each skin the way a vampire's are (`VampireLooks.glowingEyes`, now shared). Now and then
  (`FLAME_EVERY` 12 ticks) a small flame, sometimes with smoke, rises off a hand; not in water or rain, not round your
  own first-person camera. Textures from `tools/gen_ember_look.py` (vanilla's fire colours, nothing copied).
- The heritage attachment now syncs to everyone (was its own player only), so other players see the glow.
- Andy: "prisoner npcs are not skinned either". Captives (`Captives.take`) were built bare with MCA's
  `VillagerFactory.build`, which skips `finalizeSpawn`, where MCA rolls genes and skin; they're now finalized as a fresh
  spawn before they go in.

## 0.7.78: mimics keep Lootr chests' loot (pack 0.11.106)
- Andy: "are our lootr chests dropping correctly?". Lootr's chest is a `ChestBlockEntity` that still carries its loot
  table, so 6% of opened structure chests (`MIMIC_CHANCE`) woke as mimics, but `unpackLootTable` does nothing on a
  Lootr chest (each player's share lives in Lootr's own data): the mimic held nothing and the chest was gone for
  everyone. `Monsters.mimic` now rolls the chest's loot table itself for the player who opened it (luck counts), so a
  mimic always drops that player's share. Vanilla loot chests behave as before.

## 0.7.79: a look for every heritage (pack 0.12.0)
- Andy: "give every lineage a new visual like emberborn ... dont change warrior born or the hearthborn [Heartlander]".
  Designed as mockups first (project files `art-overhaul/lineages/`) and changed twice on his word. Client
  `HeritageLooks`, textures from `tools/gen_heritage_looks.py`; drawn like `EmberLook` (marks lit like skin, more
  emissive over them, eyes found on each skin with `VampireLooks.glowingEyes`, a particle off a hand now and then):
  - Stoneborn: stone over the hands, amethyst glowing in its cracks; violet eyes, glowing by day only; amethyst glints.
  - Tideborn, fishmen: gills on the cheeks and sides of the head, fins (flat model parts) in one of three sets by UUID
    (crest; ear fins; crest, ear fins and long arm fins), all with fins on the backs of the forearms; scales and
    deep-sea lights on the forearms; cyan eyes; bubbles under water, drips on land.
  - Sylvan: a thick vine winding round each arm, its own way on each player (four drawings by UUID); pale green eyes;
    fireflies.
  - Verdant: only pink forearm patches that glow in the dark (no eye glow, no particles).
  - Frostborn: the same pink patches, rime on the fingers; ice-white eyes; snowflakes.
  - Nightborn: shadow soaking the hands, stars in it; moon-silver eyes glowing by day only; dark wisps at night only.
  - Earthling: training wraps and a ki glow round the fists; no eye glow, no particles.

## 0.7.80: Tideborn wet and dry, Sylvan glow dims, heritage text trimmed (pack 0.12.3)
- Andy's notes on the heritage screen and looks:
  - The hint under the heritage cards ("Your heritage gives free attribute points ...") shows once a game session
    and fades out after 5 seconds (`HeritageScreen.LEAD_MS`, `LEAD_FADE_MS`).
  - Cut from the cards, for players to find out on their own: Verdant's stretch punch, Frostborn's ice prison,
    Warrior-born's tail keys and villager fear and its growing-out-of-the-flaw numbers, Earthling's "no tail, no
    mending, no frost".
  - Tideborn get wet touching water or rain and stay wet `Heritage.TIDE_WET_TICKS` 2400 (2 minutes) after (`WET`,
    saved). Wet, their blows land `TIDE_WET_DAMAGE` 15% harder and they drip; dry, the drips and glow stop and the
    hunger flaw starts. Their marks, eyes and fin edges glow under water always, on land only at night while wet.
  - Sylvan glow dims while sneaking and goes out entirely for a Thief or anyone with a Stealth perk; no fireflies
    while sneaking. The server syncs both through `Heritage.LOOK`.

## 0.7.81: heritage looks under costumes (pack 0.12.5)
- With Jaymo's Powers loaded, `PowersCover` asks its `BodyCover` (by reflection) what a costume or the symbiote's suit
  covers: under a face mask or the suit no heritage look, Emberborn marks or vampire face at all; under a hood no
  antennae or Tideborn head fins; under any costume no Tideborn arm fins poking through the sleeves.

## 0.7.82: world tiers (pack 0.12.9)
- Andy: "world tiers are just what i need" (Apotheosis). Our own take on the idea, `WorldTiers`: no code, art or text
  from Apotheosis. Five tiers, Hearth (the plain game), Borderlands, Wildlands, Dread, Legend. Each player picks any
  tier open to them on the World Tier screen (`client/TierScreen`, a button on the skill screen K, or its own key,
  unbound at first); a minute between changes, none in creative. Ops: `/kingdom tier set|unlock|get`.
- Every second, hostiles within 40 blocks of a player (monsters that aren't a player's thrall, werewolf beast or pet;
  barbarian-style folk, orcs, goblins, assassins) take that player's tier if it's higher than theirs (`MOB_TIER`,
  saved): more health (15/35/60/100%), damage (10/25/40/60%), armour (+2/4/7/10), toughness (+0/2/4/6) and speed
  (0/5/8/12%), keeping how hurt they are. On a server the toughest tier of anyone who comes near wins.
- The player gets luck (+1/2/3/5), extra experience from tiered foes (15/30/50/75%), and a chance for each stack of a
  tiered foe's drops, or of a chest they open, to come doubled (10/20/35/50%).
- Tiers open by feats (`WorldTiers.Progress`, saved, synced to its player): great foes slain (anything with a boss
  bar, a monster of legend, the Wither, the Ender Dragon, an elder guardian, a warden; everyone within 64 blocks
  shares the kill, 160 for the dragon). Borderlands 1; Wildlands 5 and a trip to the Nether; Dread 12 and the Wither;
  Legend 25 and the Ender Dragon. Numbers are the constants at the top of `WorldTiers`.

## 0.7.84: every villager can be downed, villagers tend the downed, shut-in villagers get out
- **Downed** (Andy: "villagers should try to help others that are downed, and they can all be downed now, unless
  decapitated"): every villager (MCA's, children too) and a settlement's own folk (not rulers, orcs, raiders or
  barbarians) go down instead of dying; a beheading still kills. When they die at last they leave their body and
  belongings as anyone would, guards too (Andy: "guards should have corpses too").
- **Tending**: the nearest grown villager within 24 blocks who isn't afraid, fighting or asleep walks over, kneels and
  tends a downed one (hearts, a hand at work); `TEND_TICKS` (10 s) at their side gets them up. A player's hit starts
  it over. Helpers are kept from routines and work while they tend.
- **Shut in** (`ShutIn`; Andy's clip of a realm's townsfolk packed in a closed pocket of a building): a villager near
  a settlement who hasn't got anywhere for 2 minutes of the day and has no path to open ground nearby turns up there
  in a puff. Not near player-placed blocks (trading halls stay), nor asleep, leashed, carried or downed. Frightened
  villagers now run for ground about their own height, not the roof over it, and a new MCA hire turns up on open
  ground by the seat rather than at it.

## 0.7.87: the tallest size is the Verdant's (pack 0.13.5)
- Andy: "max height should only be accessible to verdant". The top of MCA's Size slider is a Verdant's alone: anyone
  of another heritage is held to `Heritage.MCA_SIZE_CAP` 0.75 of it whenever their MCA look is saved
  (`McaPlayerSizeMixin`), when they pick a heritage, and every 10 s (`SIZE_CHECK_EVERY`, for players who were
  already tall). Players with no heritage yet aren't held, since MCA's editor comes before the heritage screen.

## 0.7.88: Nightborn blood (pack 0.13.6)
- Andy picked these from a list of Nightborn and vampire synergies (not the gentler sun: the sun's rules are unchanged).
  All in `NightbornBlood` (tuning at the top), hooked into `Vampires`, `VampirePlayers`, `NightGifts`, `Heritage`,
  `Moons`, `Cures` and the client looks.
  - **Old Blood:** a Nightborn bitten at night turns `OLD_BLOOD_TICKS` 600 (30 s) after the bite instead of at the next
    nightfall (bitten by day: at that dusk, as before): a heartbeat every second, reeling (Nausea) the last 5 s. No
    vampire's bite takes an unturned Nightborn below `OLD_BLOOD_FLOOR` 2 health (a feeder that gets there has had its
    fill and lets go), and they wake at full health, the bite's Weakness and Hunger gone. Still time for a golden apple.
  - **Long thirst:** a Nightborn vampire starves after `STARVE_NIGHTS` 3 nights without blood (others 2); blood vision
    follows.
  - **Kin-sense both ways:** a vampire's (or dhampir's) kin-sense shows, under the kin line, a faint pull toward the
    2 nearest unturned Nightborn players within 96 blocks (`FAINT_*`, distances to the nearest 10). The Elder never
    hunts an unturned, unbitten, uncursed Nightborn: at night, with no prey, he walks to the nearest within 48 blocks,
    and within 3.5 (in sight) whispers an offer with two chat choices, `[Bare your neck]` / `[Turn away]`
    (`/kingdom nightkin yes|no`), their screen darkening a moment. He waits 30 s, watching. Yes: a gentle bite (2),
    they're bitten (so Old Blood turns them 30 s later), and he's fed and rests. No, 30 s without an answer, or going
    14 blocks off: he leaves them be for `LEAVE_BE_TICKS` 3 days. Anyone who hurts him is still fought.
  - **Starlit blood** (client `HeritageLooks`, `VampireLooks`; textures `nightborn_blood_*` from
    `tools/gen_heritage_looks.py`, the same shadow redrawn): a Nightborn vampire's starry shadow on the hands is deep
    red with blood-bright stars; their eyes glow moon-silver by day and red-silver at night (in place of the vampire's
    red eyes); the night wisps are blood mist.
  - **Night-deep gifts:** while a Nightborn's dark bonus is on (`Heritage.dark`), Blood Dash goes 1.3x as far (and the
    great leap 1.3x as far along), and Groundpound throws 1.4x as wide (7 blocks), 1.3x as hard, and shows people
    1.5x as far (48 blocks) (`DEEP_*`).
  - **Born dhampir:** a Nightborn dhampir (the golden apple) gets the vampire's night Strength too, still with no
    weaknesses.
  - **What the cure leaves:** the rite on a Nightborn who was turned leaves one trace for good, picked at random then
    and saved (`nightborn_trace`): night vision whenever they're in the dark, or a faint kin-sense (the nearest vampire
    within 64 blocks, roughly). Told by a line at the rite.
  - **Blood Moon kin:** a Nightborn vampire's dark bonus is doubled under a Blood Moon (+20% blows, +10% speed) and
    tripled under a Super Blood Moon; the dead risen out of the ground (tagged `jaymoskingdoms_risen`) won't target
    them while it lasts (`ThrallTargetMixin`, `NightbornBlood.spares`).
- **0.7.89, villagers have bloodlines:** MCA villagers and the settlements' own human folk (guards, archers, rulers,
  builders, farmers, miners, woodcutters, carters, stable hands; not orcs, goblins, illagers, pirates, barbarians,
  casters or plain vanilla villagers) are born into a heritage the first time they're in the world, saved and carried as
  the same `jaymoskingdoms_heritage_<key>` tag players have (`VillagerHeritage`, weights at the top).
  - **Odds:** Heartlander ~60%, then Earthling, Stoneborn, Tideborn, Sylvan, Emberborn, Frostborn, Nightborn,
    Warrior-born (~1 in 37) and Verdant (~1.6%). A heritage that suits the land counts 5x (Frostborn in the cold,
    Emberborn in the heat, Tideborn by water, Sylvan in forests, Stoneborn in mountains or deep down, Verdant in
    jungle); Emberborn and Frostborn are rare in the wrong climate; all born in the Verdant World are Verdant.
    Children take one parent's heritage (MCA pregnancies and baby items, a player parent too).
  - **Looks** on MCA villagers (`VillagerLooks`, `McaEyes`): Verdant skin and antennae, each heritage's glowing marks,
    eyes lined up with MCA's own, Tideborn fins, embers, particles. Folk get the eyes, marks (mirrored on the left)
    and antennae, but no Verdant green (their skins are painted).
  - **Warrior-born never show a tail:** 60% keep it wrapped round the waist, 40% had it cut (a stump that never
    regrows). They say why: a new MCA option, "Ask about their blood", plus now and then in chat and gossip; other
    bloods have lines of their own.
  - **Gifts that suit a villager:** Emberborn fireproof (weaker in the cold), Frostborn never freeze, Tideborn breath
    and swimming, Stoneborn a little slower, Sylvan soft landings and lighter blows, Verdant burn longer, Nightborn
    quicker in the dark and weaker in the sun, Warrior-born hit harder badly hurt. Player-only ones (attribute points,
    character level, hunger, skill experience, mining) are skipped.
  - The wrapped ones' full-moon ape is Jaymo's Powers' (0.13.10).
- **0.7.92, heritage looks no longer flicker on MCA villagers:** MCA doesn't skin the animated model; it draws the skin,
  face, clothes and hair each on its own copy of its body, inflated a little more each time (0, 0.01, 0.0625, 0.125).
  The heritage marks were drawn on the bare animated model at the skin's own depth and z-fought it. On MCA villagers
  they're now drawn on MCA's body just over the skin (0.005), and the glow (glowing marks and eyes) just over the face
  (0.02), both under the clothes, posed like MCA's own layers (`McaLookModels`, numbers at its top). Folk are unchanged.
- **0.7.92, the Earthling's last stand:** an Earthling's ki glow round the fists (the training wraps stay) now lights
  only when they're nearly dead. Brought to 25% health or less, players and villagers alike, their ki flares: a flash,
  a beacon hum, and one random buff for 5 minutes (Strength, Speed II, Resistance, Regeneration, Haste II, Jump Boost
  II or Absorption II). The glow stays lit for as long. It can't come again for 5 minutes after (`EarthlingSurge`,
  numbers at its top).
- **0.7.92, the folk call you by your chosen name:** villagers and folk no longer use your Minecraft username. Their
  own lines (greetings, deeds and gossip, heritage talk, accents, assassins' whispers, identity reveals, exiles) now
  use the name you picked in MCA's creator (its family tree; your username only if you never set one), and the
  costume's name while you're masked to them. MCA's own dialogue already used the creator name and the costume swap
  (`Identities.trueName`, `McaBridge.name`).
- **0.7.93, bring the downed round with food:** Andy: "shift click holding a downed npc raises a meter to revive them,
  it takes one piece of food from your inventory". Sneak and hold use on a downed villager or folk: a bar fills over the
  hotbar (`Downed.REVIVE_TICKS` 60, 3 s), your hand at work with hearts and a rustle; full, they get up as their own
  recovery does (30% health, the wounds forgotten) and it costs the least filling food you carry (lowest nutrition,
  then saturation; the first of equals; none in creative). With nothing edible on you, a hint and no bar. Letting go,
  standing up, looking away, stepping out of reach or someone lifting them empties it. A quick sneak-tap still picks
  them up over your shoulder, now when you let go (within 0.4 s) rather than on the click; carried bodies, disarming
  and throwing are unchanged.
- **0.7.95, a Warrior-born comes back stronger:** Andy: "warrior borns that get damaged to low health and survive get
  an attribute point. this effect resets every 5m." Hit down to a quarter of their health or less, a Warrior-born is on
  the brink; once they're through it (10 seconds without a hit, or healed back over half), they win an attribute
  point to spend on the K screen, with a roar and a level-up chime. Dying on the brink wins nothing. After a point, the
  next can't be won for 5 minutes. The points are kept for good, through death and respecs (`WarriorZenkai`, numbers at
  its top; counted in `Stats.earned`).
- **0.7.96, Survivalist hearts and Warrior-born fury:** Andy: "Survivalist increasing base hearts" and
  "Warrior-born low health visual indication/boost". A Survivalist still starts with a heart more, and gains another
  every 10 character levels, up to 5 more at level 40 (`ClassPerks.SURVIVALIST_LEVELS_PER_HEART`, `_HEALTH_MAX`;
  the class card shows 11-15 hearts). A Warrior-born's fury now grows the more they bleed: below half health their
  blows land 10% harder, rising to 35% at 3 hearts (15% of their health), and they're knocked back up to 30% less
  (`Heritage.rage`/`fury`, numbers at its top; Warrior-born villagers too). It shows: their own screen's edges burn
  red and throb with a heartbeat that quickens as they near death, and everyone sees them breathe hot, ragged puffs,
  faster as the rage grows (client `WarriorFury`; health is already synced, so nothing new goes over the wire).
- **0.7.97, small spiders that hunt:** Andy: "Can we make every spider this size? (More Babies) then give them this ai
  (NYF's Spiders, Yuka)". Every spider is now half size and every cave spider about two thirds, hitbox and all (the
  sizes of More Babies' baby spiders, all rights reserved, so only its proportions; `wild.SpiderMinds.size`, the
  game's scale attribute, saved with the spider so corpses keep it). NYF's Spiders has no 26.3 build; Stormie's
  Spiders, already in the pack, is its fork, so the wall and ceiling climbing was already there. On top of it, our
  own hunting after Yuka's ideas (MIT; `wild.SpiderMinds`, numbers at its top): spiders remember where they last saw
  you and which way you went, and in the dark hear you run or jump (sneak past); they run at where you'll be, spread
  round you when several hunt you, run off when badly hurt and short of friends (then come back), search where you
  went when they lose you, meander in curves when idle, and now and then hang from a ceiling in the dark to drop on
  whoever walks under. The tarantula keeps its size.
- **0.7.100, bigger rewards for returned captives:** Andy: "villagers should reward you more for returning a captured
  prisoner". Each captive is now someone at home: one of its folk, one of its guards (20%), or kin of its ruler (10%,
  only where there's a ruler); freeing them says which. Brought home, their family pay 6-12 emeralds (guard 8-16, kin
  12-24) and the settlement adds a reward from its treasury: 8/16/40 plus 2%/4%/8% of its worth, up to 30/60/150,
  never leaving it under 20 (a rich realm pays far more for its own). A guard hands you a sword, shield, crossbow or
  helmet; the ruler's kin a golden apple and 1-3 diamonds. Reputation 60/90/150 (was 40), the one brought home's hearts
  50/60/80, witnesses' 5, and two of their family (real MCA kin first, else the nearest neighbours) come to thank you
  for 20 hearts each. Members gain 10/25/60 standing. The settlement then owes you a favour (2 for kin, up to 3): while
  it does, its guards neither toll nor search you, and the next time they'd arrest you they let it go (spends one).
  A realm at war with you, or one that doesn't know you (masked), pays no treasury reward and owes no favour.
  Numbers at the top of `Captives`.
- **0.7.101, hoods and the hidden blade (the stealth tree):** Andy: "flesh out the stealth tree, id like any hooded
  costumes to add to stealth, while not completely concealing identity like it does now ... They also need a hidden
  blade weapon, craftable at all times but usable only by those with sufficient stealth skill." Powers tags a hooded
  player (`jaymospowers_hooded:<key>`, a Hood or Peaked Hood with the face showing). Witnesses see a hooded player at
  0.75 the range (thefts, trespass, deeds; on top of sneaking), and a hood is a fragile mask: a stranger in it is "a
  hooded figure" with its own name and crimes, unless a witness within 6 blocks, facing them, in the light, sees the
  face when a crime is seen: then that settlement knows who it is ("recognised") and the crime is theirs. Lowering
  the hood in view gives it away like a mask; paranoid guards ask hooded strangers to lower it; MCA villagers still
  know a hooded friend (`Identities`, `Theft`, `Pickpocket`, `Trespass`, `Deeds`, `Approaches`). The hidden blade
  (`HiddenBlade`, numbers at its top): a leather bracer with a spring blade, always craftable (` I `/`LNL`/` S `),
  a dagger (4 damage, 2.2 a second, iron durability, Better Combat dagger moves, a light blade), worked only from
  Stealth 5 (below: 1 damage a blow, "the spring jams"). Fall 4+ blocks onto a living thing with it in either hand
  and the fall damage is theirs, not yours, as your own blow, and they're Crippled (an attachment, saved: 70% slower,
  no jumping or sprinting) for 6 s, +1 s per 2 blocks more, at most 20 s; a miss is a normal fall. Every
  full-strength stab at a Crippled foe deals 10x (`LivingEntityHurtMixin`, after ClassPerks and CombatArts). The
  rulers' assassin draws a hidden blade now (same blows, same stab). Icon drawn by `tools/gen_items.py`.
- **0.7.101, freerunning (the Stealth tree's second page, with Powers 0.14.3):** Andy picked "Full freerunning".
  Stealth goes to level 25 (`Skills.STEALTH_MAX_LEVEL`; alignment still counts to 15, pocket practice only the first
  15 levels) and gets a second page, "Freerunning" (12 points): Climber (lvl 3, 3 ranks), Freerunner (lvl 3, 2),
  Sure Footing (lvl 8, needs Climber), Leap of Faith (lvl 8, 2 pts, needs Freerunner), Bar Swing (lvl 12, 2 pts, needs
  Sure Footing), Wall Runner (lvl 12, 2 ranks, needs Leap of Faith); appended to `Skills.Perk`, so no ordinal moves.
  The moves are Jaymo's Powers' (`Freerun`), reading the ranks through `Skills.rank(Player, String)`. Stealth
  experience: +1 for a freerun move at most every 20 s (Powers tags the player `jaymospowers_freerun`;
  `Skills.freerun` takes the tag off and pays), +6 for a hidden blade leap that lands (`HiddenBlade.leap`). Like Strength's, the Master title is now at the
  pillar's top, so a Master Rogue needs Stealth 25 (players at 15 keep the Rogue title until then).
- **0.7.102, Emberborn too hot to hug:** Andy: "hugging emberborn villagers and players is impossible without fire
  resistance". Hugging an Emberborn (villager or player) without being Emberborn, fireproof or under Fire Resistance
  burns you (3 s fire, 2 damage) and throws you back; no hug, no hearts. MCA's hug menu works both ways (an Emberborn
  player burns the villager they hug). Villager hugs and partner affection between an Emberborn and someone unsafe
  mostly turn into a chat (75%); otherwise the unsafe one is burned, says so, and the meeting ends with no regard or
  mood change. Numbers at the top of `EmberHugs`.
- **0.7.104, the downed state moves into Jaymo's Powers:** Andy (2026-10-19): "remove our downed system for a universal
  one". Going down, the AI hold, bleeding out, a player's wounds, the food revive, picking up and throwing, and the
  lying pose now live in Jaymo's Powers 0.15.1 (`core/wounds/Downed`), for players and tamed animals too (see its
  MODLOG and `docs/Wounds-Phase2.md`). Kingdoms never compiles against Powers: `Downed.isDowned` reads its scoreboard
  tag `jaymospowers_downed`, and the folk who may go down (the same `downable`: villagers, guards, a settlement's folk,
  not rulers, orcs, raiders or barbarians) are marked `jaymospowers_downable` when they're hurt. What stays here:
  villagers tending the downed (now looked for round each player every second; when the tending is done the body is
  tagged `jaymospowers_revive` and Powers gets it up), taking a downed guard's weapons (a plain right-click), and
  `Rearm`, `Pickpocket`, `ShutIn`, routines and workers leaving the downed alone. `DownedMixin`, `DownedMobMixin`, the
  client `DownedPose` and the `jaymoskingdoms:downed`/`downed_wounds` attachments are gone (anyone downed under the
  old system just gets up). Without Jaymo's Powers, Kingdoms' folk simply die.
- **0.7.104, barber-surgeons, bandaging and spirits:** Andy (2026-10-20): "I dont want any modern medicine". Kingdoms'
  side of Jaymo's Powers' Wounds phase 4 (`docs/Wounds-Phase4.md`, track B; written without a compiler, not yet built
  or tested). **Clerics are barber-surgeons:** a grown cleric also sells Powers' linen bandages (3 for 2 emeralds),
  splints (3), smelling salts (6) and leeches (4), and Kitchen's strong spirits (4); right-clicking one with emeralds
  while your bones are broken still has them set (Powers' `Fractures`). **Taverns and festivals sell drink:** a grown
  villager standing in (or at the door of) one of a realm's standing taverns, or raising a toast at its festival, sells
  Kitchen's ale (2 for 1 emerald) and strong spirits (3); once offered, the drink stays on their trades (so walking out and back in never restocks it)
  (`Remedies`, prices at its top). The wares go on when a player right-clicks the villager, just before the trading
  screen opens; every item is looked up by id and skipped when its mod isn't loaded, so Kingdoms still stands alone.
  **Villagers bandage the downed:** when a villager's tending is done the body is tagged `jaymospowers_bandaged` beside
  `jaymospowers_revive`, and Powers stops its bleeding and gives it a bandage's heal.
- **0.7.105, Just-In COMBAT's ideas, our own way (with Powers 0.15.2's rapier):** Andy picked all five (the mod is All
  Rights Reserved, so its ideas only). **Shields** (`MobShields`): a melee mob with a shield in its off hand (guards,
  kings and lords already carry their realm's; tiered zombies now too) raises it 55% of the times it could, for 1 to
  2.5 s, while a foe is within 7 blocks, and lowers it to strike (the goal's blow waits, `MeleeAttackGoalMixin`); it
  goes up at once against a drawn bow or loaded crossbow within 28. Vanilla's blocking does the rest (front only); an
  axe knocks it aside for as long as a player's (`LivingEntityBlockMixin`); watchers see the raised arm
  (`ShieldPoseHumanoidMobRendererMixin`). **Blade clash** (`BladeClash`): when two armed fighters face each other and
  one's blow lands within 3 ticks of the other's swing (a full-strength swing for a player, one that hadn't landed yet)
  or just before a Better Combat mob's wound-up blow, neither hurts: sparks, a ring of steel, both pushed back; then
  no clash for a second. Hitting straight back after being hit is a trade, not a clash. **Tiered monster gear**
  (`MonsterGear`): a zombie or skeleton taking a World Tier above Hearth for the first time fills empty armour slots
  (25/40/55/70% each by tier), may take a weapon (zombies) or trade its bow for a blade (skeletons), and an armed zombie
  a shield; each piece one of five grades (leather and stone, copper, chainmail and iron blades, iron and rapiers or
  longswords, diamond), rarer the higher, odds rising with the tier; rare pieces sometimes and legendary always
  enchanted. **Duellists**: a lord, and one guard in five, fence with a rapier of their sword's metal when Powers is
  there (`Outfits.fencer`). Bleeding, the fifth idea, is already Wounds' (Powers' PR #331).
- **0.7.106, five new heritages and the Blood Rite:** Andy: "Can you brainstorm 5 more heritages" / "Additional
  Heritages/ unlockable classes", then "these are great. go for it." (`Heritage`, ideas in
  `heritages/new-heritages-brainstorm.md`). Two can be picked at the start and three are earned:
  - **Stormborn** (Agility, Finesse): faster under open sky, slower deep underground, immune to lightning; in a
    thunderstorm under open sky their hits arc a spark to the nearest enemy, and their eyes glow
    electric blue in any thunderstorm (Andy: "The stormborn eyes should glow during storms along with the sparks."). Villagers lean Stormborn up high.
  - **Dawnborn** (Might, Grit): heal slowly in sunlight, hit undead 20% harder, weaker and hungrier in the dark.
    Villagers lean Dawnborn in savannas.
  - **Endborn** (Wits, Finesse), earned near a dying Ender Dragon: endermen ignore their stare, a hit can blink them
    away, water and rain burn them.
  - **Dragonblood** (Might, Endurance), earned near a dying Ender Dragon or a tamed dragon: half fire and dragon
    breath damage, stronger with a hoard of gold and gems in their pack, weaker when poor.
  - **Deepborn** (Endurance, Wits), earned near a Warden digging away: silent to sculk, sense living things nearby
    while sneaking, explosions stun them, weaker in sunlight.
  Waking an earned heritage gives a **Blood Rite** (`jaymoskingdoms:blood_rite`); drinking it reopens the heritage
  menu to switch to one you've woken. The menu shows earned heritages as "???" with a hint until woken, and fits on
  small screens. New looks (`tools/gen_heritage_looks.py`), `/kingdom heritage unlock <players> <heritage>` for ops.
  Hidden effects stay off the cards so players find them. Not done: Stormborn metal-armor lightning and dragon slit
  pupils; Berk's dragons aren't installed, so any tamed dragon-like mob counts.
- **0.7.107, the pirate bombardier and dynamite:** Andy liked Zombie More's dynamite zombie, "but maybe as a pirate or
  barbarian variant that lobs them instead". Pirates already live on gunpowder, so it's a pirate: the **Bombardier**
  (`Pirates.BOMBARDIER`, role 76, off the roster like gunners). Zombie More is all rights reserved, so only the idea is
  taken: the mob, its skins (`tools/gen_pirates.py`: soot, singed brows, a bandolier of dynamite) and the dynamite icon
  (`tools/gen_items.py`, vanilla style) are our own. From 3.5 to 20 blocks he keeps his distance (backs off inside 7)
  and lobs a lit stick at where his foe is going, sight or no sight, so walls and roofs don't save you; closer, he draws
  his cutlass. **Dynamite** (`Dynamite`): a 2.5 s fuse from the throw; it arcs, bounces and sizzles, then bursts like a
  cannonball (12 at its heart, 3.5 blocks, a shove) and leaves blocks whole; it never hurts his own side. Water puts it
  out (the stick drops). Hit a lit stick and it flies where you look and is yours. The item throws the same (16 to a
  stack; string, gunpowder, paper in a column make 2); bombardiers drop 1-2 two times in three. One bombardier guards
  each pirate cove (a second after 4 days; a lost one comes back with the next day's growth), and every pirate
  landing party comes ashore with one or two. Numbers at the top of `Dynamite` and `Pirates`.
- **0.7.108, Better With Mods batch 1 (Powers 0.15.9):** `MobTweaks`: baby zombies -25% speed and damage
  (ADD_MULTIPLIED_BASE, sums with tier bonuses); endermen that have taken a tier call nearby endermen onto their
  attacker, and every teleport rumbles (`EndermanThunderMixin`). Zombies and husks hunt
  `zombie_prey` livestock (`Prey`), spiders `spider_prey` (`SpiderMinds`); leashed, named and penned animals are
  safe. `disasters/LooseGround`: sand or gravel over air gives way under you unless you're weightless. Forager:
  hoeing grass turns up `tilled_seeds` (`SurvivalSkill`). Bats drop bat wings for the Scroll of Draining.
  `RoadPace`: `road_pace` blocks +15% (horses too; TRMT's worn dirt and sand paths count), `slow_going` (sand, snow, leaves) -15% on foot, FOV held steady.
  `tinkering_melting/armour.json` lets Powers' smeltery melt the paladin, gladiator, ronin and barding sets.
- **0.7.109, villagers remember you (and MCA's chat AI is told):** Andy wanted MCA's chat AI worked in, more villager
  interactions, and villagers who remember you and what you did with them. Each villager (and folk) now keeps
  `Recollections`: per player (masked strangers under the costume's stand-in), up to 12 memories of 30 kinds, each with
  a weight and a lifetime (small things 2-6 weeks; a life saved, a captive brought home, kin murdered: for good). They
  are recorded from MCA gifts (and whether they were welcome), trades, chats/jokes/stories/hugs/flirts/kisses (by
  whether hearts went up or down), approach answers (kind, rude, lied, confessed, threatened, fed, bribed, bouts,
  stood with the realm in war, played), blows, crimes seen, pickpocketing, a kinsman killed in sight of witnesses,
  lives saved, fires put out, captives freed or brought home (their family remembers too), quests done or given up,
  letters, and what was said to them through the chat AI. Without any AI: someone with something unsaid comes over to
  say it (an approach greeting), brings it up in an MCA chat, gossips about what they saw other players do, passes the
  weighty things on to friends when neighbours meet (so word gets round), and one who bears you enough ill will won't
  chat, joke or hug at all and won't come over friendly. With MCA's chat AI switched on (an op runs
  `/mca chatAI default`; off by default), `ChatPrompts` adds the realm, its ruler and temper, its wars, how the realm
  takes the player (reputation, rank, wanted, deeds, renown, tongue), tasks the player is doing for them, their friends
  and foes among the neighbours, and every memory, to the villager's prompt (mixin on MCA's `ChatAIContext`).
  `/kingdom memories` (ops) shows what the nearest villager remembers of you. Numbers at the top of `Recollections`.

## 0.7.110: free rein riding (pack 0.15.11)
- **Free rein** (`equestrian.FreeRein`, client `FreeReinClient`, `FreeReinViewMixin`/`FreeReinYawMixin`, `MouseHandlerMixin`):
  steering any horse-type mount in third person, the mouse turns the camera on its own and the movement keys turn your
  horse toward where the camera looks (W ahead, A/D to the sides, S back toward the camera), a quarter of the way each
  tick, with the breed's own turn limits on top. Aiming a bow turns you to the camera at once; the crosshair targets
  where the camera looks; a flying mount climbs where you look. Back in first person you face where the camera faced.
  Key "Free Rein on/off" (unbound) switches it. The idea is Better Mount Steering's (Seramicx, MIT, no 26.3 build); our
  own code. The side-pass gait keeps its strafing.
- Fixed master's `LivingEntitySwingMixin` target (dots in a descriptor) that stopped the game from starting.
- **0.7.111, mob senses: Stealth's third page, Shadows (after Dynamic Stealth's ideas):** Andy wanted Dynamic Stealth
  (MIT, 1.12.2) worked in. It is written afresh in `Senses` (constants at the top).
  - **Sight:** how far hostile and neutral mobs notice you is their range × light (dark 0.25), sneaking (0.6, through
    `Skills.sneakFactor`), standing still (0.8), being outside their 120° view cone (0.3, unless within 2 blocks), a
    hood (0.75) and glowing or burning (1.5). It's applied in a mixin on `getVisibilityPercent`
    (`LivingEntityVisibilityMixin`), on top of Darktide's veil.
  - **Losing track:** after 100 ticks without sight, a mob walks to where it last saw you, looks around for 5 s, then
    gives up. Bosses, Folk and spiders are exempt; spiders keep `SpiderMinds`.
  - **Awareness eye:** a HUD eye above the crosshair (`SensesClient`, `textures/gui/awareness_eye.png` from
    `tools/gen_items.py`). It fills while a mob is noticing you, turns amber and half-closed while one searches, and
    red and open once one has spotted you.
  - **Unaware blows:** melee on an unaware mob is a sneak attack for ×2. With Assassinate, a full hidden blade stab is
    an assassination for ×10, not stacked with Crippled's ×10. Never on players, bosses or pets.
  - **Perks** (page 3, Shadows):
    - Shadowed (3×1, level 5);
    - Quiet Step (2×1, level 5);
    - Slip the Net (1×1, level 10);
    - Sneak Attack (2×1, level 10; ×2.5 / ×3);
    - Assassinate (1×2, level 15).
    Stealth XP: +1 per sneak attack (at most every 20 s), +3 per assassination.
  - Also, Kingdoms' Climber, Freerunner and Wall Runner descriptions now mention Powers 0.15.12's new moves.
- **0.7.112, Jaki's ships:** Andy asked to use the ships of Jaki Versatile Structures: Sails & Sea for our templates
  and for pirates and other factions. Its licence is GPLv3, so its structure files ship unchanged in
  `data/jaymoskingdoms/structure/jaki/` with the licence and a notice in `licenses/jaki/` (our code stays MIT).
  `JakiShips` reads five of them when a ship is launched (brought up to date by the game's data fixer) and makes each
  one of ours: no water, crew or decoration data, a helm on her quarterdeck, the galleon's 22 blackstone guns made ship's
  cannons (gunport left open, gunner where the button was), guns along the trireme's and drakkar's rails, loose sails
  tied on with chain rigging (a ship casts off only what touches her face to face), one sail colour in her crew's, and
  vanilla shipwreck/outpost loot in some barrels. Who sails what: pirates' big ship is the **galleon** (black sails; was
  the brigantine) and their small one a sloop or (40%) a **drakkar**; a navy under the frigate treasury sails a **war
  trireme** (was the brigantine); merchantmen are a **cargo ship** or a **trader** (was the cog) in their realm's colour.
  Wool hanging in a ship's rigging now catches wind (3 blocks = one sail, `Hull.CLOTH_PER_SAIL`), so their wool sails
  drive them, and anyone's. Ships may hold 5000 blocks (was 3000) for the galleon. Crews stand on any open deck under a
  raised quarterdeck too. Ops: `/kingdom fleet ship <rig>` builds any rig on open water near you and casts her off
  empty. Left out: their catamarans, small trireme and submarine.
- **0.7.113, free rein with Shoulder Surfing:** Andy found the movement keys didn't move the horse under free rein. With
  Shoulder Surfing Reloaded's coupled camera (the pack's preset), it turned you to its own camera every tick, so the
  keys couldn't turn you and the mouse couldn't turn its camera. Two optional mixins (`client/mixin/shouldersurfing`,
  skipped without it): under free rein its tick leaves your heading alone, and its camera looks where the free rein
  camera looks instead of swinging round with the horse. Seen in the dev game with Shoulder Surfing: D turns the horse
  to the camera's right and S rides it back toward the camera; the mouse turns only the camera.
- **0.7.114, even they can get got:** the patch notes said bosses and tamed pets are never caught unaware; the user
  said they can be. `Senses.sensing` (sight, the gauge, unaware blows) now takes bosses; `minded` (losing track and
  searching) still leaves them out, so a boss never gives up the hunt. A boss is harder to catch: stealth cuts its sight
  no lower than 0.4, its view cone is 240 degrees, it is unaware only while it is after no one (the dragon, which never targets anyone, only while perched), and a sneak attack or
  assassination on it deals at most 15% of its max health (Wither 45, Ender Dragon 30), never less than 1.5x the plain blow
  (`BOSS_*` constants). The dragon's view is flipped (it flies along its yaw's back). A tame pet can be caught unaware
  by anyone but its own owner, so you can't one-shot your own dog by accident. Folk stay exempt. Only wolves count as pets here: cats and parrots aren't hostile or neutral, so Senses never takes them.

## 0.7.115: the hidden blade is worn on the wrist
The user: "more like the web shooters, equipped in either one or both hands, and with an empty hand right clicking
should deploy the blade." `HiddenBlade` + `HiddenBladeItem` + `client/HiddenBladeClient` + `client/mixin/blade/*`.
- The bracer is no longer a sword. Use it to strap it on that hand's wrist (else the other's): one or both arms, kept in
  the synced, saved `jaymoskingdoms:hidden_blades` attachment, dropped on death unless keepInventory.
- Use with that hand empty (nothing else taking the click: no door, no villager) springs the blade out or sheathes it
  (`HiddenBladeUseMixin`, at vanilla's held-item use in `Minecraft.startUseItem`); main hand first, both together when
  both hands are empty. Anything taken into the hand sheathes it. Sneak + use at the sky takes a sheathed one off.
- Out over an empty main hand: +3 attack, -1.8 speed (an iron sword's 4 and 2.2/s), a light blade for the class perks
  and Combat Arts (`HiddenBlade.weapon`), the 10x stab on the Crippled and Assassinate on the unaware. Unskilled
  (Stealth < 5) the spring jams and it won't come out.
- The leap needs a blade on a wrist whose hand is empty, and springs it.
- Drawn on the arm (bracer, steel housing, blade past the fingers when out) in third person and first person; an empty
  off hand's arm is drawn in first person while its blade is out. Texture from `tools/gen_hidden_blade.py`.
- Old held blades are the same item id: use one to strap it on. Better Combat's dagger attributes for it were removed
  (the bracer in hand isn't a weapon). The assassin NPC still holds the item in hand (its damage is set directly).

## 0.7.116: your own designs and work areas (pack 0.15.21)
Andy wanted the builder side of Villager Workers (talhanation, All Rights Reserved: ideas only, nothing of its code or
art) in our builders, and picked "your own designs + areas" first. Written from how it behaves, not from its code.
- **Surveyor's rod** (`Surveyor`, `jaymoskingdoms:surveyor_rod`; stick, red dye, gold nugget on a diagonal). Sneak and
  use it to change mode: Design, Plot, Field, Quarry, Storage. Use it on two blocks to set two corners (shown with
  sparks while you hold it); plain use at the sky clears them. Corners and mode are kept in memory, per player.
- **Designs** (`Designs`): build something, mark its corners in Design mode (take in its floor), then
  `/kingdom design save <name>`. It's saved as a structure file, `<world>/jaymoskingdoms_designs/<name>.nbt` (the same
  format as a structure block's, so those can be dropped in too). On a plot, `/kingdom plot build design.<name>` has the
  builders put it up block by block from storage like any catalogue building, standing as it was built if it fits
  that way. Up to 32 x 32 across, 48 high, 16000 blocks; 24 per player (creative: any). Only blocks: containers come
  empty, no entities. `/kingdom design list | delete <name>`; a design a project or plot is using can't be changed or
  deleted. Lost buildings from a design are rebuilt like the others (`Rebuilds`). The template-to-blueprint reader moved
  from `GrimSites` to `Blueprint.fromTemplate`.
- **Plot** mode marks a plot of any size from two corners (`Plots.markBox`); the stake still marks a square.
- **Field** (`Areas`, kept as a `field` among the faction's buildings): 3 to 17 a side. A farmer is taken on at once
  and keeps it like a farm (`Farmers.farms` counts fields). Till it and sow a row; he does the rest.
- **Quarry** (`quarry`): 3 to 16 a side, from the higher corner down to the lower one (8 deep if they're level; at most
  64). Each quarry wants a miner (`Gatherers.wanted`), taken on at once. Miners dig quarries before their mine: the
  highest layer with anything left, nearest block first, carried to storage, torches as it gets dark. Water or lava
  beside a block is stopped first with stone he carries (or a cobblestone from storage), else the block is left. A
  stair is left along the north edge (step j dug j layers down); deeper than that, the stuck-walk puff gets them in and
  out. Grass, containers and his torches are left. A dug-out quarry is looked over again every minute.
- **Storage** mode adds a chest or barrel (both halves of a double chest) to the settlement's storage, or takes it out
  (not the depot or the last one). The first one of a settlement with none becomes its depot.
- `/kingdom area list` shows the fields and quarries (outlined) and how many storage containers; `/kingdom area clear`
  unmarks the field or quarry you stand in. Marking needs a lord's rank (or creative), in the faction's own land, clear
  of buildings and plots.
- **0.7.117, the Emberborn's Kindle:** the heritage card names the new gift, Kindle (held left mouse: flame, heat and a
  fireball), which Jaymo's Powers 0.15.22 runs.

## 0.7.120: storms stop burning the forests down (pack 0.15.27)
The user: "lightning strikes during storms absolutely destroys forests. water should still put fires out."
- Why: a storm cell (`Storms`) strikes real lightning every few seconds under it, and each vanilla bolt lights its spot
  and up to four around it on Normal/Hard, plus more on its after-flashes. And the fire never saw the cell's rain:
  `FireBlock.tick` asks the world's weather (`isRaining()`) before it lets rain put it out or stop it spreading, so
  under a cell on an otherwise clear day the fires burnt on through the downpour.
- `FireBlockRainMixin`: in the fire's tick, rain under a storm cell counts as rain, so cell rain now puts fires out and
  stops them spreading as vanilla's rain does. Where it rains is still `isRainingAt` (open sky), so a fire under leaves
  or a roof isn't put out by it, as in vanilla.
- `LightningBoltMixin`: a storm cell's bolts (tagged `Storms.BOLT_TAG`) light a fire only 1 in 4 strikes
  (`Storms.STRIKE_FIRE_CHANCE`), and then only the block they hit: no sparks around it, none from after-flashes.
  They still strike, hurt and charge lightning rods. Vanilla's own lightning, tridents, powers and the dry-lightning
  wildfire disaster are unchanged.
- Water placed on fire always put it out; nothing here changed that.

## 0.7.121: villagers fight back (pack 0.15.28)
The user linked the Villager Retaliation mod (NeoForge 1.21.1, all rights reserved, "don't port without permission"),
so only the idea is used; nothing of its code. Most of what it does Kingdoms already had: witnessed crimes (`Theft`),
reputation per faction (`Reputation`), each villager's memory of each player and gossip (`Recollections`), quests,
hired workers. What was missing was a villager standing up for themselves when struck: `Retaliation`.
- Strike a grown villager or a settlement's civilian (not a guard, ruler, orc, thrall, captive or someone downed) and
  they square up and punch back (1 damage, every 25 ticks each, the crowd lands one every 10 ticks). Onlookers who see
  it (`Theft.witnesses`) join in, up to 5. Seen, it's `Crime.ASSAULT`, a light crime (-10 reputation, a cry, a memory).
- Someone under 30% health runs off. The rest cool off 30 s after your last blow on one of them, or when you're downed.
- Feared (reputation -600 or under, or an outlaw there): the struck one runs instead, and nobody joins in.
- Paying off: emeralds in hand, use a brawler. 3 emeralds plus 3 per extra blow you landed, at most 32. That one stops;
  paying the one you first struck ends the whole fight. They remember being paid (`Kind.PAID_OFF`) as well as the blow.
  At reputation -100 or under (or an outlaw) they refuse your coin.
- A brawler counts as `Theft.rioting`, so firefighting, tending and deeds leave them be. Hitting one of an arsonist's
  crowd still scatters the crowd rather than starting a brawl. No crime against a realm at war with yours (they run).
- Tuning: the constants at the top of `Retaliation`.

## 0.7.122: the hidden blade moves, and shows
The user: "hidden blades should animate for players as well, and you should hold it in front of you so it is visible;
right now it is held to the side of the player and is caught in the costume." `client/HiddenBladeClient` +
`client/mixin/blade/HiddenBladePoseMixin`.
- Springing it is animated: the arm comes up into a guard, then the blade runs out of the housing (`SPRING_TICKS` 5,
  the first half the arm, the rest the blade); sheathing runs it back. Tracked per player on the client each tick,
  so everyone sees everyone's, in third and first person.
- Out over an empty hand, that arm is held up ahead of you (`GUARD_*`: about 65 degrees up, turned in across the body,
  following your look a little), not hanging at your side. The pose is set at the end of `PlayerModel.setupAnim`, so
  costumes (which copy the player model's pose) follow it. Skipped while swimming, gliding, or holding a two-handed item.
- A blow with the blade out is a straight thrust along your look (`STAB_*`) instead of a punch.
- The bracer stands 0.4 px prouder (`BRACER_GROW`), clear of the assassin costume's own bracer and any suit sleeve it
  used to z-fight with; the housing and blade sit out with it.

## 0.7.123: seasons (with Kitchen 0.5.6, pack 0.15.30)
- Andy chose our own seasons (2026-10-21); Kitchen keeps the calendar, read here through `KitchenCompat.season`,
  `dayOfYear` and `temperate` (all soft without Kitchen 0.5.6).
- Heritages: in a temperate land, summer counts +0.25 warmer and winter 0.8 colder, so plains are hot for an
  Emberborn in summer and cold (a Frostborn's thriving, an Emberborn's wilting) in winter. Verdant feed even better
  on spring sun (hunger x0.5 instead of x0.65).
- Frostborn don't feel Kitchen's winter cold (no extra hunger out in the open): `KitchenCompat.skills` hands Kitchen the
  predicate.
- Raids: camps raid 1.5x as often in summer and 0.4x in winter (`Raids.SUMMER_RAIDS`, `WINTER_RAIDS`).
- Festivals: every realm fit to keep one holds a festival on the first day of each season.
