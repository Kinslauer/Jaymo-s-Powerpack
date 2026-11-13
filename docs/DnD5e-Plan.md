# Plan: fifth edition picks (2026-11-12)

Andy asked for "a whole checklist on fifth edition DnD" (note on the second left-out list, row chat-death-names-strangers).
Pick page: https://claude.ai/artifact/KoVSm7FS7iyENABo9mUSdH (db doc `picks/andy`). Andy ticked 91 of 94 rows; not
picked: J7 swamp hags, J11 mummy rot, K1 Bard. His page-wide note: "please tell me if anything cannot be done."

Ideas only, from the 5e SRD (OGL 1.0a / CC-BY-4.0): our own names, code and art. Real time, no dice on screen; every rule
works on NPCs too. Speak with Dead and the Stranger death messages belong to the audit thread and are not here.

## Andy's notes on rows
- B3 exhaustion: "we also pass out from hunger but the progression here is better." Exhaustion steps feed Fainting.
- C9 undead fortitude: "add a zombie head smash or stomp execution, that plays at random if you are unarmed."
- E5 reincarnate rite: "give players a Limbo button alongside reincarnate that puts them in a black box and they are
  allowed to spectate around their corpse, so that they can watch as their teammates bury them or wait to be brought
  back in a new blood."
- F2 darkvision: "the lizard and feline beast folk should get that too."
- G3 carousing: "you should get a screen recalling what you did from the character's POV."
- G4 dice and cards: "should be playable with other players."
- G7 forged papers: "acquired from the speakeasies or their especially shady patrons."
- I1 attunement: "the baubles you find should work this way."
- J6 blood bats: "the vampire bats should work like this instead."
- J8 copies: villagers found under the ground near Grim Kingdoms sites and villages are the ones whose lives were
  stolen; they say they were kidnapped and try to lead you back to their village.
- K6 Ranger: "we can wrap our ranger and survivalist into this". Andy decided 2026-11-12: Ranger replaces Archer and Survivalist.

## Patches (one branch, one PR, a commit per patch)
1. **Core feel** (the eight first-patch rows plus C2): A1 upper hand and bad footing, A2 saves from Stats, A5 noticing
   without looking, B3 exhaustion steps, C1 opportunity attacks, C2 polearm reach strikes, C9 undead fortitude and the
   unarmed head stomp, H1 one held spell. (G5 start features moved to patch 5, beside the talk and downtime rows.)
2. **Conditions and fight moves**: B1 frightened, B2 charmed, B4 held fast, B5 deafened, B6 nets, B7 poisons,
   C4 surprise, C5 sneak attack bursts, C6 reckless blows, C7 shove and trip.
3. **The field**: A6 lend a hand, C3 cover, C8 damage kinds, C10 under water, E1 catching your breath, E2 keeping
   watch, F1 travel pace, F2 darkvision, F4 weather in the fight.
4. **Bosses**: D1 legendary resistance, D2 legendary moves, D3 lair beats, D4 regional signs, D5 the one who comes back.
5. **Talk and downtime**: G5 start features, A3 Presence, A4 sparks (ideals and bonds beside Flaws), G1 talk checks, G2 liars,
   G3 a night on the town (POV recall screen), G4 dice and cards with other players, G6 chalk marks, G7 forged papers,
   G8 lifestyle, G9 teachers, G10 research.
6. **Magic rules and the first spells**: H2 components, H3 rituals, H4 counterspell and dispel, H5 cantrips,
   H14 scrolls above your level, H6 camp spells, H7 getting around, H8 battlefield spells.
7. **More spells**: H9 mind, H10 finding, H11 nature's rites, H12 familiar, H13 wild magic.
8. **Magic items**: I1 bonding (baubles), I2 cursed, I3 unknown properties, I4 weapons with a will, I5 charges,
   I6 wondrous gadgets, I7 Deck of Fates, I8 potion clashes.
9. **Creatures**: J1 troll, J2 rust beast, J3 stone gaze, J4 clear cube, J5 ceiling hunters, J6 blood bats (the
   vampire bats), J8 stolen lives, J9 bog lights, J10 singers, J12 pack tactics, J13 vampire rules, J14 shadows.
10. **Death and the land**: E3 revive, E4 keeping a body fresh, E5 rebirth rite and the Limbo button, F3 nature's traps.
11. **Classes**: K2 Cleric, K3 Druid, K4 Monk, K5 Paladin, K6 Ranger, K7 Warlock, K8 Tinkerer, K9 second class,
    K10 feats, K11 paths.

## As built
### Patch 1
- **A1 upper hand / bad footing** (Kingdoms `UpperHand`): prone, crawling, tripped, downed, asleep or blind targets,
  and the high ground (1.5 blocks up, within 8), give the upper hand: blows x1.15, a quarter of them telling (x1.5,
  crit sound). Wading, ice, poison, blindness, or a player striking into darkness without darkvision: x0.85, a quarter
  glance (x0.5). Both cancel; neither stacks. `UpperHand.add` / `addSight` for later patches (frightened, darkvision).
- **A2 saves** (Kingdoms `Saves`): seven attributes, chance 5%-60% for players by attribute, 10% for mobs, 40% for
  bosses. Endurance shortens poison, wither, hunger, nausea; Grit shortens slowness, weakness, mining fatigue (mixin
  on `addEffect`); Agility softens blasts, fireballs and dragon breath on a moving player (to half); Arcana throws off
  the werewolf's and vampire's curse bite; Grit keeps a held spell through a blow.
- **A5 noticing** (Kingdoms `Notice`), with the first part of **F1** (pace changes it): each second, by Wits, a
  tripwire within 5, a shut mimic within 8, or a hunter on you from out of sight or invisible within 14 gives itself
  away to you alone (a sound from it, a glint). Sneaking helps, sprinting or riding hard costs. `Notice.add` for later
  patches (traps, ceiling hunters, chalk marks, pickpockets).
- **B3 exhaustion** (Powers `Exhaustion`): six steps from sleeplessness (two days awake, then one a day), sprinting
  3 minutes without a 10 s breather, 2 minutes near empty of food or water, 1 minute frozen through. Slower feet,
  slower digging, weaker and slower blows, tunnel vision (the faint's darkness), swooning (`Fainting`), and at six a
  collapse (knocked out 2 minutes, never death). Sleep clears the sleepless steps and one more.
- **C1 / C2 opportunity attacks and polearms** (Kingdoms `Opportunity`, tag `polearms`): stepping out of a foe's
  reach (3, polearms 4) gets a free blow, unless dodging or backing off shield up facing them; knockback doesn't
  count. A polearm also strikes a foe stepping in and stops them. One free blow per 2 s; a player's needs the swing
  80% ready.
- **C9 undead fortitude** (Powers `UndeadFortitude`): a downed zombie gets back up after 4 s, three times in four,
  once, unless the blow was fire, a beheading, a Smite blade or a crit. **Andy's note**: an unarmed finisher on a
  zombie is now a stomp, a head smash or the fist, at random (`Finishers` STOMP, HEAD_SMASH, `FinisherPose`).
- **H1 one held spell** (Kingdoms `Concentration`): raising thralls, warding, sanctuary circles, chants and the
  sorcerer's ward are held spells. Casting another ends the first; a blow may break it (a Grit save, 10+ always
  breaks).

### Patch 2
- **B1 frightened** (`Fear`): sources are dragons, the Wither, the Warden, wraiths (tag `frightful`, optional lich/hag
  entries for later), a witch's thrown potion (the hag's curse), Dread's brutal-kill witnesses, a monster finishing a
  downed body. Crawl toward it, aim shakes, bad footing; out of sight 3 s, a Grit save every 3 s, or 20 s ends it.
- **B2 charmed** (`Charm`): mob vampires' gaze and witches' song; no blows on the charmer, 30% off a charmed trader,
  softer haggles; hurting the charmed breaks it; Wits save. Vampire players have no gaze yet. `Charm.charm` for H9.
- **B4 held fast** (`HeldFast`): spiders (12%, cave spiders 20%), claws tag (wraiths, husks; the pack has no ghoul);
  keys locked client-side, eyes free; close blows x1.5 crits; Grit save every 3 s. `HeldFast.hold/afflict` for spells.
- **B5 deafened** (`Deafened`): explosions, thunderbolts, dynamite, cannons; 4-30 s by distance. Sounds cut to 15%
  under a held high note (a volume cut, not a filter); no noticing, no far speech bubbles; spoken spells fizzle 55%.
- **B6 nets** (`Nets`, `ThrownNet`): crawl, bad swings, upper hand on them; Might save every 3 s, a blade frees
  (yourself or another), rots off at 40 s. Weaponsmith level 2 trade, craftable, guards (arrest chase) and orc
  slave-keepers carry one a day. Stand-in art: cobweb.
- **B7 poisons** (`Poisons`, `PoisonItems`, `GasBottle`): five vials, coat/dose/smear/gas; sleep 20 s, stiffening
  holds 8 s, truth 3 min (`Poisons.truthful`, tag `jaymoskingdoms_truthful`, for G2), midnight kills at the next
  midnight. Cleric sells the antidote (no herbalist profession exists). New trades reach villagers who level after
  this. Our own small vial icons (one bottle shape and colour each).
- **C4 surprise** (`Surprise`): 14-tick freeze both ways; `Surprise.addImmunity` for Alert (K10).
- **C5 sneak attack** (`ThiefSneak`): x1.5 + 2, once a second.
- **C6 reckless** (`RecklessBlows`): 35% glance at x0.4, else x1.75; open 2 s.
- **C7 shove and trip** (`Shoves`): key default Insert (no free key fit; remap in Controls). Players only; Might
  contest, bosses immune.

### Patch 3
- **A6 lend a hand** (`LendAHand`): lock picking (window x1.35, strain x0.7), carrying (+10% speed, +2 Kitchen
  hauling brawn), "watch out" (a friend facing a foe hunting you from out of view shouts; next blow has the upper
  hand), haggling (+0.25). The pack's wagon is horse-drawn, so "a cart" is covered by Carry and corpse hauling.
- **C3 cover** (`Cover`, `ProjectileCoverMixin`, `RangedTactics` TAKE_COVER): crouched behind fences, walls or
  carts, up to 85% of shots hit the cover and stick. Raised shields are vanilla blocking, unchanged. Spell bolts count
  via the `cover_stops` entity tag.
- **C8 damage kinds** (`DamageKinds`): 13 kinds, a family table (skeletal, undead, ghostly, slime, metal golem,
  fiery, snowy). Ghostly = vexes and wraiths: only enchanted, silver, holy weapons, Divine-born or Dawnborn in sun.
- **C10 under water** (`UnderWater`): blades x0.6 and slower; spears/tridents/daggers exempt; arrows at 0.3 speed;
  bolts last 8 ticks. Water-folk exempt.
- **E1 catching your breath** (`CatchBreath`): a minute seated at a lit fire, a bench or a still horse, unhurt for
  10 s and unhunted; reserve 8 + 12 x Endurance share + class bonus; refilled by a full night's sleep.
- **E2 keeping watch** (`NightWatch`): watcher = awake player, companion or tame wolf by the fire; visitors are
  wolves, a bandit or a pack thief (Vindicators as stand-ins), at most once an hour per camp.
- **F1 travel pace** (`Pace`): hold key "Walk slowly" (default Caps Lock; Left Alt is roll): -35% speed, quieter,
  harder to see, notices more. Riding hard is loud.
- **F2 darkvision** (`Darkvision`): Nightborn, Deepborn, Stoneborn, Hollow, goblin, orc, moon elf, and per Andy the
  lizard (Serpen) and feline beast folk, plus arachnid. A client depth-based grey lift within 16 blocks (may need
  tuning in play). Replaces the three eye perks' night-vision effect.
- **F4 weather** (`FieldWeather`): gale arrow drift and placed torches blowing out (to the burnt torch villagers
  relight); rain douses fire arrows and cuts mob sight (x0.85, storm x0.7); deep snow -30% (sprint or horse -42%).
  Held torches and lanterns don't blow out.

### Patch 4
- **D1 legendary resistance** (`Legendary`, Powers `KingdomsCompat.shrugs`): 3 for the great bosses (dragons, the
  Great Necromancer, patron champions, Wither, Ender Dragon), 2 for any other; one back per day; a barrage within
  1.5 s costs one. Covers HeldFast, Charm, Nets, Fear, Chrono and Darktide stuns, Gravity Lift, Crush's pin, the
  Gravity Well's kneel and Time Stop. Bosses that used to be simply immune to holds/nets are now holdable once
  their resistances run out.
- **D2 moves** (`BossMoves`): every 4th blow inside 8 s (a growl on the 3rd), 16-tick tell, 9 s cooldown; tail sweep,
  dash, shriek by family; damage half the boss's attack (3-9).
- **D3 lair beats** (`LairBeats`): every 7 s while a fighter is in the arena; rocks, roots (Might save or netted),
  fog (darkness 4 s), flare (fire columns at lit fires). Homes: a wild dragon's den is where it was first seen
  (dragons keep no fixed home), throne rooms from `Factions.keep`, the minotaur's labyrinth, the necromancer's crypt.
- **D4 land** (`GreatLairs`, `LairLand`): radius 6/3/4 chunks; foul water (nausea, 1 damage, never below 2 hearts),
  85% fewer animal spawns (herds left alone), songbirds muted, bad dreams, crops stuck at half. Lifts on the master's
  death or a dragon's taming.
- **D5** (`GreatNecromancer` "Halvard the Hollow", `Necropolis`, `Vessel`): crypt built in code near a player (one per
  768-block cell in two, 150+ blocks from villages); soul urn in a sealed room 24-46 blocks away under a cairn; he
  rises 2-4 days after death until it's smashed. Hints: sexton's tally (barrel), the Hollow Man's page (chest),
  villager talk within 700 blocks, `Notice` hum. Stand-in skin: the court necromancer's. No spawn egg.

### Patch 5
- **G5 starts** (`StartPerks`): 36 starts mapped to a circle of folk and boons (passive: Underworld trust, discount,
  guard salute; asked once a day per villager via "Ask a Favour": food, hiding, bed and healing, news, passage, a
  foe lesson). Several starts share a perk pair.
- **A3 Presence** (`Stats.PRESENCE`, `Presence`): +0.4% talk odds, +0.5% warming, +0.6% order reach per point; 10 free
  points for everyone (20 Warlord/Sorcerer); migration for old saves.
- **A4 sparks** (`Sparks`): ideal/bond/flaw picked on the life story summary page; the flaw list is its own (temper,
  greed, drink, pride) because life-story Flaws are physical. One spark at most; rescues a failed parry (Powers
  `Guard`), lockpick or fright; gift from the Standing screen.
- **G1** (`Persuasion`): "Talk Them Round"; remembered failures (`Recollections`); seen threats are ASSAULT.
- **G2** (`Liars`): price lies +30%, wrong directions, guard alibis; Wits tell as a bubble stage direction; truth
  draught stops it. Zone of Truth hook `Liars.zone` for patch 7 (H9).
- **G3** (`Carousing`, `RecallScreen`): three purses (2/6/18 emeralds); outcomes are real (villager hearts, a real
  structure rumour, a real crook's debt collected after 2 days, real cells via `Busts.lockUp`, MCA engagement via
  `McaBridge.engage` on the villager's side). A 5 s blackout, then Andy's first-person recall screen; world time
  doesn't jump.
- **G4** (`Tables`, `GameScreen`): server-side dice and twenty-one, up to 4 players, villager house when alone,
  stakes 1-25 emeralds; Finesse cheat, Wits catch. Faces drawn as plain shapes.
- **G6** (`ChalkMarks`): dawn marks on real places; readable by the Underworld-trusted; player chalk (bone meal +
  clay). Own glyph textures.
- **G7** (`ForgedPapers`): per Andy, sold only by speakeasy fences and shady crooks; pass and pardon; FORGERY crime.
- **G8** (`Lifestyle`): sleep, spending, tailor clothes, baths (5 s soaking in still water); audiences +-20 rep floor,
  guard halts, priests' alms.
- **G9** (`Lessons`): tongues (4 lessons), trades (3), fighting styles (3, plus a Powers boxing lesson).
- **G10** (`Research`, `FoeLore` new): sage or a lectern with 6+ shelves; topics "creature fears" and (added by the
  lead) "where a heart is hid" for Halvard's urn within 1500 blocks. `Research.addTopic` for I3 later. A day's study
  is 1-2 minutes, once a game day. FoeLore stars at 3/12/30 kills: +4% dealt, -3% taken each.

### Patch 6
- **H2** (`SpellParts`): voice + free hand for every staff/tome/scroll cast (Spell Engine: voice and hand only).
  Parts: diamond dust (restoration, sanctuary, dispel), bones (raising 2, death's door 3), ender pearl (dome,
  identify); foci (staffs, tomes, mana wands, tag `spell_foci`: totem, enchanted book, totem staff) replace string,
  slime, bat wing, sticks, kelp. The pack had no gag: new Rag Gag (wool + string), used on the helpless; captives
  gagged only mumble.
- **H3** (`Rituals`): Magic key (Page Up, the one free key) opens the screen; 10 s still with a book; identify shows
  enchantments/curses/imbues; `Rituals.onIdentify` hook for I3. Known rituals only (Sorcerers know all four).
- **H4** (`Counterspell`, `Dispel`): NPC cast tell 18 ticks; NPC rivals and players snuff it; player staff casts are
  instant so NPCs can't counter them. Dispel respects boss resistances and saves.
- **H5** (`Cantrips`): Kitchen has no dish temperature, so warm/chill marks the dish and eating it shifts body heat
  for 3 minutes.
- **H14** (`ScrollGamble`): below Knowledge 4: work once / fizzle (scroll crumbles) / backfire (hurt + mana strain).
- **H6** (`CampSpells`): ward line 12 blocks for 10 min; dome for a night (projectiles back, hostiles out, body heat
  held; rain still shows); create food drops bread; purify cleans poison and spoilage. Fields don't survive a restart.
- **H7** (`Strides`, Powers `MagicMoves`): feather fall (you and allies in 4), spider climb 1 min (held), water walk,
  long jump, misty step 7 blocks.
- **H8** (`BattleSpells`, `Fields`): web (GRAB save or netted), grease (trips), fog, darkness (blinds, blocks mob
  sight), silence (no spoken spells), spike growth. Fire burns web and grease.
- 20 new scrolls with own textures (`tools/gen_fifth_p6.py`); casters carry one 1 in 4.

### Patch 9
- Placement: `Haunts` rolls places near players every 5 s (persisted, tier-gated), not worldgen structures.
- **J1 troll** (`Troll`, `TrollLimb`): heals 1.5/0.5 s; severed arms crawl back; falls and rises unless burned.
  Swamps/mangroves, tier 1. The model doesn't show missing arms.
- **J2 rust beast** (`RustBeast`): rusts iron gear 25% per touch, iron weapons 20% per hit; eats iron; fed, calm
  for 2 days. Caves below y50.
- **J3 basilisk** (`Basilisk`, `Gaze`, `Statues`): the great lizard (the row's "or a snake-haired woman": the lore's
  gorgon stays a legend); stone builds while you look, held fast at full (40 s); looking away is bad footing; hand
  mirror turns it back; a friend's pickaxe frees; statue den in about 20% of Grim/ruin rolls.
- **J4 clear cube** (`ClearCube`): swallow, 2 acid/s, gear wear, GRAB save every 2 s, out after 15 s; floating items
  rendered. Mineshafts, strongholds, trial chambers.
- **J5** (`Piercer`, `CloakFlier`): drop damage 3 + 1.2/block (max 16); cloak flier below y10, snuffs lights, wraps
  the head; a held light or a pull frees you.
- **J6 blood bats** (`BloodBat`, `Latches`): per Andy the vampire bats now behave the same way.
- **J8 stolen lives** (`Doubles`): the real ones are found in dungeon captive rooms, new cellars near settlements and
  shut underground under grim_kingdoms/village structures (the Grim buried villagers come from that mod, caught by a
  scan; test with the real mod). They plead with true facts and lead you home; copies appear near the seat and slip.
- **J9 bog lights** (`BogLight`, `QuicksandBlock`): lead to hazards or lay a quicksand pit; feed on Fear.
- **J10 harpies** (`Harpy`, `Song`): sea cliffs; CHARM save each second; deafened or in a boat exempt; ear wax.
- **J12** (`Packs`, `Digger`, `Warrens`): UpperHand reason; digger warrens with trap-cover pits (sneak passes).
- **J13** (`OldRules`): homes = roofed area with a bed; invitations by sneak-use, kept; running water; coffin =
  bed with dark oak slabs; stake in the coffin kills for good (players too).
- **J14** (`Shadow`, `ShadowDrain`): -5 Might a touch, mends 1 per 30 s; at the floor knocked out, your shadow
  peels off carrying the points.

### Patch 7
(Resumed after a machine restart; the partial work was kept and finished.)
- **H9** (`MindSpells`, `Truth`, `Geas`, `Forgetting`, `Choices`): charm and hold are held spells; command (flee,
  drop, grovel) and suggestion (come, wait, leave, sleep, follow, 30-60 s) picked on the Magic screen; zone of truth
  is a held field wired to `Liars`, raised by judges (Arrests), jailers (Busts), rulers (Audiences), and in
  `Questioning`/`ForgedPapers`; geas 3 days (can't strike the caster, straying past 40 burns, captains don't betray);
  modify memory wipes bad memories and witnessed crimes, but not an already-set wanted mark or bounty.
- **H10** (`Divination`, `Hair`, `Scrying`): detect magic is patch 6's find magic; locate object (128) and creature
  (600, needs hair) as a mote thread; augury 9 in 10 right, signs only; scrying is a top-down live map of 20 blocks
  around them for 30 s (not a camera), needs their land loaded.
- **H11** (`NatureRites`, `Passers`, `Moonbeam`, `HeroesFeast`): passer log per 16-block square (2 days, lost on
  restart); plant growth is instant in 12 blocks (not overnight) and dries the farmland; control weather sets vanilla
  weather for half a day (FieldWeather follows); moonbeam reveals werewolves (mob and player), disguised dragons, and
  (wired by the lead) shapeshifter copies; heroes' feast at any "table" block, a day without fear or poison.
- **H12** (`Familiars`, `FamiliarBodies`, `FamiliarClient`): five bodies on vanilla models with recoloured skins
  (stand-ins); eyes key Page Down; send to hand an item over; touch spells through its eyes; ash and recall ritual.
- **H13** (`WildMagic`): Sorcerer "wild" option (1 in 6 casts) and great-lair land or `addCursedPlace` (1 in 8);
  13 surges; the fireball can hurt a very low-health caster (players go down, not die).

### Patch 8
(Restarted after a machine restart.)
- **I1** (Powers `baubles/Bonds`, Kingdoms `ItemBonds`, `BondRest`): per Andy, found baubles work this way; found
  relics, willed blades and unknown items do nothing until bonded on a full sleep or a 1-minute calm seated rest;
  3 at most; Bonds screen on the bauble screen. Quest-gifted relics also need bonding.
- **I2** (`RelicCurses`): clinging (can't drop or unequip), thirst (2 HP per kill, never kills), whispers, misfortune
  (Luck -3). Lifted by a cleric (14 coins, 4 at a shrine), `Dispel`, or dispel done as a slow rite (the lead made
  dispel a ritual: the remove-curse rite).
- **I3**: unknown until 10 minutes' bonded use, identify, a Research topic, or a court wizard (6 coins).
- **I4** (`SentientBlades`, `Seizures` BLADE, `OwnSpeechHud`): wants undead/weak/gold; grudges; a POSSESSION save
  before it takes the arm.
- **I5** (`ChargedWand`): six new wands; 25% crumble on the last charge. Other mods' staffs untouched.
- **I6** (Kingdoms gadgets, Powers `Wondrous` boots and cloak): loot only. The jug gives Kitchen clean water (lead's
  fix). The horn skips any faction's land unless you rule it, locked blocks, block entities, fluids, hardness > 5.
- **I7** (Deck of Fates): 1-3 draws chosen first; 12 cards; the throne raises a keep via
  `KingdomEvents.found(..KEEP)` or gives a keep deed; the death knight is a strengthened wither skeleton (no own
  model); the wish is four boons (life, riches, learning, mercy), never free-form.
- **I8** (potion clash mixin): ~45% clash chance, lowered by Wits.
- Art: `tools/gen_fifth_p8.py`.

### Patch 10
- **E3** (Powers `Revival`, Kingdoms `CallingBack`): a final death is held up to 60 s if a reviver is near (a cleric
  within 28 with the fallen carrying a diamond or dust; a player within 12 who knows Calling Back with dust); not for
  beheaded, void or lava deaths; the death screen says someone may reach you; no reviver for 15 s and it goes ahead.
  Back at 30% health, weak and slow 5 minutes, half the levels kept; not a wake-up. Chrono's R comes first, untouched.
- **E4** (`BodyKeeping`, Kitchen `Keeping`): rite of keeping (ritual, 10 days, bodies within 6), wrap (6 days; also
  `Freshness.preserve` on food), clerics keep nearby dead 4 days at a time; kept bodies don't rot or rise and can't
  be raised. `BodyKeeping.isPreserved` for Speak with Dead (not on master). Also shovel graves (`Graves`, Kitchen
  `Burial`).
- **E5** (Powers `Limbo`, Kingdoms `Rebirths`, `Groves`, `DeathRites`): Andy's Limbo button: spectator tethered 14
  blocks from the body in a black frame, kept across logouts; Reincarnate from Limbo by holding Backspace 3 s. Rebirth
  within 10 days at a grove (5+ logs, 40+ leaves in 7 blocks) with the body (corpse or grave): an old druid (a still
  villager in 35% of 128-block forest cells; bring the body to them) or the Scroll of Rebirth. New random blood
  (not the old one, not earned or brood bloods); keeps name, knowledge, map, reputation, class, skills, inventory and
  the body's hotbar; loses blood powers and heritage perks; wakes downed at the grove.
- **F3** (Powers lake crust, Kingdoms `traps/Traps`, `ColdMouldBlock`, `SporeMouldBlock`, `SlimeMouldBlock`):
  holding your breath is sneaking (air drains, spores take you when it's out); vinegar is a new item (bottle +
  2 apples), Kitchen has none. Moulds drop themselves; textures from `tools/gen_fifth_p10.py`.

### Patch 11a (new classes)
- All: class key J via `Abilities`; `Rests` pools (short rest = `CatchBreath`, sleep = all); teachers (`ClassTeachers`:
  priest = holy symbol, farmer/shepherd = sprig, librarian = pact seal, smiths = gadget recipes; 4 silver a lesson);
  14 cards on the class screen (portraits are gear icons as stand-ins); no class NPCs added.
- **K2 Cleric** (`Clerics`): patron as the class option; turnings 2 + patron tier per rest; bless (absorption, +15%
  saves, fright lifted); allowed spells include Calling Back and keeping.
- **K3 Druid** (`Druids`): study by watching a beast 90 s; shape 3 min, 2 per rest; stand-in beast bodies (wolf, polar
  bear, parrot, cod) with the player's body hidden; the hawk has real flight (lead's fix), the fish water breathing;
  first-person hand still shows. May learn rebirth.
- **K4 Monk** (`Monks`): fist damage with no plate; arrow catch on a timed key, thrown back from the pack (flat
  damage); stun via `HeldFast` (bosses resist); slow fall by walls.
- **K5 Paladin** (`Paladins`): 3 smites (+6, doubled on the dead), 20 health of hands, aura +12% saves in 10 blocks,
  fearless; oath breaking: mercy = killing the helpless, justice = any crime, honour = theft or striking the unaware
  non-hostile; atone by shrine prayer or 8 silver to a priest.
- **K6 Ranger** (`Rangers`): per Andy replaces Archer and Survivalist (save migration; their perks and boons kept
  in `ClassPerks`). Foe picked as the class option (dead, raiders, beasts, spiders, greenskins, monsters): +25%, +15%
  more on favoured land; land set from the starting biome, re-claimed with sneak + key once a day; tracking via
  `TrailReading` (the favoured foe's prints read farthest/oldest and larger: lead's addition); quieter.
- **K7 Warlock** (`Warlocks`): 2 pact slots, x1.5 power, 0.4x cooldown; a debt every 5 casts due in 3 days (favour,
  errand, blood, coin); unpaid: slots withheld, then favour -25, weakness and darkness.
- **K8 Tinkerer** (`Tinkerers`): infusions at Powers tinkering stations, 3 at once: returning (tridents only, real
  Loyalty), springing boots, glowing shield (night vision + glow, no placed light); recipes for rod, rope, jug,
  light, smoke bottle (invented, need a balance pass); hound and horn stay loot-only.
