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
- Whale oil's uses (Andy: "lamps or even explosives?", then "Go"):
  - `whaling/OilLampBlock` (iron nugget, glass pane, copper ingot): stands or hangs like a lantern, light 15. Comes
    empty; a bottle of whale oil fills it (4 measures, bottle back), a measure burns 5 minutes. Empty hand lights/snuffs.
  - `whaling/Lamplighters`: in a settlement, the nearest grown villager lights its lamps after dark (filling dry ones
    from the stores, a bottle each) and snuffs them at daybreak. While the stores hold whale oil, a burnt floor torch
    they come to relight becomes a lit oil lamp instead (`Torches` hook), so oil-rich towns turn lamp-lit.
  - `whaling/Firebomb` (whale oil, string, wool) and the powder firebomb (+2 gunpowder): thrown, they shatter into burning
    oil (fire on 70% of a 2-block splash, 3 for powder; everyone in it alight 6 s and scalded 3), flames on water burn
    boat crews; the powder one also bursts (power 2, blocks left whole). A player throwing one on a faction's land
    commits arson (`Theft.firebombed`, new).
  - `whaling/OilPotBlock`, the boiling oil pot (cauldron over iron bars): set on a wall's edge facing out, fill with
    oil (4 measures), light it, it boils in about 10 s. Empty hand tips a measure down a 3-wide column (to 24 below):
    boiling, 8 damage and alight 5 s; cold, a slick (Slowness III 5 s). On a settlement's land its guards (or a grown
    villager) by the pot light it and tip it on foes below: monsters, folk and sworn players of realms at war, players
    the town wants.
- Stage 4 (polish, Andy: "stage 4 can proceed"):
  - `whaling/Sharks`: a carcass in the water draws Naturalist's great white sharks (none without Naturalist): every
    20 s, 35% chance, one arrives from 16-24 blocks out, up to 3. Sharks near it close in and bite (10% each per 2 s),
    each bite taking a stroke's worth of flesh, with blood and splashes. Cut in fast.
  - The white whale: 1 in 60 wild-born whales. Pale, scarred sperm whale (`white.png`), 520 health, charges faster
    (0.42), turns on whoever harpoons it 80% of the time. Its carcass always holds ambergris, 3 lumps.
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
## 0.7.125: bounties, bandits and pirates
Andy: "Add a bounty board in town for criminal npcs and players ... add bandit masks and bandanas that hide your
identity the same way a costume does but npcs call you a bandit/ pirate depending on location". With Jaymo's Powers
0.15.32. Built without a game run; compile-checked against the 26.3 jars.
- **Bandit mask and bandana** (`Bandits`; crafted from black or red wool and string; worn on the head): a mask to
  `Identities` (its `maskKey` sees them even without Powers), the identity `bandit_<uuid>`, one per player whichever of
  the two they wear. Its own reputation, wanted time and bounties; given away like a costume (put on or off in view, a
  guard's unmasking, powers seen used both masked and bare-faced). The folk call you **the Pirate** in or on the water,
  in a boat or on a ship, or on a sea, beach or river biome, **the Bandit** anywhere else. Townsfolk who see a masked
  bandit come in cry out (once in 2 minutes per settlement). Art by `tools/gen_bounties.py`.
- **Bounties** (`Bounties`): a player seen at a medium or heavy crime gets a price on their head with the realm that
  holds the settlement (6 or 15 emeralds a crime, up to 320), on their mask if they wore one. Crooked villagers get
  theirs from `Crooks` (the NPC crime patch) through `Bounties.crime(crook, faction, emeralds)`.
- **The bounty board** (`BountyBoard`): every kingdom, fiefdom and village puts one up 3-8 blocks from its bell (again 3
  days after it's broken); craftable too (planks, paper, sticks). Right-clicked, it lists its realm's bounties on the
  quest screen, highest first: the name and price, the crime, what the head's worth, whether they're dangerous. Take
  one (3 at a time) to learn roughly where they were last seen (40 blocks either way, rounded to 10; ask again for
  fresher word). Taken bounties open to others again after 3 days; untouched ones come down after 10 days.
- **Bringing them in:** carry a downed target (Powers' Wounds) to the realm's ruler and right-click the ruler: the full
  price, and reputation (a quarter of the price). A crook goes to the cells; a player is fined what emeralds they carry
  (up to the price), forgiven their wanted time there and got up. A target killed by a player drops a **head** (a
  player's own face) worth half, handed to the ruler the same way.
- **Minibosses:** a crook with a bounty of 40 or more, once someone takes it, is armed when found: a relic weapon
  (royal from 90; it drops, or it's yours when you bring them in), 60 health plus 1.5 per emerald (240 at most), 10
  armour, a boss bar, and it fights the nearest player it can see within 24 blocks (6 damage plus 1 per 25 emeralds).

## 0.7.126: crooks among the villagers, and the guards arrest them
Andy: "Npcs can be criminals this consists of stealing, attacking other npcs, arson, stealing from the player, and upon
prompts of good deals (8 cod 3 emeralds) they can scam you ... rep has an effect, the higher it is, the less likely to
be robbed, scammed, or attacked." `Crooks` (+ small hooks in `Approaches`, `Theft`, `JaymosKingdoms`).
- 8% of a settlement's grown villagers (not guards, outlaws or cultists) are crooks, decided once (`jaymoskingdoms:crook`,
  saved). Every 5 s a free crook near a player has a 5% chance to set about something: lift from a neighbour's pack, mug
  a lone neighbour (3 blows, never below 3 hearts), pick a player's pocket (up to 4 emeralds, else half a stack, never
  the held item or a weapon), mug a player (night only), or set fire to logs/planks/wool/hay (night, once in 3 days per
  settlement, inside their own town only, players' builds there included; `Firefighting` answers it).
- Seen (a non-crook villager, folk or golem with sight of it, or the victim noticing; a mugging is always known): the
  witness cries out over their head, the guards and golems within 32 chase the crook, the crook runs, and they're a
  known criminal: `jaymoskingdoms:notoriety` (saved), scoreboard tag `jaymoskingdoms_criminal`, and `Crooks.SEEN`
  listeners (the bounty board) get the faction and a bounty (5 + 4 per notoriety).
- Pickpocketed players feel it half the time (named in chat), or only notice it gone 30-90 s later (unnamed).
- Scams: a crook offers bargains from any trade (craftsmen's own deals otherwise), 70% of them scams; a scam reads half
  again as many goods for one emerald less. You pay, they say they'll fetch it and walk off, and 4 s later crow about it
  over their head.
- Owed: whatever a crook took from you (theft, mugging, scam) can be demanded back for 5 minutes by right-clicking them:
  35% they hand it over (+25% with a weapon in hand, +4% per 100 reputation, 10-85%), else they bolt. Downed or killed
  within a day, their pockets go straight back to you (within 24 blocks) or drop. Killing a known criminal, or a crook
  who owes you, isn't murder (`Crooks.fairGame`); guards and golems stop beating a downed crook.
- Reputation: every chance against a player is scaled by `1 - rep/750`, between 0.1 and 1.5 (a friend of the town is
  rarely robbed; a disliked stranger is robbed more).
- Arrests (`Arrests`). Andy: "can you make guards attempt to arrest before killing criminals? either give them a visible
  audience with the king or make a jail cell building ... and executions." Took the audience. Where a realm keeps folk
  guards, a seen crook isn't chased by every guard and golem: the nearest free guard runs them down and calls a halt.
  They come quietly (55%, -6% per notoriety, at least 15%) or resist, and are beaten down and got up again
  (`jaymospowers_revive`). The guard marches them to the ruler (or the seat with no ruler about) for an audience: he
  names the crime, the ruler pronounces sentence aloud, players within 48 are told in chat, and it's carried out there.
  Sentence score = notoriety x 10 + temper lean (tyrant +30, noble -20, warlord +15, paranoid +20, reveler -10) + 0-30:
  under 20 a pardon, 20+ a flogging (3 blows, never below 2 hearts), 45+ exile (they walk 80 blocks out and are gone),
  70+ execution (beheaded: `jaymoskitchen.missing.head`, then killed). `Arrests.SENTENCED` takes the crook off the
  bounty board (`Bounties.sentenced`), and `Crooks.SEEN` posts seen crooks there (`Bounties.crime`).
  A guard who's killed, downed or called away lets the crook bolt. Villages without folk guards still send golems.
- Compile-checked (the changed files against the 26.3 jars); not yet seen in game. Pack 0.15.35.

## 0.7.128: wool clothes and steel plate (Better With Mods batch 2, pack 0.15.36, Kitchen 0.5.8)
- Wool cap, shirt, pants and socks (`WoolClothes`): dyeable light armour (0/1/1/0), crafted from wool, sold dyed by
  shepherds. Each piece takes `WOOL_COLD_SHARE` (20%) off an Emberborn's cold weakness, slowness, freezing and cold
  hunger, and (tag `jaymoskitchen:warm_clothing`) 20% off Kitchen's winter cold hunger for anyone.
- Steel plate armour (`Armory`): 3/7/6/3 (19), toughness 2.5, knockback resistance 0.05, tagged `heavy_armor`. Made from
  `#c:ingots/steel` (Powers 0.15.36) and `#c:leather_straps` (Kitchen 0.5.8), or with plain leather for 1-2 more steel.
  Melts back into steel by the cheaper recipe only (4/6/5/2 ingots). Flat vanilla-style layers
  (`tools/gen_steel_plate.py`), not one of the 3D Armory sets.

## 0.7.129: the Nightfall Plague (pack 0.15.37)
Our own take on Nightfall Plague (Forge/NeoForge, All Rights Reserved: only its ideas, no code or art), in
`nightfall/`, woven into our vampires. Art and data by `tools/gen_nightfall.py`.
- **Atrophy** (`Atrophy`, effect `jaymoskingdoms:atrophy`): a weak poison (never kills on its own; level II pricks twice
  as often); each blow taken may leave you weak 2 s. Whatever dies with it in its blood (not players) rises as a
  vampire bat. It doesn't take in the undead, vampires, or anyone with Immunity.
- **Immunity** (`Immunity`): 10 min (20 long) of no plague, now and then clears hunger, wither or poison, a heart every
  5 s. It burns the undead and vampires instead. It does **not** cure a turned vampire (still the priest's quest).
- **Brewing** (data recipes, 26.3 style): awkward + Cursed Tusk = Immunity; + redstone = long; Immunity + fermented
  spider eye = the Plague potion; + glowstone = strong. Splash/lingering as usual, so tipped arrows: the Immunity Arrow
  and the Tusk Arrow (the Plague's).
- **Night Bleeder** (`NightBleeder`, an illager): 50 health, 6 bite; rare at night, and up to 2 in a raid's last waves
  (15 s fire resistance). Bites heal it and give the plague (worse if already in you). Calls 2-3 vampire bats in a
  fight, vanishing a second; leaps to about 3 blocks short of you; burns by day and runs for shade unless fighting;
  a wooden blade does 2.5x. Brought down by anything but a critical hit (falling strike, full-draw arrow) it breaks
  into a **Blightwing** (`Blightwing`: 14 health, flees, may dive at whoever hits it) that lands as a Night Bleeder
  again in 30 s at half health. 3 looks. Drops 3-5 emeralds, 1-2 Cursed Tusks, 2% a totem. Invisible in a spyglass.
- **Vampire bats** (`VampireBat`): 8 health; in flocks at night under the open sky; latch on and drink (a blow shakes
  them off) or charge and reel. Summoned ones vanish after 50 s. They leave every vampire alone.
- **Callings** (`Callings`): hand one of our vampires a brewing stand, smoker, chiseled bookshelf or note block and it
  becomes an Alchemist, Slaughter, Chronicler or Bard. Right-click with emeralds for its ware (Immunity potion 6,
  3 Dried Meat 2, Cursed Card 12, Lute 10); with red food (4 for 1 emerald), books (1) or music discs (3) to sell.
  Raw beef quiets any vampire for the night (`Vampires.feedBeef`). Sneak-click is still the villager's own screen.
- **Relics** (`Relics`): Cursed Tusk (hits give the plague), Cursed Card (use: a vampire's dash; held, sometimes Speed),
  Lute (monsters within 15 calmed 4 s and can't land a blow, Night Vision 20 s + 2 s a monster), Dried Meat (1-3
  hearts, 1.5x at night, 2x under a full moon).
- **Nightmares** (`Nightfalls`): falling asleep at night, 3% (+15% with the plague, +3% per plague relic carried, at most
  50%): 4 s you can't leave the bed, then you're torn awake by 5-7 vampire bats or two Blightwings about to turn.
- **The Nightfall** (`Nightfalls`): with the plague in you, a totem saving you, a nightmare, or 8+ of the plague's
  creatures within 32 blocks set it off (not within 5 days of the last). 2-3 nights: each dawn the clock skips to the
  next dusk, nobody sleeps (creative aside), vampires get Strength and Speed, the undead don't burn, fires smoke out,
  more bats and Bleeders about, and a pale miasma fog (`NightfallClient`, `MiasmaFogMixin`). `/kingdom nightfall
  start|stop` (ops).
- Not remade: the music disc (it needs a song of its own), achievements, the cleric buying tusks.

## 0.7.130: the Purist ruler (pack 0.15.38)
Andy (2026-10-20): "Add a new king temper, Purist, they insist on a high population of its own heritage. Ridiculing
and stepping over those who are not. Very prideful ruler". `Purists`, plus a column in `Tempers`, `Retinue` and
`Approaches`.
- **Who:** one ruler in 6 (`Tempers.PURIST_ONE_IN`), drawn on its own so every other ruler keeps the temper he had.
  His blood (`Purists.blood`) comes from his id like his temper (any heritage, Heartlander likeliest); he's made of it.
- **His people:** 85% born within 96 blocks of his seat are of his blood; every folk his realm sets up is; MCA guards
  are drafted from his own kind first. `/kingdom info` says "a purist, proud of his X blood".
- **On his rounds:** bread and praise for his own kind; anyone else gets a sneer and is pushed out of his path.
- **His guards about him (`Retinue`):** his own blood may clasp his hand and gets a proud word, bread and +1 good name
  once a day. Anyone else close is shoved and sneered at, and thrown out of the city (5 minutes) if they keep hanging
  about (heat 60).
- **Guards' business (`Approaches`):** two columns, `purist` (outsiders: searched and tolled often, 4-emerald toll,
  10% haggle, refusing gets you shoved, bribes refused, insults drawn on) and `purist_kin` (never searched, 1-emerald
  toll, friendly bouts). Guard lines name your blood and his (`%2$s`, `%3$s`).
- **Townsfolk (`Purists.tick`):** his-blood folk jeer at an outsider, shoulder them aside when in the way, greet kin;
  now and then turn on a neighbour of another blood in front of a player (who slinks off). Gossip: pride, or grumbles.
- **Prices:** his land's traders charge outsiders 35% more (at least 1), kin 20% less (`VillagerPriceMixin` on
  `Villager.updateSpecialPrices`). **Good name:** kin win it at 150% and lose at 75%; outsiders the reverse.
- Also: regicide decrees (30%, "cut the rot out"), assassination contracts (2x, often paid by his own people), and a
  harsh lean (+25) when he sentences an arrested crook.

## 0.7.131: the Hornkin, a Nether faction (pack 0.15.40)
Andy (2026-10-20): "I want a new nether faction along the lines of" Just-In NETHER (NeoForge/Forge, All Rights
Reserved: only its idea, bull-folk at war with piglins over gold, no code or art). Stage 1 of 3 (plan:
project-files `nether-faction/Nether-Plan.md`), in `nether/`; art and data by `tools/gen_nether.py`.
- **Hornkin** (`Hornkin`, `Nether`): bull-headed Nether folk, in war parties in the Nether wastes and crimson forests
  (weights 8 warriors in 2-4s, 4 hurlers in 1-2s, 1 brute; not on nether wart blocks). Fireproof. Our own model
  (`HornkinModel`: a man's body, bull's head, muzzle with a gold ring, swept horns, loincloth).
  - **Warrior** (30 health, golden axe or sword): a horn charge at a foe 5-12 blocks off (every ~6 s), goring and
    throwing the first it meets (1.5x its blow).
  - **Hurler** (22 health): lobs magma bombs (`MagmaBomb`) that burst 2.5 blocks, 5 damage at the heart, set alight,
    blocks left whole.
  - **Brute** (70 health, drawn 1.45x, 6 armour): stamps the ground every ~7 s at anyone within 3.5 (7 damage, thrown).
  - **Zombified Hornkin**: what any of them turns into after 15 s outside the Nether (as piglins do); shambles arms
    out, its blows bring hunger, hunts players and villagers, wants no gold.
- **The gold feud:** Hornkin attack piglins and piglin brutes on sight, and any piglin within 16 blocks that sees a
  Hornkin turns on it (`ANGRY_AT`), so a dropped gold pile brings both. Hornkin go for players within 16 blocks, or
  within 32 if they wear gold. **Emberborn** players are left alone unless they strike first.
- **Gold:** they pick gold (the piglin-loved tag) off the ground and take it out of chests near them (not loot chests
  nobody has opened yet), then stand 6 s admiring it, ignoring everyone but whoever hits them. A gold ingot buys 1-3
  of their own wares (quartz, magma cream, fire charges, blaze powder, leather, bones, blackstone, gilded blackstone,
  crimson fungus, cooked pork, glowstone dust, arrows); anything else goes to the hoard, dropped as nuggets at death.
  Holding gold, they don't despawn.
- Spawn eggs for all four; loot: nuggets, leather, magma cream, the brute gold ingots and bones.
- Next (stage 2, if Andy wants it): Hornkin war forts and a piglin war camp, a Hornkin Warlord and a Piglin Warboss
  leading hosts against each other.
- **0.7.132, clearing the dead, graves, mourning, looters:** Andy: "i want someone to clear the battlefield of dead, or
  maybe come try to rob the corpses". With Jaymo's Kitchen's corpses (0.5.9): once it's quiet round a body in a
  settlement's land (no monsters, nobody fighting, no raid), a grown villager, a working folk or an idle guard carries it
  off (`Bearers`, numbers at its top). Its own dead (villagers, traders, its own folk) are buried in a graveyard it lays
  out in rows on open ground 16-44 blocks from the seat: a dug mound of coarse dirt and a headstone wall (cobblestone in
  a village, stone brick in a realm, sandstone in the desert); right-click a headstone empty-handed to read who lies
  there (`jaymoskingdoms:graves`). The enemy's dead are left two minutes, then burned (`Undertakers`' safe fires). With
  MCA, the spouse and kin come to the grave, kneel, weep and speak, the first leaves a flower, and their mood drops; for
  four days after they may come back to visit. No family: a neighbour or two; a soldier: his comrades. A player who
  carried the body there is thanked (+3, three times a day). Robbers (`Looters`): where 3+ bodies with something on them
  lie near a player, a band of 1-3 ragged looters (Folk role 80) may creep in (likelier by night), kneel at each body
  and strip it, then slip away; a guard or golem who spots them, a blow, or a player close and upright sends them
  running (not a sneaking player, nor one who's been robbing bodies too). Killed, their sack spills and the settlements
  they robbed thank you (+6). A settlement's jobless and nitwit villagers sometimes rob a body in its land; a guard
  shames them into dropping it, a player who hits one at it gets +3. A player taking anything off a settlement's own
  dead in its land commits grave robbing (`Theft.Crime.ROBBING`, medium, once a body). Players' bodies are never
  touched; monsters stay the guards' and golems' (who now leave bodies being buried or robbed alone).
  Judgement: Andy: "you can also drop a downed one in front of a guard for judgement (you'll later hear or see them
  jailed or executed, or freed!)". Looters now go down instead of dying (nobody tends them). Carry one (sneak + use) and
  set him down within 4 blocks of a free guard: the guard takes his sack, you're thanked (+8, and +6 from each other
  settlement he robbed), and he's marched to the ruler like any crook (`Arrests.handOver`), charged with robbing the
  dead, notoriety 1 + the bodies he robbed. The sentence: pardon (freed), a flogging, the cells (new `JAIL`, in place of
  exile for a looter: walked off to the seat and taken below), or death. Within 48 blocks you see it; further off, the
  next grown villager of that settlement you pass tells you how it went. One who slips his guard runs again.
  Andy: "ALL HUMANOIDS go down": every folk is now marked downable (rulers, orcs, raiders, barbarians, looters too), and
  Powers 0.15.41 downs every humanoid monster. Villagers only tend their own (villagers and a settlement's folk), never a
  downed looter, raider or monster.
## 0.7.133: an eased pickpocket's reach (with Powers 0.15.42, Kitchen 0.5.10)
- Andy, on Powers' animation preview: "do all this". The pickpocket's hand goes in on a soft spring of its own (a little
  high first, then slipping down into the pocket), the body leaning in and the head bowed to watch it, and comes back
  out the same way.

## 0.7.135: the hidden blade's guard holds, and shows in first person (pack 0.15.48)
Andy: "the blades still are not held in front of the player, i want to be able to see the blade deployed in first
person, and also have other players see that it is deployed. it should be held up in guard up front while idle, then
back to normal when retracted. and only move when the player does."
- Why 0.7.122's guard never showed: it was set at the end of `PlayerModel.setupAnim`, and Entity Model Features (Fresh
  Animations' player animations) animates the model after that, laying its own arms back over it. Now the guard goes on
  right after every animation of the model (for the layers, and as the body, costume and armour are each drawn, from
  the existing `MobSwing*` hooks), and EMF pauses its animations for a player whose blade is sprung (`EmfBladePause`,
  as Powers does for its poses). `HiddenBladePoseMixin` is gone.
- The guard is higher (the arm nearly level, `GUARD_PITCH` -1.3) and still: only the walk sways it (a fifth of
  vanilla's swing), no idle bob. Sheathed, the arm goes back to normal.
- First person: each empty hand with its blade sprung comes up into a guard (`firstPersonGuard`, `FIRST_*`): in toward
  the middle of the view, raised and turned in so the blade points ahead past the fist; the off hand rises in from
  below; a blow thrusts the arm forward. `HiddenBladeOffHandMixin` became `HiddenBladeFirstPersonMixin`, both hands.
  With First-person Model drawing your body, the third-person guard puts the arm (and blade) in front of you.

## 0.7.137: Notus, Boreas and Zephyrus, the first wind dragons
- Andy: "Names : Boreas, Zephyrus, Notus, Eurus, and Aeolus." The plan for all five is `docs/Dragons-Plan.md` (our own
  design; nothing taken from Berk's Dragon Academy). Notus, the south wind, came first, end to end; Boreas and
  Zephyrus share its body (`dragons/WindDragon`: the wild AI, taming, riding, stamina) with their own shape, breath, home
  and nature.
- **Notus** (`jaymoskingdoms:notus_dragon`, `dragons/NotusDragon`, on the horse base like the war elephant): a heavy
  fire dragon, 220 health, tough hide, tail club and back spikes. A miniboss: a boss bar while wild (`BossBars`), in
  `c:bosses`. A hand-written model with procedural animation (flapping, gliding, hovering to breathe, landing, walking,
  perched, crashed, sitting); art painted by `tools/gen_dragons.py`.
- Andy: "can you slightly differentiate every dragon visually like the heritages?" Every dragon rolls a look at birth
  and keeps it (`DragonLook`, synced): one of four hide shades, long, short or twin horns, now and then a lost back
  spike, and one of four glowing marking patterns (an emissive layer, with glowing eyes). Shared by the dragons to come.
- **Wild:** a rare encounter in the badlands, and now and then in the basalt deltas. It circles high, perches on the
  tallest spot near, and goes for players within 22 blocks: swoops, hovers and breathes fire, or lands to bite and
  tail-sweep. Andy: "fire burns blocks": a wild one's fire follows mobGriefing; a ridden one's always burns.
- **Taming:** worn below a third of its health it crashes and stays grounded a while. Crouch near it with empty hands
  and a trust meter (yours only, on your HUD) fills; cooked mutton fills it faster, Emberborn twice as fast. A drawn
  weapon, sprinting at it or hurting it drains it; empty, it bolts. Its trust is its own, not Equestrian's Bond.
  Only kills count as beating a dragon (for Eurus and Aeolus, later).
- **Tamed:** follows, sits (sneak-use, empty hands), defends you. Saddle in the horse slot. Flight with the free-rein
  camera: Space takes off and climbs, W flies where you look, S brakes to a hover, Shift dives (and lands; on the ground
  it dismounts). Stamina: hard flight and fire drain it, meat refills it, exhausted it glides down. R breathes fire
  where you look (Powers 0.15.53 lets go of its first ability while you steer one). Andy: "you can have a piggyback rider": a second
  player sits behind the owner. Only the owner steers: left alone in the saddle, the passenger can't fly or breathe
  and the dragon lands; in the air the passenger's Shift doesn't throw them off.
- Wild ones despawn far from players like other monsters (not tamed or named ones), none come on Peaceful, and the boss
  bar leaves your screen once you're out of its range.
- **Boreas**, the north wind (`jaymoskingdoms:boreas_dragon`, `dragons/BoreasDragon`): a long, lean frost dragon of the
  frozen and jagged peaks and the ice spikes, 180 health, a thinner hide. A glider that barely flaps and dives in
  silence (soft wingbeats, no roar before the swoop, faster than Notus; ridden it tires slowly aloft). Its breath
  (`DragonFrost`) is a cone of freezing mist: freeze damage, Slowness and the powder-snow chill; it puts out fires
  (burning creatures, fire, lit campfires) and turns still water to frosted ice that melts again (mobGriefing, as fire).
  It never freezes itself. Raw salmon; the Frostborn and Cryokinesis users win it over twice as fast (Powers read
  through `PowersCompat`, never required). A white boss bar. Its own model (a five-piece neck, a blade-finned tail,
  very long swept wings, icicle spikes) and art (`tools/gen_boreas.py`).
- **Zephyrus**, the west wind (`jaymoskingdoms:zephyrus_dragon`, `dragons/ZephyrusDragon`): a low, broad, amphibious
  dragon of the swamps and mangroves, 190 health. Wild, it lies half sunk in the water (only its head and back
  showing) and bursts out to swoop on whoever comes near. It swims as well as it flies: it breathes underwater, the
  current doesn't push it, and ridden in water it swims where you look and doesn't sink. It spits acid (`DragonAcid`):
  gobs that hurt and poison and leave a toxic puddle (never changing blocks); its own poison doesn't touch it. Tropical
  fish; Verdant and Tideborn twice as fast. A green boss bar. Its own model (a short thick neck, a flat frilled head,
  a paddle tail, webbed feet and ragged wings) and art (`tools/gen_zephyrus.py`).
- Encounters: Boreas over the frozen peaks, jagged peaks, snowy slopes and ice spikes, Zephyrus in swamps and mangrove
  swamps (as rare as Notus). The breath key is now "Dragon Breath (riding)".
- Spawn eggs; ops: `/kingdom dragon [notus|boreas|zephyrus] [tame]` summons a wild one (Notus unless named), or a
  tamed, saddled one.
- Not yet seen in game: written without a compiler, so the first build may need fixes.

## 0.7.140: the Stormborn is earned (pack 0.15.54)

- Andy: "Stormborn should be unlocked as a heritage when wind users corrupt their power." Stormborn is now an earned
  heritage like the Endborn: hidden as "???" at character creation with a hint, and woken (with its Blood Rite) once
  the player's Jaymo's Powers Wind is corrupted (Stormbound), checked with the other unlocks every 5 s
  (`PowersCompat.corrupted`, by reflection on Powers' `Fate.isCorrupted`). Anyone already Stormborn keeps it; villagers
  are born Stormborn as before.
- Compile-checked against the 26.3 jars; not yet run in game.

## 0.7.141: Eurus and Aeolus, the dragons in disguise
- Andy: "Eurus and Aeolus are actually MCA villagers, they can spawn anywhere and will transform after questioning you
  if you have already beaten two other dragons." Skins by a friend of Andy's (`textures/entity/dragon/<kind>/villager.png`).
- **Records** (`dragons/DragonRecords`, kept through death, synced): which kinds of wind dragon a player helped kill (only
  kills count, Andy), and whether they ever tamed one.
- **Disguises** (`dragons/Disguises`): now and then, out of sight near a player in the Overworld, a stranger: an MCA
  villager wearing the friend's skin (no clothes, hair or face layers; a plain villager without MCA). Right-click and
  they ask who you are and what you've slain. Eurus shows itself to anyone who's beaten 2 kinds of wind dragon, Aeolus
  to 3; Wind, Arsenal, Chrono or Darktide users, dragon tamers and anyone with an earned heritage are trusted anyway.
  Everyone else is shooed off (a shove, then 5 minutes' cold shoulder). They never take a town post, and wander off
  after 3 days unvisited.
- Andy: "The disguised villagers can be damaged 3 times, then they will violently assault the player who did. No
  transformation, just increasingly condescending messages until they snap." Nothing hurts them; a player's first two
  blows earn a sneer each, the third and they lay into the player for six seconds (Eurus 5 a blow, Aeolus 8), throw
  them clear, and won't speak to them for 5 minutes.
- **Eurus** (`dragons/EurusDragon`): the east wind, a storm dragon. Fastest and frailest of the five, lean with a whip
  tail; faster still in rain. Its breath (`DragonStorm`) is a bolt that chains to 3 more creatures, and in a thunderstorm
  may call real lightning. Cooked rabbit is its favourite; Stormborn bond twice as fast. A Wind-power rider flies it
  faster (1.15x, 1.3x in rain). A wild one with nothing to fight flies off.
- **Aeolus** (`dragons/AeolusDragon`): the father of winds, the final boss. Huge (1600 health), can't be tamed or ridden.
  His breath turns through fire, frost, acid and storm. Below two thirds: gusts that throw you back. Below a third: a
  gale every so often, warned by a ring, that throws everything near. Beaten, he calms every wild wind dragon within 192
  blocks for a day and leaves four eggs.
- **Eggs** (`dragons/DragonEggs`): Notus, Boreas, Zephyrus and Eurus eggs. Use one on a block to hatch a tamed hatchling,
  which grows with time and faster on its favourite food or meat. A wild dragon that's calmed won't pick fights.

## 0.7.142: Nether war forts and their warlords (pack 0.15.55)
Andy (2026-10-21): "Stage 2 please" (of the Nether faction plan, project-files `nether-faction/Nether-Plan.md`).
`NetherForts`, `FortBuildings`, plus the warlord in `Hornkin`.
- **Forts:** every 2 minutes, 30% per player in the Nether with no standing fort within 320 blocks: a rival pair rises
  72-140 blocks off, out of sight (no player within 48), on level ground with headroom, at least 240 from older forts.
  A **Hornkin war fort** (wastes or crimson forest; blackstone-brick walls with gilded stones and crenels, four towers
  with soul lanterns, soul fires, the warlord's keep with a throne, a gold block and the hoard chest) and, 56-88 blocks
  off, a **piglin war camp** (nether-brick palisade, basalt posts with lanterns, red and black tents, a war fire, the
  warboss's gold seat and war chest). Gates face each other. Hoards: gold ingots and nuggets, gilded blackstone, maybe a
  golden apple; magma cream, leather, 15% netherite scrap (Hornkin) or a crossbow, arrows, pork (piglins). Fort
  Hornkin leave their own hoard alone.
- **Hornkin Warlord** (`Hornkin.Kind.WARLORD`, own skin with a gold crown, drawn 1.75x): 260 health, 13 blow, 10
  armour; red boss bar (to whoever hits him, as every boss); the horn charge (his breaks a raised shield for 5 s) and
  the ground stamp; below half health, a rally roar (every 40 s): Strength and Speed to Hornkin within 24, they turn
  on his foe, two more warriors come. Spawn egg too.
- **Piglin Warboss:** a piglin brute made 1.45x, 220 health, 12 blow, gold helmet and chestplate, netherite axe, yellow
  boss bar; leaps at foes 4-12 blocks off (every 10 s) and slams down where he lands (8, thrown); below half health a
  war horn (every 40 s): his piglins rally and two more come.
- **Garrisons:** 7 Hornkin (warriors, hurlers, a brute now and then) and 6 piglins (some brutes); they keep within 10
  of their walls and take a new recruit every 5 minutes up to 10. Fort piglins don't zombify.
- **War hosts:** about every 6 minutes while a player is within 160 of either, each fort sends 4-6 out of its gate to
  march on the other's walls (in 16-block strides), and hold there once they arrive. They fight whoever they meet;
  both sides attack players as before (Emberborn still spared by the Hornkin).
- **A warlord falls:** his side breaks. Everyone of his fort (garrison and hosts) runs from players and is gone a
  minute later once none is near; the walls and hoard stay. The news goes to players within 96.
- Ops: `/kingdom netherfort found` (a pair just ahead; works with `execute in the_nether positioned ...`),
  `/kingdom netherfort host` (the nearest fort sends a host now), `/kingdom netherfort info`.

## 0.7.143: building (Better With Mods batch 4, pack 0.15.56)
Batch 4 of the BWM port (MIT, our own code).
- Carpentry (`carpentry/`, generator `kingdoms/tools/gen_carpentry.py`): Siding, Moulding and Corner (half, quarter,
  eighth blocks, waterloggable, placed by where on the face you click) in 10 woods (oak to pale oak, bamboo) and
  stone, cobblestone, stone brick and brick: 42 blocks. Crafting: block -> 2 sidings, 2 slabs -> 2 sidings, siding -> 2
  mouldings, moulding -> 2 corners, and 2 of a piece back up; stonecutter for the stones. No Kitchen chopper recipes
  (its chopper is code-only, logs only).
- Benches and Tables per wood (20 blocks), joining in rows with legs at the ends. Right-click a bench to sit
  (`BenchSeats`: a temporary, unbreakable cushion of the kind `Cushions` uses, gone when you stand or the bench breaks).
- Wattle, Grate and Slats: wooden panes that join like iron bars.
- Plot stakes stand as posts (`PlotLines`): string from stake to stake (straight, up to 31 blocks) draws a line; four
  strung into a closed rectangle mark a plot of exactly that size through `Plots.mark`. An empty hand on an unstrung
  stake still marks 11x11 (sneak: the chunk). Breaking a stake drops it and its string.
- Dirt, Grass and Dirt Path Slabs (`EarthSlabs`); a shovel paths a dirt or grass slab. King's Roads climb one-block
  steps in half steps (path slab or the paving's own slab) where the neighbouring chunk is already paved; they count
  as road for `RoadPace`.
- Wind chimes, bamboo and iron (`WindChimes`): ring in rain and storms and when something walks into them; a sneaking
  Soft Step player slips past.
- Rope lift (`RopeLift`): climbable rope lowered from an anchor, a pulley cranked by hand (a little hunger) or turned by
  a Kitchen shaft (`KitchenShafts`, read by block id and its turning/axis state, so Kingdoms needs no Kitchen code),
  and a platform that steps one block at a time carrying whoever stands on it; cut the rope and it falls (fall damage
  as normal).
- Generator `kingdoms/tools/gen_building.py`. javac clean; nothing played yet.

## 0.7.144: atmosphere (Better With Mods batch 5, pack 0.15.57, Kitchen 0.5.12)
The last batch of the BWM port (MIT, our own code).
- Gloom (`Gloom`, client `GloomClient`), Overworld only: pitch darkness at the eyes slowly builds a hidden gloom.
  30 s: quiet cave whispers; 1 min: darkened screen edges; 2 min: the edges breathe and short Darkness pulses come.
  Andy: real damage, only on Blood Moons, so at the top stage on a Blood or Super Blood Moon night it deals half a
  heart every 2 s through armour (damage type `gloom`, "was taken by the dark"); never on other nights. Light drains it
  6x faster than it builds: a lit spot, Night Vision, or any light-giving block held in either hand. Nightborn,
  creative and spectators are immune. No meter; respawning or rejoining clears it. Emberborn flame hands don't count
  (only Powers tracks them).
- Beacon watch-light (`BeaconWatch`, `mixin/beacons/BeaconWatchMixin`): a beacon whose whole pyramid is one block keeps
  its normal powers, plus: all iron makes hostiles in its range glow; all emerald raises the World Tier loot of
  hostiles killed in range one step (capped at Legend; loot only, not XP; one line in `WorldTiers`).
- Skittish herds (`Herds`, entity tag `skittish`): hit a farm animal and others of its kind that can see it flee the
  attacker for about 4 s; sprinting or breaking blocks within 8 blocks spooks them (2 less per Soft Step rank;
  sneaking never does). Leashed, tamed and named animals ignore spooks but still flee when hit. Pens aren't detected.
- **0.7.145, the Aether's remnants: monoliths and their Giants:** Andy: "the aether scattered remnants of itself
  throughout the overworld, the last surviving puzzle pieces being the monoliths and their respective Giants, The
  Highfield Giant, Magnetic, Arctic, and Irradiated all spawn near one and increase world tier when the first is
  killed." Neither Ancient Remnants nor the Aether II runs on Fabric 26.3, so Andy chose a remake (`docs/remnants.md`).
  Monoliths of our own (a broken banded pillar of its element's stone, a glowing core floating over it) rise now and
  then out in the wild near a player, never within 1200 blocks of another, their element by the biome. Coming near one
  wakes its Giant: four new bosses (320 health, a ground stamp, boss bars in their colours) with their element's power:
  the Highfield gust, the Magnetic pull, the Arctic freeze, the Irradiated sickness. The first Giant slain in a world
  raises the world's floor tier to Borderlands for everyone (`WorldTiers.raiseFloor`); each counts as a great foe.
  Ancient Remnants' own monoliths count too if it's ever loaded (`AncientRemnantsCompat`). Ops: `/kingdom monolith
  raise|list`, `/kingdom tier floor`. Numbers at the top of `remnants/Monoliths` and `AetherGiant`. Compiles against the real 26.3 jars;
  not yet seen in game.

## 0.7.149: character names in place of usernames (pack 0.15.63)
Andy: "your name on scoreboard should be the one you pick at the start, not your default user. to aid with immersion.
it should no longer show up anywhere."
- `CharacterNames`: the name picked in MCA's creator (its family tree, `McaBridge.name`) is a player's display name
  (`mixin/mca/McaPlayerNameMixin`, at the return of `Player.getDisplayName`): chat, death, join and leave messages,
  the name over their head, Jade, and every mod line that names a player by display name. The tab list shows it too
  (`McaTabListNameMixin`), with the team's colours and no hover card (it would give the username away). A synced
  attachment (`jaymoskingdoms:character_name`) carries it to clients; it's checked every second, so a rename in MCA's
  editor shows at once.
- Xaero's Minimap (`client/mixin/xaero`, optional, nothing copied) labels tracked players with the tab list's name.
- A costume, Rig Helm or bandit mask from Jaymo's Powers still wins: its name is given at the head of the same
  methods, so this never runs while masked. With no name picked (or without MCA) the username shows as before.
- `/kingdom info` lists members by character name; a werewolf player's beast carries their display name.
- Left alone: commands still take usernames (`/tp`, `/msg`, `/list`), the sidebar scoreboard and the social
  interactions screen.
- Compile-checked against the 26.3 jars; not yet run in game.

## 0.7.148: outlaws and disguises among barbarians and pirates; camp folk as MCA villagers (pack 0.15.62)
Andy: "Is there a way to affiliate with the barbarians? If not they should also be MCA and become non hostile when you
are negative rep." There was no way before (only goblins had a disguise).
- Outlaws (`Barbarians.outlaw`, `spares`): Andy, "only the nearest camp, unless you get so infamous that word travels
  that far." A barbarian camp or pirate cove lets you be when the standing realm nearest it holds your reputation
  below 0, or any realm has you at -500 or worse. That covers its raiders, chief, shamans and raids (pirates: crews,
  captains and landing parties too). Masked, the costume's or bandit mask's reputation counts. Near a camp that
  spares you (40 blocks) you're told so, once a day per camp. Orc, goblin and illager camps are unchanged.
- Camp grudges: killing a barbarian camp's or cove's own costs -40 with it (`KingdomEvents.lostHand`); at -100 it
  hunts you again, outlaw or not.
- Disguises (`Barbarians.passesFor`): Andy, "Wearing the bandana works as a pirate disguise and so does the full
  barbarian outfit + stone weapon." A bandana among pirates, or a leather jerkin and leggings (leather or nothing on
  head and feet) with a stone axe, sword or spear among barbarians, and they take you for one of their own whatever
  your reputation. Striking one of them gives you away to that camp for 5 minutes.
- With MCA, a camp's raiders at home and a cove's deckhands ashore become MCA villagers (`McaBridge.convertRaider`,
  from `Folk` every second): an MCA guard of the same heritage in the same gear (also in their pack, since MCA guards
  dress from it), 30 health, enlisted to the camp in the same role (still on its roster). They fight through MCA's
  guard brain with the camp's targets (`McaCompat.foe`). Realm guards (folk and MCA) hunt them; other camps leave them
  be; killing one isn't a crime (`Deeds`). When the camp falls they scatter like the folk do. Chiefs, pirate
  captains, gunners, bombardiers, ships' crews and raid war bands stay folk (their pistols, dynamite and ship
  stations are folk AI).
- Not tested in game.

## 0.7.147: Nether raids, warlord bounties, trophies and the gold truce (pack 0.15.61)
Stage 3 of the Nether faction (our own take on Just-In NETHER, which is ARR: nothing of it used).
- Portal raids (`nether/PortalRaids`): while a Hornkin war fort stands in the Nether, a town with a Nether portal
  within 96 blocks of its seat and a player within 96 may see 3-6 Hornkin (sometimes a brute) step out of it (3% a
  check every 20 s per town; a town isn't raided again for an hour). Andy: only for a player in world tier
  Borderlands (the second) or higher. They walk into town, roam within 28 blocks of the
  seat, take gold from chests within 10 blocks, fight whoever crosses them, and after 5 minutes, or once each has 3 gold, walk back
  into the portal with it (killed, they drop it). Raiders don't turn zombified in the Overworld and can't use portals
  themselves. `/kingdom netherfort raid` sends one on the nearest town now (needs a standing Hornkin fort).
- Warlord bounties: a king's quest is now and then (20%, while any Nether fort stands) a bounty on the warlord of the
  standing fort nearest his realm by Nether reckoning: Hornkin Warlord or Piglin Warboss. Done like a chief quest:
  you struck him down or were within 160 blocks.
- Trophies (`nether/NetherTrophies`): Hornkin Horns (warriors and hurlers 0-1, brutes 1-2, warlords 2); a ruler with a
  court pays 2 emeralds a horn and a little reputation (hand them over like a bounty head). A fallen warlord or
  warboss drops a Warlord's War Horn: blown, you and players within 16 blocks get Strength I and Speed I for 30 s
  (2 minute cooldown).
- The gold truce: an Emberborn can hand a Hornkin gold directly. An ingot buys 2 more wares than usual and a 25% chance
  of a rare one (blaze rods, gilded blackstone, crying obsidian, magma blocks, a ghast tear, netherite scrap); a gold
  block buys 3 rare ones. An Emberborn who hands a raider a gold block buys off its whole party.

## 0.7.150: crash when a crook was caught (pack 0.15.65)
- `Rulers.target` read a mob's brain ATTACK_TARGET without checking it was registered; folk brains don't register it,
  so the guard search in `Arrests.arrest` threw "Unregistered memory fetched" and crashed the server (Deck, 2026-10-21).
  It now checks `hasMemoryValue` first.

## 0.7.152: Giants as big as a Great Ape (pack 0.15.68)
- Andy: "make all the giants the same size as warrior born great apes". The Aether's four Giants (Highfield, Magnetic,
  Arctic, Irradiated) now stand 30.6 blocks tall and 10.2 wide (a player's 1.8 x 17, the Warrior-born Great Ape's
  size), drawn to match. The vanilla Giant gets a scale to the same height.
- Their reach grew with them: stamp trigger 12 / range 14 (lands 6 ahead), element power 24, aura 14, flare wither
  12, step height 3, follow range 64.
- Too big to path through woods, a Giant with no path strides straight at its target. Andy: "it should knock down
  any tree, or building. dont get in the way": natural trees it walks into topple away from it, and everything else
  breakable in its way (buildings included) is smashed, a quarter of it dropped. Only its own monolith is spared.
  Needs mobGriefing on.

## 0.7.154: Stormborn and Dawnborn buffs (pack 0.15.72)
Andy picked every Stormborn buff and the Dawnborn's numbers and Sunflare.
- Stormborn: 12% faster under the open sky (was 8%). Sparks fly in any rain, not just thunderstorms, deal 4 (was 3),
  and leap on to a second monster. Lightning that strikes them charges them: Speed II and Strength I for 30 s. The
  underground slowdown starts below Y 0 (was sea level).
- Dawnborn: 15% harder in the sun (was 10%), heal 1 every 3 s (was 5 s), 30% more against the undead (was 20%), and by
  day their blows set the undead alight for 4 s.
- The two left-hold moves (Thunderclap, Sunflare) live in Jaymo's Powers 0.15.72 (`power.heritage.HeritageMoves`).

## 0.7.155: zombie awareness (pack 0.15.73)
- Andy: "player zombies should work like this [Zombie Awareness], and maybe work it in to help with our stealth".
  Zombie Awareness is ARR, so `Awareness` is our own code for its ideas. Zombies (tag `jaymoskingdoms:aware`: zombie,
  husk, drowned, zombie villager, and Kitchen's reanimated player corpses `jaymoskitchen:zombie_player`, Andy: "it should
  apply to the reanimated corpse zombies too"; zombie corpses already rise as plain zombies; add skeletons, creepers or witches there to make them aware too) go and look, the
  same search `Senses` uses for a lost target, when they:
  - hear noise (`ServerLevelGameEventMixin`): mining, placing, explosions (48 blocks), bows, eating, doors, levers,
    buttons, plates, pistons, chests, note blocks, jukeboxes, fuses, lightning, a sprinting player's footsteps. They go
    for the loudest they hear, so pistons and TNT draw them off. Sneaking halves your noise; Quiet Step cuts it to a
    third (rank 2: a fifth).
  - see a light in the dark: a held torch (anything that gives light), being on fire, or bright block light at your
    eyes, from 40 blocks with a line of sight. Shadowed shortens it 20% a rank.
  - smell blood: hits spill blood (red on the ground) and below 30% health you drip; zombies within 24 blocks follow
    the freshest trail. It lasts a minute; Slip the Net halves that.
  - hear the horde: a zombie that goes after you groans, and idle zombies within 16 blocks come.
  Lured zombies show on the awareness gauge as searching, and count as unaware for sneak attacks.

## 0.7.157: one dynamite, Balkon's (pack 0.15.75)
- Andy: "change dynamite to just the balkon recipe and design across all requirements". The pack's one dynamite is
  Balkon's WeaponMod's (`weaponmod:dynamite`, MIT). With WeaponMod loaded, pirate bombardiers hold and drop its stick,
  their thrown sticks look like it and leave it behind when doused (`Dynamite.stick()`). Our own `jaymoskingdoms:dynamite`
  is off the creative tab, its recipe is off, and an old stick crafts into Balkon's (shapeless, `dynamite_to_balkon`).
  The bomb glider, Kitchen's exploding pot and the mining charge already took only Balkon's.
- Without WeaponMod ours stands in, as Balkon's: its recipe (string over two gunpowder makes two, was string, gunpowder,
  paper) and its sprite and held pose (`textures/item/LICENSE-WeaponMod.txt`).
- The bombardiers' skins (`tools/gen_pirates.py`) paint the bandolier's sticks in Balkon's red with a grey fuse.

## 0.7.159: Giants rarer, asleep before Borderlands (pack 0.15.79)
- Andy: "the giants need to be rarer, and they shouldnt activate until after borderlands world tier, the monument will
  only hum." Monoliths rise a third as often (2% per player in the wild every 2 minutes, about one per 100 minutes)
  and at least 2400 blocks apart (was 1200).
- A monolith's Giant rises only for a player at Wildlands or higher, the tier after Borderlands (Andy: "do
  wildlands"). Anyone below
  who comes near only hears it hum (a low beacon drone, its element's motes round the crown); no chat text.

## 0.7.160: no usernames left on show (pack 0.15.80)
- Andy: "usernames should only show up for commands, it still appears in scoreboard and over characters and it
  shouldnt". 0.7.149 only stood in a character name when the one in MCA's creator differed from the username, and the
  creator starts with the username, so most players still showed it on the tab list and over their head.
- Now a player whose MCA name is their username (or blank) is given one from MCA's own name lists for their gender
  (`McaBridge.randomName`), written into MCA's family tree (so villagers call them that too) and kept on the player
  (`CharacterNames.GIVEN`, saved), so it is always the same one. It is checked every second, so saving the creator
  with the username again just puts the given name back. A name of their own, typed in MCA's editor, wins as before.
  Commands still take usernames.
- **0.7.162, Make Do (OmniCrossbow as a skill):** Andy: "Marksman class should get acess to this
  (modrinth.com/mod/omnicrossbow)", then "It should be a node in the skill tree under survival, unlocks a utility across
  classes". OmniCrossbow (LGPL-3.0, by ThePotatoArchivist) joins the pack unchanged (Powers 0.15.85,
  `packModsOverride`). A new Survival perk, **Make Do** (level 5, 2 points, under Forager, tier 1 middle), lets any
  class's crossbow load anything from the off hand. Then "No omni enchant at all": the mod's Omni enchantment is
  stripped from every loot table's drops (`LootTableEvents.MODIFY_DROPS`; an Omni-only book becomes a plain book) and
  from mobs' gear as they join the world (the raid's last pillager), and one that turns up anyway (`/give`) does
  nothing in a player's hands. `OmniCrossbows` decides, through `mixin/omni/PlayerProjectileMixin` (priority 1500,
  after the mod's own mixin on `Player.getProjectile`), applied only when the mod is loaded; without the mod the perk
  can't be learned. Nothing of the mod's is compiled against.

## 0.7.164: dark nights keep shaders on (pack 0.15.95)
- Andy asked whether True Darkness Compat (MIT, 1.20.1 Forge only) could replace switching shaders off on dark nights,
  then "yeah lets try that, my players want shaders". Its idea, remade for 26.3 Iris (none of its code): the sky light
  level Iris hands a shader pack (`gl_MultiTexCoord1`, for Sodium's terrain and for entities, hands and block
  entities) is multiplied by a new uniform, `jaymo_SkyLight`, the same moonlight factor the vanilla lightmap gets
  (`DarkNights.ShaderSkyLight`, `client/mixin/iris`, Iris by reflection and string targets, no compile dependency).
  Under a new moon the open sky lights a pack's world no more than a cave roof does; a full or event moon is
  unchanged. It isn't tied to particular packs (True Darkness Compat patched four): any pack that reads the light levels
  (BSL, Complementary, Sildur's, Photon...) follows it. A pack's own "minimum light" setting still keeps some sight.
- Shaders now stay on all night. If the hook doesn't take (an Iris update renames what it patches), the old switch-off
  comes back by itself, with a warning in the log.
- Not yet seen in game: needs a check with a shader pack on a new-moon night.

## 0.7.165: the creator's name sticks (pack 0.15.105)
- Andy: "character names still randomize even though both players input their own when joining the server just now.
  we had to use the command to make it correct". MCA's start-up creator (`DestinyScreen`) keeps the typed name on the
  client only: its name page's Accept button moves on to the body, traits or destiny page without saving, and nothing
  later saves it either (only the editor's own Done button does, which is why `/mca editor` fixed it). So the server
  still had the name `CharacterNames` gives a player who hasn't picked one.
- `client/mixin/mca/McaDestinyNameMixin`: leaving the creator's name page now saves the editor to the server
  (`syncVillagerData`, as Done does), so the typed name reaches MCA's family tree and replaces the given one at once.
- Compile-checked against the 26.3 jars; not yet run in game.

## 0.7.166: costumes and disguises on the tab list (pack 0.15.106)
- Andy: "it should do the same with equipped costumes and disguises. right now it doesnt change the scoreboard name".
- `CharacterNames` now works out what a player shows as, not just their character name: a hood (a costume's head
  piece that leaves the face out) shows the costume's name, or "Hooded Figure"; a goblin disguise (`Disguise`) shows
  "Goblin". Full masks (face-hiding costumes, Rig Helms, bandit masks and bandanas) stay Powers' to name; Kingdoms now
  stands aside for them on both sides (`Identities.maskKey`, `maskedAs`) rather than relying on mixin order.
- The tab list is resent whenever a player's tab list name changes (checked every second). Powers resent it only when
  a costume or Rig Helm went on or off, so a bandit mask or bandana never showed there; now it does.
- The synced attachment is now `jaymoskingdoms:shown_name`, a text component, so "Hooded Figure" and "Goblin" are
  translated on each client.
- Barbarian dress isn't named: leather and a stone weapon is everyone's early-game kit.
- Compile-checked against the 26.3 jars; not yet run in game.

## 0.7.168: the bounty board is several blocks, with notices to pull and pin
- Andy: "the bounty board should be multiple blocks, allowing for item frame like pulling on and placing onto the
  board, some villagers will be seen posting to it."
- **The board** (`BountyBoard`) is 3 blocks wide and 2 tall: spruce log posts at the ends, a stained plank face and a
  small roof, one notice spot per block (`column`, `row`, `notice` block state). It goes up from its bottom middle
  block and needs room for all six; breaking any block takes the whole board down, and it drops once. Pistons can't
  move it. A town's board from before this is taken down and put up again whole (or somewhere it fits near the bell).
- **Notices** (`bounty_notice` item): punch or use a pinned notice to pull it off, like an item off a frame. That takes
  the bounty (the same checks as the list) and hands you the notice: "Wanted: name", the price alive and for the head,
  the crime and where they were last seen. Use a bare spot with a notice in hand to pin it back up: that gives the hunt
  up. Use a bare spot, or sneak-use, to read the whole list as before. Taking a bounty from the list also hands you its
  notice; giving it up or being paid takes the notice away. Notices for bounties that are over come down by themselves.
- **Villagers post them** (`Bounties.post`, `errands`): in daylight, now and then (`POST_CHANCE` each 5 seconds), a
  board with a bare spot and an open bounty of its realm that isn't up yet sends a grown villager from within 32
  blocks (awake, hands free, not wanted themselves; MCA villagers included) walking over with the notice in hand. At
  the board they swing an arm, it's pinned with the item frame sound, and half the time they call it out ("Wanted:
  name! 40 emeralds to whoever brings them in."). They give up after 40 seconds. The highest price goes up first.
- **Town notices** (`TownNotices`). Andy: "they can place quests or useless notices too, to add to immersion, (bring
  me some leashed animals, or feed, etc,) add about 5 per village type". With no bounty to post, a villager now and then
  pins up one of their village type's five notices instead (at most 3 on a board, down again after 2 days): three
  errands and two bits of news for each of vanilla's seven types (plains, desert, savanna, snowy, taiga, jungle, swamp).
  Pulling an errand off is a quest from the villager who posted it, turned in to them as usual: bring hay, eggs,
  water, coal, berries, firewood, cocoa and so on (gather quests), or bring sheep, a camel, cattle, llamas, a goat, pigs,
  hens or frogs to them on a lead (a new `Quest.LEAD` kind: the animals are counted within 12 blocks on your lead, and
  handed over they stay in town). Pulling news off gives you the paper ("LOST: grey cat, one white paw. Answers to
  nothing."), which you can pin up again on any board. Town notices are white paper with a brass pin (`town_notice`).
- Art (`tools/vanilla_style/bounty_board.py`): vanilla's spruce planks stained dark for the face, spruce log posts,
  three notice designs (a crook's face, a masked bandit, a written notice under a red seal) in the old board's poster
  colours, and new 16px icons for the board and the notice.
- Not yet seen in game.

## 0.7.169: blood doesn't twinkle (pack 0.15.109)
- Andy: blood shouldn't twinkle like redstone. Rend's bleeding, vampire bites and leaps, Night Bleeders and their bats,
  blood scent and the Lycan's tearing out now send Jaymo's Powers' still blood fleck (`PowersCompat.blood`, looked up by
  id) instead of vanilla dust; without Powers it's dust as before.
- Compile-checked against the 26.3 jars; not yet seen in game.

## 0.7.170: Executioner unlocks the weapon finishers (pack 0.15.110)
- Andy: "make all the other executions unlocked in the combat arts tree replacing the current executioner function".
  The Combat Arts perk Executioner (same place in the tree, same cost, so nobody's ranks move) no longer makes a combo's
  4th full-strength hit deal double on a foe under 30%. It now unlocks Jaymo's Powers' weapon finishers on the downed
  (hold use with a weapon: Run Through, Executioner's chop, Skull Crusher, Pinned...). Powers reads the rank by
  reflection (`Finishers.weaponsUnlocked`). Bare hands need no perk.

## 0.7.172: no TaCZ flintlocks (pack 0.15.118)
- Andy didn't like the TaCZ flintlocks, so Jaymo's Powers no longer ships them. Musket guards get WeaponMod's musket
  (with an iron bayonet) or a crossbow, pirates WeaponMod's flintlock or a crossbow, and the Marksman card shows
  WeaponMod's musket and bullets. A guard still holding an old TaCZ musket is handed a new one at his next check.
  `TaczGuns` is gone.
- Parse-checked only; not yet seen in game.

## 0.7.173: the server starts again (pack 0.15.119)
- 0.7.168's bounty board loot table used `minecraft:block_state_property`, which 26.3 doesn't have, so the whole
  datapack failed to load and the server wouldn't start. It uses `minecraft:match_block` with `state` now, as vanilla's beds do.

## 0.7.174: crash when the plague weakened someone (pack 0.15.123)
- `Atrophy.onMobHurt` added Weakness while vanilla was still walking the hurt mob's effect map in `hurtServer`, so a Night
  Bleeder's bite crashed the server (ConcurrentModificationException, Deck 2026-10-22). The Weakness now goes on a task
  run after the hit.

## 0.7.176: rulers' and guards' standing on MCA's screen, their old ways back, and lives among their people (pack 0.15.129)
- Andy: "the King or queen's temperament and status should be on this screen. same with guards. it doesnt seem like they
  still have the old behaviors anymore either. allow them to maintain and build relationships with their people."
- MCA's interact screen (name, trade, mood, personality) now also shows, for a ruler: "Queen of X", their
  temperament (coloured; hover for what it means), "Reign: N days" (hover: their blood), and what they're about now
  (round, court, judging, among their people, home for the night). For a guard: "Guard of X", who they're sworn to
  (hover: that ruler's temperament) and their duty (watch, off watch, escorting, leading a prisoner, seeing a stranger
  off, fighting). `Standing` works the lines out on the server and syncs them on the villager; `McaInteractInfoMixin`
  draws them. The Reign record now keeps the day they were crowned (older reigns count from when this loads).
- The temper ways that 0.7.138 left with the old folk rulers work for villagers crowned and the realm's MCA guards:
  the Retinue (kneel to the tyrant, sheathe your blade by the warlord, the noble's bread, the reveler's gifts, the
  paranoid's and purist's guards shoving, dogging and throwing strangers out), the warlord's drilled guards, the
  assassin's stab, poison and parry, the guards he calls, curing a poisoned ruler (milk, honey, golden apple) and warning
  one, and carriages and rickshaws for the round. Talking to a villager ruler (right-click) doesn't count as laying hands
  on them.
- Rulers keep a day like their people's: their round from dawn, court until mid-afternoon, then their own time in town
  and the night in their own bed (before, they held the throne all day and night with their brain switched off). Guards
  get a shift off by day and the evening. Off duty, both join the townsfolk's routines and neighbours, so friendships,
  courting, feuds and marriage go on with them (they had been left out of `Routines.freeOf`).
- Not ported: the folk archers on posts (MCA's archers stand in). Compile-checked against the 26.3 jars; not yet seen in game.

## 0.7.178: no Minecraft skin under the MCA villager body (pack 0.15.137)
- Andy: "it looks like the default skins are still being drawn underneath the MCA villager custom and it is causing
  more clipping issues" (his account's Minecraft skin, seen in Time Flow's over-the-shoulder view).
- Cause: MCA hides a villager-bodied player's own Minecraft body by giving LivingEntityRenderer.getRenderType no render
  type. Shoulder Surfing Reloaded gives AvatarRenderer its own getRenderType, and while it fades the player in front of
  its close camera it returns a see-through render type of the Minecraft skin without asking the method MCA hooks, so
  the Minecraft body was drawn under MCA's and its sleeves and hat poked out. `McaShoulderSurfingBodyMixin` (after
  Shoulder Surfing's, only when its method is there) gives an MCA body no render type again.
- Andy: "MCA should cover the bare body too if that shows". MCA draws its bare skin-coloured body, then the clothes
  0.0625 of a pixel over it, so from a distance or once an animation moves the copies apart the bare body showed through.
  `McaSkinCover` hides the bare body's torso, arms and legs wherever the clothes' texture covers them whole (with the
  jacket, sleeve and trouser layers), as the body is drawn, the way Powers' costumes cover the skin (`CostumeCover`).
  Never the head (faces and hoods have holes). Library clothes are read from their in-memory texture.
- Compile-checked against the 26.3, MCA and Fabric API jars; not yet seen in game.

## 0.7.184: player groups and handshakes (pack 0.15.147)
- Andy: "add a group system for players so you can see their player tags once finding and interacting with them, (both
  players right clicking each other, for a cinematic action cam handshake, then a success sound) and scoreboard team
  integration allowing for reduced team damage and other benefits."
- `Handshakes`: use on another player, empty handed and not sneaking (sneak + use stays the vampire's bite and the
  downed's pick-up), and you hold your right hand out to them for 10 s, for everyone to see. If they use on you back
  while it's out, you shake: set facing each other at arm's length, held still, three slow pumps (2.3 s), a leather
  creak as the hands meet and a level-up chime with an amethyst ring at the end. A blow, going down or stepping apart
  breaks it off. Not behind a full mask (as with `/introduce`).
- Each of the two sees it as a film (`client/HandshakeCinematic`, camera via `HandshakeCameraMixin`): black bars, the HUD
  cleared, a wide profile pushing in, a low close shot circling the grip, then over your shoulder onto their face.
  The arms are `client/HandshakePose`.
- Afterwards the two know each other's names (`Introductions`) and share a group (`Groups`), which is a plain
  scoreboard team: the offerer's, or a new "<name>'s Company" in a random colour. An op's own `/team` counts too.
- Group perks: groupmates always see each other's names (nametag, tab list) in the group's colour; a groupmate's blows
  land at 25% (arrows and powers too, `Groups.FRIENDLY_DAMAGE`); each groupmate within 8 blocks takes 5% off blows from
  anyone else, up to 3 (`SIDE_GUARD`); invisible groupmates show; no shoving; vanilla `/teammsg` works.
- `/group` lists your group (character names; offline members only counted, so no usernames), `/group leave`,
  `/group name <name>`. Leaving or renaming an op's team is refused.
- Andy: "The initiating player's group is what is joined, everytime. Players within the same group can still
  handshake, but the cutscene only ever plays once for the same combo of players. Every other time it is a quicker
  single dap variation." The taker always leaves their group for the offerer's (an empty one of ours is removed). Each
  player keeps whom they've shaken hands with (`Handshakes.SHOOK`, saved); a pair who have gets a 0.7 s dap (hand up
  high, one clasp and a tug, a lighter chime) with no film.
- Compile-checked against 26.3; not yet seen in game.
## 0.7.184: guards make way for quest carriers, and take you to the ruler (pack 0.15.147)
- Andy: "guards should make way for you regardless of their king's temperament if you are carrying a quest item, you
  can also request an audience from a guard and they will escort you to the king/queen as long as your rep isnt too low."
- Free passage (`Retinue.passage`): carrying a quest's letter or keepsake, or a ruler's own quest done and ready to turn
  in (`Quests.questItem`), or being led to the ruler (and 90 s after). Whatever the temper, no heat builds, no shoves,
  no fines or kneeling demands, touching the ruler is allowed, and a temper ban doesn't see you out of the city (a
  vigilante's exile still does). Guards between you and the ruler, or in front of you, step aside ("Make way!").
- Audiences (`Audiences`): "Seek an audience" in an MCA guard's menu, or the "[Ask to be taken before the ruler]" line
  a folk guard's audience shows (`/kingdom audience`, the nearest guard within 6 blocks). With good name of at least
  -20, not banned, and the ruler about (within 160 blocks) and awake, the guard walks you over, waits when you fall
  behind ("Keep up"), gives up past 40 blocks or 3 minutes, and announces you; the ruler answers by temper. Otherwise
  he says why not (busy, banned, refused, not holding court, asleep). MCA's screen shows the guard's duty as "Taking a
  visitor to their liege". Tuning at the top of `Audiences` and `Retinue`.
- Compile-checked; not playtested.
## 0.7.184: Earthling ki glow for Z Fighters only, and no marks poking through costumes (pack 0.15.147)
- Andy: "Earthlings should have no special glow or ki effects on their heritage skin unless they are z fighters, and
  even then the weird costume clipping is back." The near-death surge still gives every Earthling its random buff, but
  the fists' ki glow, the sparks and the flare's sounds only come for a player with the Z Fighter power
  (`EarthlingSurge.shows`, read through `PowersCompat`). Earthling villagers never glow.
- Under a costume (or the symbiote's suit) a player's heritage marks and their glow aren't drawn at all. They're drawn
  on the plain body's pose while the costume follows MCA's, so where a cut leaves skin bare (the gi's forearms) the
  wraps and the glow slid out through the sleeves in motion. Eyes and fins work as before.
- Andy: "Can you add the wraps to the gi?" `HeritageLooks.earthlingWraps` tells Jaymo's Powers whether the player is an
  Earthling and whether their ki is lit, so the gi draws the wraps (and a Z Fighter's glow) itself, in its own pose.
- Compile-checked; not yet seen in game.
## 0.7.184: Lineage replaces the Blood Rite (pack 0.15.147)
- Andy: "add a lineage system that replaces blood rites, now instead unlocking a heritage will allow you to swap
  perspectives with your offspring, and then use the heritage and new class." Then, after the first cut: a child
  "should draw from a pool of heritages you unlocked and the heritage you currently have. That way it isnt directly
  influenced"; no keeping inventory or powers: "the power you had is given a high affinity to your child, they still
  have to awaken it themselves"; you choose whether to hand your things down or leave them on your elder; "shift right
  clicking your teen and above should prompt you if you're sure you want to progress your bloodline's story".
- Waking an earned heritage (Endborn, Dragonblood, Deepborn, Stormborn) no longer hands out a Blood Rite or changes your
  own blood. A player's child is born to their heritage or any earned one they've woken, evenly at random
  (`VillagerHeritage.passed`, used by both MCA birth paths), so the blood isn't theirs to pick.
- Sneak-use one of your own MCA children, a teen or older, with an empty hand (`Lineage`, registered before Retinue,
  Pickpocket and Quests): `LineageScreen` asks "Progress your bloodline's story?", naming the child, the blood they were
  born to and the power you'd hand down. Hand them down / My elder keeps them / Not yet.
- The swap (`McaBridge.takeOver`): you wake where the child stood with their MCA look (MCA's editor patch of the child
  merged into your player look, sent to everyone), name and heritage. The child villager is gone. The body you leave
  stays where you stood as an MCA villager with your old look, name and heritage, already an elder
  (`CourtWizards.ELDER_DAYS` grown), so it greys and dies of old age in time; MCA family can be asked to follow as ever.
- MCA's family tree follows: your node takes the child's place (their parents, with your old self as one of them; their
  partner and children), the old self's new node takes yours (your spouse's partner, your other children's parent),
  and the child's node is removed. Marriage states are set to married-to-player or married-to-villager as the new
  partner is. Links are written straight into the nodes (reflection on `FamilyTreeNode`'s father/mother/partner).
- Your power leaves you and is handed down (`PowersCompat.passDown` → Powers' `Affinity.passDown`): it awakens in you,
  the child, after a fresh 3-5 days. Your class is unmade and the class screen asks again (can't be shut); every skill
  and attribute point comes back (`Skills.respec`; experience and levels stay).
- Things: handed down, you keep your inventory as the child. Left with the elder, they wear your armour and what was in
  your hands (dropped whole when they die) and carry the rest in their MCA pack; what doesn't fit falls at their feet.
- Elders live lives of their own (`Elders`, attachments `elder_journey` on the elder and `elder_roads` on the
  Overworld). Andy: "you could ask your father/mother to go on an adventure with you for old times sake, or even find
  them on travels of their own", then "Is it possible to see them traveling along the way?" / "Yes please". Asked along,
  they follow you as MCA family does. Left be, they stay home 2-5 days, then set off on foot for an MCA village or a
  standing realm's seat 250-1500 blocks off, stay 1-3 days and walk home; they never set off while a player is within 64
  blocks, following someone, downed, asleep or riding. On the road, like the caravans: unseen, the journey goes on in
  the record (0.1 blocks a tick, along a King's Road where one runs their way, else straight); a player within 80 blocks
  of where they've got to and the elder is put back in the world there (their whole saved self, same UUID and family)
  and walks on for real (helped over anything they're stuck on for 12 s); nobody within 112 and they go back into the
  record. A copy left in an unloading chunk is put away when it loads again. Their heir gets an MCA letter when they
  set off and when they're home.
- (Andy) A Nightborn mother's child is Nightborn 70% of the time (`VillagerHeritage.NIGHTBORN_MOTHER_CHANCE`), players'
  and villagers' alike; otherwise the parents' blood as before.
- Old Blood Rites still load (`jaymoskingdoms:blood_rite` stays registered, "Blood Rite (crumbling)"); read, one crumbles.
  The heritage screen's rite mode is gone. `/kingdom heritage set|unlock` are unchanged.
- Compile-checked against the 26.3, MCA and Fabric API jars; not yet seen in game.
## 0.7.187: Flame Avatars in the Nether (pack 0.15.151)
- Andy: "there should be mca avatar in the nether seen fighting the other two factions. They will not speak to the
  player unless they are emberborn, and will not be hostile unless you are near one of their encampments". The
  third Nether side beside the Hornkin and the piglins: Emberborn consumed by their fire (Powers' Flame Avatar).
- **Camps** (`nether/AvatarCamps`, built by `FortBuildings.avatarCamp`): scorched blackstone and basalt ground round
  a netherrack fire ringed by campfires, six basalt pillars crowned with eternal fire and a cache chest (blaze rods,
  fire charges, magma cream, gold, sometimes netherite scrap). One may rise every few minutes 64-128 blocks from a
  player in the Nether, at least 200 from another camp, using the forts' site search. 4 avatars at first, a recruit
  every 4 minutes up to 6. Ops: `/kingdom avatarcamp found | info`.
- **Avatars** are real MCA villagers (guards, born Emberborn, 40 health, gold or iron blades) wearing the Flame Avatar
  look (charred body, glowing veins, burning). They keep to their camp, and every few minutes a band of 3 marches on
  the nearest Hornkin fort or piglin camp. They attack Hornkin and piglins on sight, and players (not Emberborn, not
  creative) only within 24 blocks of their own camp. Anyone not Emberborn gets no talk or menu from them.
- Compile-checked against the 26.3 and MCA jars; not yet seen in game.
## 0.7.197: Bounty boards face the open square
- Andy: "the bounty board shouldn't be facing blocks so close like this when there is a wide open square in front of
  it". A town's board used to go up at the nearest free spot by the bell, always facing the bell, so it could stare
  at a wall or a market stall one block away.
- Now every free spot 3-8 blocks from the bell is tried in all four facings and scored by the open ground before it
  (5 lanes, counted out to 8 blocks until something you can't walk through), plus a little for facing the bell, minus
  a little per block from the bell. The best one gets the board. Numbers: `BOARD_VIEW`, `BOARD_VIEW_SIDE`,
  `BOARD_BELL_BONUS`, `BOARD_OPEN_ENOUGH`, `BOARD_TURN_GAIN` at the top of `Bounties`.
- Boards already up that see less than 20 open blocks are looked over once per server run; if another spot sees at
  least 6 more, the board moves there (pinned notices come down; villagers pin new ones).
- Compile-checked status in the PR; not yet seen in game.
## 0.7.200: blizzards and sandstorms reach body heat (pack 0.16.13)
- Kitchen 0.5.25 adds thirst and body heat. `KitchenCompat.skills()` makes Emberborn proof against the heat (as
  Frostborn already are against the cold) and hands Kitchen `Calamities.bodyHeat`: a full blizzard takes 45 off the
  air around you, a full sandstorm adds 25 (`BODY_SNOW`, `BODY_SAND`), a third of that under a roof.
## 0.7.201: Letters, messenger pigeons, couriers across the land; the news by letter (pack 0.16.14)
- Andy: "i want a messenger system for getting information across the lands, this is so we get less information
  through chat and the world is more immersive. it should work like this for birds, you dont have to add owls if this
  cant be ported directly, and mca couriers who operate similarly" (Courier Owls: All Rights Reserved, no 26.3 build,
  so remade from its description; nothing of it copied).
- **Letters** (`Letters`): a blank Letter (2 paper + a feather makes 2) opens a writing screen (`client/LetterScreen`):
  who it's for (a character name, on or not; a username works too) and up to 600 characters. Seal makes a Sealed
  Letter, "Letter to X", "From Y" beneath; using one reads it. Names: `Post.resolve` against everyone's character
  name as last seen (`Post.NAMES`).
- **Messenger pigeons** (`Pigeon`, a parrot underneath with its own art from `tools/gen_post.py`; no shoulder riding,
  no squawks, no mimicry): wild in plains, meadows, forests, savannas and cherry groves; tamed with seeds like a
  parrot. Use a sealed letter on your own pigeon: it climbs away, is taken out of the world (`PigeonPost.Flight`),
  and comes down to whoever it's for after the time the distance takes (1.1 blocks a tick, 10 s to 5 min), wherever
  they are; it hands the letter over and flies home to you the same way. Nobody to take it for two days: it brings
  the letter back. Killed on the way, it drops the letter.
- **Couriers** (`Couriers`, rewritten): sneak and use a sealed letter on a grown villager of a realm or village that
  doesn't hold you in ill repute and they take it anywhere in the Overworld (`/kingdom deliver <player>` still takes
  whatever you hold). Past 160 blocks they saddle a courser and ride. Like a travelling elder they go on in a record
  (`Couriers.Errand`, along the King's Roads) once nobody can see them, and come back into the world on the road
  when someone's within 80 blocks; the one with your letter rides up to you, says who it's from, and rides home.
  If the one it's for isn't on, they wait at home; after two days it goes back to the sender. Errands are saved now
  (a restart no longer hands everything back). Their horses go back to the stable unseen.
- **The news by letter** (`Post`): what `KingdomEvents.announce` (and the first fallen Giant) put in everyone's chat
  is gathered for 20 s and written up as a "Tidings" letter for each player on. MCA's mail (family letters,
  condolences, the elders' journeys) is taken out of MCA's inbox, and MCA's "You have mail" toast no longer shows
  (`McaMailMixin`). Each is brought one at a time per player: by a townsman who runs it over when you're in a town,
  else by a wild pigeon that comes down to you (not under the ground: it waits till you're out). Letters that can't
  be brought yet wait (`Post.WAITING`, saved). The headlines still go to the server log.
- Compile-checked and dev-server booted in the cloud; not yet seen in game.

## 0.7.202: weathering, and builders' upkeep (pack 0.16.15)
Andy's immersion pass, stage 2 (2026-10-25): Immersive Weathering is 1.20.1 only (LGPL, on Moonlight), so this is our
own take, nothing copied. Andy: weathering may hit villages, "upkeep is already something builders should be doing".
- `Weathering`: every second 24 blocks near each player (40 out, from the surface down 16) are looked at; an exposed
  one may age. Cobblestone and stone bricks (stairs, slabs, walls too) go mossy where it's wet (rain on it, or beside
  water or moss, which doubles it), never in deserts. Stone, deepslate, nether and blackstone bricks and deepslate tiles
  crack: frost under the open sky in cold lands or winter, heat beside fire or lava, rarely anywhere. Bare dirt in the
  rain turns to mud; mud in the sun dries back. Grass under leaves gets leaf litter (much more in Kitchen's autumn) that
  rots away outside autumn. Grass in the open grows tufts in spring and summer. Copper ages by itself already. Game rule
  `jaymoskingdoms:weathering` (on). Tuning at the top of the class.
- `Upkeep`: what weathering changes within 96 blocks of a standing settlement's seat is noted for it (Overworld
  attachment `upkeep`, at most 256 a settlement). By day, one builder of each crew (the last; a lone builder once 8 are
  waiting) walks round scrubbing moss off, mending cracks, raking litter and turning mud back to dirt. Only blocks
  weathering changed, so a Grim hold's old moss stays. Wraps the builders' brain right after `Construction`, so rubble,
  mines and groundwork still come first.
- Compile-checked; a Kingdoms dev server boots. Not seen in game yet.

## 0.7.203: MCA villagers take orders only from folk they're involved with; a foreigner's talk menu is gibberish (pack 0.16.18)
Andy (2026-10-25): Pick Up, Locations, Follow Me, Stay Here, Ride Mount and Armor "should not be available for villagers
you aren't involved with"; and the line at the top of the talk menu should be scrambled when you don't speak their tongue.
- `Strangers`: involved means MCA family, kin, spouse, betrothed or promised, whoever hired them, or a friend of 50 hearts
  (`TRUSTED_HEARTS`; MCA's gold heart is 100). To anyone else the interact page hides those six buttons
  (`McaStrangerButtonMixin`, looked at again when MCA's ties for the villager arrive), and the server refuses `pick_up`,
  `follow`, `stay`, `ridehorse`, `armor`, `sethome`, `setworkplace` and `gohome` with a word over the hotbar
  (`McaCommandMixin`). "Move" (let a follower go) stays for anyone. Gift, talk and trade are untouched.
- `McaMessageMixin`: MCA's talk-menu question line is garbled like their chat for a foreigner (the answers were already
  cut to Hug/Kiss/rock-paper-scissors).
- Compile-checked; not seen in game yet.

## 0.7.205: the handshake film shows the hands meeting (pack 0.16.24)
- Andy: "One of the frames the cutscene puts front and center NEEDS to be the hands actually coming together.
  Everything else looks fine." The hands meet at tick ~10, which fell in the wide opening shot. The film now opens on
  the hands: side on and a touch low, aimed at where they meet (1.15 blocks up, at shoulder height less the held-out
  arm's drop), easing in from 1.6 to 1.25 blocks. It holds through the meeting and the first pumps (to tick 24), then the
  wide shot draws back from it (to 34), then over the shoulder as before. The one taking the hand waits 3 ticks
  before reaching (`HandshakePose.REACH_DELAY`) so the shot sees it come in; the grip (creak, pumps) moves to tick 12.
- Compile-checked; not yet seen in game.
## 0.7.205: flint struck on a rock counts as lighting a fire (pack 0.16.24)
- Powers 0.16.24 lets players light fires with flint in one hand and a rock in the other. `Theft`'s arson watch now
  treats that like flint and steel (`strikesFlint`: flint plus any `jaymospowers:*_rock`). Compile-checked.
## 0.7.206: whaling, stages 1-2: great whales, roped harpoons, carcasses, cutting in, tryworks, whale trade, oil lamps, firebombs, boiling oil, sharks, the white whale (pack 0.16.25)
Andy (2026-10-25): a whaling system: larger whales in the oceans, crews throwing roped javelins that tie their boat to the
animal, more thrown till it succumbs, crews at it for their kingdoms over days, big ships to haul and butcher. Staged:
1 whales and harpoons (this), 2 hauling alongside a big ship, cutting in and tryworks (whale oil), 3 the kingdoms' whaling
crews, 4 polish. Our own beast and model, nothing copied (Naturalist's whale art is all rights reserved).
- `whaling/GreatWhale`: about 14 blocks long (model built at a quarter size, drawn 3.5x; body box 4 x 2.8). Pods of 1-3
  in deep oceans (weight 2, one likely spot in 3). Cruises 7 down, drifts with its pod, comes up every 1.5-2.7 minutes to
  blow (spray, splash, roar) for 12 s, turns back from water under 10 deep. Blubber lets 30% of any blow but a harpoon's
  through. Struck: bolts from its hunters (the ends of its lines), weaving, sometimes sounding; its pod scatters. Each iron
  bleeds 1 every 2 s and tires it faster; each taut line slows it. Spent (stamina under 12%), it wallows at the surface
  blowing blood. Sperm whales (the dark look) may turn on whoever struck them and stove in the boat (riders hurt). It
  dies into a carcass, irons and all. Naturalist's whale is out of natural spawning (`Encounters.NO_NATURAL_SPAWNS`);
  Kitchen makes no corpse of ours (`jaymoskitchen:no_corpse`).
- `whaling/Harpoon` + `HarpoonItem`: draw and throw like a trident (iron ingot, stick, lead). Lodges in whales,
  carcasses and anything 1.4+ wide; its line is made fast to your boat, the ship you stand on, or you. The line pays
  out while a whale runs (to 48), drags the boat when it's out, is taken in as you close (to 4), parts strained 14 past
  its length. The pull on a boat is worked out by the steering player's game, on ships and whales by the server
  (`Ship.tow`, new). Sneak + use shears or an axe to cut your lines. A miss drops a harpoon to pick up.
- `whaling/WhaleCarcass`: floats belly up, you can stand on it, tow it on lines (lodge fresh harpoons in it too). Flense it
  with an axe, sword or knife: 48 strokes, 1.2 s apart, of blubber, raw whale meat or whale bone; 12% of whales hold
  ambergris. Stripped (6 bones float up) or after three days, it sinks, and its irons drop free.
- Items: harpoon, whale blubber (for stage 2's tryworks), raw whale meat and whale steak (smelt, smoke, campfire), whale
  bone (6 bone meal), ambergris, great whale spawn egg. Art: `tools/gen_whaling.py`.
- Stage 2 (Andy: "All the whale parts are extremely valuable to kingdoms and their inhabitants"):
  - Cutting in: only 16 of a carcass's 48 strokes can be cut from the water or a boat. The rest needs it lashed
    alongside a big ship: a harpoon in it made fast to a ship of 600+ blocks, and it within 6 blocks of her side
    (`WhaleCarcass.LOOSE`, `BIG_SHIP`, `ALONGSIDE`).
  - `whaling/TryworksBlock` (bricks, cauldron, campfire): use blubber to fill the try-pot (2 a measure, 8 measures),
    flint and steel or a fire charge to light it; it renders a measure into a bottle's worth of whale oil every 8 s (4
    at most) and goes out when dry. Glass bottle draws whale oil. All in its block state, so it rides on a ship's deck.
    Light 13 while lit, greasy smoke.
  - Whale oil (16 a stack, gives the bottle back): 4 torches with 4 sticks.
  - `whaling/WhaleTrade`: grown villagers buy whale goods dearly, by trade (fishermen and butchers meat and blubber,
    clerics oil 5 and ambergris 48, librarians and cartographers oil, leatherworkers blubber, smiths oil and bone,
    fletchers and toolsmiths bone, masons, farmers), and anyone buys whale steak, oil and ambergris (40). Sales pay the
    realm's toll like any trade.
- Tuning: the constants at the top of `GreatWhale`, `Harpoon`, `WhaleCarcass`, `TryworksBlock`, `WhaleTrade`, `OilLampBlock`, `Lamplighters`, `Firebomb`, `OilPotBlock`, `Sharks` and `Whaling`.

## 0.7.210: whaling, stage 3: the kingdoms' whaling crews (pack 0.16.32)
Andy (2026-10-25): "Start stage 3", crews seen at the task for their kingdoms over days.
- `Whalers`: a realm with a harbour and 600+ emeralds fits out a whaler (Jaki's cargo ship in its colours, a sea
  captain and 5 hands) every 2 days at most (40% a look). Her voyage (saved as `crew.mode` plus a `Voyage` on the
  Overworld): fitting out at the mooring for 5 minutes (a player who comes aboard signs on), outward to a ground
  160-320 blocks out to sea, hunting (her lookout sees whales 96 out; she chases, her hands throw harpoons in range,
  lines fast to her; a pod comes by out of sight after 3 minutes with none; gives up after a day), cutting in (hove to
  by the carcass on her lines, a stroke every 1.2 s into her hold), 2 whales, homeward with her tryworks smoking
  (blubber to oil aboard), home: hold into the realm's stores, catch's worth into its purse (oil 3, ambergris 40,
  bone 2, meat 1), signed hands aboard paid a lay (10% of the worth, at least 4 emeralds, plus 2 oil).
- Out of every player's reach for 2 days she's taken to have made her voyage unseen (1-2 whales' catch credited);
  seen again later, she's stowed. She runs from pirates like a merchantman, then takes up her voyage where she left
  it (a merchant's "run" mode no longer overwrites hers); sunk, the voyage ends.
- Whaleboats (Andy: "it'd be hard for the larger ship required for processing to keep up"): a whale within 72 blocks,
  she heaves to and lowers 2 spruce rowboats off the side toward it, an oarsman and a harpooner from her hands in each
  (one hand always stays aboard). The server rows them (head brought round 5 degrees a tick, 0.04 a tick pulling),
  they close to 7 blocks and the harpooner throws irons from 12, lines fast to the boat, so a running whale drags it.
  She follows under 0.3 sail beyond 40 blocks. Dead, the boats tow the carcass back on their lines while she closes;
  within 8 blocks of her side the lines are passed up and made fast aboard her (`Harpoon.passTo`) and she cuts in.
  Boats with no orders (whale lost, cutting in, homeward) row back and are hoisted in beside her, their hands back
  at their old stations; she won't sail home without them. A boat with no one left to row is cast adrift. Boats
  carry a saved `whaleboat` attachment (her id and each hand's station), so they survive a reload.
- `Harpoon.thrownAt` (crew harpooners, leading and lobbing), `WhaleCarcass.cutByCrew`, `Crew.Side.WHALER`,
  `Corsairs` hooks, `Navies.launch` now package-wide. Op: `/kingdom fleet whaler` (status, or fit one out now).

## 0.7.211: the underworld, stage 1: Pmeh and Shash bricks, speakeasies, dice, busts and the cells (pack 0.16.33)
Andy (2026-10-25): "Id like to see illegal goods traded amongst the people. Rare commodities like Pmeh and Shash bricks go
for sale around specific cities that eventually become known as hotbeds for trafficking goods and gambling. You should be
able to find these speakeasies hidden from the guard as being caught facilitating sale would end up with you flogged,
imprisoned, or worse."
- `Underworld`: the wealthiest third of realms (Andy: "One in three cities that are wealthier, giving the criminals a
  pool to siphon from", ranked by `Wealth.worth`) keep a den, which makes one of the two goods; a realm that grows into
  the top third gets one too, and a dug den stays if its city grows poorer. Goods: **Pmeh** is Jaymo's Kitchen's own herb (Andy: "Pmeh has to be
  rolled like it is in kitchen": roll it into a joint at a prep surface, then smoke it; looked up by id, so without
  Kitchen every den deals in Shash; since Kitchen 0.5.29 it no longer comes from tall grass, only from rare wild
  Pmeh in jungles and savannas, or grown from seed and dried on a rack) and the new **Shash Brick** (a trade good).
  Kitchen's fresh Pmeh and joints count as contraband too. **Grades** (Andy: "you just have to be good at growing and
  drying the plant to have quality enough to sell like the dealers do"): Kitchen grades Pmeh scraggly/fair/fine/prime;
  dealers sell prime and buy only fine (the listed price) and prime (x1.5), so wild fair Pmeh won't sell. Art: `tools/gen_contraband.py`.
- `Speakeasy`: the den is dug under open natural ground (nothing over it but leaves) 14-50 blocks from the seat (earth over all of it, no block
  entities or water in the way): a spruce hatch in the grass by a hay bale, a ladder down a stone shaft, a 9 x 7 x 4
  room 9 below: a bar of barrels with the fence penned behind it, a red runner, a dice table with a green cloth and
  stools, crates, hanging lanterns, cobwebs.
- The **fence** (a villager tagged `jaymoskingdoms.fence`, replaced a day after he's gone) sells and buys both goods,
  re-stocked daily: cheap where his den makes it (Pmeh 3, Shash 7; buys at 2 and 4), dear where it doesn't (sells at 12
  and 24, buys at 8 and 16), so it pays to carry goods between hotbeds. His sales pay the realm no toll.
- **Heat**: +1 a sale, +2 a day, +1 every 4 dice games, -8 a bust. From 10 a **dice-thrower** sets up: hold emeralds and
  right-click him (up to 8 a throw, two dice each, higher total takes double, a tie returns the stake). At 40 the city is
  a **known hotbed** (Tidings news), and its crooks buy contraband in the street (Pmeh 5, Shash 11).
- **Selling in the street** (Andy: "you can also sell to any villagers receptive enough to listen"): sneak and
  right-click any grown villager with contraband in hand. They listen if MCA hearts with you + mood x2 + their
  personality's lean (relaxed, odd, lazy, playful, flirty, extroverted, witty +15; greedy, confident +10; anxious,
  sensitive, shy, peaceful -10; crabby, grumpy, stubborn -15) + 15 in a known hotbed reaches 30. Then they buy one at
  street price (Pmeh 5, joint 3, Shash 11), once a day each; seen by others, it's DEALING. Townsfolk can't tell
  grades apart (Andy: "not everyone knows the difference"), so any dried Pmeh fetches 5; only dealers check the grade.
  Fresh Pmeh is "still green". A no costs 5 hearts; under -10 they tell the guard (a DEALING charge).
  Guards and rulers always do. Tuning: `Underworld.PITCH_*`.
- **Whispers**: in a den's town, until you've found it, a crook (in a known hotbed, any grown townsman) with no guard
  within 12 may whisper the way: compass direction and paces to the hatch. Stepping into the room finds it.
- **Crimes** (`Theft.Crime`): DEALING (a contraband trade outside a den, seen by anyone of the realm but the buyer and
  other crooks) and CONTRABAND (Pmeh or Shash in hand in sight of a guard within 10). Both medium: hunted and charged.
- `Busts`: a charged player who's downed with a guard of that realm within 6, or yields (sneak, empty hand, within 3 of
  a guard, 2 s), is sentenced on the spot by the guard: contraband confiscated, the hunt called off, then by score
  (15 per past offence there, the ruler's temper lean from `Arrests`, 0-30 luck, -15 for yielding, +15 for an escape):
  under 40 a **flogging** (5 strokes of 2, never below 4 health, then Slowness and Weakness 3 min), 40-79 **the cells**
  (a sealed stone pit 12 under the seat, 5 min + 2 min per past offence, death doesn't get you out, leaving it is an
  escape: outlaw there and charged again), 80+ **beheaded** (killed, headless). Cells and executions go in the Tidings.
- `/underworld den` (ops) gives the realm you stand in a den now; `/underworld heat <n>` sets its heat.
- Stage 2 (not yet): guards raiding a den that runs too hot, smuggling runs between hotbeds, the trade spreading.
- Tuning: the constants at the top of `Underworld`, `Speakeasy` and `Busts`.

## 0.7.212: Restoration, the healing school, and mana with attunement (pack 0.16.34)
Andy (2026-10-25/26), from the design doc: "a restoration arcana that focuses on healing magic. a sorcerer specializing
in this would heal with j and cast healing magic with sneak", and "some kind of attunement system, adding mana".
- **Mana and attunement** (`Mana`): Mages and Sorcerers are attuned when they take the class; anyone else by coming
  within 3 blocks of a budding amethyst. Attuning overloads you: mana to 150% of its most, the screen washed blue
  (`client/ManaClient`, fading over 10 s), the extra bleeding off at 3 a second. Most mana 100 for a caster, 50
  otherwise; back 2 or 1 a second. A seventh attribute, **Arcana**, shows on the K screen's Attributes tab once you're
  attuned (+10/+5 most mana, +0.15 a second per point), with your mana in its detail. Staff casts and the Sorcerer's
  keys cost mana (`Mana.COST`); reading a scroll still costs nothing. Ops: `/kingdom mana attune [player]|fill|clear`.
- **Seen by the attuned only:** a faint blue glow round each attuned player (Powers' ki glow look, our own copy of
  its texture), brighter the fuller their mana, flaring while overloaded; an imbued blade's idle trail; Lifebloom's
  threads.
- **Spells for everyone attuned:** an attuned player who isn't a Mage or Sorcerer learns a scroll's spell by reading
  it and casts it from a staff at scroll strength (Mage 1.75x), with 1.5x the cooldowns and no Echo.
- **The Restoration Sorcerer** (`Restoration`, a seventh element; the blade never takes a charge): J **Mend** (8
  blocks, 6 health over 3 s worst part first, closes a cut, 8 s; sears the undead 6); sneak+J **Renewal** (6-block
  ring, 4 health each and fever eased, 20 s, 2 hunger; undead burnt 4 and slowed), or on a downed friend within 4
  **Rekindle** (they rise 3 s later at 30% health, 90 s, 4 hunger). **Lifebloom**: each blow on a foe heals the most
  hurt friend within 12 by 1. Poison and withering hurt them half as much.
- **Spells** (scrolls, staffs; Mage first spell can now be Mending): **Mending** (6 health and a cut, 6 s on a staff),
  **Sanctuary** (a 4-block circle for 15 s: friends heal 1 every 2 s, undead thrown out; 60 s), **Restoration** (bones
  set, festering cured, cuts closed, up if downed, healed whole; 6 hunger; 5 min). Sorcerers can't read Sanctuary or
  Restoration. Court wizards now drop Mending and Sanctuary too; Restoration is 8% in stronghold libraries, ancient
  cities and mansions.
- **Healers:** a court wizard (sneak and use) also mends your wounds for 3 emeralds (free and your bones set at 100+
  with his realm), sells Mending for 4, and at 400+ a Restoration for 24 once a week. In a fight he brings his side's
  downed round before mending anyone, and every mending shows a gold ring. A village **cleric** (sneak and use,
  empty-handed or with emeralds) sells Mending and mends you; near players, clerics bring downed villagers round
  (once a minute) and mend the hurt below 80% (every 10 s).
- **Mercy:** healing a villager or one of the folk warms them by a heart (two for a rekindling) and they remember it
  (`Recollections.Kind.MENDED`, once in 5 minutes); bringing a downed monster or barbarian round is mercy, and devout
  or kindhearted onlookers within 16 warm to you and remember it.
- The **healer's staff** (wizard's staff + golden apple, shapeless): casts exactly as the wizard's staff.
- Healing goes through Powers' new `core/wounds/Mending` (reflection, `PowersCompat.mend/closeCut/easeFever/restore/
  rekindle`); without Powers it heals hearts only.
- Tuning: constants at the top of `Mana` and `Restoration`, `Spellcraft.MAGE_COOLDOWN`/`ATTUNED_POWER`.

## 0.7.214: heritage variants, three looks to every heritage (pack 0.16.36)
Andy: "stoneborn should all have a greyer hue ... i want heritage variants added. like Emberborn's avatars but more
ubiquitous. Extremely hardy obsidian skinned stoneborn, or magma cracked stone ember hybrids. Sylvans should have a
thorny rose inspired variant among another, and give each heritage 2 additional variants".
- `HeritageVariants`: 30 variants (two per heritage), each with a skin tint, its own marks, eyes and particle (client
  `VariantLooks`, textures `textures/entity/heritage/variant/` from `tools/gen_heritage_looks.py`) and one small trait
  (attribute modifiers; Thornrose pricks melee attackers for 1). Kept in the synced `heritage_variant` attachment
  (`base` for the first look) and in an MCA villager's saved data.
- Stoneborn skin is greyed (`STONE_GREY`, 60%) through MCA's skin layer (`McaVerdantSkinMixin`), which draws players too.
- Villagers roll a variant at birth (first look 50, each variant 25), x3 on its land (Obsidian-hide deep, Magmavein
  hot/Nether, Reefcrowned warm seas, Thornrose flower meadows, Blossomkin cherry groves...), x4 within 800 blocks of a
  monolith of its element (Skymarked, Blossomkin, Galeborn: Highfield; Lodeborn: Magnetic; Rimeglass, Aurora,
  Rimewyrm: Arctic; Glowcap: Irradiated). Children take a same-heritage parent's variant half the time; hybrids
  (Magmavein: Ember parent, Abyssal: Deepborn, Mosshide: Sylvan, Ashen: Frostborn, Twilight: Nightborn, Geode: Stoneborn,
  Rimewyrm: Frostborn, Umbral: Endborn) come 60% of the time with the other blood's parent.
- Players pick a look on the heritage screen (the Look button; the Choose packet carries it). Players who already had a
  heritage are asked once in chat on joining (`/kingdom blood <variant>`, only while unset). Lineage swaps keep each
  body's own variant.
- Preview: project files `art-overhaul/heritage-variants/` (variants-1..3.png, variant_preview.py). Not seen in game.

## 0.7.215: the hermits love alchemy (pack 0.17.0)
- Andy: the floating islands' hermit wizards are eager to talk about Jaymo's Powers' alchemy. `HermitAlchemy`: "Ask
  about alchemy" in MCA's menu (hermits only) gets excited words and a hint at the first thing on the alchemy path the
  player hasn't learned (lens and Codex first, through crucible, flux, infusion, gear); a worried word first if they're
  badly corrupted or standing on blighted land. Players who've started get an excited greeting and alchemy chat.
- Reads Powers' alchemy by reflection (`PowersCompat.alchemy`, `PowersCompat.flux`); without Powers it tells how to begin.
## 0.7.216: the life story, a newcomer's character told as their past (pack 0.17.1)
Andy: "take inspiration from Bannerlord's intro asking about your life into adulthood and use the answers to craft your
character's heritage, spawn-scenario, power affinity and class". Bannerlord is all rights reserved: the idea only, every
question, answer and line here is our own. Design doc: https://claude.ai/artifact/5vHNJoQbkJbP7Bst1RsHH9
- `LifeStory`: six questions (`Question`). Born where = heritage (the 11 pickable ones); raised by = +1 attribute (a
  foundling a free point; nobles name a realm for its goodwill; merchants start with emeralds); as a child = +1
  attribute, a lean to a heritage look and a power; growing up = class; "then one day..." = Jaymo's Powers' affinity
  (only asked with Powers there; "fate" gives the childhood's power half the time); what sets you on the road = the
  start (`Start`: a realm's capital, banished by a named realm, a cave with a spider's bite, a beached wreck, sworn to a
  named realm at its keep, a village of your homeland's kind, a monolith, the wild, or fate). A start that echoes the
  power (cave/Web, wreck/Darktide, monolith/Wind) answers its calling a day sooner (`PowersCompat.call`).
- Saved in the synced `life_story` attachment (an answer key per question); its points go through `Stats.fromStory`
  (parchment pips on the stats screen). Small kit per start (bread; torches underground; coins in a capital).
- Client `LifeStoryScreen`: a question a page, the answer's tale and what it gives on the left, a realm page after an
  answer that names one, and the story read back with the heritage's look and a Mage's spell / Sorcerer's element.
- Replaces the heritage and class screens for newcomers only (`Classes.next`); they still open for a Writ of Rebirth
  and for anyone part-made before. MCA's "where do you start" page is skipped for newcomers (`McaDestinySkipMixin`).
- `/kingdom story reset <players>` (ops) unmakes heritage, class and story and asks again.
- Andy's doc edits: new wording for six answers and their tales; a seventh start, "Unarmed" (with Powers only: you
  wake in the wild missing one random arm or leg, healed over, through Powers' new `Dismember.bornWithout`; it grows
  back the usual ways); the cave start wakes you at 4 health with Poison, Slowness and Weakness; the village start
  rolls a random job (butcher, woodcutter, builder, farmhand, miner, fisher, smith's hand) with its tools, kept as a 7th
  story entry. Sworn, Banished, City, Caving and Village starts come with the class's gear, armour worn (`GEARED`);
  Sworn adds full iron, a sword and a shield; Caving a pickaxe. The story page has heritage and class buttons to change
  either after the result (the heritage must be open to them; checked on the server).
- Tuning: constants at the top of `LifeStory`. Not seen in game.
## 0.7.218: gifted villagers (pack 0.17.3)
- Why a Stormborn purist king could exist: villager births still rolled Stormborn (from 0.7.106, when it could be picked)
  after 0.7.140 made it earned. Now no one is born Stormborn but a Stormborn parent's child; a gifted Wind villager whose
  power the storm takes (corrupted) wakes to it (`GiftedFolk.step`). Stormborn already in a world keep their blood: MCA's
  screen reads "Of the storm-taken line".
- `GiftedFolk`: every MCA villager is weighed once when they come of age (or, in an older world, the first time they're
  stepped): 1 in 80 awaken (Andy's number), a gifted parent's child three times as often, leaning to the parent's power
  (x6) and their blood's (x4: Frostborn Cryo, Emberborn Smoke, Tideborn/Deepborn Darktide, Stoneborn Gravity, Sylvan and
  Verdant Web, Warrior-born Z Fighter, Stormborn Wind, Heartlander/Earthling Arsenal). Callings: a Breeze killed (Wind),
  a cave spider's bite (Web), a long fall (Gravity), freezing (Cryo), burning (Smoke), monsters killed bare-handed
  (Z Fighter) or with a weapon (Arsenal). Temperament sets their style (hotheaded/brave/proud/spiteful bold;
  timid/gloomy/suspicious/kindhearted wary).
- Gifted townsfolk are crowned 60% of the time when there's one among the heirs (`McaBridge.heir`); a gifted ruler
  unleashes their ultimate when anyone of the realm is hurt within 16 blocks of the throne while they're there.
- MCA screen: "Gifted: <power>" once you know their name. Powers holds the gift and its moves (`PowersCompat.give` etc.).

## 0.7.219: heritage marks move with the character (pack 0.17.4)
- Andy (2026-10-26): on the server the new heritages' character stood still while the drawn heritage marks moved
  (jungxvv). A player MCA draws shows MCA's skin body, posed once from the animated model and then by Touch'n Motion;
  the marks were drawn on the hidden animated model, posed a second time on their own, and the two came apart.
- `HeritageLooks` now draws a player's marks, glow, eyes and Tideborn fins on MCA's skin body (`McaLookModels`
  `playerMarks`/`playerGlow`), the way villagers' looks already are: same pose as the skin, clothes and hair, just
  over the skin and under the clothes. Without MCA, the animated model as before. Compile-checked, not seen in game.
## 0.7.220: player jobs (`Careers`, pack 0.17.5)
Design and Andy's answers: the "Player jobs: design" doc. Wages from the town's storehouse, as many jobs at once as you
can keep up with, Hunter/Courier/Watch added, stronger perks, a Master's apprentice and stall, the title over your head
once introduced and in the tab list and character screen.
- Ten jobs (Butcher, Woodcutter, Builder, Farmhand, Miner, Fisher, Smith's hand, Hunter, Courier, Watch), each held for
  one settlement. Taken by "Talk about work" in MCA's menu (any grown villager of a settlement), by pulling a Help
  wanted notice off a bounty board (`TownNotices`, a job the town is short of), or from the life story's village start
  (leans to who raised you; taken up at the first town you reach). Tools the first time only.
- Orders each morning (2 new, at most 5 waiting), from what the storehouse has least of (a builder's from what the
  next building is short of): goods handed over in the same talk, or work in town (fell, place, harvest, ore, smelt,
  slay, a night's watch, a parcel carried to another town). Paid at dusk from the storehouse's emeralds (2/3/5 an order
  by rank, 1 less per other job held, at least 1 even when it's empty); wages wait while the storehouse is unloaded.
- Ranks: Journeyman at 100 experience, Master at 350 (10 an order, 3 reputation an order). Perks in the lang
  (`job.jaymoskingdoms.<job>.perk.1|2`); hooks in Trees (chops), BlockItemMixin (stock), LivingEntityHurtMixin
  (beasts), RoadPace (couriers), guard targeting (a Master Courier's free pass), and `mixin/jobs` (anvil, fishing hook,
  furnace result slot).
- Masters: take the villager you're talking to as an apprentice (16 of the trade's goods a day into the storehouse, 2
  emeralds' cut); open a stall (the next chest or barrel you open in town) that sells up to 12 emeralds' worth a day of
  mastered goods; a Master Builder picks the next building (`Construction`); a Master of the Watch calls 3 guards to
  follow (MCA's follow).
- Fired (and barred 3 days) for hurting the employer's people, being wanted for theft there, or reputation below 0.
- Shown: `job_titles` payload to everyone; under the nametag (`IntroNameTagMixin`) and after the tab-list name
  (`IntroTabListMixin`) once introduced; a Jobs tab on the K screen; orders in the journal; `/job`.
- Tuning: constants at the top of `Careers`. Not seen in game.

## 0.7.221: non-human heritages looked down on (pack 0.17.6)
Andy (2026-10-26): "The more non-human the heritage the less well it is regarded amongst most. and everyone knows it.
the tide born, stone, warrior and sylvan fall into this".
- **How far from human** (`Prejudice.degree`, in Andy's order): Tideborn 4, Stoneborn 3, Warrior-born 2, Sylvan 1; every
  other heritage passes for human. The heritage card says it ("Shunned: ...").
- **Who minds** (`Prejudice.minds`): most townsfolk. Not those of the same blood, not anyone non-human themselves, never
  the kindhearted, cheerful or devout; always the proud, suspicious and spiteful; of the rest 55% of MCA's warm
  personalities, 25% of plain ones, 8% of sour ones and no crabby one don't mind (fixed per villager). A purist ruler's
  own people always mind, 1.5x as hard. A full mask hides your blood.
- **What it does**: traders who mind charge 6% more per degree (Tideborn 24%, 36% for a purist's); chats, jokes and hugs
  win 15% fewer MCA hearts per degree (at least a fifth kept); good name with a realm is won 8% slower and lost 8% faster
  per degree (x1.5 in a purist realm, kin excepted); guards halt you sooner (Tideborn every day instead of every three)
  and search and toll you more; villagers start cooler (8 regard per degree) to a neighbour of such blood; MCA's chat AI
  is told what they think of your blood.
- **Talk**: the nearest townsperson mutters about your blood now and then (worse lines from degree 3), children point,
  and they edge away from a Tideborn or Stoneborn standing close. Ones who don't mind have a kind word, and one of your
  blood a knowing one. In a purist realm the Purists' own jeers take the place of the mutters.
- **Havens and the rivalry** (`Havens`): Andy: "you will often see villages of solely non humans because of this fact, as
  the ones who lived among the humans got tired of the ridicule. raising a sort of rivalry, between human and nonhumans
  across the entire world." A quarter of new villages and a tenth of new realms (never a purist's or a camp) are havens:
  everyone born there and all its folk are non-human, of the one blood its land suits (Tideborn by water, Stoneborn in the
  hills, Sylvan in the woods, else any of the four) or, 35% of the time, all four mixed. Drawn once per settlement and kept;
  settlements already in the world when 0.7.221 first starts stay as they are; folk first loading within two days of a
  haven being drawn take its blood. Non-humans mind humans back: a little anywhere (degree 1), hard in a haven (degree 3):
  dearer prices, fewer hearts, slower good name with a haven realm, mutters ("We left your kind behind for a reason"),
  and the chat AI is told. A haven welcomes any non-human. Gossip: a haven's people tell why their folk left; anyone now
  and then talks of the rivalry from their side.
- **Villagers who up and leave** (`Emigrants`): Andy: "allow villagers to up and leave on their own volition, packing
  their things and carts to depart for another civilization. maybe that explains the settlements you can find of people
  living on their own away from any kingdom, they should tell you as much when you speak with them." Each grown MCA
  villager is weighed about 20 times a day. A day's chance: 0.3% restless, +0.4% per degree their neighbours mind their
  blood (to 6%), +3% not of a purist king's blood, +1% under a tyrant, +1.5% at odds with 3+ neighbours, +1% MCA mood -8
  or worse; the biggest is their reason. They go with their partner and children; never a player's family (MCA's tree,
  online or not), a ruler, a lineage elder or one following someone; settled ones stay at least 10 days. No cap on how many
  go (Andy: "If they lose more than that then it is what it is, the kingdom will either improve it's qol for villagers or
  it will fall to ruin with no peasants"): a realm once peopled whose survey (with its seat entity-ticking) counts no
  villagers for 3 days, over at least 3 counts, falls to ruin like a slain ruler's (folk scattered, announced). Where: the ridiculed to a haven that takes
  their blood, a purist's outsider to a realm of their own blood; else half the time a settlement away from any kingdom
  (no realm within 160 blocks, no overlord), else any village or realm 200-1500 blocks off. Seen, they say goodbye and
  pack for 30 s (a draft horse and cart where a realm is near, else a chested donkey, led on a rope by the head of the
  house); unseen they go at once. The road works like the Elders' (a record while unseen, walked for real near players).
  Arrived, the cart is unpacked, MCA's `leaveVillage` lets them settle there, and they remember where from and why: in a
  chat, 35% of the time they tell you. Folk in a settlement away from any kingdom with no story of their own tell (25%)
  of their family leaving the nearest realm long ago.
- **Rulers answer the leaving** (`Reforms`): Andy's "please" to rulers reacting. Each household leaving a ruled realm
  adds 1 pressure (2 for a big household), ebbing 0.35 a day. At 2, at most every two days, the ruler answers by temper:
  reforms 95% noble, 85% reveler, 50% warlord, 35% purist, 30% paranoid, 20% tyrant; else digs in (news, and stubborn
  gossip). Each reform step (up to 3): taxes -20%, tyrant/purist/unhappy pushes to leave -25%, a purist's outsider
  markup -25%, guards' toll and search weights -1, MCA mood +4 for villagers about the seat, news via Tidings, gossip.
  Ten days with nobody leaving and he slips back a step.
  Andy: "More". Reformed realms draw settlers (a realm counts once more per step as somewhere emigrants go). When the
  ridiculed were most of those who left, the reform comes with an edict of tolerance: his townsfolk mind a blood 25% less
  a step (`Prejudice.minds`). Digging in has teeth: a tyrant's, paranoid king's or warlord's guards stop 40% of households
  at the gate (a guard's halt, MCA mood -3, no new try for a day), and a purist who dug in drives out those not of his
  blood (+12% a day, reason "expelled", to a realm of their own blood or anywhere).
- Tuning: the constants at the top of `Prejudice`, `Havens`, `Emigrants` and `Reforms`.

## 0.7.223: five more life-story starts (pack 0.17.8)
Andy: "Brainstorm 5 more starts." then "Add them all". Each answers a power's calling that had no start of its own
(lost in time instead wakes Chrono outright).
- **Fell from the sky** (Gravity): a crater dug in natural ground far from spawn; you drift down into it on Slow Falling,
  with Aether ambrosium shards (amethyst without the Aether).
- **Lost in time** (Powers only): Powers' `Future.strand` puts you in the future's ruined city (Andy: "Always wait for the future":
  if it hasn't been downloaded yet, that starts the download and you wait, held still and unhurt, until it's there). Andy: "Lost in time starts you with chrono level 1": Chrono
  awakens at once with its first ability (`PowersCompat.awaken` → Powers' `Powers.awakenAs`), whatever question 5 said.
- **Your village burned** (Smoke): a small burnt-out house built round you (charred walls, ash floor, a cold hearth) and
  three random things you saved.
- **Snowed in on a mountain pass** (Cryo): the nearest frozen peaks, jagged peaks or snowy slopes; half-frozen and slowed,
  a lantern and a leather coat (worn).
- **Escaped from barbarian captors** (Z Fighter): by the nearest barbarian camp with nothing at all; its raiders near you
  come after you for a day (`LifeStory.hunt`, unless the camp spares you).
- Fate can pick the sky fall, the burned village and the pass; lost in time and the escape only when chosen.
- Tuning: the new constants at the top of `LifeStory`. Not seen in game.

## 0.7.224: creeds, tension and great wars (pack 0.17.9)
Andy (2026-10-26): "I like the idea of larger scale wars between wealthy kingdoms, differing ideologies cause conflict
making war inevitable. And the more money the more war." Ideas from Warring Nations (All Rights Reserved: war goals,
coalitions, rebellions, war betting), remade; nothing copied. All in `GreatWars` (saved as the `great_wars` attachment),
hooked into `Wars`, `WarParties`, `WarAlert`, `WarCommands`, `/kingdom info`.
- **Creeds:** every realm holds to the Crown, Chivalry, Conquest, the Faith, Coin or the Old Blood. A ruled realm takes its
  ruler's temper's creed 65% of the time (tyrant Crown, noble Chivalry, warlord Conquest, paranoid Faith, reveler Coin,
  purist Blood), else any, and keeps it through heirs; vassals hold their overlord's. Players' realms get one at random;
  their lords may pick another at peace (`/kingdom war creed <creed>`). Creeds clash 0-3 (`FRICTION`); Blood against Blood
  by whose blood; a reformed realm's edict of tolerance eases Blood's clash by one.
- **Tension** (`/kingdom war tensions`): once a day, for free kingdoms within 1600 blocks: +2.5 a point of friction, times
  1 + their worth together / 500 (at most x4); +2 for seats within 450, +1 already rivals, +2 two rich (250+) realms where
  one is Coin; -3 kindred creeds; allies -5; a truce cools it. Rivals at 35; at 80 war is inevitable: the richer, readier
  NPC realm declares whatever the armies say (players' realms are never made to declare). After a declaration or peace it
  drops to 30.
- **More money, more war:** a king's daily war chance is x(1 + worth/500, at most x4), and he counts the sellswords his
  treasury could hire (30% of it at 10 a head, at most 16) in his army when weighing a weaker rival.
- **Great wars:** a war between two realms each worth 260 (or 750 together). Free NPC kingdoms within 2000 blocks whose
  creed, alliance or tension leans clearly one way pledge to that side (3 a side at most) and fight as its members (with
  their vassals: war relations, parties, banners, alerts). Each principal hires sellswords (30% of its treasury, 10 each,
  at most 16): they count in its army, and its parties (up to 16, sent twice as often) take 4 each. Score to win x1.5,
  length x2, unseen skirmishes twice the blows. Daily upkeep: 4 + 3% of the treasury + 1 a sellsword (allies 1%); below
  15 emeralds the sellswords leave and the realm sues for peace.
- **War goals:** an attacker who wins makes its creed's peace (`GOALS`): Crown vassalises from a lead of 60 (not 90);
  Chivalry tributes from 10 but never takes land and keeps a double truce; Conquest takes land from 25 and twice the
  chunks; Faith converts the loser; Coin tributes from 5 and takes half the treasury; Blood takes land from 30 and 6
  chunks. A great war adds 10% to tribute and half again to land. A vassal always takes its overlord's creed. Winning
  allies each take 10% of the loser's purse; losing allies pay 10% of theirs to the winner.
- **Rebellions:** in a great war, each day a fiefdom (with a lord whose temper's creed clashes 2+ with its overlord's)
  whose overlord is behind by 20 rises 15% of the time: its lord is crowned, it takes its own creed and goes over to the
  other side.
- **Bookies** (`Bookies`): Andy: "War wagers can only be done with bookie npcs in speakeasies." Every den keeps a bookie
  (hired like the fence, replaced a day after he's gone). Right-click him for his book: each war from its warm-up until a
  day into the fighting, both sides at his odds (each side's share of the armies, sellswords included, squeezed into
  15-85%, the house keeping 10%; 1.2x to 5x), and the pit's next bout while its card is called. Hold emeralds and click a
  line (`/bookie war|bout`, within 6 blocks of a bookie): up to 128 a war (one side only), 32 a bout. Winnings come into
  your inventory (when you're next online); a white peace refunds war stakes. There's no other way to bet on a war.
- **Fight pit** (`FightPit`, `Speakeasy.digPit`, attachment `fight_pits`): Andy: "You can also bet on fighters in the pit
  ... Maybe another backroom dug into the speakeasy or in a makeshift ring if the room allows." Every den digs a backroom
  through its south, west or north wall (whichever has earth over it and nothing in the way): 9x9 inside, a coarse-dirt
  ring roped with spruce fence (5x5 inside, a gate toward the door), plank aisle for the crowd, benches, lanterns. With no
  room for that, a 5x5 makeshift ring room with posts at its corners. While a player is within 24 blocks, a bout every
  minute: the bookie calls the card (two MCA fighters, each with power 1-3, guard 0-35%, wind 12-24, and odds the house
  sets by simulating the bout 400 times), bets for 30 s, then the bell. They trade bare-knuckle blows (swings, crits,
  knockback, cheers; no real damage) until one's wind is gone and he yields. Winning bets pay at the odds; the winner
  stays on as champion (+1 wind a win) and retires after 5 wins. Each bout warms the city by 1.
- **Fighting in the pit yourself:** Andy: "Sure add that". The bookie's book offers a place on the card (3 emeralds held,
  `/bookie fight`): you take the newcomer's corner (his backers get their stakes back, the odds are redone) or a card is
  called for you against the champion. At the bell you're put in your corner. Punch with an empty hand (a full swing lands
  2-3, a half-charged one less; the fighter's guard turns some); sneak to cover up (turns half his blows); your wind (20)
  shows over your hotbar. Anything in your hand is a foul, stepping out of the ring or leaving throws it. Win: a purse of 8 + 4 per win the beaten had, and he remembers you beat him (`Recollections`); lose: he
  remembers he beat you. You can bet on yourself, not against.
- **Real blows, held gear, the attendant:** Andy: "You should take real damage, but get patched up ringside afterward by
  an attendee. They offer you rags instead of bandages most of the time, so you may just want to heal yourself. But as soon
  as you enter the pit, your armor and gear is held." At the bell everything you carry (armour and offhand too) is held
  (`pit_held` attachment, kept through death) and handed back when the bout's done; a bout cut short by a logout, death or
  restart gives it back the next time you're about. His blows now hurt you for real (his 1-4 in health, no armour to soak
  it) while yours count 2-3 against his wind, so it's about even. Your wind is your health above 4; at 4 you're done, and
  no blow takes you lower. Afterwards the pit's attendant (an MCA villager ringside, or by the bookie when the ring fills
  the room; replaced a day after he's gone) offers to patch you up if you're hurt: 75% a rag, else a linen bandage.
  Andy: "if you accept he does it for you, bandaging wherever you may be hurt, or splinting." Click [Let him] or [No
  thanks] (`/bookie patch yes|no`, open a minute): yes and he dresses every hurt part and splints every break through
  Powers' wounds (`Treatment.tendAll`, new in Powers 0.17.9); without Powers he's worth +1.5 or +3 health.
- **Fighters' stories:** Andy: "make the villager winners/champions bring that up in conversation." Every fighter keeps a
  record on himself (`pit_record` attachment: measure, wins, losses, champion/retired, which city's pit). The beaten and the
  retired go back up to town to live (newcomers who never fought are just let go). In an MCA chat, one with a win brags of
  it 40% of the time (champion, retired champion or plain winner lines), and MCA's chat AI is told his record.
- Ops: `/kingdom war tension <a> <b> <0-100>` (looked at once a day).
- Tuning: the constants at the top of `GreatWars`, `Bookies` and `FightPit`. Compiled only, not seen in game.

## 0.7.225: players on the throne (pack 0.17.10)
Andy (2026-10-26) asked how a player becomes king (no way did), then "Build all your ideas in please". All in `Crowns`
(saved as the `crowns` attachment: faction id to the player who wears its crown), hooked into `KingdomEvents.rulerDied`,
the succession tick, `Banners` (a seat taken), `Theft`, `Festivals`, `/kingdom leave` and `/kingdom info`.
- **Sovereign and steward:** the realm's leader stays a villager on the throne, ruling in the player's name as steward,
  so the realm runs itself while they're away. A steward who dies is replaced by the next day and the realm doesn't
  fall, go to war's judgement or a random fate. A sovereign is sworn to the realm at lord rank (treasury, war and peace,
  festivals, plots), nothing they do in it is a crime, and they wear one crown at most. `/kingdom crown` shows yours (or
  the roads to one), `/kingdom crown abdicate` gives it up (so does `/kingdom leave`), `/kingdom info` names them.
- **Usurping:** a lord of the realm who kills its ruler within 16 blocks of the throne takes the crown at once: their
  wanted status there is cleared, their good name brought up to welcome, and every realm allied with it thinks 200 worse
  of them. A player sovereign slain the same way by a lord of their own realm loses the crown to them.
- **Inheriting:** when a ruler dies (old age, an assassin, or slain outside a war's judgement and a usurper), a player
  married or betrothed to them (MCA) takes the crown, unless they did it or already wear another; it comes before the
  random fate of a slain ruler.
- **Conquest:** a lord who plants the banner on an enemy seat, or slays its ruler (villager or player) in a war, may claim
  its crown for a day (`/kingdom crown claim <name>`, a clickable line): the realm is freed from vassalage (a kingdom
  again), crowned theirs, and allied with their old realm, which they leave without the sting.
- **Founding:** `/kingdom crown found [name]` pays 256 emeralds into a new kingdom's treasury at your feet (in a village's
  own buildings if you stand in one, else a castle), at least 320 blocks from any realm and off any town's land.
- A player sovereign slain by the other side of a war ends the war as a ruler's death does.
- Tuning: the constants at the top of `Crowns`. Compiled only, not seen in game.

## 0.7.226: rarer Eurus and Aeolus (pack 0.17.11)
Andy (2026-10-26): "aeolus and eurus should be FAR rarer, and only even spawn after killing your first world boss".
- `Disguises`: Eurus now comes about once in 15 hours of play (1/900 a minute, was 2.5 hours) and Aeolus once in 40
  (1/2400, was 6 hours).
- Neither turns up at all until the world's first world boss, the first of the Aether's Giants, has fallen
  (`Monoliths.firstFallen`, the same flag that raises the world tier to Borderlands). It's world-wide: once anyone fells
  a Giant they can come to anyone. Ops' `/kingdom dragon` spawns and the dragons' eggs are unchanged.
## 0.7.227: gifted villagers part 2 (pack 0.17.12)
- `GiftedFolk`: one gifted in `MASTER_ODDS` (80) awakens a master; whether they hide it (`open`) by temperament (hotheaded,
  proud, spiteful, greedy 0.6; brave, cheerful 0.3; the rest 0.08; masters +0.25). Gifted from before are weighed once in
  `step` (`jaymoskingdoms_gift_ways`). `close` tells Powers who they hide from: kin and partners, players who are MCA
  family or 40+ hearts, villagers with 40+ regard (any villager or player without MCA).
- Resonance's calling: living through a Warden's sonic boom (0.5).
- `GiftTales` (Andy: "The mca screen should only show it once the player has seen, and asked about the powers they saw"):
  Powers tells us each player who sees a gift at work (`PowersCompat.giftWitnesses`, kept per player in `gifts_seen`);
  that player then gets "Ask about their power" in the MCA menu. The answer depends on hidden/open/master/corrupted
  (three lines each; a grudge refuses), and only then (`gifts_asked`, synced) does their MCA screen show the gift;
  masters read "Master of <power>".
- `PowersCompat.give(..., master, open)`, `giftWays`, `giftMaster`, `giftRevealed`, `giftCloseOnes` (older Powers: as before).

## 0.7.228: a whaling start, and no power lean line on the life story (pack 0.17.13)
Andy: "remove this text" / "from every entry": the "Leans to <power>, if fate decides your power" line is gone from the
life story's answers and story page (the childhood still leans the power when fate decides; it just isn't shown).
Andy: "another origin where you start with whalers, on the crew as a harpooner". New start **Signed on with the
whalers** (Darktide's calling): `Whalers.signOn` signs you on the whaler fitting out at the harbour nearest spawn (one
is fitted out for you if no realm's is in port, whatever its treasury or last voyage) and you wake on her deck with 8
harpoons, shears and oilskins; come home aboard her for the usual share. No realm with a harbour: the wild.

### also in 0.7.228: every life-story start can be had, and the cave start no longer kills
Andy: "spawning in the cave poisoned made me die instantly, and jungxvv didnt spawn in a rich city, instead he spawned
in the wilderness ... I need you to generate whatever is required for the player to choose any of the options".
- **Cave:** you wake at half health (`CAVE_HEALTH` 10, was 4). Every story landing now clears the fall carried over
  from where you stood and gives 5 seconds of full Resistance (`ARRIVAL_GRACE_TICKS`), against a fall or the cave's
  spiders.
- **`StoryGround`:** makes what a start needs when the world hasn't got it.
  - **A capital.** In a new world no realm exists until someone walks up to a village's bell. Now, when a newcomer
    joins (or the story screen is about to open) and there is no realm, the village nearest spawn is kept loaded
    (a ticket) and becomes a kingdom's capital (`KingdomEvents.foundIn`, else its citadel). If it isn't ready within
    90 s, a citadel kingdom is raised there instead. The story screen opens once it stands, so the city, sworn,
    banished and noble answers always have realms. Players waiting are kept safe and told.
  - **A barbarian camp** 200-360 blocks out for the captive start (`Camps.found`).
  - **A monolith** 160-320 blocks out for the monolith start (`Monoliths.raise`; `fit` and `SETTLEMENT_CLEARANCE` are
    public now). Fate can now pick the monolith start in any world.
  - **A harbour** for the whaling start (`Whalers.berth` says none is free). It is built by the nearest realm whose
    seat is within reach of the sea, with its land kept loaded until `Navies.build` succeeds. If no realm is on a
    coast, a citadel kingdom is raised on the shore nearest spawn, which then builds it. After 90 s with no harbour,
    you start in the wild.

## 0.7.229: the life story before MCA's creator, and typed names always saved (pack 0.17.16)
Andy: "can you choose your characters story BEFORE the MCA creator and name choosing? also it doesnt always save the
name players choose, i think it requires an extra enter prompt".
- **Story first.** On joining, a newcomer's life story opens at once (`Classes` JOIN no longer waits for MCA's
  creator). On their client, `McaDestinyWaitMixin` holds MCA's `DestinyManager.tick` until the story is told, and
  the creator opens as soon as it is. `McaDestinySkipMixin` skips the creator's "where do you start" page for anyone
  who has told a story, as it did for newcomers.
- While MCA's creator is still up, the player can't be hurt and nothing hunts them (`ALLOW_DAMAGE` and the shoo check
  include `McaCompat.destinyPending`). This matters because they already stand at their start, maybe in a cave.
  Finishing the creator gives the 5 s arrival grace again (`LifeStory.arrived`).
- **Names.** The creator's first page is `intro`: MCA sends its `general` tab there. `McaDestinyNameMixin` saved only
  on leaving `general`, so a typed name reached the server only if the player had also visited and left the general
  tab, which is the "extra step" Andy saw. Leaving `intro` or `general` now saves.

## 0.7.230: strangers' MCA page, introductions first (pack 0.17.17)
Andy: "introductions should be the first option available. before that none of this is visible, no options but gift
trade or introduce".
- `McaStrangerPageMixin` (on `AbstractDynamicScreen.setLayout`): MCA's main page for a villager you haven't been
  introduced to shows **Introduce yourself** in MCA's first row, then **Gift** (MCA's gift mode), then Trade if they
  trade. Every other MCA button is hidden: Talk, Interact, Inventory, Work and Family Tree. `McaButtonExAccessor`
  reads which button a widget is.
- `McaStrangerInfoMixin`: no name, trade, mood, personality, temperament or standing lines, and no icons (MCA's gift
  icon still shows while you hold out a gift). When you become known, the page is laid out again with everything.
- Introduce sends a new `main` answer, `Introductions.INTRODUCE`. If you share a tongue, it asks their name (as "Ask
  their name"); if not, it offers a handshake. The screen stays open.
## 0.7.231: death and blood magic (pack 0.17.19)
Andy (2026-10-26): "death magic, and blood magic, is there a way you can work that concept in?"; picked "two new Sorcerer
elements". Design doc: https://claude.ai/artifact/5M6smjEanKy32pyLVFEm9X. Defaults I picked for the open questions: a crime
only in a kingdom's land (and only if its people see), raised bodies crumble after 3 minutes, players' bodies never rise.
- `Necromancy` (Sorcerer element `death`): the blade blights every blow (`blighted`: `LivingEntityHealMixin` cancels
  `heal`; any Restoration heal lifts it, `PowersCompat.mend`/`restore`), a burst withers. Sneak and J: Reap a downed one
  (Powers' `Downed.execute`, 25 mana back), a death bolt at the living, or Raise the Fallen (a Kitchen body near where you
  look rises as your thrall, a monster as itself, people and horses as the undead; 3 at most, 3 minutes, 20 s cooldown).
  Withering halved. Spells: Blight, Whispers (a body tells its death message and how long ago; deaths kept in memory, so
  not across a restart), Death's Door (5 minutes; fall and you're straight back up, -4 max health until you next sleep).
- `Hemomancy` (Sorcerer element `blood`): paid in health (`COST`), from a drunk pool first (bleeding things near feed
  it, 20 max); never below 2 health; under half health a cast opens a cut on you. The blade cuts every blow, a burst
  hemorrhages. Sneak and J: Blood Grip a bleeding foe (dragged in; a mob drops its weapon, a player weakened), a bolt that
  cuts, or Hemorrhage at nothing (every cut near bursts 4 bleed ticks). Bleeding halved. Spells: Hemorrhage, Blood Grip,
  Frenzy (a cut for 30 s of Strength, blows heal 1). A staff casts them for blood. Vampires pay 3/4, cast x1.5, fever +3.
- `ForbiddenArts`: every death cast adds 1 lingering corruption (raising +2), every blood cast 2 (`PowersCompat.corrupt`);
  `Theft.forbidden` makes it a heavy crime (`NECROMANCY`, `BLOOD_MAGIC`) in any kingdom whose people see, even one that
  believes in magic. The old Raising and Draining scrolls count as death magic now. Court wizards and clerics refuse a
  known practitioner (`Restoration.services`).
- `Sorcery.BLOOD_WITCH` (43): one per barbarian camp from day 4 (`/kingdom caster blood_witch`): bolts that cut, grips,
  hemorrhages, drinks the bleeding to heal; drops a blood scroll. Necromancers now also drop Blight, Whispers and Death's
  Door. Six new scroll textures (recoloured from Draining) and a crimson blood witch skin.
## 0.7.232: fallen trunks to push, roll and build on; leaves snap off (pack 0.17.20)
Andy asked whether Tree Physics (CurseForge, by Farcr) could replace our trees. It can't ship: it's All Rights
Reserved, 1.21.1 only, and runs on the Sable physics library (also 1.21.1 only). Our felling, growing, seeding,
woodcutters, tornadoes and Giants all stay. Andy: "add all of em" to three of its ideas, done our own way:
- **Leaves snap off.** A falling tree's wood breaks through other trees' leaves in its way, up to `SWEEP_MOST` (12) a
  tick, and they drop what they would. When it lands on its side, each of its own leaves may snap off, dropping
  saplings, sticks and apples. The chance is up to `LEAF_SNAP` (50%) and grows with how fast that leaf hit the ground,
  reaching the full chance at `LEAF_SNAP_FULL` (1.5 blocks a tick). So the crown of a tall tree loses more than a
  short one (`FallingTree.sweep`, `snaps`).
- **Push and roll fallen trunks** (`FallenTrunks`). A tree that comes to rest on its side is kept as a fallen trunk
  (attachment `fallen_trunks` on its world, the newest `KEPT_MOST` 512). Right-click one of its logs with an empty
  hand and it moves a block away from you, for 0.05 hunger per log, once every `PUSH_TICKS` (8). Pushed along its
  length it slides. Pushed across, it rolls, and if that takes it over an edge it keeps rolling downhill a block every
  `ROLL_TICKS` (4), up to `ROLL_MOST` (16), until it reaches level ground. Rolling or dropping onto something living
  hits it for `ROLL_HIT` (4) and shoves it. Leaves in its way break; anything solid stops it. More than `PUSH_MOST`
  (20) logs is too heavy for one person. Breaking logs out of a trunk drops them from it, and a split trunk moves as
  two pieces.
- **Built on, it stays.** Set any block on top of a fallen trunk (not its own logs or leaves) and the next push finds
  it pinned. It is forgotten as a trunk and stays as plain logs for good. Trunks already stayed as real blocks, so
  nothing despawns.
## 0.7.233: harpoon lines hold aerwhales (pack 0.17.21)
Powers' aerwhales are hunted like great whales now. A Kingdoms harpoon already lodged in one (it's 5 wide, past
`BIG`); now `Harpoon.tether` also pays line out to a running aerwhale and holds it back by `WHALE_SHARE`, like a great
whale, and stops paying out once it wears Powers' `jaymospowers_spent_whale` tag. Powers stays optional: the aerwhale
is found by its entity id (`skyWhale`). Merged in #551 claiming 0.7.232, which #550 had already used, so renumbered.

## 0.7.234: locked loot chests, the Pin and Rotary lockpicking games (pack 0.17.23)
Andy asked for Locks Unofficial's minigames. That mod is All Rights Reserved and Forge 1.20.1 only, so both games are
our own, built from the idea alone (`Lockpicking`, `client/LockpickScreen`).
- **Read before filled.** The first time anyone tries to open or break a loot chest or barrel (vanilla's or Lootr's,
  Lootr's by reflection), its loot table is rolled with the chest's own seed (a vanilla chest with none is given one,
  so what's read is what it will hold) and weighed (`worth`): rarity (uncommon 3, rare 8, epic 16), enchantments (6),
  valuables by the piece (diamond 4, netherite ingot 30, ...), and 6 more for treasure tables (stronghold, end city,
  bastion, ancient city, mansion, buried treasure). Worth `LOCK_WORTH` (14) or more and it's locked, level 1 to 5 at
  14/24/38/60/90, Pin or Rotary at random. The result is kept on the chest (attachment `lock`); a double chest is
  locked if either half is. A Lootr chest someone already opened is left alone.
- **Locked.** Using it rattles (`CHEST_LOCKED`) and, with a **Lockpick** in your inventory (2 iron nuggets and a
  stick make 2), opens the game. Without one you're told what kind of lock it is. A locked chest can't be broken
  outside creative.
- **Pin lock.** 4 to 7 pins (3 + level) to set in a hidden binding order. A/D or the mouse picks a pin; hold W or the
  mouse button to push it. A loose pin springs up and drops; the binding pin pushes stiff, trembles, and ticks as it
  reaches its shear line. Let go there and it sets. Past it, the pin drops and the pick strains (0.35); from level 4 the
  last pin set drops too. Rattling a loose pin at the top strains a little.
- **Rotary lock.** 3 to 5 gates on a dial, turned to in alternating directions (right, then left, ...). A/D, the arrows
  or the wheel turn it, Shift slowly; the pick trembles and ticks faster near the gate. W, Space or a click sets it: in
  the gate (11 degrees less 1.5 a level, either way) it catches, else the pick strains (0.3). Turning back undoes the
  last gate.
- **Strain** eases off slowly. Full strain snaps the pick: the server takes one and the lock starts over. A picked lock
  stays open for good, opens the chest, and gives Stealth experience (2 + 2 a level).
- **Stealth boosts.** Practice: each Stealth level (first page) widens the sweet spots 2%. **Lockpicking Prowess**
  (3 ranks, level 5, Stealth's first page) takes **Slip Away**'s place: per rank, sweet spots 15% wider and 20% less
  strain; from rank 2 the binding pin glows and the dial's pick lights in the gate; at rank 3 the first slip of each try
  is forgiven. Ranks saved under Slip Away count for nothing now, so those points come back to spend. Ghost needs
  Quick Hands and Lockpicking Prowess.
- The lockpick icon is painted by `tools/gen_lockpick.py`.

## 0.7.235: the whaler start works
- **Signed on aboard, not in the wild.** A whaling story was fitted out at once when some realm already had a
  harbour, out where the sea round her mooring wasn't loaded and ticking, so she wouldn't float and the story fell back
  to the wild. Now every whaling start waits at spawn ("Awaiting a harbour...") while `StoryGround` keeps the harbour's land
  ticking: its harbour built if it has none, then its whaler fitted out (`Whalers.ready`) once the sea is ticking 32
  blocks round her mooring. A realm whose harbour can't be built, or whose whaler won't float, after 15 tries is passed
  over for the next nearest coastal one (a new kingdom raised on the shore if none is left); it gives up after 3
  minutes. Sign-on then takes the realm made ready, not just the harbour nearest spawn.

## 0.7.236: a loading screen while a story's start is made, and your heritage in MCA's creator (pack 0.17.25)
Andy: "can the player be held in a stasis menu or faux load screen at the beginning while everything loads?"
- `StoryGround.Hold` (server to client): the server checks every 5 ticks what each player is held for and sends it
  when it changes. A held player is waiting for one of:
  - the first capital to be raised (before the story screen opens, or a city start);
  - a whaler being fitted out;
  - Powers' future, for lost in time (`PowersCompat.waitingForFuture` reads `Future.WAITING`);
  - 3 s after any story landing while the land loads (`StoryGround.settle`, `SETTLE_TICKS`).
- Client `StoryHoldScreen`: an opaque screen that can't be shut. It shows "Your story is being written", what it's
  waiting for, and a sweeping bar. It comes back if anything else closes while the player is still held. MCA's creator
  waits behind it, since MCA opens only onto an empty screen. The overlay lines from `StoryGround.step` are gone.

Andy: "apply the character's heritage to the character creator please".
- `McaEditorHeritageMixin`: when MCA's editor is open on your own character (the start-up creator, now after the
  story, or the editor later), the figure you dress gets your heritage and variant attachments. It is drawn with your
  blood's marks, fins, eyes and look, as villagers of that heritage are (`VillagerLooks`).

Andy: "the sliders should only allow the color ranges that the heritage allows".
- `SkinTones.skinRange`: each heritage's melanin range (0 pale to 1 dark), as constants at the top of `SkinTones`. The
  ranges are my picks from each blood's land. Frostborn, Nightborn, Endborn and Deepborn are pale; Emberborn and
  Dawnborn are dark. Heartlander, Earthling and Verdant have the whole range (Verdant is green whatever its melanin).
- `McaEditorSkinMixin` (on `Genetics.setGene`, client): on your own figure in MCA's editor (`EditorFigure`), the skin
  picker's melanin is kept inside your heritage's range, so that is what gets saved. A figure that starts outside the
  range is brought into it.
- `McaVerdantSkinMixin` also finds the editor figure, which isn't in the world. The figure now shows your heritage's
  skin too: a Stoneborn's grey, a variant's tint, a Verdant's green.

Andy, asked whether villagers' skin should be limited too: "yes".
- `VillagerHeritage.born` brings an MCA villager's melanin into its heritage's range (`McaBridge.keepSkin`,
  `SkinTones.bornSkin`). One born outside the range lands a little way in from the nearer edge (up to
  `BORN_INSIDE`, a quarter of the range), so they don't all sit on the edge. The land still sets where in the range
  they fall. `born` also runs when a villager loads, so villagers already in the world are brought in once.
- `McaGeneticsMixin`'s climate roll keeps to the range when the villager's heritage is already known.

## 0.7.238: black, not the world, until MCA's creator (pack 0.17.27)
Andy: "this screen at the start should not be opaque. it should stay black until the character creator. i just dont
want the player seeing anything in world while they are spawning."
- The life story and the loading screen draw on solid black (`extractBackground`), not over the blurred world.
- Client `StoryCurtain`: a HUD layer drawn last that blacks out the world from the moment the story or a hold arrives
  until MCA's creator (`DestinyScreen`, matched by name) opens. It covers the gaps between screens while the server
  moves the newcomer. Without MCA, or when the creator doesn't come, it lifts `LIFT_AFTER_MS` (2.5 s) after nothing
  holds them. Returning players never see it. 0.7.237 is left for the mana spirits PR. Not seen in game.

## 0.7.242: ship crews are MCA villagers (pack 0.17.30)
Andy: "i need all the ship crew to integrate with MCA, it doesnt make sense i have fully kitted knights all silent and a
stoic brick for a captain. they need at the very least a personality."
- `Crewmen`: with MCA, every crewman `Corsairs.launch` puts aboard (a pirate's captain, gunners, deckhands and powder
  monkeys; a warship's sea captain and knights; a merchantman's and a whaler's hands) is an MCA villager. Each gets a
  name, face, personality, mood and temperament, and MCA's talk and chat AI. `McaBridge.crewman` builds them from a folk set up for the role,
  never added to the world. It copies the folk's blood, armour, weapons and toughness, and the powder monkey's size.
  Fighting men are MCA guards, and sailors and powder monkeys are fishermen. Each is enlisted to their side in their old role and
  tagged `jaymoskingdoms.crewman`. They stay off every roster and never turn up in a town's worker searches,
  guard calls or scatters.
- Aboard, their brain waits (`Workers.step` → `Crewmen.step`). `Corsairs.deckwork` still walks them along their
  `Ship.SEAT`, so PR #461's deck walking, gun manning and helm are unchanged. In place of the brain, they take foes as the folk
  did, plus whoever hurt them and boarders (a cove's prey, or players the realm counts hostile). Pirates keep their
  pistols and shouts (`Pirates.crewFight`). Others loose arrows if they hold a bow, and everyone swings at arm's
  length. They look at whoever is talking to them.
- Talk (`Crewmen.chatter`): while a player is near, one line every 15 to 35 s per ship. The line fits their side
  (8 lines each for pirates, navy, merchant, whaler and powder monkeys), comes from the captain (4 per side), or, one
  time in three, from their temperament. The captain welcomes each newcomer aboard.
- MCA's screen (`Standing`) shows "Gunner of the Resolute", their captain and what she's about. The captain's nametag
  reads "Captain" once you know his name. The chat AI is told their berth and ship (`Crewmen.prompt`).
- A cove's pirate captain as a villager keeps his boss bar, still leads the cove, and clears it when he dies
  (`KingdomEvents.workerDied` → `Camps.chiefSlain`). Slay quests still find him (`Quests.campOf`).
- Folk crews already at sea in a world are each replaced at their station by a villager of their blood
  (`Crewmen.convert`). The ship's captain, gunner and cove-leader records follow them.
- Home from a whaling voyage (`Crewmen.payOff`, from `Whalers.home`), the crew come ashore at the harbour with
  their lay in emeralds. Each decides whether to sign on again or leave the sea (Andy: "they should be able to decide to
  continue as a whaler or leave with their riches"). The base chance of leaving is 30%, plus 8% a whale (at most +30%).
  The greedy, timid and gloomy lean toward leaving, the dutiful, brave, cheerful and devout toward staying, and the
  captain leaves half as often. Those who stay wait about the harbour (MCA's screen: "Whaleman of X, in port") and board
  the realm's next whaler before anyone new is taken on (`Crewmen.waiting`). Those who leave are discharged and live as
  the town's villagers. Each says why, and the town hears how many signed on.
- Whaleboat hands stay crew in the boats. Set down ashore, a crewman lives as an ordinary villager.
  Without MCA the crews stay folk. Javac-checked against the 26.3 jars, not seen in game.

## 0.7.243: no lag spike before the life story (pack 0.17.31)
- **The first capital is raised near spawn.** A new world's first realm (raised so the story screen has realms to name)
  went to the nearest village anywhere within 1,600 blocks. In Andy's world that was 900 blocks off, and loading and
  making all that land at once froze the server for about a minute before the story opened. Now only a village within
  24 chunks (384 blocks) of spawn is taken (`CAPITAL_VILLAGE_SEARCH`), which also makes the search itself quick; with
  none that near, a citadel goes up by spawn at the first try, where it used to wait out the village's 90 seconds first.
- **Ships find their way out of a bay.** Andy: "the whaling ship got stuck trying to navigate". A captain only looked 18
  blocks ahead for open water, so a whaler (or any crewed ship) whose heading ran through a headland backed off, came
  about onto the same heading and ran back into the same bay, over and over. Now, once she has lain stuck, she looks
  along 16 headings for open water she can sail straight to (`Fleet.offing`), takes the one that comes out nearest her
  heading, and makes for it (at most 30 s) before taking her heading up again. Each try from the same bay looks farther
  out (48, 96, 144, then 160 blocks); the count starts over once she's 96 blocks clear (`Corsairs.DETOUR*`).
## 0.7.244: mana grows: attuned folk, power in the glow, amethyst, sickness, alchemy, the wand, meditation, ripples (pack 0.17.32)
Andy: "the mana flare on acquisition needs to dim to a dimmer minimum." Before, the glow came down from the overload's
flare only as far as mana's fullness, so a full caster (and the overload's spill) kept it at its brightest for good.
Now `ManaClient` eases the flare down to a resting glow set by power and fullness (below). Mana past full no longer
brightens it; the flare at its height is a little brighter
than before (`FLARE` 2.0) and eases away with the same curve as the blue on your screen, which still fades out fully.
Not seen in game.
Then Andy: "court wizards and clerics should have to be attuned as well. meaning other magic users can see their glow, and
know their power level from the intensity of the glow. mentioning it if asked. amethyst should be now crushable for mana.
add a type of mana sickness for punishing over zealous casters, can we link alchemy? using vis and flux slowly attuning or
corrupting you? corruption would be anti magic, reading the mana user's intentions ... Arcana increases that minimum ... A
mana infused wand ... mana can be regenerated using meditation from the survival tree. when casting a spell the aura
should ripple, indicating the caster's intent."
- **Power in the glow** (`Mana.POWER`, synced): a player's is their most mana / 25 (a finder 2, a fresh caster 4, Arcana
  raises it), 1-10. The resting glow is `REST_BASE` 0.05 + 0.07 a power step + 0.25 at full mana, so the weakest glow
  dimmest and Arcana lifts the floor. `ManaClient.Folk` draws it on every non-player humanoid too.
- **Attuned folk** (`ManaFolk`): hermits 8, court wizards and necromancers 6, blood witches 5, shamans (orc ones too) 4,
  village clerics 3, each give or take one; attuned as players come near (every 5 s within 48 blocks) or when they cast.
  MCA's "Ask about their magic" (only on the attuned): a line by tier, then how it measures against yours if you're
  attuned, or a remark if you're corrupted, or that you can't see it.
- **Ripples:** every cast ripples the caster's aura, a swelling ring through the glow coloured by intent: red harm, gold
  healing, white warding, blue will (raising, whispers, the wand). Players' casts (`Mana.cast`, `Spellcraft.afterCast`)
  and the folk's (`Sorcery` spells, clerics, court wizards' rekindle, the blood witch).
- **Amethyst:** an attuned player uses a shard to crush it for 10 mana (0.5 s apart), up to 150% of their most.
- **Mana sickness** (`ManaSickness`): mana spent, and mana forced in past full, is strain against your most mana; it
  eases 4% of it a second (x3 meditating). Past 1.0x: queasy (nausea, half regen, 60 s); 1.6x: sick (weak, slow, hungry,
  no regen, casts cost 1.5x, 90 s); 2.2x: burnt out (mana gone, 4 damage, no casting, 120 s). A pulsing grey-green tint.
- **Alchemy** (`ManaAlchemy`, Powers by reflection): essence carried in phials (vis) wears attunement into the unattuned
  (0.05 a point every 5 s, up to 60 points: at 100 they're attuned as a finder) and trickles mana into the attuned (up to
  0.5/s). Flux of 100+ in your chunk drains flux/200 mana a second. Corruption shrinks your most mana (0.5% a point, at
  most half); at 25+ you see glows and ripples as the attuned do, and an attuned player casting within 6 blocks of you
  pays 1.5x.
- **Meditation:** settled in with Powers' Meditate key, mana comes back 3x/4x/5x by Meditation rank.
- **Mana-infused wand** (`ManaWand`, stick + gold nugget + amethyst shard): holds 100 mana. Sneak-use pours 25 in;
  use at an attuned player gives it to them, at nothing draws it back. Past-full strains the receiver. Art from
  `tools/gen_mana_wand.py`.
javac-checked against Andy's 26.3 jars; not Loom-built or seen in game.

## 0.7.245: mana spirits: the mage hands, Grimsby and Varro (pack 0.17.32)
Andy: "I want to add a mage hand type of ability that mimics the way stands work but not their presentation. use of the
mage hands calls upon a mana spirit, each of which has a differing personality of 5 ... these spirits should conjure as
arms behind you with the same effects as your mana aura making up their entire form ... some just give you buffs ... and
some have entire move sets but all can be upgraded and leveled and the ones with personalities can kick in on their own to
assist you at times similar to how the symbiote operates. make them speak like wise wizards from ancient times looking
after a young child or grumpy old wizards that are tired of looking after said young child ... Make them give battle
advice while in combat at higher bonding levels." Look: Nero's Devil Breaker to Doppio's partial manifestation, "all
different sizes". JCraft: Eyes of Ender is All Rights Reserved: ideas only, our own code and art. Plan: `docs/Spirits-Plan.md`.
First patch: the shared mage hands plus two of the five spirits; Ambrose (ward), Thessaly (reach), the Quiet One (echo),
Clash, Spirit armour, Spirit ride and Sense come next. All in `spirits/` and `client/spirits/`.
- **Gaining one** (`SpiritSickness`): an attuned player left at a fifth of their health (or downed) has a 1 in 18 chance,
  once per two minutes, that a spirit answers (Grimsby or Varro, leaned by heritage). Then spirit sickness: mana back at
  half pace, the spirit-to-be whispering once a day, a flicker of an arm at the edge of sight; it wakes on the fifth day
  at the latest, sooner the more you push it (meditating, casting, nearly dying again), and the waking cures it. One for
  life: `/kingdom spirit release` (twice) lets it go for good, empties your mana and burns you out.
- **Summoning** (`Spirits`, key L): tap to call it (10 mana), it drains half a mana a second out and fades when you're
  nearly empty; sneak-tap cycles its moves, tap casts the chosen one (hold to charge the javelin), sneak-hold puts it
  away. Every move costs mana through `Mana.pay`, so it strains you toward mana sickness. Dying, logging out or spectating
  puts it away.
- **Bond and level**: bond grows with time out (x3 meditating) and fights won, drops when you burn out with it out; from
  40 a second arm, from 20 it acts on its own, from 40 idle remarks, from 60 battle advice by foe (casters, archers,
  creepers, the armoured, crowds, the big), from 80 advice before the fight. Level from kills and casts: +5% damage a
  level, moves unlock by level, 20 at most.
- **The mage hands** (`HandMoves`, every spirit): far hand (use chests, doors, levers, frames, pull items from 16 blocks,
  +1 a level), grab and throw (lift a mob or item, hold it, hurl it; charge for speed), catch (snatch arrows, tridents,
  javelins, fireballs and potions from the air; from level 3 on its own when it can pay), shove (a cone of knockback),
  hold the line (pin a mob in place), second pair of hands (the spirit holds your shield or torch while you fight
  two-handed). Allies (pets, thralls, team, your faction's folk) are never harmed.
- **Grimsby the Weary** (blade, steel-blue, an arm's length of bony fingers and a blade): five conjured arms (sword,
  greatsword sweep, spear thrust through two, axe that breaks shields, hammer that knocks up), each a 3-tap combo with a
  heavier third hit; flurry, riposte (a parry with a counter-cut), guard break (a slam that breaks shields and stuns),
  finisher (a single cut on a downed foe). On his own: parries a blow you didn't see coming, then complains.
- **Old Varro, the Siege-Mage** (siege, amber, a gauntlet as tall as you): mana javelin (`SpiritJavelin`, charge it,
  pierces three), volley (a fan of five), bombard (a slow orb that bursts), spear wall (javelins rise in a line), mark
  (your hits on it do 1.3x). On his own: a javelin at whoever draws a bow on you.
- **The look** (`client/spirits/SpiritArms`): the arm grows out of your own, scaled by the spirit's size and eased in,
  pushed behind the shoulder, three aura shells in the kind's colour over mana's blue, posed per move and lerped back to
  idle, wisps at the hand; only the attuned (and the corrupted) see it. Every autonomous act flashes your screen in its
  colour (`SpiritHud`), and the HUD shows the spirit, its move and level/bond while it's out.
- **The voice** (`SpiritVoice`): chat lines only you see, in its colour and name, for waking, summoning, dismissing,
  tiring, kills, hurts, low health, idling, bond, levels, acts, release and the five sick days; Grimsby grumbles, Varro
  barks.
- **Folk spirits** (`FolkSpirits`): court wizards and hermits carry Grimsby or Varro (level from their power, bond 50),
  out while they fight: Grimsby folk flurry and parry one blow in three, Varro folk throw javelins and volleys.
javac-checked against Andy's 26.3 jars (the usual optional-mod errors only); not Loom-built, not seen in game. The L key
is the only letter neither mod nor vanilla binds.

Second patch (same release): the other three spirits, the clash and the last two shared hands. Andy: "two players' spirits
lock arms; it uses the beam-clash film", All Might style; the Quiet One "seemingly non-responsive" and speaks once.
- **Old Ambrose, the Warden** (ward, pale gold, a hand twice yours): aegis (a wall of palm that stops blows and arrows
  from the front), steady hands (a toggle: no knockback, no flinch, 2 mana a second), bless (a touch that heals an ally
  and gives resistance), catch me (he catches your fall and hauls you out of lava), mend (a toggle that closes cuts and
  heals a point a second while you can pay). On his own: raises the aegis at a blow you didn't see, catches falls, hauls
  you from lava, blesses a hurt friend, mends you after a fight.
- **Thessaly the Learned** (reach, violet, a slim glove with three rings): long reach (draws items and mobs from 12
  blocks), unseen hand (lifts from a pocket at 6 blocks, Pickpocket's lift), rune trap (a glyph on the floor that snares
  the first foe to cross it), unlock (picks a locked chest with Lockpicking's own game, by her level), juggle (lifts up to
  four things and hurls them one by one; costs per thing lifted, the cooldown waits for the last throw). On her own: points
  out an unopened chest within 24 blocks with a line of sparks and a remark, once a minute.
- **The Quiet One** (echo, grey, a plain featureless arm, conjured at a different size each time: half to three times
  its own): echo strike (a toggle: every melee hit lands again 10 ticks later for half), sense (motes on hidden mobs,
  locked chests and ore near you, only you see them), mirror step (a sidestep that leaves a glowing double the mobs
  chase for two seconds), stillness (three seconds where mobs within 8 stop and arrows hang in the air), silent end (one
  blow kills a foe under 30% health, never a boss; once a day). On its own: now and then ends a fight with a silent end
  or a stillness. It says nothing, ever, but the five sick-day whispers; at bond 100 it speaks one word, once. Who answers
  a brush with death is leaned by heritage (Tideborn, Verdant, Dawnborn: Ambrose; Sylvan, Nightborn: Thessaly; Stoneborn,
  Warriorborn: Varro; Emberborn, Earthling: Grimsby), the rest even; the Quiet One never leans.
- **Spirit armour and Spirit ride** (`HandExtras`, every spirit): armour wraps the arms round you like plate for six
  seconds (half damage, no knockback); ride plants both arms as stilts and vaults you forward and up, the arms taking the
  landing.
- **The clash** (`SpiritClash`, `client/spirits/ClashCinematic`): two keepers with spirits out, within four blocks, who
  strike at each other (grab, shove, pin, a blade, a javelin) within a second lock arms: both rooted, a five-second contest
  where every tap of L adds to your side and the spirits' levels tell, the first to a 40-point lead wins; the loser is
  thrown six blocks and takes 8, the winner's bond grows by 3; a draw throws both. The film: letterbox bars, the HUD gone
  but for a tug bar in both spirits' colours, the camera swung side-on, the screen shaking harder as the lead swings, a
  flash in the winner's colour; attuned players within 24 blocks get the bars. No summoning, cycling or dismissing while
  locked: the loser is thrown. Grimsby, Varro, Ambrose and Thessaly crow or grumble; the Quiet One says nothing.
- **The look**: Ambrose's broad open hand (five spread fingers), Thessaly's glove and rings, the Quiet One's bare arm at
  its rolled size; a pose for every new move (the aegis wall, the palm overhead, arms down and open, a hand on the chest,
  wrapped round the body, stilts, juggling arms, the locked clash); the HUD marks steady hands, armour and echo "(on)"
  while they last (`SpiritState.toggles`, synced).
- **Under the hood**: a move's pose, cooldown and cost for the cast just happening are kept per keeper (`CastHints`), not
  on the move (one object shared by every keeper, and by the client's HUD in singleplayer). Upkeep (holding the line,
  steady hands, mending) is drained with no strain, so being healed doesn't make you mana-sick. Everything the hands hold
  up is tagged, so a player or mob saved mid-hold gets its gravity back on load. Putting the spirit away ends every hold,
  ward, armour, echo and juggle. `Lockpicking.lockLevel` and `pickBySpirit`, `Pickpocket.liftBySpirit`,
  `MobShields.disable` and `BossBars.boss` opened for the spirits.
- **Where the arms sit, and first person** (Andy's first look, 2026-10-27): Varro, Ambrose and the Quiet One float above
  and ahead of your shoulder, raised forward, as a stand hovers; Grimsby's blade and Thessaly's glove stay on your own arm.
  In first person your spirit's arms are drawn too (`SpiritFirstPersonMixin`, after the hands), where they sit on your body,
  so the floating ones hang in view and every strike comes out in front of you.
  Every move blazes the arms to full brightness while its pose holds, then they settle back to your glow.
- **Jointed arms, and blows that land** (Andy: "animated with joints and fingers, more like the nero gif ... more detail
  and oomph"): `SpiritRig` rebuilds every arm as shoulder, upper arm, elbow, a forearm that stretches out on a thrust
  (the Devil Breaker), wrist, palm, two-jointed fingers and a thumb, each spirit in its own make: Grimsby bony with
  knobbed knuckles, claws and the blade on its guard; Varro in plate (pauldron, couter, vambrace, gauntlet, plated clawed
  fingers, studs); Ambrose's flared sleeve, broad palm and ward sigil; Thessaly's armlet, bracelet and three rings; the
  Quiet One smooth with long fingers. Each move has a shape: strikes wind up (drawn back, elbow bent, fist) then snap
  past the mark and settle; swings raise high and cut across; grasps reach open then close; wards spread the palm. Idle,
  the fingers flex. The floating spirits no longer swing with your walk, and their blows follow where you look. A blow
  leaves two fading ghosts of the arm behind it, swells the hand as it lands, and bursts where it lands (dust in the
  spirit's colour, sparks, crits and a crack only the attuned hear). Wisps of the floating spirits rise above and ahead.
- **Wisps, a living idle, Grimsby's weapons** (Andy's second look): three faint ghosts on the outer shell trail every
  movement, idle included, and brighten on a blow. Idle, the elbow works in and out, the shoulder rolls, and now and then
  the arm draws in and the hand closes as if stretching. Grimsby's forms are now their own weapons growing out of his
  fist as the blade shrinks away: the greatsword (guard, fuller) and the hammer grow to 1.35x; the hammer and the guard
  break heave up overhead and slam down in front, throwing a ring of aura along the ground with a boom. The axe turns
  his aura to ragged embers that churn and flicker; the spear draws it into a pale, bright, streaming core.
- **The spirit key works on every install**: `SpiritKey` unbinds vanilla's Advancements from L once per install
  (marker `jaymoskingdoms-spirit-key-v1`). Powers' key pass had already run on existing installs, so L kept opening the
  advancements screen and the spirit never came.
- **Dev runs load worlds again**: the silver sword and the eight steel plate recipes wait for `c:ingots/silver` and
  `c:ingots/steel` to have items (`fabric:tags_populated`). Powers fills those tags, so in the pack nothing changes; a
  Kingdoms-only run used to fail registry loading on the missing tags.

## 0.7.246: crews know their hands, stand on deck, and dress for the sea; villagers talk of your story (pack 0.17.34)

- **Shipmates** (Andy: "I shouldnt have to introduce myself to the ship crew ... refer to me as a powder monkey or
  deckhand"): the crew of the whaler you signed on to (`StoryTalk.shipmate`) know you and you know them, aboard
  (`StoryTalk.knowEachOther`, every 5 s) and on MCA's menu. They never give MCA's stranger's greeting; they open with
  "Aye, deckhand?" and the like. The captain welcomes you back aboard by your berth, and deck chatter is now and then said
  to you by it. Berth: powder monkey if you're small or to the spiteful, proud and hotheaded, deckhand otherwise.
- **Your story, told back** (Andy: "each start to have some kind of story adjacent reference from the villagers you
  meet"): `StoryTalk.LANDING` records where a life story set you down, when, and the realm or camp it was about. For three
  days, a villager you first talk to within 112 blocks of there, or in that realm's land, opens with a line about how you
  came (`story.jaymoskingdoms.met.<start>.0-2`, three for every start) instead of MCA's "Are you an adventurer?". Each
  says it once (MCA memory `jaymoskingdoms.story.<player>`). Everything after that is up to MCA.
- **One knight to a ship** (Andy: "you're not wearing full plate out to sea"): her first hand in mail or plate who isn't
  her captain is her knight (tag `jaymoskingdoms.crewman.knight`), an MCA guard in his armour. Everyone else sails as a
  fisherman (a sailor's clothes) in no mail or plate; leather stays. Crews from before are looked over every 5 s.
- **Crews stand on deck**: Andy saw all the sailors seated as if riding something. `McaCrewStanceMixin` clears
  `isPassenger` as the last thing MCA's villager renderer sets, and EMF's animations pause for crew at their stations
  (`ShipClient.crewAboard`), so no riding pose comes back.
- **Crews get their downed up** (Andy: "the ship crew should rush to revive each other, including the player"):
  `Crewmen.care` (from `Corsairs.deckwork`) sends the nearest free hand running along her deck to anyone downed aboard,
  one of hers or a player she isn't hostile to (never her captain, nor a gunner mid-fight), with a shout. They kneel 6 s
  and get them up (Powers' revive and bandage tags), and tell a player so. Ashore tending (`Downed`) skips riders, so
  nobody did this at sea before.
- **Ship stairs are steps**: a ship's collision took each stair as a full block (`Hull` lo/hi), so a stairwell under a
  deck beam could be neither stepped up nor jumped. `Hull.steps` keeps a stair's own boxes and `Ships.collisions`
  turns them as she lies.

## 0.7.247: attuning waits till a newcomer can see it (pack 0.17.36)
Andy: "delay the player's spawn at the start because if they spawn attuned they miss the animation."
- `StoryGround.unseen(player)`: true while their story is owed, the loading screen is up (or settling), or MCA's
  creator is still to come, and for `REVEAL_TICKS` (2 s) after they come out from behind all of those.
- `Mana.tick` only attunes a Mage or Sorcerer (or an amethyst-touched player) when they aren't `unseen`, so the
  flare, the sound and the blue sight play once they're standing in the world. Not seen in game.

## 0.7.248: spirit arms only while used, a ghost's look (pack 0.17.37)
Andy: "the arm should appear and disappear while not using it. the idle it too distracting, plus it probably costs
concentration to keep a projection up ... something about them looks too busy. maybe use the ghost of the great kung lao
as inspiration? a more solid looking yet wispy projection?"
- **Up only while used** (`SpiritArms.presence`): a called spirit waits unseen (the odd wisp at your shoulder) and its
  arms form over 3 ticks when a move starts (summoning is one, so it shows itself once when called), stay through the
  move and 12 ticks after, then fade over 10. Held things, steady hands and spirit armour keep them up, and a hold that
  re-stamps its pose doesn't re-form. They form by fading in and swelling the last fifth, not growing from nothing.
  The idle mana drain is unchanged.
- **Kung Lao's ghost** for the look: the arms have their own shells, a core that is the arm itself (not inflated),
  bright and pale, its texture near still, under two thin, dim edges in the spirit's colour that drift slowly. They show
  near full even with a dim glow. Paler, bigger wisps smoke back and up off them while they're up.
- **Calmer**: the idle stretch is gone, the elbow, shoulder roll, sway and finger flex are a third of what they were,
  and the ghost trail is two soft arms behind a fast move only (a blow's snap, a grasp closing), none at rest.
  Not seen in game.

## 0.7.249: the life story keeps your power a surprise (pack 0.17.38)
Andy, of the "Then one day..." page: "this screen should not show the class you will get".
- The "Then one day" answers and the story page no longer say which power wakes ("Power: Cryokinesis, awakening in a
  few days" / "whatever fate decides" are gone, and their lang keys with them). Lost in time still says Chrono wakes at
  once, since that's the road's own doing.
- A road that matches the gift still says it wakes a day sooner, without naming it.
## 0.7.250: job halls and Blossom villages, the pack's Japan (pack 0.17.39)
Andy pointed at Tax' Village Architect ("adds the new buildings we need for jobs (butcher stable, etc) it also adds a
new cherry blossom village which is perfect for the unused japanese/samurai armor we have. they can be the analogue
for japan"). It is all rights reserved and Forge/NeoForge 1.21.1 only, so nothing of it is used: these are our own.
- **The cherry grove was snowy land**: `Palette.of` tested "grove" (the snowy grove) before "cherry", so a seat in a
  cherry grove got the snowy palette, the Northern tongue and no ronin. Cherry is now tested first, so the Blossom look,
  tongue and harness finally show (`Outfits.VERSION` 3 dresses folk again once).
- **Job halls** (`JobHalls`): a butcher's shop (open counter, smoker, chopping block, chains), a fishery (drying rack,
  dried kelp, a hut with the catch barrel) and a hunting lodge (log cabin, fletching table, pelts, a target on a hay
  bale), drawn from each settlement's kit. Realms build them from 22/26/30 people, villages from 10/14/16. Their job site
  blocks give villagers there the butcher, fisherman and fletcher trades.
- **Jobs work out of them** (`Careers.Job#hall`): butcher → butcher's shop, fisher → fishery, hunter → hunting lodge,
  smith → smithy, courier → stable. A town where the job's building stands is always hiring for it and pays
  `HALL_BONUS` (1) more an order; the offer says so.
- **The Blossom way of building** (`Blossom`): among the cherry trees every building's kit roofs in deepslate tile over
  white plaster, and a house is a minka (dark timber frame on a stone footing, boards and plaster, paper windows, tatami,
  futons, a low table, a paper lantern, a wide hipped overhanging roof, often an engawa).
- **Blossom villages** (`BlossomVillages`): no village is generated in cherry groves, so one rises there by itself
  (checked each minute, 35%): a cherry grove all round, 384 blocks from any settlement, no bell within 128, out of sight
  of the player. A bell on a stone plinth, a shrine, a torii over the gravel road in, stone lanterns, four minka, a
  butcher's shop, a hunting lodge and two stalls, villagers and a golem; then an ordinary village (elder, shed, crew,
  growth) with a Blossom name (Hanamura, Kirizawa...). `/kingdom found blossom` raises one where you stand.
- **Samurai guards**: a realm's guards in the Blossom land always wear the ronin harness in the realm's colour (not
  only one realm in two), Epic Knights doesn't replace it, and with MCA every settlement there puts its MCA guards in it.
  Not seen in game.

## 0.7.253: you wake where your story began
- **Respawn at your start.** Every life story start now sets your respawn point (forced, no bed needed) where it
  placed you (`LifeStory.home`), so dying doesn't send you to the world spawn; sleeping in a bed moves it as usual.
- **Whalers wake aboard.** A signed hand who dies is put back on her deck (`Whalers.reboard`) while she's within
  reach; otherwise they wake at her harbour's shore.

## 0.7.258: spirit hands become what they do (pack 0.17.49)
Andy: "The aegis hand should animate like a shield infront of you ... they should be arms, that is only their idle, and
they pop in whenever they speak passively as the arm or default. every other use becomes what it entails, the catching
hands should animate beneath you and the weapon forms look great, but they need to reach way out to whatever you aim at,
within 20 blocks. that should also be far reach's extent. animating the hand the whole way for all who can see it."
- **Speaking:** a spirit's line (`SpiritVoice`) shows the arm by its keeper for 3.5 s (new pose `Poses.SPEAK`), unless a
  move is showing it.
- **Reach synced:** `SpiritState.show` now packs how far the hand reached (whole blocks, up to 31) beside the pose, set by
  the move through `CastHints.setReach` (`SpiritState.reach()`, `shownAt` now `show >> 11`).
- **20 blocks:** the mage hands reach 20 blocks flat (`HandMoves.REACH`; it was 16 plus 1 a level), and Thessaly's unseen
  hand, rune trap and unlock reach as far. Grimsby's weapon forms strike the foe you aim at (`Arcana.sighted`) out to 20
  blocks when it's beyond the form's own reach, with the foes within 0.6 of the form's reach of it; the cut's arc and sound
  are drawn there. Closer, the old cone. Far hand, grab, pin, unseen hand, trap, unlock and Thessaly's loot pointing all
  report where they reached.
- **What a use becomes** (`SpiritArms.shape`): a move that reached shoots the hand out along your aim the whole way there,
  the forearm stretching, landing with the blow, swords and axes sweeping in from the side, the hammer raised and brought
  down; the burst (and a far slam's ring) is where it landed. The aegis and Varro's ward bend the hand up palm out and swell
  it to 2.8x as a shield in front of you (two hands side by side at bond 40). Catch me turns the hands palm up beneath your
  feet, cupped, swelling to 2.6x.
- **The axe fells trees** (Andy: "the axe should chop trees in a single blow"): with no foe in reach, Grimsby's axe at a
  tree you aim at (out to 20 blocks) brings the whole tree down at once (`Trees.fellAtOnce`), toppling away from you as a
  felled tree does, and a woodcutter's orders count it.
- **Seen the whole way:** `SpiritCullingMixin` grows a keeper's culling box by the reach while it's out, so the arm is drawn
  even when its keeper is off screen. Not seen in game.
## 0.7.259: no guards at sea, sailors take up their weapons for a fight (pack 0.17.51)

- Andy (after 0.7.246): "all the sailors are guards still. I dont want any guards on these ships ... if you put a guard,
  they CANT have armor. but all the sailors should double as both. just picking up their weapons when the time comes."
  Every crewman is now an MCA fisherman (a sailor) in no armour: no knight, no guards. Their folk's weapons are stowed
  (`Crewmen.STOWED`); they take them up the moment they have a foe (`Crewmen.arm`) and put them away 20 s after the
  last one (`Crewmen.stow`). Crews from before are changed in place within 5 s: guard profession to fisherman, armour
  off and out of their packs, weapons stowed, the knight tag gone. A navy hand is an "Able seaman", not a "Marine".
  (0.7.246 only changed crew in mail or plate, so unarmoured MCA guards stayed guards.)
- **The sea no longer shows in a ship's hold** (Andy's clip of the whaling start's whaler: "it looked to me like the
  hull wasnt actually bailed"): her hold was dry, but the renderer's water mask only covered empty cells. Slabs, stairs
  and other part-blocks count as wall, so the sea was drawn in their empty half. `Hull.masked` adds the part-blocks with
  no sea beside them, and `ShipRenderer` masks those too.

## 0.7.261: the Giants stomp with every step and sway as they walk (pack 0.17.55)

- Andy: "Giant enemies should emit a stomp with every step, emitting a shockwave signified by a radiating particle
  effect, cracking the ground and dealing damage to those who dont jump. The giants should also move with a slow sway,
  keeping their move speed but removing that fast swing." Covers the four Aether Giants (`AetherGiant`); the vanilla
  Giant (sized up by `Monoliths.sizeVanillaGiant`) has no AI and never walks, so it's left as it was.
- **Steps:** the server counts its stride by the ground it covers, one step every `STEP_LENGTH` (6) blocks, right foot
  on the even steps. Each footfall (`AetherGiant.footfall`) thuds, throws up the ground's dust, cracks the blocks under
  the foot and five wandering lines out from it (`destroyBlockProgress`, fading over 5 s, nothing broken), and sends a
  shockwave out along the ground at 1.25 blocks a tick to 10 blocks. Whoever the wave front passes standing on the
  ground (not jumping, flying or riding) takes 5 damage (half at the edge) and a jolt up and away; each wave hits each
  one once. The client draws the wave as a ring of dust and puffs running out over the ground. Vanilla's step sound is
  gone (the footfall has its own).
- **Sway:** the model's legs follow the server's stride (`GiantState.stride`, kept in step by an entity event on each
  footfall) instead of vanilla's walk cycle, which ran a 30-block body's legs about four times too fast. Smaller
  swings, its weight rolling onto the foot coming down, shoulders turning with the forward arm, head kept level, and it
  sinks onto each footfall so its feet stay on the ground. Same speed as before. Blows, the stamp, the flinch and the
  fall-and-shatter death are unchanged. Not seen in game.

## 0.7.262: three new bloods, Andy's NOEA picks (pack 0.17.56)
Idea from the Dragon Block NOEA race list (a short mention only; all names, code and art our own). Three heritages,
picked at the start like the first ten (keys `divine`, `biobuilt`, `rubberfolk`, so the entity tags are
`jaymoskingdoms_heritage_<key>`: Jaymo's Powers reads them for ki sense, scouters and the lineage, which reads as Earthling).
- **Divine-born** (`DivineBorn`): halo and gold filigree, +1 health every 4 s on top of the body's own, blows 25% harder on
  the wicked (undead, illagers, barbarians, a Rogue's Stealth alignment, anyone whose power or gift is corrupted),
  Restoration spells and the Sorcerer's Mend, Renewal and Rekindle heal 30% more and cost 25% less mana. Flaw: hunger 25%
  faster.
- **Bio-built** (`BioBuilt`): a downed foe (`Downed.isDowned`) looked at within 3 blocks and held on the heritage key for 3 s
  is absorbed (consumed): a share of its health back and its worth added; 4 and 12 worth fold the body into the second
  and third form (health, armour, blows; a changed look: seams, then plates to the elbow, then to the shoulder and
  cheeks). Blows drain 8/12/18% of their harm back. Third form: the key pressed on no foe buds up to 2 small copies for 45 s
  (risen through `Thralls`, so they obey the master and crumble). Flaw: frail first form (2 fewer hearts), lightning and
  magic 50% harder. Nothing grabs and nothing is goo: it never reaches for anything but the downed in front of it.
- **Rubber-folk** (`RubberFolk`): pink skin tint, +1 block of reach (the stretch), a lost limb regrown every 20 s (Powers'
  `Dismember.regrow` through `PowersCompat.regrow`), small weak or downed foes swallowed for a heal (held key), sweets
  (cookie, pies, honey, berries, melon, cake slices, and Kitchen's tarts, pies, cobbler, brownies by `KitchenCompat.sweet`)
  heal 2 to 3 hearts. Flaw: fire burns 60% longer, arrows and spines 25% harder.
- **The held heritage key** (`HeritageHold`): `;` held is sent every other tick (the same key as the Warrior-born's tail); the
  server runs one absorbing bar per player.
- Each has three looks (`HeritageVariants`: Haloed, Starlit, Seraphic; Seamed, Brassbound, Opaline; Rosy, Candy-striped,
  Berry-glossed), marks from `tools/gen_race_looks.py`, a halo model part, lang lines, a place in the life story (born at a
  hilltop shrine, in a workshop's vats, at a travelling fair), villagers (Rubber-folk fairly common, Divine-born very rare,
  Bio-built only by inheritance), MCA skin tints and skin ranges.

## 0.7.263: the monoliths stand three times as big, of the Aether's own stone, broken only by high-tier Tinkers tools (pack 0.17.57)

- Andy: "the monoliths should be triple their current size and be built from the materials their respective aether
  materials. they are only mineable with high teir tinker tools."
- **Triple size** (`Monoliths.SCALE`): the same design, every block of it now a 3x3x3 cube. The pillar is 9 wide and
  39 to 54 tall, its broken crown 3 wide, its core and four shards 3 blocks a side, its ground 12 out, and its rubble
  lies 9 to 15 out in pieces up to 3 across. Ground under it may be 6 uneven (was 4). Giants rise 16 to 22 out (was 9 to
  14), clear of the pillar, and spare the whole monolith when they trample (`Monoliths.part`).
- **Aether materials** (`Element`, Jaymo's Powers' blocks looked up by id; the old vanilla blocks without Powers):
  Highfield holystone banded with holystone bricks, a zanite core, on Aether grass; Magnetic ferrosite banded with
  undershale bricks, a gravitite core, on ferrosite sand; Arctic icestone banded with icestone bricks, an arkenium core,
  on arctic snow; Irradiated irradiated holystone banded with agiosite bricks, an ambrosium core, on coarse Aether dirt.
- **Only high-tier Tinkers tools:** a monolith's stone, bands and core (ours, on the Overworld) break only to a Jaymo's
  Powers Tinkers mining tool of diamond tier or better (amethyst bronze, pig iron, slimesteel or steel head; checked by
  the tool's own tier rule, `Monoliths.highTinkers`). Anything else is told so and the block stands; creative is
  unaffected. Explosions and pistons aren't stopped. Monoliths raised before keep their old look; their core still goes
  dark when their Giant falls. Not seen in game.

## 0.7.264: parties (Andy's NOEA picks)

- Andy's NOEA picks: a party system. Ideas only; the code and names are our own.
- **`/party`** (`Parties`). Andy: "parties should be what are started with handshakes", so a party is the handshake
  group (the `jk_group_` scoreboard team): taking someone's offered hand joins their party (`Parties.shaken`, called from
  `Handshakes.done`; one is started, led by the offerer, if they have none), and `invite <player>` (a clickable Accept and
  Decline; lapses after 60 s) does the same from afar. Also `accept`, `decline`, `leave` (`/group leave` too), `kick <name>`,
  `list` and `pvp on|off`. Up to 6 (`MAX_SIZE`; a handshake into a full party only swaps names). Only the leader invites,
  kicks and sets PvP; the lead passes to a member who's on when the leader leaves, and a party of one is disbanded.
  Members are the team's; leader (username) and PvP are kept by team name on the Overworld (`party_meta`). Old groups
  become parties as they are, led by the first member on. The group perks (known names, colour, shoulder to shoulder,
  `/group name`) stay; an op's own team is still a group but not a party.
- **No friendly fire**: `ALLOW_DAMAGE` cancels a blow between two members while the party's PvP is off (the default).
  The attacker is the damage source's entity, or, for an `OwnableEntity`, its owner, so arrows, tamed beasts and Powers'
  powers (all damage with a player behind it) are covered.
- **Shared slay quests** (`Quests.shareSlain`): when a player's kill is counted for a slay quest, each other member
  within 64 blocks in the same dimension with a slay quest of the same target is credited too. Other kinds and bounties
  (head-based) are not shared.
- **Stats at any distance**: once a second `Parties.Sync` sends each member the party's health, max health, hunger,
  dimension and block position (names are character names, a hood's or disguise's, via `CharacterNames`);
  `client/PartyClient` draws the others under the kin-sense compass at the top left (hidden with F3).
- **API for Jaymo's Powers (by reflection)**: `Parties.members(ServerPlayer)` (online members, the player first) and
  `Parties.allied(ServerPlayer, ServerPlayer)`.
- Lang lines in `en_us.json`; `docs/Abilities.md` rows 441 to 445.

## 0.7.266: rides go by on a road screen (pack 0.17.65)
Andy: "this cutscene system should replace our carriage rides, they always get stuck and you cant see anyway, at least
this gives you an animation and hides imperfections." The idea is Stagecoach's (its Bukkit plugin can't run in a
Fabric pack; nothing copied, the screen is our own).
- **`Journeys`**: every player's ride to another place (a rickshaw, a rich realm's cab, a milestone's coach) is seen
  setting off for `DEPART_TICKS` (1.5 s), then the road screen comes up and the ride stands still where it was. The
  trip takes its length at `RICKSHAW_SPEED` (16 blocks a second) or `CARRIAGE_SPEED` (30), between 5 and 30 seconds.
  At the end the player is moved to the far end (a rickshaw to the town's seat, a coach to the road's end) and the
  rickshaw or carriage with them, and they're set down beside it. The puller or cab heads home as before (a puff back
  to the rank once nobody's near); a coach goes after 3 seconds.
- Nothing hurts a traveller on the way (`ALLOW_DAMAGE`). **Turn back** (the screen's button), getting down while it
  sets off, or logging out puts them down where they started with their fare back (taken from the realm's treasury).
- A ride back from a restart with a player aboard goes on the road screen again, with no fare to give back.
- **`client/TravelScreen`**: drawn by hand in blocky pixels (`SCENE_PIXELS` = 120 to the screen's height): the sky at
  the hour they set off (sun, moon, stars, drifting clouds), far hills (snow-capped in snow country), near hills with
  trees, the road with ruts and tufts, a fence going by in front, and the ride: a rickshaw (its puller walking between
  the shafts, the hood in the realm's colour, the rider sat back) or a carriage in the realm's colour (a coach with
  luggage up top) behind a galloping horse with its driver up. The land turns from the look of the start to the look of
  the end on the way (grass, snow, sand, cherry blossom; trees change one by one). A wooden sign up top names the place,
  the blocks and the seconds left, with a bar below. Hooves or footsteps and a creak now and then. Fades in from black
  and out to black; the world fades back in after.
- Lang lines in `en_us.json` (`journey.*`); the hire messages now say the fare comes back if you turn back.

## 0.7.267: fighters guard and parry; attack tells (pack 0.17.67)
Andy: "Build those ideas too" (after Powers' guard and parry, PR #591).
- `MobShields`: a fighter fighting Better Combat's way with no shield raises its blade instead (its weapon's guard from
  Powers), 30% of the times a shield-bearer would (`BLADE_GUARD_CHANCE`); `raisedFor`, `snap`, `drop`, `guardHand`.
- `MobParry`: a player's blow in the first 8 ticks of a fighter's raised guard is parried; with its guard down, 12% (30%
  for veterans, `WeaponForms.experienced`) of blows are. A parry takes nothing, restarts your attack timer, slows and
  pushes you, and the fighter answers at once (`MobCombat.hasten`); 3 s before another. Never from behind, nor against
  an axe, a two-handed weapon or the Combat Arts' area blows.
- `MobCombat` attack tells: a glint at the weapon as each swing winds up, white for a blow you can parry, red for a
  heavy one (two-handed, or 1.4x damage and up) that carries `jaymospowers.unparryable` while it lands, so Powers'
  guard only blocks it.
- `Crewmen`: a crewman's blade glints and lands 6 ticks later (`MELEE_WINDUP`), as every mob's blow now does in Powers.
- Guns glint red (Andy: "All guns should red glint too"): `MobCombat.aimed` shows a red glint and a flint's click at the
  muzzle 10 ticks before a pirate's pistol or a guard's musket fires (an aim not fired within 20 ticks starts over), and
  `MobCombat.unparryable` tags the ball so Powers' guard only blocks it.
- Every sailor carries a flintlock (Andy: "all sailors should have at least one flintlock as a side weapon, some carry
  more"): each crewman who fights (`Crewmen.sidearm`) takes one up in a free hand when they arm; their brace (`brace`) is
  one pistol, two for 25% (gunners always at least two), three for 10%, fired 12-16 ticks apart, each after its glint,
  at a foe 3-22 blocks off, then all reloaded together. Pirate crew pistols moved here from `Pirates.crewFight` (shouts
  only now). Folk pirate deckhands carry one in the off hand and fire it too.
## 0.7.268: the Giants grab (pack 0.17.68)
Andy: "Giants and apes need a grab attack that is telegraphed and fairly slow, but deals massive damage to a single target,
with a letterboxed animation". The Aether Giants' grab, in `remnants/AetherGiant` (the Great Apes' twin is Powers'
`power/zfighter/ApeGrab`):
- **Five phases** (`GRAB_WINDUP` 30, `GRAB_SNATCH` 8, `GRAB_HOLD` 40, `GRAB_SLAM` 8, `GRAB_RECOVER` 20 ticks): it rears back
  with a double roar, its right hand drawn up behind it and its element's motes at the hand (the warning; get more than
  `GRAB_REACH` 9 blocks from its body, or 6 above or below, and the hand closes on nothing with a puff); the hand comes down on
  its target and pins it where it stood; the catch is lifted to its chest over 10 ticks and squeezed (`SQUEEZE_DAMAGE` 4 every
  10 ticks, with the element's touch); then hurled into the ground ahead (`SLAM_DAMAGE` 40, the strong touch, thrown
  `SLAM_THROW` 1.2 and up, block dust, an explosion, the ground cracked as its steps crack it); then it straightens up and
  lets go. It starts on a target within `GRAB_TRIGGER` 9 blocks, every `GRAB_TICKS` 400 to 600, and the stamp waits for it.
- **The catch** is carried every tick: players by `teleportTo` with their rotation kept, mobs by `setPos` facing it; velocity
  zeroed, fall distance reset. Its death lets go. Synced data `GRAB_START`/`GRAB_HELD` tell every client the phase.
- **Pose** (`client/remnants/GiantModel.grab`): the right arm reared up and flared, brought down, held trembling at the chest,
  hurled; the body leans back then forward, the head follows, the other arm braces.
- **The film**: `PowersCompat.film` finds Powers' `UltimateFilm.play`/`witnessed` by reflection (Kingdoms still runs without
  Powers): the catch, a player, sees the Giant's face push in on them for the snatch, hold and slam (56 ticks); everyone within
  `FILM_RANGE` 48 blocks gets the bars.
- Lang line `message.jaymoskingdoms.giant.grabbed`. Compile-checked only.
## 0.7.274: crewed ships come off the beach (pack 0.17.77)
Andy (clip): a whaler barely moved from where she started, well after fitting out; she lay with her bow up a beach by
her harbour.
- **Backing off never backed her.** Lying stuck, her captain backed for 4 looks (40 ticks), but she only goes astern
  once her sails are in, and taking in full sail takes 50 ticks, so she never went astern at all and sailed straight
  back into the same shore. Now `Ship.kedge` warps her off: sails struck, hauled astern at `KEDGE` 0.09 (gathered at
  `KEDGE_GATHER` 0.08) for `BACK_LOOKS` 10 looks, her bow brought round toward the open water `Fleet.offing` picked.
- **Hove to isn't stuck.** A whaler fitting out holds her heading with no sail; with her shore close aboard, no heading
  was clear, so she counted as stuck and backed off, again and again, a little further each time, till she was on the
  beach. Only a ship that means to make way (sail above 0) counts as stuck now.
- **Wedged ships come off.** A ship with some of her hull already in the ground (run up a beach, or land built up round
  her) could make no move at all, since every move still struck. Now any move that drives her no further in is allowed
  (`Ship.hits` counts the struck cells), so she can back or slide off. Partly in unloaded chunks she still holds still.
- Compile-checked only, not seen in game.
## 0.7.276: grand seats, real castles for kingdoms built from nothing (pack 0.17.79)
Andy: "the default hold you generate for a kingdom needs to switch, its way to blocky and novice looking. swap it for
any of these's large buildings or castles or keeps" (Towns and Towers, Repurposed Structures' jungle fortress, Luki's
Grand Capitals, Epic Villages).
- **`GrandSeats`**: a kingdom that raises its seat from nothing (no village building fit for a king, the story start's
  capital, `/kingdom found grand`) now takes one of the castles the pack's structure mods ship, by the town's land
  (`Palette.at`): plains ATi's marble chateau, Grim's Grain Sack keep or Glimmervault fortress; taiga Grim's Winterhold
  citadel or ATi's woodland keep; snow Grim's Whitewarden keep, Winterhold or Snowgrave citadel; desert, badlands and
  savanna ATi's Catalonian castle or granite fort; swamp the woodland keep or Grain Sack keep; cherry groves ATi's
  fortified temple or dojo; jungle Repurposed Structures' jungle fortress if the mod is loaded, else the fortified
  temple. A land with none loaded takes a plains one; with no castle mod at all the citadel goes up as before.
- **Read, not copied.** Each is looked up by structure id and generated as `/place structure` does
  (`Structure.generate`, any biome), then moved: its castle pieces centred on the flattest site `Castle.site` finds
  beside the village, the start piece's foot on the floor. The streets, houses and village pieces a few of them grow
  round themselves are left out (`outlying`); their rock plinths (`terrain`) are kept. ATi, Grim and Epic Structures are
  all rights reserved ("feel free to add to any modpack"), Repurposed Structures LGPL-3.0: nothing of theirs is in our
  jar, so this works only with their own mods installed.
- **A seat, not a dungeon** (`empty`): the land in its buildings' boxes is cleared from the floor up first; after it's
  placed its monsters and loose items are discarded, spawners taken out, every chest and barrel's loot table cleared
  (the treasury is the panic room's). Paintings, armour stands, frames, horses and cats stay. Columns it stands on get
  dirt and stone down to the land (`footing`, at most 16).
- **The throne** goes in its largest room the way a village seat's does (`Seats.pick`/`Seats.keep`): the throne at the
  back away from the door, the banner, the panic room under the floor, archers' posts on the roofs; three watchtowers
  round the town as the old keep kinds had. Its buildings are guarded (`Holds.guard`), so builders never tear them down.
  A castle wider than `MAX_SIDE` 112 is passed over.
- `/kingdom found citadel` (new `Castle.Kind.WALLED`) still builds the old 61-block citadel.
- Not added: Towns and Towers (CC BY-NC-SA 4.0, has a 26.3 build; not in the pack), Luki's Grand Capitals (all rights
  reserved, newest build 1.21.11), Epic Villages (in the pack, but only village houses and town centres, no castle).
  Repurposed Structures has a 26.3 Fabric build (needs MidnightLib); the jungle fortress shows once both are in the pack.
- **Shared spawn pools** (`SharedPools`, Andy: "all the new villages and structures should be added to the pool so
  different ones can spawn interchangeably"): every structure set made only of villages (vanilla's, redrawn by Epic
  Structures, Towns and Towers', any other mod's) becomes one set with all their entries at vanilla's village spacing,
  and every set made only of pillager outposts another at vanilla's outpost spacing. Each village spot draws any
  village that suits its biome, from whichever mod. Applied to the world's structure placement in
  `ChunkGeneratorStructureStateMixin` after `AtiCompounds.filter`; the log says how many sets and structures it pooled.
- Towns and Towers 1.13.12 joins the pack (root `build.gradle`, shipped unchanged).
- Compile-checked only, not seen in game.
## 0.7.277: Towns and Towers' villages join the shared pool (pack 0.17.80)
- Read the Towns and Towers 1.13.12 jar: its villages set (`towns_and_towers:towns`, 27 villages) holds a Nether piglin
  village that isn't tagged as a village, so `SharedPools` (which pools only sets made wholly of villages) left the
  whole set out. Pooling now takes anything tagged or named a village (`SharedPools.village`); the piglin village never
  spawns in the overworld. Its outposts set was already pooled. Kingdom founding still goes by `Seats.village`.
- Checked against Epic villages: Towns and Towers narrows vanilla's village biome tags (plains village: plains only,
  meadow gets its own), and Epic Structures redraws vanilla's villages, so Epic villages keep plains, desert, savanna,
  snowy plains and taiga. Vanilla outposts are plains-only now; Towns and Towers' biome outposts cover the rest.
- Towns and Towers is CC BY-NC-ND 4.0 (not BY-NC-SA as written for 0.7.276): shipped unchanged is still fine.
- Compile-checked only, not seen in game.

## 0.7.278: Repurposed Structures in the pack (pack 0.17.81)
- Repurposed Structures 7.8.4 and MidnightLib 1.10.0 join the pack, so `GrandSeats`' jungle choice,
  `repurposed_structures:fortress_jungle` (checked in the jar), now goes up for jungle kingdoms.
- `SharedPools.outpost` takes any structure with "outpost" in its id: Repurposed Structures' are `outpost_badlands`
  and the like (12 in `outposts_overworld`), which the old "pillager" check missed.
  Its villages (`villages_overworld`, 11, and the one-village sets) already pool by name.
- Compile-checked only, not seen in game.

## 0.7.279: a floating ki-glow hand for Far Hand and Catch (pack 0.17.82)

Andy linked Mage Hand (CurseForge, SkylorBeck; GPL-3.0, ideas only, our own code and look). After a first try with hands
left behind at chests: "I dont like any of the functionality, the hands shouldnt stay behind. just make the hand follow
the wispy ki glow texture, and only use it for the far reach, and catch."
- **The floating hand** (`spirits/SpiritHand`, short lived, never saved): the spirit's own hand from its arms with no arm,
  drawn (`client/spirits/SpiritHandRenderer`) as the ki glow is: three hollow shells of scrolling wisps in the spirit's
  colour, added like light, with no solid core (`textures/entity/spirit_hand_glow.png`, Powers' ki glow texture). Only
  the attuned (and those who read magic) see it.
- **Far hand** sends it instead of stretching the arm: it flies out open (2 blocks a tick) to the chest, lever, door,
  item frame or item in sight; only when it gets there does the chest open or the item come away, and it closes on it,
  then flies home carrying the item with it and fades at your side.
- **Catch**: while the catch lasts an open hand hovers ahead of you instead of the arms; each shot snatched (cast or the
  spirit's own catch), a hand closes on it where it was caught and fades.
- Compile-checked only, not seen in game.
