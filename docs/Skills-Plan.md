# The skill tree: Survival, Stealth, Strength

Andy, 2026-10-13: "can you add a system involving stealth that upgrades your chances to steal the more you do it
successfully? we could make a skill tree maybe, pillaring Survival, Stealth, and Strength?"

## Where it lives
In **Jaymo's Kingdoms** (`kingdoms/`, `Skills` + client `SkillScreen`). Stealth works on Kingdoms' own systems
(pickpocketing, theft, witnesses), and Kingdoms doesn't depend on Powers, so the tree sits beside them. It's open to
every player whatever their power: it never touches a power's abilities or mastery, and it has its own key, **K**
(Powers keeps R/G/V/B, C, Z, H; Equestrian has N and J).

## How every pillar works
- Each pillar has its own XP, earned by doing what it's about, and levels 0-15 (level l → l+1 takes 30 + 15·l XP).
- **Practice:** each level improves the pillar's core thing a little by itself (Stealth: marks feel your hand 3%
  less per level).
- **Points:** each level is one point for that pillar's perks. The tree has 15 points of perks, so a master of a
  pillar has all of it. Perks have ranks, prerequisites above them, and a few need a pillar level.
- Progress is saved and kept through death.

## Stealth (built, Kingdoms 0.5.51)
XP: a pocket picked clean (+10; +2 empty; a guard's ×2, a sleeper's ×½), a theft nobody saw (+4, at most every 30 s).

```
             Light Fingers (5)
            /                 \
   Deep Pockets (3)        Soft Step (3)
          |                      |
   Quick Hands (lvl 5)      Slip Away (lvl 5)
            \                 /
              Ghost (lvl 10, 2 pts)
```
- Light Fingers: the hold is 8% shorter per rank (practice: 3% shorter per level).
- Deep Pockets: more of what a mark carries; another try at an empty pocket per rank.
- Soft Step: sneaking, onlookers see you from less far away.
- Quick Hands: reach in from the side; marks must turn further to catch you.
- Slip Away: half the time a mark turns on you, they can't tell who: no crime.
- Ghost: guards take no longer to rob than anyone; unseen thefts never noticed later.

## Survival (later stage, proposal)
XP: eating, surviving each night outdoors, foraging (berries, crops, fishing), enduring hunger, cold and heat,
taking and living through damage.
Practice: hunger drains a little slower per level.
Perks (draft): Iron Stomach (raw and rotten food safe), Forager (more from crops, berries, fish), Second Wind (heal
faster at low health), Weathered (cold and heat, rain), Hardy Landing (a little less fall damage, never none: falls
always hurt outside creative), Last Stand (capstone: once a day, a killing blow leaves you at half a heart).

## Strength (later stage, proposal)
XP: landing melee blows, kills, mining, chopping (TreeChop felling), carrying corpses and blocks.
Practice: a little more melee damage per level.
Perks (draft): Heavy Blows (damage), Brace (less knockback taken), Bulldozer (knockback dealt), Quarryman (mine and
chop faster), Packhorse (carry heavy corpses and blocks without slowing as much), Titan's Grip (capstone).

## Open questions for Andy
1. Survival and Strength perks above are drafts: keep, change, or swap any?
2. Should anything reset the tree (a respec item or price at a court wizard)? None for now.
3. Should a pillar's level show to others (e.g. a reputation as a known thief in Kingdoms)? Not now.
