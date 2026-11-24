---
status: done
for: any
from: Powerpack project chat, 2026-11-22
asked: 2026-11-22
claimed_by: claude-desktop-origins-mod-f1
pr: 1991
suggested_owns:
  - src/main/java/com/jaymospowers/combat/Guard.java
  - src/main/java/com/jaymospowers/stamina/Feathers.java
  - src/client/java/com/jaymospowers/client/stamina/FeathersHud.java
  - src/client/java/com/jaymospowers/client/combat/GuardClient.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Disciplines.java
  - kingdoms/src/main/java/com/jaymoskingdoms/MobParry.java
---

> Settled with Andy in the Powerpack project chat (all questions answered, see "Settled" at the end). Also in the Powerpack project as `claude/Defence-Wards-Plan.md`. Big enough to build in stages: the player side (sections 1-7, 9) first, then enemies (section 8).

# Plan: the three disciplines of defence

The ask (2026-11-22): "fights should be more decisive but also skillful." Three kinds of defence: **passive blocks,
active blocks and parries**. Any blow from in front of you, or any blow while you're locked on, is blocked by itself,
and one of your stamina icons turns into a shield icon to show the blocked blow. You get one of these by default (more
with a skill). After that, blows go through unless you block or parry by hand. Blocks after that cost stamina. A parry
gives back your auto-blocks and stamina. The parry window is tighter unless you have the skill.

Andy's changes the same day:
- Draft 2: "Restore the 60% block, and wards are a good name for what magic users can do or ki based z fighters,
  everyone else should raise their weapon to block automatically with the current fx, sparks, splinters, etc. Blocking
  with fists without ki or valor still hurts 90% so you have to parry. Parrying with fists should disarm the enemy,
  dropping it to the floor, (you should be able to take it straight from them with a skill)"
- Draft 3: "only armed enemies get wards, more as world teir increases, and a max of 5 on experienced enemies"
- Draft 4: "Snatch should be on the strength and rogue tree, and unlocked automatically by monks". And: the kind of
  passive block should come from the **character, not the weapon in hand**: ki, martial or magic. "A soldier wouldnt be
  able to concentrate on a ward just because he found a staff, and likewise a mage wouldnt have the dexterity to raise a
  guard instinctively. They should still be able to train any of these skills, just they have that specific inclination."
- Draft 5: the open questions answered (see "Settled" at the end).
- Draft 6: heritage leans corrected: "Warrior born can be either ki or arms, nightborn should be arms stormborn is ki,
  biobuilt/android is all 3, and hollow zombie and skeleton are arms the rest are arcana".
- Draft 7: Warriorborn has both Arms and Ki with no pick; "dawnborn sun is arcana and verdant can do both ki and arms,
  same with frostborn, and emberborn"; Endborn and Divine Arcana, Rubberfolk Ki.

Draft only, nothing built. Numbers are starting points to tune in game. Built on what's in the repo now: `combat/Guard`
(the weapon guard and parry windows), `ClashFx` (block sparks and chips), `stamina/Feathers` and `FeathersHud` (the
stamina row), `core/LockOn`, Kingdoms `Classes`, `Heritage`, `Mana`, `Valor`, `WorldTiers`, `WeaponForms.experienced`,
`MobShields` / `MobParry`.

## Today (for comparison)
| What | Now |
|---|---|
| Passive defence | None. A blow you don't guard lands in full. |
| Weapon guard (hold right click) | Stops 60% of a frontal blow (90° either side), costs 0.6 half-feathers per damage, breaks when you run dry (2 s) |
| Perfect parry | 0-5 ticks from the raise (+2 locked on) |
| Parry | 6-12 ticks from the raise (+4 locked on) |
| Parry refund | Perfect: 4 half-feathers back. Plain parry: nothing |
| Enemies | Raise a blade between swings; parry in its first 8 ticks, and 12% of blows (30% veterans) with it down |

## 1. The three disciplines
Every character's passive blocks come from **disciplines**, not from what's in their hand. There are three:

| Discipline | The passive block | Needs | Effects |
|---|---|---|---|
| **Arms** | **Reflex block:** the weapon or shield snaps up by itself | Something in hand to raise (weapon, shield, staff, tool). Bare fists stop only 10% unless Valor-coated | Today's `ClashFx`: sparks off steel, splinters and chips off wood, the clang. A grey **shield** icon |
| **Ki** | **Ki ward:** the body hardens and turns the blow | A little ki. Works bare-handed or armed | A flare of the user's ki colour. A gold **ki shield** icon |
| **Arcana** | **Ward:** a turning of magic | A little mana (Kingdoms `Mana`). Works bare-handed or armed | A pale rune-ring at the point of impact. A blue **rune shield** icon |

All three stop **60%** of the blow and follow the same rules (section 2). What differs is *who* can use them and what
they need. A soldier who picks up a staff can raise the staff to block it (that's Arms), but can't ward with it. A mage
who picks up a sword can't raise it by instinct until they've trained Arms.

**Inclination: where you start.** Your class decides which discipline comes naturally:

| Inclination | Classes |
|---|---|
| Arms | Warlord, Brute, Rider, Ranger, Marksman, Thief, Tinkerer, Paladin |
| Ki | Monk. Z Fighters (the power) also gain Ki, whatever their class |
| Arcana | Mage, Sorcerer, Warlock, Cleric, Druid |

**Your heritage leans too.** Each heritage (`Heritage.Kind`) takes to one discipline, two, or all three:

| Lean | Heritages |
|---|---|
| Arms | Heartlander, Stoneborn, Deepborn, Nightborn; Hollow's zombie and skeleton looks |
| Ki | Stormborn, Beastfolk, Rubberfolk |
| Arcana | Sylvan, Tideborn, Dragonblood, Dawnborn, Endborn, Divine; the Earthling's Inkmarked look (the Magi kit); Hollow's other looks (Pallid, wraith, reaper) |
| **Arms and Ki** | Warriorborn, Verdant, Frostborn, Emberborn |
| **All three** | Biobuilt (androids) |
| None | Other Earthling looks |

How a lean works:
- **A lean that matches your class** (a Sylvan Mage, a Stoneborn Warlord, an Emberborn Monk): that discipline starts at
  **level 2** instead of 0.
- **A lean that differs from your class** (a Stoneborn Mage): that discipline needs **no teacher** and trains at **half
  speed**, not a quarter. A Stoneborn Mage wards first but learns to raise a guard far sooner than a Sylvan one.
- **Two or three leans** (Warriorborn, Verdant, Frostborn, Emberborn; Biobuilt): every leaning discipline is open from the
  start, no teacher, and no choice to make. Each one follows the two rules above on its own: the one that matches your
  class starts at level 2 and trains at full speed, the others train at half speed. A Warriorborn Warlord has Arms at
  level 2 and Ki open at half speed; a Warriorborn Mage has Arcana from the class and both Arms and Ki open at half speed.

**Training the others.** Anyone can learn any discipline. Each has a level from 0 to 10, earned by using it (blows
caught, parries in that discipline's style):
- **Your class's inclination** starts unlocked and levels at full speed.
- **Your heritage's leans**, where different: unlocked, half speed (above).
- **Anything else** starts locked. A teacher opens it: the drill yard's master-at-arms for Arms, a monk for Ki, a court
  wizard for Arcana (each for a fee, once). After that it levels at **a quarter** of the speed. That's the inclination:
  a mage *can* learn to raise a guard by instinct, it just takes far longer than for a soldier.

**How many passive blocks you get:**

| Discipline level | Inclined | Trained |
|---|---|---|
| 0 | 1 | 0 |
| 3 | 1 | 1 |
| 5 | 2 | 1 |
| 8 | 2 | 2 |
| 10 | 3 | 2 |

Your blocks from every discipline you have add together, up to **5 in all**, the same ceiling as the most experienced
enemies. A blow is caught by your inclination's blocks first, then by whatever else you have that works right now (a
reflex block only if your hands hold something, a ki ward only with ki to spare, a ward only with mana).

## 2. Rules every passive block shares
- **What it catches:** a blow from your front (within the guard's 90° either side), or from anywhere while you're
  locked on. Arrows too.
- **What it costs:** one feather icon (two half-feathers) at the right end of the row is locked and drawn as that
  discipline's shield. A shield icon can't be spent and doesn't regrow. It stands for the blow you caught. Ki and Arcana
  also take a sliver of ki or mana.
- **Spent:** once every block you have is a shield, the next blow lands in full unless you guard or parry it yourself.
- Red glints (heavy blows) and gun balls use one up and still get only 60% stopped.
- Not caught: blows from behind or the side when you aren't locked on, falls, fire, magic, and a broken guard (none
  until it mends).
- **How they come back:**
  - A parry, any kind (section 5).
  - Out of the fight: 6 s without being hit or swinging, the shields turn back into feathers one at a time.
- **Locking on is now a choice.** It covers blows from every side, but it holds your eyes on one foe.

## 3. Active block: the guard you hold
- Hold right click, as now: **60%** of a frontal blow stopped, **0.6 half-feathers per point of damage**, as now. Anyone
  can do this with a weapon; it's a deliberate act, not an instinct, so it needs no discipline.
- Run the row dry and the guard breaks: 2 s without guarding, a 0.75 s stagger, and no passive blocks until it mends.
- A blow caught by a held guard never uses up a passive block. Guard when you see it coming, and save the passive block
  for what you miss.
- Shields still block for no feathers. That stays as the shield's reason to exist.

## 4. Fists
- **Blocking bare-handed hurts.** A held guard or reflex block with empty hands stops only **10%**, so you take 90%. The
  fist fighter's defence is the parry.
- **Ki or Valor make them hold.** Anyone with Ki (Monks, Z Fighters, the Ki-leaning heritages, anyone taught) wards
  bare-handed at the full 60%. Valor-coated fists (Kingdoms' `Valor`, already the coating that gets through magic) hold
  at 60% too.
- **Parrying bare-handed disarms.** A fist parry, perfect or plain, knocks the attacker's weapon out of its hand. It
  drops to the floor at their feet with a short pickup delay, and mobs scramble to pick it back up. The rest of the parry
  (stun, feathers back, riposte) happens as usual. A foe with nothing in hand gets today's stun only. The pattern exists
  already: `GravityOverturn.disarm` empties the main hand and drops the item.
- **Players too.** In PvP a fist parry drops the other player's weapon to the floor the same way, and Snatch can take it.
- **Snatch:** a *perfect* fist parry puts their weapon straight into your empty main hand instead of on the floor. Monks
  have it from the start; anyone else learns it on the Strength page or the Thief's tree (section 7).
- Red glints still can't be parried, so a fist fighter must dodge a heavy blow.
- A disarmed enemy loses its passive blocks (section 8) until it picks its weapon back up.

## 5. Parry: the reward
**Tighter by default.** Windows count from the raise, as now:

| | Untrained | Keen Timing 1 | Keen Timing 2 | Keen Timing 3 (today's windows) |
|---|---|---|---|---|
| Perfect parry | 0-2 ticks (0.10 s) | 0-3 | 0-4 | 0-5 |
| Parry | 3-6 ticks (0.30 s) | 4-8 | 5-10 | 6-12 |
| Locked-on bonus | +1 / +2 | +1 / +2 | +2 / +3 | +2 / +4 |

How this plays against our tells: a mob's glint comes 6 ticks before its blow (`AttackTells.WINDUP`). Raise on the
glint and an untrained player's window has closed by the time the blow lands (tick 6). To parry, an untrained player
waits about 3 ticks after the glint. That is the skill. A trained player can raise on the glint and still be in time.

**What a parry gives back:**
- **Perfect:** every shield icon turns back into a feather (all passive blocks back), +4 half-feathers, and today's stun
  and 1.5x riposte.
- **Parry:** one passive block back, +2 half-feathers, and today's 0.6 s stun and 1.25x riposte.
- The mashing rule stays: a raise within 15 ticks of one that parried nothing gets no window.
- Z Fighter's and Darktide's guards, and the Warlord's Read the Foe (double window at full Composure), all fire
  `Guard.PARRIED`, so they give blocks back the same way. Their own windows aren't tightened.

## 6. Decisive: what a clean hit does
A blow that gets past a passive block and a guard lands at its normal damage, plus a **0.4 s flinch**: no sprinting, and
a swing in progress is cut short. Red blows also knock you back. No extra damage.
- The same goes for enemies: break through their blocks and your clean hit makes them flinch, cutting their swing.
- Riposte chains (Crowds patch 7) stay, so a good parry against a crowd snowballs the other way.

## 7. Skills
Discipline levels replace draft 3's Stalwart: more blocks now come from training a discipline, not from points.

| Skill | Where | Level | Ranks | Effect |
|---|---|---|---|---|
| **Keen Timing** | Strength | 5 | 3 | Parry windows widen, rank by rank, back to today's (table above) |
| **Snatch** | Strength, and the Thief's tree (Rogue's Arts) | 8 | 1 | A perfect fist parry takes the foe's weapon straight into your empty hand. **Monks have it from the start, free** |
| **Iron Guard** (optional) | Strength | 10 | 1 | A held guard costs 0.4 half-feathers per damage instead of 0.6 |

- Snatch bought on one page counts for the other: a Thief who takes it in Rogue's Arts doesn't need it on Strength.
- On the Thief it suits the class: lifting a blade out of a hand is pickpocketing in a fight. On Strength, anyone can reach it.
- The Monk's tree (Class-Trees-Plan: starts with Catch an Arrow) gets Snatch as a second starting move beside it.

## 8. Enemies
**Only armed enemies get passive blocks.** A mob holding a weapon or shield (guards, bandits, orcs, duellists, illagers,
armed skeletons and zombies) gets them under the same rules as yours. Their discipline comes from what they are, not
what they hold, the same as yours:
- **Arms:** soldiers, bandits, orcs, duellists, pirates, undead with weapons. Reflex blocks, sparks and splinters.
- **Arcana:** casters (evokers, enemy mages, cultists, witches holding a weapon). Wards and the rune-ring.
- **Ki:** fighting monks and other ki users, if the pack has or gets any.
- Armed villagers and folk of a heritage (MCA guards, Kingdoms' realm soldiers) could take their heritage's lean the same
  way; until that's built they count as Arms.

Unarmed mobs (spiders, wolves, bare-handed zombies, beasts) get none: they're beaten by hitting them. A caster with no
weapon in hand gets none either (it isn't armed), even though its ward wouldn't need one.

**How many, by world tier** (`WorldTiers`). Experienced enemies are the ones `WeaponForms.experienced` already marks:
leaders (kings, lords, chiefs), a ruler's escort, assassins, the two wealthiest realms' guards, folk grown 12+ days and
illager captains.

| Tier | Armed enemy | Experienced |
|---|---|---|
| Hearth | 1 | 2 |
| Borderlands | 1 | 2 |
| Wildlands | 2 | 3 |
| Dread | 3 | 4 |
| Legend | 4 | **5 (the most)** |

- **Breaking through:** each blow you land on its front uses one up. With none left, your next blow lands clean (the
  flinch). Hit from behind or the side and none are used.
- **Coming back:** one every 4 s once it hasn't been hit for 3 s, and all of them when it lands its own parry
  (`MobParry`). A parry of yours, or a guard break, takes **all** its blocks at once, so a parry is the fast way through
  a veteran.
- **Telling how many it has:** when you're locked on, small shield pips over its head in its discipline's colour,
  greying out as you break them. Not shown otherwise, to keep the screen clear.
- **Their own parries stay, all of them:** the raised-blade parry (8-tick window) and the 12% (30% veterans) chance to
  parry with the guard down. The chance is rolled **before** a passive block: a blow it parries is turned aside and you're
  pushed back, and no block is used. So veterans are hard to break, and the way through is to parry them first.
- Bosses (100+ health) keep their own rules and get no blocks unless a boss is given them by hand.

## 9. The HUD
- The feathers row (`FeathersHud`) gets three icons in the feather's 9x9 slot, added to `textures/gui/feathers.png`
  through `tools/gen_feathers.py`, all the same heater-shield shape:
  - **Arms:** iron grey with a dark rim.
  - **Ki:** gold, with a soft glow.
  - **Arcana:** pale blue with a glyph.
- A used block is drawn as its shield at the right end of the row, left of any strain. Blocks still free show as a faint
  shield outline over that icon's feather, so you can see how many you have and of what kind.
- A parry giving blocks back: the shields flash gold and turn back into feathers.
- A fist disarm: the dropped weapon gets the riposte glint for a moment so you can see where it landed.
- An enemy's pips over its head while locked on (section 8).
- The K screen shows your three discipline levels, your class inclination and heritage leans marked, and the locked ones
  with where to find a teacher.

## Where it goes in the code
- Kingdoms, new `Disciplines`: the three levels per player (an attachment), the inclination from `Classes` (and Ki from
  the Z Fighter power), the leans from `Heritage` as a set per kind (by variant for the Hollow and the Earthling), the
  level-2 start or the half-speed unlock for each, the teachers' unlock, XP by use (full, half or a quarter speed), the
  block count table.
- `combat/Guard`: the passive check in the damage hook (frontal or locked-on, which discipline catches it and whether it
  can right now: hands, ki, mana), the red/gun rule, the 10% bare-handed share unless Ki or `Valor`, the new windows from
  Keen Timing, the refund on `PARRIED`, the fist disarm (players included) and Snatch, the clean-hit flinch.
- `ClashFx`: Arms reuses today's effects; new ki-flare and rune-ring effects.
- The guard pose (`GuardClient`, `GuardFirstPersonMixin`): played for a few ticks on a reflex block.
- `stamina/Feathers`: a "held as blocks" count per discipline beside weight and heat (locked icons that neither spend nor
  regrow), synced in the packed long.
- `client/stamina/FeathersHud` and `client/combat/GuardClient`: the icons, the flash, the timing bar's narrower gold.
- Kingdoms `MobShields` / `MobParry`: an enemy's block count (tier and experienced) and discipline (by mob kind), the mob's
  guard pose on a block, the guard-down parry rolled before a block, the flinch on a clean hit. `LockOnClient`: the pips.
- Kingdoms `Skills` and the class trees: Keen Timing and Iron Guard on Strength; Snatch on Strength and Rogue's Arts;
  Snatch given at the start to Monks.

## Settled (2026-11-22)
- **Inclinations:** as drafted. Paladin under Arms, Sorcerer, Cleric and Druid under Arcana.
- **Clean hits:** a flinch only, no extra damage.
- **Block recovery:** 6 s out of the fight, as well as parries.
- **PvP disarm:** yes, a fist parry drops a player's weapon, and Snatch can take it.
- **Training outside your inclination:** a quarter of the speed.
- **Enemies' guard-down parry chance:** kept alongside their passive blocks.
- **Heritage leans:** the table in section 1. Multi-lean heritages (Warriorborn, Verdant, Frostborn, Emberborn, Biobuilt)
  get every leaning discipline open with no pick.

## Notes
- 2026-11-22 10:23 AM Claude (desktop session): built in PR #1991 (green): passive blocks + HUD shields + tighter parries + fist disarm/Snatch + flinch (Powers), Disciplines with class inclination, heritage leans, levels and Keen Timing / Snatch / Iron Guard (Kingdoms), enemies' blocks by tier incl. armed MCA villagers by heritage lean (Andy: 'add the same capabilities to MCA'). Left for a follow-up: teachers that open locked disciplines, the K-screen display, lock-on pips, mobs picking dropped weapons back up. Open choice for Andy: a non-monk's ki ward costs a half-feather.
- 2026-11-22 11:19 AM Claude: part 4 pushed to #1991 (green): attunement gate for Ki/Arcana, amethyst 30% knack by strike or carry (players and MCA), teachers, K-screen display, lock-on pips, weapon scramble. Lore.md Attunement paragraph has a (new) line.
- 2026-11-22 12:00 PM PR #1991 merged (Andy: "Merge").
