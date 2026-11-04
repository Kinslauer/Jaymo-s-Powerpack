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

Entries before 0.7.498 are in `docs/modlog/kingdoms-0.1-to-0.7.497.md` (grep there for older history). New entries go at the end of this file.

## 0.7.498: coins replace emeralds (pack 0.20.0, Kitchen 0.5.56)
Andy's Custom Trade Currency pick, and his choice "Combine: culture coins replace emeralds". Design: `docs/Coins-Plan.md`
in the pack. The culture coins of the currencies patch (`Currency`, `Currencies`: realm silver, hacksilver, gold dinars,
shell strings, mon, the greenskins' bent copper, pirate gold) are the money everywhere; the emerald is a gem.
- **Copper bit** (`Currency.BIT`, the item `jaymoskingdoms:copper_coin`, shown as "Copper Bit", a tenth of an emerald):
  the one small coin every town takes at par, the change for any price in place of emerald change.
- **What a payer takes** (`Money`): the settlement's own coin at par, bits at par, and any other realm's coin at its going
  rate less `FOREIGN_CUT` (10%, switch `FOREIGN_TAKEN`). Never emeralds. Change comes back in the town's coin and bits.
  Outside any settlement (wild villages, wanderers) the coin is that of a settlement within 160 blocks, else realm silver.
  The trade screen stays strict (a slot needs the exact coin) and fills the slot from purses and bundles
  (`MerchantMenuCoinMixin`, `Coinage.fill`); the money changer has a real job. A price over 64 of the town's coin is asked
  in dinars with the rest in the town's coin.
- **Emeralds as a gem:** the money changer (master's profession) buys emeralds at 90% of their worth in his town's coin
  and sells them at 112%, an emerald block is nine, he buys a diamond at four and no more, and bits trade for the town's
  coin at par. Keepers (15% of villagers, 40% of wandering traders, fixed by who they are, only real traders: `Currencies.keeper`)
  still price in emeralds; a keeper's offers go back to emeralds on a save from before.
- **Coin purse** (`money/`): holds mixed coins, up to 640, and shows how full it is (leather, string, one copper bit).
  A purse from the first dev format (copper, silver, gold) reads as bits, two silver and five dinars. Worn on the belt
  slot of Jaymo's Powers and drawn on the hip. `Money` counts loose coins, the worn purse, purses in the pack and bundles,
  takes the town's coin first, breaks the smallest coin that covers the rest and puts the change back where it came
  from, and lets creative players through. `Purse` is a thin wrapper kept for old callers.
- **Dropped from the first coinage:** the silver and gold coin items, the ten-coin stacks and piles, the Coin Press, the
  Counting Table, our own money changer, `VillagerTradeCoinMixin` and `Coinage.convert` (trades are priced in coin
  when they open, `Currencies.localize`).
- **They talk about it** (`MoneyTalk`). Andy: "they must speak about this and answer you if asked".
  - Interact has Ask about Money. A keeper says so. The money changer gives his emerald rate in the town's coin. Anyone
    else says which coin the town uses and why (the trading hub's name, else the land), and that foreign coin is the
    changer's business.
  - Ask the Way has Who Takes Emeralds and A Money Changer.
  - Unasked: a keeper says it as you open their trades, and a coin trader tells you they don't take emeralds if you come
    with emeralds and no coin. Each at most once every 5 minutes to the same player (speech bubble).
  - MCA's chat AI is told the town's coin, its hub and who nearby still takes emeralds.
  - Andy: "people who use coin generally look down upon emerald traders as outdated luddites". Coin folk sneer at
    them and the keepers know it and don't care.
- **Thieves:** crooks go for a visible belt purse first and may take it whole (`Crooks`, `PURSE_LIFT_CHANCE`); a mugging
  takes a handful of whatever you carry, owed back in the land's coin. Pickpocketing a villager mostly lifts a small
  purse in the victim's own coin or a copper bit.
- **Loot:** `LootSwap` turns emeralds in chest, mob and hero-of-the-village loot into the coin of the land where they
  were found (an emerald's worth each); mined emerald ore still drops emeralds.
- **Wages and rewards:** careers are paid in the town's coin out of the storehouse's coin and bits, with its emeralds and
  other coin held as reserve at worth; pilot fares and prospector bonuses in the coin of the land. Quest rewards are in
  the giver's coin (the old `emeralds` field reads as worth, `copper` as bits). Merchant orders are asked in the town's
  coin (bits where it won't land) and refunded as the coin paid. Life-story start purses are in the birthplace's coin.
  Lockpicking counts coin by worth.
- Realm silver and hacksilver are money, not the metal their names say: vampires aren't hurt by a coin and a werewolf
  doesn't take a coin in the hand for a silver weapon.
- Emeralds stay a gem: ore, enchanting, tinkering's emerald modifier, trims, beacons and caravan gem goods.
- Built, reviewed, and a dev server boots; not played.

## 0.7.500: cold-blooded Serpen don't overheat
- Andy: "the reptile beastfolk should be cold blooded, therefore not overheating in the sun". Anyone with the
  `COLD_BLOODED` perk (the Serpen look of the Beastfolk) is heat-proof to Kitchen's body heat, like an Emberborn: the
  sun, hot lands, armour baking and fires can push them no further than comfortable, so no sweating, overheating or
  heatstroke (`HeritageKits.coldBlooded`, `KitchenCompat`).
- The cold stays their flaw as before (15% lighter blows, 20% slower in the cold lands); body heat's chill is unchanged.
- The perk's card line says so.
- Built, reviewed, and a dev server boots; not played.

## 0.7.501: tails with a mind of their own (pack 0.20.1)
- Andy: "they should pulse and slap like they have their own mind, but they swing violently whenever you walk". Beastfolk
  tails (`BeastParts.tail`: beastfolk, canine, feline, kitsune, avian, serpen) swayed at `age * (rate + walk speed)`, so any
  change of walk speed spun the phase and the tail whipped about. Now every wave runs on a fixed clock: a lazy wander of two
  slow waves, a curl rolling up the far half now and then, and an occasional slap (`slap()`: a slow gate opens for a moment
  and the tail whips quickly to the side, harder toward the tip). Walking calms all of that by half, lifts the tail a little
  (`RUN_LIFT` 0.15, was 0.25; `RUN_STRAIGHTEN` 0.2, was 0.3) and adds only a small swing in step with the stride.
- Feline tails (Andy: "like a cheetah's in sprint and otherwise, like an aileron helping them achieve speed in turning,
  sticking straight up and moving"): carried straight up with the tip hooked over, still wandering, curling and slapping;
  at a sprint (the sprint flag, or over 0.2 blocks a tick for villagers) the tail eases down level out behind and its own
  life fades. Turning swings it: level, sideways like a rudder; upright, leaning across like an aileron. It swings into the
  turn (`TAIL_RUDDER` -3.5; flip the sign to swing it out). Sprint and turn are eased per entity each frame (`EASED`).
- Not compile-checked in the cloud; not played.

## 0.7.502: the Flame Avatars take the Emberborn in (pack 0.20.2)
Andy: "make the flame avatar take you in and amicable to you", with rare Nether rocks and flint in Powers so a Nether start
isn't stuck stealing piglin axes.
- **Taken in** (`nether/AvatarCamps`): the first time an Emberborn comes within 32 blocks of an avatar camp, its nearest
  avatar greets them by speech bubble (`message.jaymoskingdoms.avatar.welcome.0-2`) and every avatar of the camp warms to
  them by 50 hearts (MCA's follow and hire). Saved per player per camp (`avatar_welcomed`, kept through death).
- **The gear only when you speak to them** (Andy): the first right click on one of a camp's avatars gives 3 flint, 4 sticks,
  2 plant string (Powers') and 4 cooked pork with a line (`avatar.gift.0-2`), once per camp (`avatar_gifted`); MCA's menu
  still opens.
- **The hearth:** an Emberborn within the camp's home radius who is hurt gets Regeneration I (11 s, renewed every 10 s).
- **They stand with you:** avatars within 24 blocks of an Emberborn go for any creature hunting them or that hurt them in the
  last 5 s (`AvatarCamps.guarding`; never other players).
- Camps rise near an Emberborn in the Nether at 60% per 3-minute roll (others still 25%).
- **Sticks in the Nether** (`Trees`): wart blocks (nether and warped) torn down by hand give a stick 15% of the time, as
  leaves do in the overworld.
- `McaCompat.hearts` is public now (the camp's warmth).
- Built, reviewed, and a dev server boots; not played.
## 0.7.503: sworn starts are known, every start carries meds
- Andy: "the relevant folk should know you as a sworn start" (his king asked his name like a stranger's). While a Sworn
  start's player stays a member of the realm they swore to (`StoryTalk.swornRealm`), its king, its lords (and its fiefs'
  lords) and its guards (`StoryTalk.serves`) know them: within 24 blocks each comes to know them once (`sworn_known`
  player attachment): you know their name with no introduction, MCA counts you as met, and they start warm (30 hearts for
  a ruler, 20 for a guard, raised to that, never lowered). Every time you open their menu they greet you as their sworn
  man or a fellow guard (`story.jaymoskingdoms.sworn.<ruler|guard>.0-2`), not as a stranger, and not only for the first
  three days. Worlds already played: the Landing a Sworn start saved is enough, so it works there too.
- Andy: "everyone should start with applicable meds. obviously still none for rock bottom, and a whole medkit for sworn".
  `StoryMeds` gives each start Jaymo's Powers' medicine (looked up by id) after its kit: Sworn a healer's satchel packed
  with 4 linen bandages, 2 clean rags, 2 splints, needle and thread, smelling salts, 2 yarrow, 2 knitbone, burn salve,
  garlic poultice, 2 willow-bark tea and poppy syrup. Nothing for rock bottom, a captive, chained, an android or the
  afterlife. Others by their road: banished 2 bandages + a rag, cave 2 bandages + yarrow, village 2 bandages + willow tea,
  whalers 2 bandages + needle, trader 2 bandages + tea + salve, burned 2 salves + a rag, sky fall a splint + 2 rags,
  snowed-in 2 rags + tea, unarmed 2 clean rags, hideout 2 rags + poppy, greenskin 2 rags + garlic, lab one of each
  remedy, sentenced/evicted/cast out 1 rag, the rest 2 rags. A Survivalist weaves 2 grass bandages more. The rags flaw
  still gives nothing.

## 0.7.504: wary folk keep their names; you bow to rulers
- Andy: "someone who has any distaste toward you would not introduce themselves, i was just on a beastfolk, and the guy
  said remember this city isnt for you, but still shook my hand, they should be as apprehensive as the old orcs were until
  you prove yourself". `Introductions.wary`: a villager who minds your blood (`Prejudice.minds`: a haven's folk minding a
  human, a townsman minding a Tideborn or Beastfolk...), whose realm holds you below welcome, or who remembers you coldly
  (`Recollections.COLD`). They don't come over to you, don't take your hand (`takesHand`), give a wary line instead of
  their name (`name_wary.0-4`, orc and goblin dialects too), and at a first meeting step back with that line in place of a
  wave or a shake (`Handshakes.standOff`), then their menu opens with no name in it. Proven: a warm memory of you
  (`Recollections.WARM`: a rescue, a kindness), or enough MCA hearts (gifts, talk): 8 per degree they mind, at most 50
  (`PROVEN_HEARTS_PER_DEGREE`); 20 hearts outweighs a bad name with their realm (`PROVEN_HEARTS_REP`).
- Andy: "introducing yourself to kings, is no longer a wave or handshake, but you bow, using all the relevant animation in
  context. (hand on pommel if wielding a sword)". `Handshakes.bowTo`: the first use of a ruler you haven't met, their
  Introduce, Ask their name and the handshake answer all have you turn to them and bow (`Hand.BOW`, 40 ticks, held still).
  The ruler inclines their head (`Hand.NOD`) and names themselves with their title; a wary ruler doesn't return it.
  `HandshakePose.bow`: bent from the waist as a crouch is, head down; empty handed, the hand over the heart and the other
  swept behind; a sword or light blade in hand, held low at your side with the other hand across on its pommel; anything
  else, held at your side with the free hand over the heart; an off hand holding something stays at your side. Mirrored
  for the left-handed; MCA bodies' clothes follow (`McaPullerPose.follow`).
- Not compile-checked against the game jars (none staged in this cloud session); a source-path javac pass showed no
  errors in our own symbols. Not played.

## 0.7.505: attack tells are easy to see and hear (pack 0.20.4)
Andy: "the particle effect for attack indicators should be more visible, and a sound should play like for time flow".
- `MobCombat.tell` (fighters' and crewmen's swings) and the gunners' aim tell (`MobCombat.aimed`) call Powers' new glint
  through `PowersCompat.tellGlint`: a full-bright star in the tell's colour (white parry, red heavy) with a white heart, and
  Time Flow's warning bell (high for parryable, low with a drum for heavy). Without Powers the old dust stays and a bell
  plays with it. The gun's flint click stays on top.

## 0.7.506: builds again (pack 0.20.5)

- A merge left `client/HandshakePose` with two `NOD` constants (the nod pose and the head tilt while shaking hands), so
  the client didn't compile. The head tilt is now `SHAKE_NOD`; nothing changes in game.

## 0.7.508: gem sockets loading hint (pack 0.20.7)
- Loading-screen hint 86 explains gem sockets at the Smithing Anvil.
- Not played.

## 0.7.509: patron gods, part (a) (pack 0.20.8, Kitchen 0.5.57)
Andy picked the Runic Gods idea (our own version, nothing taken from it). `docs/Patrons-Plan.md`.
- **Eight patrons** (`Patron`): the Hearthkeeper, the Anvil-Father, the Deep Mother, the Green Huntsman, the Lantern
  Warden, the Crowned Sun, the Ash Whisperer, and The Truth, the human faith (Andy: "it usually is the faith among
  humans, you will rarely find one who entertains another creed"). Rivals: the Sun and the Ash Whisperer, the Mother and
  the Anvil-Father, the Lantern Warden and the vampires' dark; The Truth and the seven.
- **Shrines** (`Shrine`): use a patron's offering on a plain shrine to give it to them (a glyph shows on the altar).
  Hold use to pray as before. Sneak and use with an empty hand for the patrons screen.
- **Swearing** (`Patrons`): pray at a patron's shrine three days running. A devout life story starts sworn: The Truth
  for a human, their blood's patron for anyone else.
- **Favour**: prayer, a fitting offering once a day, their kind of deeds; four tiers (10, 30, 55, 80 of 100); after two
  days without any it slips 5 a day (the new Survival perk **Devotion** takes a point off a rank), never below the
  first tier for now.
- **Blessings** (`Blessings`): a passive at tiers 1-3 and a boon at tier 4 (the left square bracket key, every three days), each
  hooked into what was there: Wounds' healing and revives, the Smithing Anvil, tool wear, ore drops, breath, storms,
  great whales, sneaking, Kitchen's meat hooks, legend hunts, Night Vision, blows on the undead, the Nightfall plague,
  reputation, guards, Valor, war parties, pickpocketing, locks, fire, the guards' hunt, accusations, spells.
- **Faiths** (`Faiths`): every villager and townsperson holds a faith, drawn once: most humans The Truth, clerics always,
  Faith-creed realms' folk always; non-human folk mostly their blood's patron. Truth and the seven, and rival pairs,
  think less of each other: a little dearer, slower to warm, more guard trouble, cooler neighbours and good name, on
  top of blood prejudice. MCA's chat AI is told.
- The patrons' books (lore) are left on a shrine the first time you meet its patron.
- Loading hints 87-88 (86 is gem sockets).
- Built and fixed up (below); not played.

### Fixes to the patrons
- Rich Seam and Father's Fire, and the deep-ore deed, need a tool that could harvest the ore.
- The Crowned Sun's siege deed is paid once, and only for a party that sacked a realm.
- An MCA villager keeps its faith across loads; it is drawn again only when its blood changes.
- A road told after the creator starts the patron story again (a devout road starts sworn).
- A true accusation of one already exposed earns no more favour or name.
- The outlaw message shows the days after Short Memories.
- The patrons button no longer covers the Valor button. The boon key is no longer Z (see the last fix below).

### More patron fixes
- The boon key is now the left square bracket. The full stop is Shoulder Surfing's swap shoulder in the pack.
- On the Strength page the footer buttons no longer overlap: Patrons sits right of Valor, and World Tier is clear.
- Dawnlight spares thralls and your realm's folk.
- Andy: Dawnborn "start crowned sun" and "say praise the sun". A devout Dawnborn starts sworn to the Crowned Sun, and most
  Dawnborn villagers hold it (`Faiths.DAWNBORN_SUN_SHARE`). At sunrise some of the Sun's folk near a player praise the sun
  aloud (speech bubbles), and Dawnborn have two more lines of talk about it.

## Patron fixes
- **Realm census** (`RealmFaiths`): each realm now keeps its own last count day. A realm whose seat was not loaded at the
  first scan of the day is counted when it loads, not skipped until tomorrow.
- **Cold shrine** (`Shrine`): the "shrine is cold to you" line goes before the prayer, so the turning progress and the
  turned line show last and are not overwritten.
- **Cleric coldness** (`Faiths.minds`): the cleric's extra coldness applies to players of the seven only, not villagers.
- **Leaving Rite** (`Turning`, `Rites`): the rite is offered for the longest live trial, and paying eases every live full
  trial of that patron, so a second longer trial no longer stays at full strength.

## 0.7.510: chopping cracks logs instead of thinning them (pack 0.20.10)
Andy asked whether Timber Tales' chopping could replace our thinning logs. Its licence allows no derivatives, so this
is our own take on the idea (none of its code or art).
- `ChoppedLog` keeps the whole log's size and shape now. The game's own crack stages are drawn on it, deeper with each
  chop (6 chops reach the deepest), and deeper still while an axe is mid-swing. Chops past 6 still go into the log above.
- Each chop throws wood chips off the face toward the chopper (players and woodcutters).
- A chop meter on the action bar (filled/empty squares, up to 12) shows how near the tree is to coming down. When it's
  full and red, the next stroke fells it. It uses squares, not the `|...` charge bar, so it never locks the mouse.
- Felling, the fall, the crack and crush, Aether Giants, the Great Ape and Towerback trampling trees, and tornadoes are
  unchanged (none of them go through `Trees.chop`).
- Not played; compile not checked against the game jars.

## 0.7.512: pink fairies are prized (pack 0.20.12)
- **FairyWonder:** Jaymo's Powers' Bottled Pink Fairy (a super rare bauble) is the rarest thing townsfolk ever see.
  Villagers (vanilla and MCA) within 8 blocks who see a player wearing one (amulet or charm slot, via
  `PowersCompat.baubles`) or holding one stop, stare and say so in a gold bubble (8 grown lines of longing, envy and old
  tales, 4 for children who beg to hold her), once a day each per player, one at a time; an MCA villager's mood rises 3.
- **MCA gift:** `data/mca/gifts/fairy.json` makes her the dearest gift there is (60; a nether star is 40), +20 for
  children, +15 for the married.

## 0.7.513: patron gods, part (b): turning away, clerics, realms (pack 0.20.14)
`docs/Patrons-B-Plan.md`. Andy: turning away should be harsh, "the harsher the better for this pack".
- **Turning away** (`Turning`): pray three days at another patron's shrine while sworn and you turn. Each of those days
  costs your patron 5 favour. The old patron leaves a **scar**, a small penalty in its domain that never fades and
  stacks with each leaving. Then come the **trials**, 7 days (gamerule `jaymoskingdoms:patron_trial_days`, 0 for none;
  `jaymoskingdoms:patron_scars` false for no scar): the old patron's omens follow you. Hearthkeeper: wounds heal slow.
  Anvil-Father: tools wear faster and smithing slips. Deep Mother: storms find you, bolts fall near you, the water pulls
  you under. Green Huntsman: wild beasts turn on you. Lantern Warden: the dead come for you at night. Crowned Sun: guards
  halt and search you more and good name comes slower. Ash Whisperer: witnesses see farther, guards hunt you longer.
  The Truth: townsfolk shun you and clerics call you out. The old faith's people mind you hard and its shrines are cold.
  The new patron's favour starts at 2 and can fall to nothing during the trials. A mark is left for part (c)'s champion hunt.
- **Clerics** (`Sermons`, `Rites`): clerics preach The Truth against the seven. Evening sermons on the square of Truth
  realms and villages (a bell, then speech bubbles), words at shrines given to one of the seven and to those sworn to
  them, apostates denounced. They are colder to the seven's sworn. The **Leaving Rite** (a cleric's Interact page) halves
  the trials and dulls the omens, during a turning or after it. It costs 8 emeralds' worth in the town's coin, 4 more a
  scar, double to leave The Truth and half to come to it. It never takes a scar.
- **Realms** (`RealmFaiths`): Faith-creed realms hold The Truth. Others hold the patron most of their folk do (counted
  daily). A haven holds its blood's patron. The ruler's info shows it. Folk who share your patron like you more (good name
  won 15% faster, lost 15% slower, now and then a heart more). The faith you left and its rivals like you less.
- **Holy wars** (`GreatWars`): realms of rival patrons grow tense each day (a named rival pair faster, more under the
  Faith creed). A war between them is a holy war: always a great war, and realms of the same faith rally to it.
- Screens: the patrons screen shows your scars and trials. The rite has its own screen. Loading hints 89-90.
- Powers 0.20.14: the Hearthkeeper's trial and scar slow every part heal.
- Built, reviewed, and a dev server boots; not played.

## Patron champion fixes
- Champions no longer freeze: the cast, turn and sleep timers now run from the entity's own tick, so wind-ups end, phase turns finish, sleeping champions wake near a Wildlands player, and a champion can be killed again. The Skeleton Lord got the same fix.
- A `/kill` and other invulnerability-bypassing damage now work on a sleeping or turning champion.
- The patron weapon a champion holds is for show only, so its blow stays the tuned number.
- A temple champion only counts players inside its leash. An archer beyond it no longer holds off the rest heal and reset.
- Adds give no experience and no loot.
- The hunt ends when the quarry dies, even if the player respawns at once.
- Pulls and pushes now reach the moved player's own client (Undertow, Chain of Truth).
- The great temple's doorway is on the north side like every blueprint, and the Inquisitor's altar is found at its real height.
- Temple sites check every chunk under the footprint for a realm's land. The forge-hall tunnel keeps its far wall. The ash-hollow ladders reach down to its floor.
- Town folk no longer fight, flee or fear a patron's champion, and never count as its foes. A champion no longer turns on its own adds.
- A reset champion goes back to its champion spot, facing the way out. The ruined cathedral's champion stands before the altar. The deep forge-hall's tunnel now ends in a ladder shaft to the surface.
- The Forge-Thane's slag cubes no longer split into plain cubes. Summoned and egg champions roll a head too. The smoke double's life and the foe memory are constants.
- The forge-hall's ladder shaft opens into its tunnel. The forge-hall's tunnel and shaft stay off realm land. A rebuilt great temple keeps its one Inquisitor. Champion wind-ups and turns last their full time. Iron Skin wears a weapon only for blows that land.
- The town's guards, royals and casters no longer see a sleeping or fighting champion, or its adds, as a foe. Champion max health can now grow past 1024 with the group and tier. Blood Grip and the Unseen Hand leave a champion's weapon alone. Adds that reload from a save are discarded. The smoke double's blind and the foe ranges are constants.

## 0.7.514: the skill web (pack 0.20.15)
- Andy: "the skill trees need a redesign, something more akin to skyrim or path of exile, where the tree branches far out,
  and you can easily see where what you pick lands. It should also allow you to more easily branch into other trees for
  builds ... a large circle with three quadrants for stealth survival and strength." He picked bridge perks on the borders.
- **K screen** (`SkillScreen`, layout in `client/SkillWeb`): the three pillar tabs and their pages are one web. You sit in the
  middle; Strength takes the top, Survival the lower right, Stealth the lower left. Every old page is a branch running
  outward (Will with Killing Intent past it, Combat Arts, Might; Foraging, Medicine; Freerunning, Thievery, Shadows), and a
  perk's ring is how deep it sits along its requirements. Drag to move, scroll to zoom about the mouse, Recentre fits it all.
  Pointing at a perk lights its path back to the middle in white and the card says what it needs and what it leads to;
  pointing at a pillar's crest shows what that pillar levels on. Perks are learnt on click-release (a drag never learns one).
  Names show on every perk from 1.5x zoom. Attributes and Jobs stay as tabs with their panels.
- Nothing about the old perks changed: ranks, costs, levels, requirements and per-pillar points are as before, saves untouched.
- **Bridge perks** (`Bridges`, `Skills.Perk.other`), two per border, opened by a rank in any one of the two perks named
  (one each side), paid from whichever side has more points (saved as `<perk>@<pillar>` in the ranks map, so each side's
  points count what it paid; `Skills.forget` undoes them when `/skills set` lowers a pillar):
  - Stealth/Strength: **Ambusher** (Sneak Attack or Grim Hand; 1 pt): a sneak attack leaves its mark Slowness III and
    Weakness for 3 s. **Cutthroat** (Assassinate or Last Look; 2 pts): a kill within 1 s of a sneak attack or a hidden
    blade assassination gives 4 s of Invisibility and Speed.
  - Strength/Survival: **Iron Hide** (Bulldozer or Rally; 1 pt): +2 armour, +1 toughness. **Beast of Burden** (Packhorse or
    Iron Stomach; 2 pts): +1 heart, hunger a fifth slower.
  - Survival/Stealth: **Tracker** (Field Dresser or Climber; 1 pt): anything you wound (not players) bleeds 30 s, a drop of its own blood every 0.75 s (Powers' blood trail, marked as its, `PowersCompat.trailDrop`), so you can follow it (Andy: "they just bleed so you can track them"). **Light
    Pack** (Plague Doctor or Bar Swing; 2 pts): sneaking and sprinting cost 40% less hunger.
- Mock-ups: project files `art-overhaul/skill-web/`. Kingdoms compiled with Gradle (compileClientJava); not played.

## 0.7.515: RPG Series class gates (pack 0.20.16)
- Andy shipped Zsolt Molnar's Wizards, Archers and Rogues & Warriors (Powers' packMods, PR #919). `RpgSeriesGate` answers
  Spell Engine's cast check (`SpellEvents.CASTING_ATTEMPT.PRE`, both sides) so only the matching class casts their spells:
  Wizards = Mage; Archers = Archer and Marksman; the rogue book (Slice and Dice, Shock Powder, Shadow Step, Vanish,
  Mutilate, Bear Trap) = Thief; the warrior book (Shout, Charge, Last Stand, Mortal Strike, Throw, Throw Net) = Warlord,
  Brute and Rider; the Survivalist also sets the Bear Trap and throws the net. Creative players cast anything. A refused
  cast shows "Only a Mage can cast this" on the action bar (every 2 s at most). Weapons and armour stay usable by all.
  Other mods' spells are untouched. Compiled against Spell Engine (compileOnly), never required. Not played.

## 0.7.516: RPG Series spells and forms on the skill web (pack 0.20.16)
- Andy: spells and moves come only from perks or a trainer. 49 perks appended to `Skills.Perk` (no ordinals moved), in six
  new branches on the web: **Archery** and **Spellcraft** (arcane, fire, frost chains) in Survival, **Rogue's Arts** and
  **Knife Arts** in Stealth, **Warfare** and **Weapon Forms** in Strength. Late branches start at ring 3 (`SkillWeb`).
- `Learnables` maps each perk to its Spell Engine spell or Weapon Form. A class spell perk is locked to its classes (hover
  shows "For: ..."); Weapon Forms are open to all. Buying a form perk teaches the form; forms learned before are marked
  taught. Spell Engine reads learned spells from a new source (`RpgSeriesGate`), and the gate refuses spells not learned.
  Tier 0-1 weapon spells (Arcane Bolt, Fireball, Frost Shard and so on) stay free with the weapon.
- **Trainers:** veteran drills (`GuardDrills`) and wizard trials (`WizardTrials`, Mage only) teach the next perk free
  (`@taught` rank: no points, survives a respec). Spell books and the binding table teach nothing. Not played.

## 0.7.517: Brute rage and Enchanted Storage (pack 0.20.16)
- **Brute rage** (`BruteRage`, Andy; idea from Warrior Rage, all rights reserved, nothing taken): melee blows given (+8%)
  and taken (+10%) and kills (+25%) fill a meter shown as a thin bar right of the hotbar (`client/BruteRageHud`); it drains
  5 s after the fight. Full, the Brute roars and rages 12 s: blows +25% and set foes alight 2 s, +0.5 knockback resistance,
  flames round them; each kill adds 3 s (20 s at most). Then they are spent: Slowness and Mining Fatigue 6 s. The class
  card lists "Rage +25%".
- **Enchanted Storage** (`StorageEnchanting`, Andy: wizards enchant storage high on the tree and gain a bag of holding): a
  3-rank Mage perk atop Spellcraft's arcane column (tier 18, after Arcane Evocation). Sneak-use an enchanting table holding
  a chest (rank 1: leather Bag of Holding, 3 levels + 2 lapis), a leather bag (rank 2: iron, 6 + 4) or an iron bag
  (rank 3: golden, 10 + 6); bags keep their contents. Bag of Holding (Fuzs, MPL-2.0) ships in Powers' packMods with its
  grid recipes dropped. Not played.
- **Spirit charms** (`spirits/SpiritCharms`, Andy: The Four Houses' spells as "spellcraft perks or moves for the ki
  spirits"; MIT, ideas only). Disarming and a shield were already the spirits' (Thessaly's Unseen Hand, Ambrose's Aegis),
  so three new moves: **Petrify** (the Quiet One, level 4: the foe in sight turns to stone 4 s, rooted and weak, dust
  falling; no bosses), **Guardian Light** (Ambrose, level 5: undead within 12 blocks take 6, burn 4 s, are thrown back),
  **Turn Back** (Thessaly, level 6: 5 s of arrows and bolts flying at you sent back, now yours). Existing poses. Not played.

## 0.7.518: patron gods, part (c): temples, champions, the patrons' weapons (pack 0.20.18)
`docs/Patrons-C-Plan.md`. Andy: champions are group fights, "only the strongest can fight alone".
- **Temples** (`Temples`, `TempleSites`, `TempleBuildings`): once a player is in the Wildlands (or higher), now and then
  a patron's temple rises out of sight in a place that fits it: the Hearthkeeper's hearth-hall in open country, the
  Anvil-Father's forge-hall carved deep in the stone or in the Nether's wastes, the Deep Mother's tide-court on pillars by
  the sea, the Green Huntsman's glade in deep forest, the Lantern Warden's graveyard and crypt (or a vault in the soul
  valleys), the Crowned Sun's sun-court on high ground or in the Aether, the Ash Whisperer's hollow (hidden under dry
  country, or in the basalt deltas), and the Truth's ruined cathedral. A Faith-creed kingdom's capital builds the Truth's
  **great temple** (`GreatTemple`). Each altar is the patron's shrine; coming near teaches you the patron.
- **Champions** (`PatronChampion`, `PatronChampions`, `champions/`): each temple's champion sleeps at its altar as a
  statue and wakes for a Wildlands player (the great temple's only when struck). Group fights: health and blows grow
  with every player near (up to six) and with the world tier; a group of three or more brings a fourth phase. Three moves
  each, opened by the phases, all telegraphed: the Hearth-Warden, the Forge-Thane, the Tide-Bride, the Antlered Hunter,
  the Grave-Lamp, the Sun-Marshal, the Cinder-Shade and the Inquisitor. A boss bar, the posture bar, great-foe credit. It
  leashes to its temple, resets when everyone leaves, and stands again three days after it falls.
- **The patrons' weapons**: a fallen champion drops its patron's iron head for each fighter, at the latest on their
  third kill (34%, 50%, sure), Perfect (a solo kill's a Masterwork). Put together at the Tool Station (Powers 0.20.18).
- **The hunt** (part (b)'s mark): after the trials, the patron you left sends its champion once, in a weaker hunting
  form, outside any town. Kill it (the new patron is pleased) or fall to it, and the hunt is over; outrun it and it comes
  again.
- Loading hints 91-92.
- Built (both mods compile); not played.

## 0.7.519: diplomacy and elections (pack 0.20.19)
Otectus pick 10 (Andy chose MCA: Kingdoms' idea; our own version, none of its code or art). Plan: `docs/Diplomacy-Plan.md`.
- **Treaties:** free realms sign four kinds, each for a while and renewable (`Treaties`):
  - **Recognition** (30 days): tension eases 3 a day, and they don't fall into rivalry. Every other kind needs it.
  - **Safe passage** (10): the partner's people aren't halted, searched or tolled by the guards, and the ruler's
    retinue lets them by.
  - **Trade charter** (15): partner caravans go more often (60% of the wait), take an extra cart and come home 20
    points fuller, and are preferred. The partner's people get 10% off at traders and pay half the gate toll.
  - **Aid** (20): when either goes to war, the other is pledged to its side like a great war's ally (its soldiers join
    the war parties, it shares spoils and reparations). Already at war elsewhere, it sends up to 100 from its treasury.
  Vassals share their overlord's treaties.
- **Envoys** (`Envoys`): NPC kingdoms send envoys once a day by creed (Coin wants charters, Conquest aid...). Courts
  weigh offers by temper, creed, friction, tension, rivalry, a truce, a common foe, blood (purists) and, for a player
  realm, your reputation. Envoys ride the King's Roads like couriers, with the realm's banner; you can meet them. Kill
  one and the sender (-200) and the land's realm (-60) remember. Envoys are never drafted as guards or rulers.
- **Players' realms:** an envoy waits up to two days in your hall and you get a letter. "Hear the Envoy" (MCA Interact
  page; right-click without MCA) offers Sign, Refuse or Not yet. Seek an audience with your own steward for the Ruler's
  Desk: the realms in reach, their creed, relation and tension, the treaties in force (Renounce), each kind's odds, and
  Send an Envoy (20 from the treasury, the rest from your purse).
- **Breaking faith:** declaring war on a partner, or renouncing, tears treaties up: +25 tension, +8 with the breaker's
  other partners. A player sovereign loses 150 reputation with the wronged realm and 30 with each other partner. NPC
  tyrants and warlords may break faith when tension boils over; others hold their hand.
- **News:** the town boards carry every treaty; the Tidings only aid, a player realm's treaties, great charters,
  broken faith and war.
- **Succession** (`Elections`, MCA realms): Coin realms elect, and Warriorborn/Frostborn realms of the Old Blood or
  Conquest acclaim at a moot. Notables are the grown townsfolk (profession level, guard service, kin to the late
  ruler, a gift, temper, luck) and sworn players of Knight rank or higher; a player who wins wears the crown with the
  old steward. In other realms blood inherits, but a child heir gets a regent (the best councillor) until they come of
  age; then the regent steps down. If the child dies, the regent rules on. A teen is never crowned outright now.
- **Ruler info** (MCA interact screen): how the realm chooses its rulers, how this one came to the throne, "Regent for
  ...", and its treaties (hover for the list).
- Loading hint 93 (89-92 belong to other open PRs and show raw until those merge). Not played; compile-checked with
  `kingdoms/gradlew build`.

### Fixes: desk, aid coin, wording
- The Ruler's Desk now stays open: seeking an audience with your own steward no longer opens the quest screen over it
  (`KingdomEvents.audience` reports that the desk opened; `Rulers.audience` and `Folk` skip `Quests.open`).
- Renewing a treaty of aid no longer charges an ally's coin again; it is paid once, when the aid is first signed or the
  war begins.
- The aid-in-coin board line and the Ruler's Desk treasury and envoy cost now name the realm's coin (the Desk packet
  carries the worded amounts).
- Election score: the reputation divisor and cap are now constants (`PLAYER_REP_DIV`, `PLAYER_REP_MOST`).
- Ruler's Desk: a full road or a closed kind now says so, instead of "your envoy is on the road".
- Player-crowned realms no longer send envoys or sign treaties on their own in the daily round.
- A ward who comes of age at the regent's death is now announced on the board.
- A chartered allied pair no longer loses the trade friction twice.

## 0.7.520: RPG Series kit for starts, guards, archers and wizards (patch 3)
- `RpgKit` looks up the Wizards, Archers and Rogues & Warriors items (old gear stays without them).
- **Class starts** (`Classes.gear`): Thief + flint dagger, rogue's tunic and boots; Warlord warrior's chest; Mage the
  wand of their school (warding = frost, mending = novice) and the wizard's hat and robe; Archer a small quiver and
  archer's hood and boots; Brute berserker's chest; Marksman + rapid crossbow and 16 arrows, archer's tunic.
- **Guards** (`Outfits.guard`): veterans carry an iron glaive (30%, with their shield) or an iron double axe (15%,
  two-handed). **Archers**: in temperate lands the Archers' hood (40%) and tunic (50%) instead of the dyed leathers.
- **Court wizards and hermits** wear the Wizards' pointed hat over the realm's robes; a slain court wizard drops a
  school's wand 35% of the time, besides the staff. Not played.

## 0.7.521: ChoiceTheorem's villages: trade buildings and havens
- **Trade buildings from CTOV** (`CtovHalls`): ChoiceTheorem's Overhauled Village (CC BY-NC-ND 4.0, shipped unchanged in
  the pack) has a building for every trade in every kind of land. A town's butcher, fishery, hunting lodge, smithy and
  mason are now CTOV's own for its land (`ctov:village/<land>/jobsite/butcher|fishing_hut|hunter|smith|mason`), read
  whole from its structure file in the game and put up block by block by the builders like any design. Its jigsaws
  become the blocks they're meant to, its street entrance turns to our door side (north), and containers come empty.
  A realm favours CTOV's fortified towns and a village the plain ones (`CtovHalls.lands`). Ours stay among the cherry
  trees (Blossom), in havens other than CTOV villages' own, on every second staking try (so a tight site still takes our smaller one), when CTOV has
  no building there, and on the plain design. Only variants staked from now on carry the CTOV mark (top 16 bits
  `0xC70A`, `Planner.variant`), so a half-built or standing hall from before keeps its look.
- **CTOV villages are havens** (`Havens.ctov`). Andy: "those should function like the havens, there should rarely be
  a human there and if there is theres a REASON." A settlement founded in one of its villages (`ctov:*`) is always a
  haven, of the land's blood or mixed (never a purist's). About one human in 25 standing there stays human
  (`Havens.keptHuman`, by who they are), and says why when asked their name: wed in, cast out of the human towns, a
  foundling, its healer, working off a debt, or a deserter it hid. Compile-checked; not played.

## 0.7.522: Stegonaut, Atroxiia, Varasuchus, Ignivorus and Aetherion, the first of the lesser dragons (pack 0.20.24)
Andy: "we NEED these dragons" (Saint's Dragons). It is a 1.20.1 mod whose code is MIT and whose art is All Rights
Reserved, so its eight dragons are rebuilt here with our own models and paint (`docs/SaintsDragons-Plan.md`, patches 1
and 2 of 5; the MIT notice is in `licenses/saints-dragons-MIT.txt`).
- **Walkers** (`WindDragon.flies`): a dragon that never takes off. Wild it strolls and grazes about its home, fights on
  its feet where it stands, and bolting it lumbers off; tamed and saddled it's ridden like a horse, and R sends its own
  move. New hooks for the lesser dragons: `aggressive` (a grazer fights only whoever hurts it), `bossBar` (beasts, not
  minibosses), `windDragon` (killing one doesn't count toward Eurus and Aeolus's disguises), `crashKey`/`boltKey`,
  `walkerFight`, and `action` (the move its model is playing, with `actionSeen`/`actionAge` on the client).
- **Stegonaut** (`StegonautDragon`, `jaymoskingdoms:stegonaut_dragon`): a tall plated dragon (2.8 x 3.4 blocks, its back
  about 2.9 high and its plates over 4) of the jungles and
  plains (`has_stegonaut_dragon`), spawning with the herds in ones and twos and staying put like cattle. 100 health,
  armour 15. Besides the bite and the tail sweep: a **chin slam** (8, a throw up), a **ground slam** (rears, stamps:
  20 in a ring of 5, then seven earth spikes burst along the ground toward its foe, 10 each), **ground eating** (at 7-24
  blocks it bites up the earth, chews, and spits a clod in an arc, 10) and, worn down or ridden and hit, a **roar** that
  hardens its plates (Resistance and Absorption for it, its riders and its owner near it, 10 s). Tamed the usual way
  (wear it below a third, crouch empty-handed, raw beef its favourite, Stoneborn twice as fast); ridden, R is the ground
  slam with its spikes running where you look (5 s).
- **Its look** (`client/dragons/StegonautDragonModel`, `tools/gen_stegonaut.py`, which prints the model's body plan from
  its own UV table): Andy wanted it "taller, to be imposing": long pillar legs with long shins ("longer cannons"), a long neck
  raised high in four segments,
  two staggered rows of tall horn plates, a bony brow and a shovel jaw, a long four-spiked tail;
  four hides (moss stone, jungle green, sandstone, slate) with moss on its back and four glowing-lichen marking patterns;
  short/long horns or a nose horn and a broken plate by its DragonLook; drawn 1.3x. It plods, rears and stamps for the
  slam, noses the ground and chews, and flares its plates to roar.
  Andy: "all the joints should move and blend smoothly": every move's envelope is smoothstep-eased and ends where it
  began, sitting/crashing/swimming ease in and out, the knees fold on a smooth curve, and the neck's bob and sway roll up
  it a beat per segment.
- **Swimmers** (`WindDragon.swims`): a walker of the waters. It breathes under water and currents don't push it; wild it
  floats with its back out and dives straight at whoever it fights there; ridden in water it swims where you look (W
  ahead, Space up and out at a bank, Shift down, S brakes) and keeps its riders' air topped up. Shared helpers for the
  lesser dragons' moves: `spareable`, `inFront`, `fighting`, `stun` (rooted and heavy-handed, stars over the head).
- **Atroxiia** (`AtroxiiaDragon`, `jaymoskingdoms:atroxiia_dragon`): a pale serpent wyrm on two great arms and its tail, no
  hind legs (3 x 4.3 blocks, its head about 4.5 up) of the snowy plains, ice spikes, snowy taiga, snowy beaches and frozen rivers (`has_atroxiia_dragon`),
  alone and rare. 200 health, armour 10, a grazer that fights only whoever hurts it, and it leaves frosted ice on still
  water where it strolls. Besides the bite and the tail sweep: a fist **slam** (16), a claw **swipe** (12), the
  **precise strike** (three jabs of its tail spear at 3-6 blocks, 9 each, dragging them in, the last a 2 s stun),
  **Gungnir** (the spear drawn back, a lunge, and driven through everything in an 8-block line: 40 and a stun), the
  **devastating sweep** (pressed close: the tail spun round, 13 in a ring of 7, a big throw) and the **Helheim quake**
  (at range, or freely once below half: it rears and brings both fists down twice, 25 to everyone on their feet within 9, the second
  a 5 s stun, and the water round it freezes). Raw cod its favourite, Frostborn twice as fast; ridden, R is Gungnir
  where you look (5 s).
- **Varasuchus** (`VarasuchusDragon`, `jaymoskingdoms:varasuchus_dragon`): a crocodile dragon (4 x 3.3 blocks, its back
  3.2 up, about 10 long with its tail) of the beaches and swamps (`has_varasuchus_dragon`), alone and rare. 200 health,
  armour 8, a hunter: it goes for players who come near, and it's quick in the water. Bite 15, **claws** (8) and a
  **horn gore** (8, a toss), a **dash** at 6-16 blocks (it charges: claws 15 as it reaches you, then its tail lashed
  round, 10), a **slash barrage** up close (eight claws, 5 each), a **phase shift** (hit, it may slip past you in a blur
  and come out behind you, untouchable meanwhile) and the **tailguard** (hit, it may curl its tail round as a guard: the
  next blow within 8 blocks is parried with our parry's sparks and ring, the striker takes 10, is slowed and thrown back).
  Below half it **rages**: bites 25, horns 15.8, claws 12, every move a third quicker. Raw salmon its favourite, Tideborn
  twice as fast; ridden, R charges (a burst of pace, goring whoever it reaches, 4 s).
- **Their looks** (`AtroxiiaDragonModel`/`VarasuchusDragonModel`, painted and printed by `tools/gen_atroxiia.py` and
  `tools/gen_varasuchus.py`, sharing `tools/lesser_dragon_art.py`): both tall and long-limbed. Atroxiia: a serpent's body
  raised on two long, heavy arms (gold cuffs, three-fingered hands) with no hind legs, the eleven-segment tail as its
  third support, curling up over its back into a gold-bound spear of ice; a three-segment neck and a head in a striped
  nemes cloth with a gold band (some wear a short cloth, some an ice uraeus on the brow), gold collar; hides glacier, deep ice, rime grey,
  midnight frost, with glowing frost veins, rims, stars or a spine line; drawn 1.15x. Varasuchus: long sprawling legs, two
  rows of bony scutes, a heavy blocky head (squared skull, brow ridge, cheek plates, a broad snout with a blunt tip) with
  horns swept up and back (a nose horn on some), and a twelve-segment double-crested tail about seven blocks long, long
  enough to wrap round its flank to the front; raging, it rears up on its hind legs (a generic `standing()` stance on
  `WindDragon`) and fights with its arms; hides swamp olive, river brown, mangrove black, pale sand, with marsh-light bands, spots, scute
  rims or a jaw line; drawn 1.3x. Every move eases in and out and ends at rest, and the neck and tail roll a beat per
  segment: Atroxiia's tail jabs, coils and drives straight for Gungnir, swings round low for the sweep; Varasuchus's
  snakes as it walks, lashes after the dash, wraps round to its front for the tailguard and snaps out on a parry; both tuck their
  legs back and scull their tails in the water. (Stegonaut's chewing jaw lost its last kink too.)
- **Ignivorus** (`IgnivorusDragon`, `jaymoskingdoms:ignivorus_dragon`), Andy: "put the effects onto Kintsugi's model
  then put that in the nether": the Nether's fire dragon, drawn as eanncrod's Kintsugi wyvern (as Eurus and Aeolus) at
  0.55 (about 4.3 blocks tall, 14 across the wings; box 4.4 x 3.7), its hide obsidian, basalt ash, ember or crimson and
  its seams glowing lava cracks (orange, magma yellow, white-hot, crimson; `tools/gen_kintsugi_dragons.py`). Every 30 s
  each player anywhere in the Nether (`#minecraft:is_nether`) has a 1 in 360 chance of one turning up flying about
  40-64 blocks off (about once in three hours there; never with one within 160 blocks). 450 health, armour 12, bite 22,
  fire-proof, a boss bar (`c:bosses`), and it leaves players be until one hurts it. Then, besides the swoop, hover, bite
  and tail sweep: fire breath (Notus's), a **fireball volley** at 10-48 blocks (three exploding fireballs, every 8-11
  s) and, on its feet within 16 blocks, a stamp that sends a line of seven **magma pillars** bursting toward you, one
  after another (a smoking warning, then 12 damage, 6 s of fire and a throw upward). Cooked porkchop its favourite,
  Emberborn twice as fast; tame, it's saddled and ridden like the wind dragons (R breathes fire). Drops leather, bone,
  magma cream, blaze powder, obsidian and sometimes netherite scrap. Not one of the wind dragons for Eurus's and
  Aeolus's showing themselves. Its moves follow Saint's Dragons' Ignivorus (none of that mod's art).
- **Aetherion** (`AetherionDragon`, `jaymoskingdoms:aetherion_dragon`): a star dragon Andy sent (a public-domain,
  CC0 GeckoLib dragon, not Saint's Dragons'): "the wings and head might need upgrading". Our own model on its body plan
  (`AetherionDragonModel`, `tools/gen_aetherion.py`, a 512 sheet; drawn 1.25x: its back 3.7 blocks up, about 17 across the
  wings; box 3.2 x 3.8): four long legs, a four-segment neck rising in an S, a seven-segment tail ending in a crossed
  fan, crystal spines down its neck, back and tail. Each wing is an arm, a forearm and a hand of three fanned finger
  bones with a thumb claw, a membrane behind each bone tapering to a scalloped trailing edge; the head has a heavy brow,
  two great swept horns (short, or with a lower pair, by its look), cheek frills and a jaw that opens. Hides night sky,
  pale silver, dusk violet, dawn gold, with glowing constellations on the membranes, starlit veins, rims or a sky of
  stars. The wingbeat rolls out from the shoulder a beat per joint, the fingers spreading on the downstroke; gliding
  they're held open; landed they fold (arm up, forearm down along it, hand up, fingers closed), the fold eased in and
  out; the neck straightens in flight and (Andy: "so much so it looks like it doesn't have any in the air") the legs fold
  flat back under the belly, drawn in small and up into the body. Andy chose "Aether sky dragon, plus Nulljaw": every 30 s each
  player over the Aether (`jaymospowers:aether`) has a 1 in 240 chance of one turning up flying 40-64 blocks off (none
  within 160). 420 health, armour 12, bite 18, a boss bar, and it leaves players be until hurt; then its **bite**, a
  **breath beam** of starlight (28 blocks, stopped by blocks, 7 a strike, burns nothing) and, below half health, the
  **Cataclysm** (it rears up roaring, wings flung high, and 14 stars fall on its foes and round it, each after a closing
  ring of light: 14 damage and a throw, no block touched). Glow berries its favourite, Nightborn twice as fast; ridden,
  R is the beam.
- `/kingdom dragon stegonaut|atroxiia|varasuchus|ignivorus|aetherion [tame]` (operators).
- Compiled clean (`compileClientJava`, Gradle against the 26.3 jars); not played.

## 0.7.523: talks that matter (pack 0.20.25)
Our own take on the idea of MCA: Conversations (none of its code). Plan: `docs/Talks-Plan.md`.
- **Trust, respect and warmth** (`Bearings`): every villager now holds three hidden feelings about each player, kept
  on their memory of you (`Recollections.Person.bearing`; a masked stranger's go to the costume). Everything they
  already remember moves them (gifts, trades, help, rescues, crimes seen, blows, bouts, quests, apologies, promises),
  word passed on moves them half as much, and they fade slowly. MCA hearts and realm reputation colour them. They're
  never shown: you see them in how villagers act. Fond ones charge less and sour ones more (up to a fifth), the
  trusting gossip more and tell you secrets, the wary say nothing, a guard who trusts and respects you comes to your
  side when a monster hits you (others shout a warning), one who's fond of you looks away from your crimes, and one
  who can't stand you won't talk at all. The chat AI is told in words.
- **Have a word** (MCA talk menu, and now and then Chat): a short talk on the talk screen with two or three replies.
  52 talks about their work, family, blood, faith, realm, the weather and what you've done. Each reply has their
  answer and shifts how they feel. Some lead to a promise, a favour, a rumour or a secret, a loan, or a quarrel (no
  more talk until tomorrow). An opening line hints at how they look at you.
- **Promises** (`Promises`, kept with the player): bring something by a day, come back, look in on a child for two
  days, walk a wandering child home, speak for them to the ruler (an audience does it), keep the peace in their
  town, pay back a loan (in coin). They're listed in the journal's Quests page. Keep Your Word on the Interact page hands over
  what you brought. On the due day they may come over to remind you. Kept: trust and warmth up a lot, hearts and
  reputation, their family hears, a deed of The Truth (and of the Hearthkeeper for a child). Broken: trust drops
  hard, hearts and reputation down, they remember and bring it up, and their kin and friends cool too. Saying
  honestly that you can't costs much less. Apologise covers a broken promise.
- Loading hints 94-95. Not played; compile-checked with `kingdoms/gradlew build`.

### Fixes to Talks, Bearings, Promises and Theft
- If the one you promised to escort a child for dies, the child stops following you.
- A quarrel now lasts the full number of days, so the villager keeps sulking tomorrow too.
- Trust, respect and warmth now fade by calendar periods, so meeting a villager often no longer stops the fade.
- Talks that are told only once are never forgotten when old talks are trimmed.
- Letting go of a loan counts as a broken word. The coin is not free any more.
- Looking away is only for crimes. Renown, brawls and disguises see everyone again.
- A villager you already know now comes over on the due day to remind you of a promise.
- A chat that turns into a talk no longer closes the talk screen at once.
- Promises to come back aren't kept by the villager walking over to remind you; babies don't get the war, loan or bloodline talks; a ruler can't promise to speak to themself.

## 0.7.524: death by cause hooks (pack 0.20.26)
- Powers' death by cause (`Mortality`) now knows Kingdoms' bosses: whatever shows a boss bar (`BossBars.boss`, told through
  `PowersCompat.tellBosses`), so dying to a king, warlord, champion, wild dragon or Aether Giant is a boss's death (70% final,
  spared if you're far too weak).
- **Taken captive** (`Captors`). Andy: "if you are kidnapped by your sentient murderers (orcs goblins ogres pirates etc) theyll
  have it hidden or even wear it". Left for dead by an orc, goblin, ogre, pirate, camp raider, illager or outlaw, you wake
  chained in their camp (theirs, else the nearest within 640 blocks), shackled like the "still in chains" start (pick it or slip
  it at night; the camp hunts you for a day after). Everything you carried is taken: about half your armour and weapons are
  worn by whoever downed you and the camp's people (they drop it when killed), and the rest is in chests buried about the camp.
  With no camp near, you wake in the wild with your gear.
- Town healers treat what will kill (poisoned blood, water in the lungs, withering: `PowersCompat.afflicted`), for 8 more coins.
  Compile-checked; not played.
