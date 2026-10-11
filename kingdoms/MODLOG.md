# Jaymo's Kingdoms: MODLOG

An original mod (own code, art and buildings) for Minecraft 26.3 (Fabric). Ideas inspired by Tale of Kingdoms, but
nothing taken from it (its code is GPL-3.0 and its assets all rights reserved).
Plan and the user's answers: `docs/Kingdoms-Plan.md` (0.3: `docs/Kingdoms-Plan-0.3.md`).

## Testing
`run/` holds an offline dev server (port 25597, RCON 25598, password `kingdomsdev`) and ops.json for the dev
client's `Tester`. Start `runServer --args="nogui"`, then `runClient --args="--username Tester --quickPlayMultiplayer
localhost:25597"`, and drive it with RCON.

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
