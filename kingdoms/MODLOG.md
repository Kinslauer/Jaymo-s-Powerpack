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
