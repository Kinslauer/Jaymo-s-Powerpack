# Village mods plan

Andy's 61 picks (2026-11-19) from the pick list built off twelve village mods he linked: Millénaire, Village Names,
Recurrent Complex, Bountiful, Guard Villagers, Villager Hats, Tidal Towns, Sky Villages, Cherry Grove Villages, Ribbits,
ChoiceTheorem's Overhauled Village (CTOV) and Recruits.

Pick list: https://claude.ai/artifact/Q6keqE2GsoYjxN6Sh64dwa (picks came by screenshot). Row ids are in brackets.

Shipped as they are: CTOV (26.3 Fabric build 4.2.2a) and Tidal Towns (26.3 build 2.0, ARR with packs welcome). Every
other mod is ideas only: our own code, names and art. Villager Hats is MIT, so its code may be ported with the notice kept;
the hat looks are drawn from vanilla's own villager textures. Guard Villagers' assets are ARR and bar AI ports, so
nothing of its art or data is used.

Left out by Andy: golem floating [gv_golem_float] (replaced by better golem pathing, below), the hall of makers
[mil_hall], shipping Millénaire [mil_ship], Cherry Grove's datapack and its pieces [cg_ship, cg_pieces], and shipping
Ribbits [rb_ship]. Shipping Bountiful [bt_ship] showed ticked by mistake (Andy: no); it has no 26.3 build and would put a second board in every
village, so it is not in any patch.

Sky Villages: already ours (PR #1312). Andy's own follow-up became the steam towns in the Cloud Sea (PR #1790, merged).

Every patch: tuning numbers as `static final` constants at the top of its class, player text in a lang part, a change
note per mod in `changes/<mod>/village-mods-<patch>.md`, never `version=`/MODLOG/codemap. Check every Minecraft API
against the 26.3 sources. Lore stays oblique (no screen text stating lore).

---

## Patch 1: Illager names (Kingdoms)
- **Camp names in their tongue** [vn_tongue_names]: a name pool in `IllagerTongue` from the same syllables as illager
  personal names; `IllagerCamps` uses it instead of `Camps.name`. Existing camps keep their names.
- **Names for every hold** [vn_hold_names]: outposts, mansions and the old holds get a name on first approach, kept in a
  saved map of structure start to name (`IllagerSites`, `SharedPools`).
- **CTOV outposts are holds** [ct_outposts]: its 14 outpost ids join `SharedPools.OUTPOSTS` and the tongue's hold list.
  Needs Patch 7 to matter.
- **Rank by size** [vn_size_words]: lookout, warband camp, host, war seat, in illager words, on the entering message.
- **Scrawl until learned** [vn_unreadable]: tongue points decide whether the name reads clearly.
- **Standard at the gate** [vn_standard]: a tall ominous-banner standard with a carved board; sneak-use to read.
- **Warlord titles** [vn_warlord_title]: "Grekath of Vrakthul" on the boss bar, bounties and villager talk.
- **Muster rolls** [vn_muster]: an extra ledger page with camp name, size, warlord and next target town.
- **Villagers point the way** [vn_hints]: a Rumours/AskTopics line naming the nearest camp and its rough bearing.
- **Waymarks** [vn_waystone]: rare carved waymarks on roads near their holds, naming the hold and distance.
- **Reaped hosts named** [vn_reaped]: `ReapedHost` carries the nearest fallen camp's name.

## Patch 2: Blast mending (Kingdoms)
- **Builders mend blast holes** [gv_blast_mend]: `ServerExplosionMixin` notes blocks broken on settlement land; `Upkeep`
  sends one builder per crew by day to put them back from the storehouse.
- **Only monster damage** [gv_blast_owner]: blasts a player or siege caused stay unless the town paid; `SiegeScars` keeps
  sieges.
- **Craters heal in the wild** [gv_blast_wild]: a `Weathering` step on blast-marked chunks puts dirt, then grass and
  flowers, back over a few days. Stone and ores never.

## Patch 3: Guards that fight and mend (Kingdoms)
- **Cleric heals** [gv_cleric_heal]: the town healer throws healing splashes on badly hurt guards and villagers, a few a
  day (`FieldMedics`).
- **Smith mends gear** [gv_smith_mend]: armourers and weaponsmiths fix half-worn guard gear and patch golems, paid in
  storehouse iron (`WornGear`, `ShieldShatter`).
- **Guards eat and drink** [gv_eat]: a `Comrades` step; storehouse food; villagers hand spare food to guards.
- **Kick and sidearm** [gv_kick]: rushed archers kick (`Shoves`) and fight with a sidearm (`RangedTactics`).
- **Ring the bell** [gv_bell]: ringing a town bell during an attack calls its guards; false alarms annoy the town.
- **Raiders kill livestock** [gv_raid_beasts]: `Raids` target picks include town animals.
- **Witches go after villagers** [gv_witches]: vanilla witches throw harming potions at villagers; guards answer.
- **Golem pathing** (Andy's note on gv_golem_float, "just give them better pathing and the ability to jump up and gain
  altitude in water"): iron golems (`TownGolem` and vanilla) path around water, step up ledges, and rise in water by
  jumping instead of walking the riverbed.

## Patch 4: Board work (Kingdoms)
- **Fair pay** [bt_worth]: an item worth table in `Economy` from recipe and rarity; `TownNotices` and `BoardWork` match
  pay to the ask.
- **Decrees** [bt_decrees]: a decree item (smiths', hunters', farmers', war), up to three pinned per board, steering what
  gets posted; rulers pick from `Upkeep` shortfalls; players can earn one.
- **Deadlines** [bt_deadline]: some notices carry a time, an early bonus, and a small reputation loss when missed; the
  paper yellows.
- **Mixed asks** [bt_mixed]: more than one objective and reward per quest.
- **Asks that fit the land** [bt_local]: biome lists on top of the per-type lists.
- **Rarer notices look rarer** [bt_paper]: plain, sealed vellum, gilt, by `BoardWork` stars.
- **Progress on the notice** [bt_tracker]: held notice shows the count; a toast on completion.
- **Hand in at the board** [bt_turn_in]: drop goods into the board to collect pay when the poster is away.

## Patch 5: Town life and trade hats (Kingdoms, Powers for the hats)
- **A town builds you a house** [mil_gift_house]: offered once at top reputation (`Commissions`, `Homesteads`).
- **What the town is doing** [mil_panel]: a live page on the town board from `TownLedger`, `Upkeep`, `TownProjects`.
- **Goods each people is known for** [mil_signature]: per-culture `TownTrades` offers unlocked by trade done; Kitchen
  makes the foods.
- **The wandering pilgrim** [mil_pilgrim]: a `Tale` chain over the old age temples, hints only (lore section "The old
  age's peoples", already canon).
- **Trade hats** [vh_hats]: Baubles HEAD slot hats ported from Villager Hats (MIT, notice kept).
- **Where hats come from** [vh_drops]: zombie villager drops and Tailors trades, no grid recipe.
- **Your job's hat counts** [vh_job]: `Careers` gives a small friendliness bonus for wearing your trade's hat.

## Patch 6: Small structures (Kingdoms)
- **Tree structures** [rc_trees]: hermit's hollow oak, bandit lookout in a great spruce, a hanging tree with a crow.
- **Mazes** [rc_maze]: a hedge maze in castle gardens and a changing stone maze under a necropolis.
- **Wayside inns** [rc_inn]: halfway along King's Roads, with innkeeper, stable, board.
- **Cairn caches** [rc_cairn]: stone piles with buried chests; `Rumours` names the nearest.
- **Cave gardens** [rc_flora]: rare tended caves; a Druid may live in the biggest.
- **Design preview** [rc_preview]: a client-side ghost outline on `MasonTable` plots.

## Patch 7: CTOV back, Tidal Towns in (Kingdoms, pack)
- **CTOV** [ct_ship]: packMods line for 4.2.2a and off `removedIdsFromProfile`; `CtovHalls` and `Havens.ctov` wake again.
  Check its village pieces load in 26.3 before merging (an old build once stopped the server).
- **Tidal Towns** [tt_ship]: packMods line, credit on the pack page.
- **Tideborn live there** [tt_tideborn]: founded as a village faction at its bell; births lean Tideborn (heritage line
  already canon).
- **Part of the sea's life** [tt_sea]: `SeaTrade`/`TradeRoutes` stops, a pirate raid target, fishers' routines.

## Patch 8: Swamp goblins (Kingdoms, Powers for blocks)
- **Stilt villages** [rb_swamp]: a swamp layout in `GoblinVillages`/`GoblinBuildings` on stilts with plank walks.
- **Swamp blocks** [rb_blocks]: toadstool caps and stems, swamp lantern, mossy oak planks, big lily pad, umbrella leaf;
  our own art, `#variable` textures.
- **Goblin bands** [rb_band]: evening fire music in `GreenskinLife`, our own sounds; the gourd shaker item.
- **Gardeners** [rb_gardener], **hedge witch** [rb_hedgewitch], **swamp trades** [rb_trades], **homes at night**
  [rb_home].

## Patch 9: Your own company (Kingdoms)
- **Raise a company** [rc2_company]: sign liked villagers on for a daily wage; missed wages cost morale, then they leave.
- **Kinds by kit** [rc2_kinds], **named groups** [rc2_groups], **patrol routes** [rc2_patrol], **supplies**
  [rc2_supply], **ship crews** [rc2_ships], **your company in a realm's war** [rc2_war].

## Lore
No new lore paragraph is expected: illager names, the pilgrim and the Tideborn draw on lines already in Lore.md. If a
patch needs one, it is quoted to Andy in full before it goes in.
