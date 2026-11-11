# Plan: Boxing (three bare-fist styles)

Proposed by Jung (Wind). **Nothing here is built, and Andy has not ruled on it.** It is a design for Andy to accept,
change or refuse. It is a core combat system, not part of any one power, so it is written as a plan instead of code.

"Decided" below means decided by Jung in the design talk. "Proposed" means a number or detail the plan suggests that
Jung did not set. The open questions for Andy are at the end.

Work is split into tracks so agents can run in parallel without touching the same files.

---

## The idea

A boxer is weak outside 2 blocks and overwhelming inside it. There are three styles: **peekaboo** (inside pressure),
**long guard** (range and control) and **Philly shell** (counters).

- **Punch:** left click. **Block:** right click. **Evade:** the dodge key (Left Alt), which becomes the style's evade
  instead of a roll while boxing.
- **Fist gear:** a style only works with fist gear in hand. Hand wraps are the most basic.
- **Timing:** an evade pressed on the incoming-blow glint (the peak of the attack) is a perfect evade and earns the
  style's strong attack.
- **Progress:** each style has 10 levels. Levels make punches faster and moves cheaper on stamina. They never add damage.
- **Fairness:** a combo breaker after 6 hits, a visible tiring and gassed state, and blur limited by a cooldown.

## What it reuses

- **The glint:** the incoming-blow cues and the guard's perfect-timing window (`IncomingBlows`, `Guard`).
- **The dodge key:** Left Alt and the roll (`Dodge`, `DodgeClient`, `DodgeRollPose`), swapped for the style's evade.
- **Stamina:** `Feathers` (20 feathers, two to an icon), including its exhausted state and `RECOVER_SHARE` 30%.
- **Right-click guard:** blocking stays on right click, as it is for weapons.
- **Poses and screen effects:** the idles, evades and blur are the same kind of work as the kick poses (`WindPose`)
  and Wind's screen effects (`WindScreenFx`).

---

## Track 1 — Core rules (Powers, server)

### 1.1 Styles and levels
**Decided:**
- Three styles. A player learns one at a time, and can only start another after mastering the one before.
- Each style levels from 1 to 10. Level 10 is full mastery.
- A style only works with fist gear in the main hand (Track 4).

**Needs:** a per-player record of which styles are known and each one's level (a persistent attachment, listed in
`JaymosPowers.registerAttachments()`), synced to the owner for the HUD and to everyone for the idles.

### 1.2 Range, damage, stun
**Decided:**
- Range is 2 blocks for every style. Long guard also has 2 blocks, and moves in and out of it with its pull.
- Little stun. A well-trained boxer's punches stun about as much as a dagger.
- Bare fists lose to armor. Gear bridges the gap.

**Proposed:** a basic punch does 1.5 to 2 against an unarmored target, so a 6-punch burst is about half an unarmored
player's health and a kill takes two or three exchanges.

### 1.3 Punch speed by level
**Decided:** a beginner throws a punch every 0.8 s. A master throws at most 3 a second.

One game tick faster per level, from 16 ticks at level 1 to 7 ticks at level 10 (7 ticks is the closest the 20-tick
clock gets to 3 a second without passing it):

| Level | Ticks between punches | Seconds | Punches a second |
|---|---|---|---|
| 1 | 16 | 0.80 | 1.25 |
| 4 | 13 | 0.65 | 1.5 |
| 7 | 10 | 0.50 | 2.0 |
| 10 | 7 | 0.35 | 2.9 |

### 1.4 Hit immunity
**Decided:** masters break the hit-immunity barrier.

Hit immunity lasts 10 ticks, which a boxer matches exactly at level 7. From level 8, a boxing punch shortens the
target's immunity to the boxer's punch interval. Only boxing punches do this. Below level 8, a punch thrown as the
immunity ends lands in rhythm.

### 1.5 Stamina
**Decided:** boxing spends the pack's stamina. One "shank" is one icon (2 feathers).

| Move | Level 1 | Level 10 |
|---|---|---|
| Punch | 1 shank | 4 punches per shank |
| Slip, roll or pull | 1 shank | 2 per shank |
| Strong attack (uppercut, pull counter, roll straight) | 1 shank | 1 shank |
| Each blocked attack | 1 shank | 1 shank |
| Each blocked attack, peekaboo guard | more than 1 | same |

Costs between level 1 and 10 scale evenly. Strong attacks and blocks never get cheaper.

**Proposed:** the peekaboo guard costs 1.5 shanks (3 feathers) per blocked hit. Feathers are whole numbers, so
fractional costs need a running remainder per player.

### 1.6 Tiring and gassed
**Decided:**

| State | When | What everyone sees | Effect on the boxer |
|---|---|---|---|
| Fresh | 5 or more icons | the style's idle | none |
| Tiring | 4 icons or fewer | a faint, slow breath puff; the idle still plays | none (a warning) |
| Gassed | stamina empty, until the bar is back to 30% | a clear breath puff; heaving shoulders replace the idle | 45% less damage, never more than 2 a hit; extremely slow punches (Mining Fatigue) |

- The breath puff is silent in both stages.
- Recovery at 30% is `Feathers.RECOVER_SHARE`, so boxing needs no threshold of its own.
- Vanilla Weakness removes a flat 4 damage, which would zero out a punch, so the 45% cut stands in for it.

### 1.7 Combo breaker
**Decided:**
- After 6 hits in a row, the player being hit automatically slips out of the combo and gets breathing room.
- Blocked punches do not count toward the 6.
- The breaker clears any blur on the player it frees.

**Proposed:** the count resets when the victim blocks, evades or goes a couple of seconds unhit; the freed player
slips about 3 blocks out and that boxer's punches cannot land on them for about a second.

---

## Track 2 — The three styles (Powers, server and client input)

Punches are chosen automatically from where the boxer is and what they just did. The player only left clicks.

| | Peekaboo | Long guard | Philly shell |
|---|---|---|---|
| Left click | a one-two, then alternating hooks, looping | jab, jab, cross | the right jab only |
| Evade (Left Alt) | slips: left, right, or a bob straight in | pulls: a lean and step back out of range | shoulder roll in place, or a slip to either side |
| After an evade | slip right: right hook; slip left: left hook; bob in: uppercut | the pull steps out; the counter steps back in | a left straight after the roll |
| Strong attack | uppercut after a perfect bob | pull counter after a perfect pull | roll straight after a perfect roll |
| What makes it special | chains evades into punches | the longest reach of any attack in the system | the highest damage of any attack in the system |
| Block (right click) | high, tight guard close to the head and body | cross block in front of the shoulders | the right shoulder rounds off and the left hand tucks in tighter |

### 2.1 Peekaboo
**Decided:** slips replace the roll. The punch after a slip follows the side slipped to; a bob straight in gives the
uppercut. Its guard drains more stamina than the others.

### 2.2 Long guard
**Decided:** pulls replace the roll. A pull timed on the glint delivers the pull counter. The pull counter has the
most range of anything in the system.

**Proposed:** the pull counter wins on reach, knockback and stagger, not damage, since the shell owns top damage.

### 2.3 Philly shell
**Decided:** southpaw: the right shoulder leads and rolls. It throws the right jab only. After a shoulder roll it
throws a hard left straight, the most damaging attack of the three styles. It is only truly strong on a perfect roll.

**Proposed:** a roll that is not perfect gives a weaker straight, and still glances the blow for reduced damage.

### 2.4 Blur and daze
**Decided:**
- Each style's strong attack blurs the vision of a player it hits, and only when earned with perfect timing.
- After being blurred, a player cannot be blurred again for 5 seconds.
- Against mobs the same attacks daze instead. Undead are never dazed.
- A boss is dazed for 1.5 seconds at most, whatever the attack.

**Proposed:** blur lengths of about 1 s (uppercut), 1.5 s (pull counter) and 2.5 s (roll straight), scaled to how
hard each is to land. The blur must not hide the glint or the HUD, or a blurred player could never defend.

---

## Track 3 — Look and HUD (Powers, client)

### 3.1 Idles
**Decided:** each style has a moving idle. It plays only with fists up, and it deliberately reveals the style to
opponents.
- Peekaboo: sways slightly side to side.
- Long guard: the lead hand paws the air to measure distance.
- Philly shell: shrugs the right shoulder every so often.

### 3.2 Evade, punch and block poses
One pose set per style: the slips and bob, the pull and counter, the shoulder roll and straight, and the three guards
from the table in Track 2.

### 3.3 Tiring and gassed
**Decided:** the breath puffs from 1.6 (no sound), and heaving shoulders in place of the idle while gassed.

### 3.4 Blur
The screen effect for 2.4.

### 3.5 HUD
**Decided:** the HUD shows the name of the style and its level.

---

## Track 4 — Fist gear (Powers items)

**Decided:**
- Three kinds: wraps, cestus, gauntlets. Wraps count as fists for every fist-only quest.
- Hand wraps are the most basic: rags made from plant string or string.

**Proposed:**
- Wraps for speed, cestus for damage, gauntlets for blocking.
- Tiers in leather, iron and steel (gold and diamond gear are off in the pack).
- Anything gear adds on a hit (stun, bleeding) applies once per burst, not on every punch.

**Needs:** items, recipes, models and textures (a `ground` pose for each held model), and a `runServer` boot check,
since this track adds items.

---

## Track 5 — Teachers and quests (Kingdoms)

**Decided:**
- Village elders and high guards teach boxing. They tend to be the stronger races: Stoneborn, Warriorborn, Emberborn
  and Earthling.
- To learn a style, the teacher sets a quest: kill a set number of hostile mobs with fists only. Only hostile mobs
  count, and wraps count as fists.
- Sparring is simply fighting in general, and it only counts while the opponent is hitting back. A teacher can also
  train the player, like the battle training guards already give.
- On the way to level 10 the teacher gives quests to defeat stronger mobs with fists only.

**Proposed:**
- About 15 hostile kills for the first quest.
- Each teaching race owns a style: Emberborn peekaboo, Stoneborn long guard, Earthling Philly shell, with Warriorborn
  teaching any.

Track 5 reads Track 1's record of styles and levels. Kingdoms should reach it through a compat class, as other
cross-mod reads do.

---

## Order

Track 1 first: the others read its styles, levels and stamina rules. Tracks 2 and 3 follow it and can run side by
side (2 owns input and rules, 3 owns poses and HUD). Track 4 only needs 1.1's "fist gear in hand" check. Track 5 is
last.

---

## Open questions for Andy

1. Whether to add boxing at all, and where it lives.
2. Damage per basic punch (1.5 to 2 proposed) and the damage of the three strong attacks.
3. Blur lengths, and an option to swap the blur for a vignette for players who get motion sick.
4. How much more the peekaboo guard drains per blocked hit (1.5 shanks proposed).
5. The combo breaker's breathing room: how far the freed player slips and for how long that boxer cannot hit them.
6. Whether mobs get the combo breaker, or only players.
7. The size of the perfect-timing window (generous enough for server lag), and whether an early click queues the
   next punch.
8. Gear tiers and recipes.
9. The number of kills for the first quest, and which mobs the later teacher quests name.
10. Whether fist gear and powers stack, for example a Wind user boxing in flight.
11. In-world names for the styles. "Long guard" fits a medieval world; "peekaboo" and "Philly shell" may not.
12. Whether the HUD also shows the combo breaker's count.
13. How "every melee weapon parries and has a skill bar move" applies to fist gear.
