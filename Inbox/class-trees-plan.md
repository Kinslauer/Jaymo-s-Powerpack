---
status: blocked
for: claude
from: Powerpack project chat, 2026-11-17
asked: 2026-11-17
claimed_by: 
pr:
suggested_owns: []
---

> Parked for pickup: not for a bot to start. The Monk, Paladin, Cleric, Druid and Warlock trees get fleshed out with Andy first, then it is split into build tasks. Also in the Powerpack project as `claude/Class-Trees-Plan.md`.

# Plan: class levels and class trees

The ask (2026-11-17): every class gets its own skill tree. "Every class should get its own level, and you spend those
points within its tree, so the rest of the skills are still attainable." The class key starts as a small version of
the class's trick and the tree unlocks the full one (the Sorcerer's sigil before the imbue). The Tinkerer's levels open
the world tiers' locked gear early: "Your extensive time behind these gadgets gave you expertise others didn't." The
Marksman and the Tinkerer share one tree, after the 5e homebrew classes they came from (D&D Wiki's *Marksman (5e
Class)* and *Tinkerer (5e Class)*): ideas only, nothing copied.

## How class levels work
- **A level per class**, 0 to 25, beside Survival, Stealth and Strength. Own XP, earned by doing the class's thing (below).
  Level l → l+1 takes 30 + 15·l XP, like the pillars.
- **Points:** one per level, spent only in that class's tree. Each tree holds about 30 points of perks, so a level-25
  class still picks a build, and the pillars' points stay free for the general skills.
- **Crossroads (the ask: "no two builds should be the same, so each path needs valuable picks"):** every tree has two or
  three forks where two perks face each other. Taking one closes the other until a respec. With the forks and the
  ~30-point trees, no two characters of a class come out the same.
- **Practice:** each level makes the class's starting move a little stronger by itself (the sigil's damage, the lay on
  hands pool, the catch window...), the way pillar levels improve each pillar's core thing.
- **Second class:** its own level, earned at half speed (`Classes.SECOND_SHARE`); its perks work at that share too, as
  paths do now.
- **Respec:** a court wizard or a bottle o' enchanting gives back class points the same way as pillar points (the level
  stays). A Writ of Rebirth wipes the class and its level.
- **The K screen:** a fourth tab, the class's own tree, coloured for the class.
- **Moving the class pages off the pillars:** Rogue's Arts (Stealth), Spellcraft and Sorcery/Forbidden Arts (Survival),
  Archery (Survival) and Warfare (Strength) move into their class trees. Their pillar points come back. An existing
  character starts at the class level matching the pillar level those pages used, and keeps the perks already bought,
  paid from the new class points, so nobody loses a move on the update.
- **Shared trees:** Ranger gets Archery to itself. Marksman and Tinkerer share the Workshop (below); each perk there is
  marked for which classes may take it. Warfare is split: the Warlord and the Brute get mirrored trees (Discipline and
  Fury), and the Rider gets an equestrian tree of its own. Warfare's perks are shared out: Shout and Throw Net to the
  Warlord; Throw, Warrior's Charge and Warrior's Last Stand to the Brute. A Rider who had any gets the points back.

## Class XP
| Class | XP from |
|---|---|
| Thief | pockets picked, locks opened, unseen thefts, sneak attacks |
| Warlord | full-strength blows, blocks and parries, orders given |
| Brute | blows dealt and taken while raging, kills with bludgeons and axes |
| Mage | spells cast and landed, scrolls learned |
| Sorcerer | sigils and imbued blows that land |
| Warlock | pact casts, debts paid on time |
| Cleric | turnings, friends healed and blessed, rites |
| Paladin | smites, health laid on, each day the oath holds |
| Druid | beasts studied, time in a shape |
| Monk | fist blows, arrows caught, stuns |
| Ranger | hits on the favoured foe, bow hits, tracks followed |
| Marksman | gun hits, long shots, guns and shot made |
| Rider | distance ridden, blows and shots from the saddle, jumps cleared, a horse's disciplines raised |
| Tinkerer | infusions, gadgets built, abilities studied, gear made at the stations |

## Sorcerer: Sigil first, Imbue learned
Today J does nothing until Sorcery is bought, and Sorcery then gives the imbue, the bolt, the spells and absorbing at once.
- **From the start, the Sigil (tap J):** a glyph flicked off the held blade flies a few blocks and bursts with the
  element's effect (fire alight, ice freezes, lightning chains, earth staggers, air launches, water douses and slows).
  Small damage, growing with Sorcerer level; a short cooldown. It replaces the old sneak+J bolt. Death keeps its tap
  (raise a body); Wild rolls an element each cast.
- **Tree:**
  - Sigilcraft (3 ranks): wider burst, longer flight, shorter cooldown
  - Imbue (level 5): hold J soaks the blade for 2 minutes (today's imbue)
  - Ward Sigil: sneak+J lays the sigil on the ground as a trap
  - Lingering Imbue: twice as long
  - Elemental Burst: end an imbue early in a blast round you
  - Absorbing; Forbidden Arts (Death and Blood) after it, as now
  - Twin Imbue: both hands
  - Etched Blade (top): while imbued, every 4th combo link throws a free sigil
  - *Crossroads:* Ward Sigil or Twin Imbue (level 10); Elemental Burst or Lingering Imbue (level 15)

## The Workshop: Marksman and Tinkerer
Three columns: the Marksman's on the left, the Tinkerer's on the right, and the bench in the middle that both may take.

**Tinkerer from the start:** infusions (Returning, Springing, Glowing; one kept at level 0, `Tinkerers.MOST` reached by
the tree) and the notebook. **Marksman from the start:** the class's gun numbers today, with reload speed growing a
little per level.

**The world tiers: a gifted craftsman's eye (Tinkerer level, no points).** The Tinkerer's level is a second key to
`TierGate`, for them alone: they see, buy, make and use what a tier holds back, its stations included.
- Level 5: Borderlands (pig iron, seared stone and their parts, casts and stations)
- Level 10: Wildlands (silver, rose gold, amethyst bronze)
- Level 15: Dread (slimesteel, steel)
- Level 20: Legend
- Their smiths forge to the grade of whichever tier is higher, their world tier or their craft, and the Tinkerer's own
  forging takes the same ceiling.
The monsters round them stay at their real world tier: the gear comes early, the danger doesn't.

**Marksman column**
- Steady Aim (3 ranks): standing still and crouched, the aim assist widens and the shot hits harder per rank (5e's *To Aim*)
- Speed Load: every few kills, the next reload is instant
- Called Shot (level 10): sneak + fire aims at the head, arm or leg: a heavy hit, a dropped weapon, or a limp (Crippled, short)
- Quick Shot (level 15): a foe that swings at you or a friend within 10 blocks while your gun is loaded takes a shot first, once per short rest
- Precision (level 20, top): a full-strength shot that lands on an unaware or Crippled foe is always a critical

**The bench (both)**
- Gunsmith: make muskets, flintlocks and shot at the stations (a Marksman without it buys them)
- Upgrades (3 ranks): fit a gun with a part at a station, one per rank: Muffled (no Senses noise), Long Barrel (range), Choke (blunderbuss spread), Bayonet (a melee blow)
- Clockwork Scope: zoom on a gun or crossbow, and the shot reaches further
- Trick Shot: grapple, oil, water and shrapnel bolts from the stations (oil sets up fire, water sets up lightning)
- Field Kit: small repairs away from a station

**Crossroads in the Workshop:** Called Shot or Quick Shot (Marksman, level 10); Iron Defender or Iron Hound (Tinkerer, level
18); Upgrades' parts are a pick each, never all four.

**Tinkerer column**
- Spare Hands (2 ranks): one more infusion kept per rank
- More infusions: Muffled boots (mobs notice you from less far), Warded chest (slower freezing and burning), Keen blade (slowly mends)
- Contraption (level 5): a throwable bundle, built in a few seconds from scrap, that bursts with a random effect: flash, net, healing mist, water, oil slick, bang, or a dud. Educated Guess (a rank) lets you throw again without it vanishing on a dud
- Dismantle (level 10): take apart a trap or a lock at a touch, and deal heavy damage to golems and clockwork foes
- Iron Pup (level 12): a little clockwork dog that fetches, follows and bites; it grows at level 18 into an Iron Defender (guards you, patches you up) or an Iron Hound (fast, fights, scouts)
- Imitation (level 22, top): study an ability by watching it once, no notebook time

## Warlord and Brute: two sides of one coin
The ask: "one more controlled warrior and the other a rageful berserker type". The two trees have the same shape, row
for row, each answering the other. Each class gets a gauge on the HUD.
- **Warlord, Composure:** built by full-strength blows, blocks and parries; a clean hit taken knocks some of it away.
  Full Composure is what the Warlord's best moves spend.
- **Brute, Rage:** built by blows dealt and taken; it fades out of a fight. While raging the Brute hits harder and
  can't block, drink or eat. That is the price.

| Level | Warlord (Discipline) | Brute (Fury) |
|---|---|---|
| 0 | **Measured Strikes** (3): full-strength blows build more Composure; each rank, a little more damage at full Composure | **Blood Up** (3): Rage builds faster; each rank, more damage per point of Rage |
| 3 | **Riposte:** a blow within 1 s of a block hits 50% harder and staggers | **Pain Feeds:** blows taken while raging heal a sliver back |
| 5 | **Shout** (from Warfare): orders that steady friends near you | **Throw** (from Warfare): hurl your weapon, and it comes back hard |
| 8 | **Throw Net** (from Warfare): snare a foe | **Warrior's Charge** (from Warfare): a reckless run that knocks foes down |
| 10 | *Crossroads:* **Sword and Board** (shield bashes stagger; blocking builds double Composure) or **Two-Handed Master** (two-handed swings sweep wider and pierce armour) | *Crossroads:* **Reckless** (no shield: +25% damage while raging) or **Juggernaut** (heavy armour weighs nothing while raging) |
| 15 | **Read the Foe:** at full Composure, the parry window is twice as wide | **Shrug It Off:** at full Rage, no knockback and no stagger |
| 20 | *Crossroads:* **Commander** (orders reach twice as far, and your hirelings and companions follow them) or **Duelist** (+30% damage when only one foe is near) | *Crossroads:* **Blackout** (Warrior's Last Stand: at full Rage you can't go down until it runs out) or **Bloodlust** (each kill while raging refills Rage and some health) |
| 24 | **Perfect Form** (top): spend full Composure on a blow that can't miss and staggers anything, bosses too | **Rampage** (top): spend full Rage to cleave every blow for 8 s; then you're spent, slow and weak for a while |

Their class paths (champion and battlemaster; berserker, juggernaut and wrestler) stay as they are, on top of the trees.

## Rider: the saddle
The Equestrian systems are the Rider's tree: gaits and stamina, trust, the horse's disciplines, the herd's orders,
the mounted charge, the second rider, the banner, the mythic mounts and the war elephant.
- **From the start, Spur (class key at the gallop):** a short burst that costs no stamina; its cooldown shortens with
  Rider level.
- Level 0: **Long Wind** (3): your horse's stamina drains less per rank
- Level 3: **Horse Whisperer**: your horse never shies or throws you, and a timid one steadies under you
- Level 5, *Crossroads:* **Lancer** (a spear at the gallop hits harder the faster you go; the mounted charge builds
  twice as fast) or **Horse Archer** (bows and crossbows from the saddle at full speed, no draw slowdown, steady aim)
- Level 8: **Stay in the Saddle**: blows and blasts don't knock you off
- Level 10: **Trick Riding**: hang off the horse's side to shield yourself from arrows; lean down at the gallop to scoop up
  an item or a downed friend
- Level 15, *Crossroads:* **Cavalier** (barding weighs nothing; charges and tramples hit harder; the war discipline levels
  twice as fast) or **Outrider** (faster at the trot and canter; your horse takes no falls; a whistle swaps you onto a
  fresh horse from your herd on the move)
- Level 18: **Herd Master**: the command wheel orders all your horses at once, and they come to your aid without the
  trust a stranger's horse needs
- Level 20: **Banner Bearer**: with a banner flown from your saddle, friends near you fight braver and their horses
  gallop longer
- Level 24, *Crossroads (top):* **Bonded** (your horse goes down instead of dying, and you can bring it round) or **Beast of
  Legend** (mythic mounts and war elephants trust you at once; a bat horse flies twice as long)

## The other classes (from the brainstorm, to fill out)
Each starts with a small class-key move; the tree has the rest.
- **Monk** (starts with Catch an Arrow): Deep Breath (+ki), Deflect, Stunning Blow, Flurry, Quivering Palm
- **Paladin** (starts with Lay on Hands): Smite, Deep Wells, Shield of Faith, an oath node (Spare the Fallen, Judgment, Challenge), Vigil
- **Cleric** (starts with Turn the Undead): Devout (+turnings), Bless the Band, Sanctify Ground, Last Rites, a patron node
- **Druid** (starts with one shape): more shapes, Long Shape, Fox and Stag, Pack Leader, Barkskin
- **Warlock** (starts with one slot): Extra Slot, Patient Creditor, Pay in Kind, Renegotiate
- **Thief, Mage, Ranger:** their old pages, moved over, each given forks of its own
- Every tree gets two or three crossroads, as above

## Settled (2026-11-17)
- Warlord and Brute: mirrored trees, discipline against fury. Rider: its own equestrian tree.
- Builds must cost something: the trees are bigger than the points, and the crossroads make picks exclusive.
- The Tinkerer's tier steps 5/10/15/20 are good for now.
