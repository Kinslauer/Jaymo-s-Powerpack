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
