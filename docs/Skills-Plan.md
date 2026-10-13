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

## Stealth (built, Kingdoms 0.5.52)
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

## Survival (built, Kingdoms 0.5.52)
XP: a ripe crop harvested (+1), every 2 hunger points eaten back (+1), a night lived through (+15), taking damage and
living (+1 per 4, every 5 s at most). Practice: hunger drains 2% slower per level.
- Forager (5): 8% chance each of an extra harvest; any rank also makes you experienced with butchering (judge a
  butcher's skill from their cuts, Kitchen 0.4.32).
- Second Wind (3): under 30% health you start to heal.
- Weathered (3): burn shorter, freeze slower.
- Iron Stomach (lvl 5): food never gives you Hunger.
- Hardy Landing (lvl 5): falls hurt 30% less, never not at all. It scales vanilla fall damage only, so powers that
  cancel fall damage (Gravity's Weightless and descents, Wind, Web) work exactly as before.
- Last Stand (lvl 10, 2 pts): once a day, a killing blow leaves you on half a heart.

## Strength (built, Kingdoms 0.5.52)
XP: melee blows landed, monsters killed (+4), stone, ore and wood broken. Practice: +1% melee damage per level.
- Heavy Blows (5): +4% melee damage each.
- Brace (3): knocked back less.
- Bulldozer (3): knock foes back further.
- Quarryman (lvl 5): break blocks 25% faster.
- Packhorse (lvl 5): sprinting, mining and fighting tire you less (hunger).
- Titan's Grip (lvl 10, 2 pts): a full-strength blow may stagger its target.

## Combat Arts (Strength's second page, Kingdoms 0.6.5)

Andy, 2026-10-13: "give the strength skill tree access to even more combat combos and skills". `CombatArts` (numbers
at the top). Strength now goes to level 25 (`Skills.STRENGTH_MAX_LEVEL`; the others stay at 15, and alignment only
counts experience up to level 15 so Strength doesn't win it by default). The 8 new perks cost 19 points, so with the
first page's 15 you can't have everything: pick a build.

- **Combo chain:** melee blows that land within 2 s of each other are a combo (one link a swing, however many a sweep
  or Better Combat arc hits). The count shows on the action bar once you know any chain perk. Works with or without
  Better Combat: the swing's charge is read in `PlayerAttackMixin`, damage in `LivingEntityHurtMixin`.
- Tier 4 (level 15, after Titan's Grip): **Momentum** 3 ranks (+4%/rank per link, up to 5 links), **Cleave** 3 ranks
  (a full-strength blow carries 20%/rank into up to 3 foes beside the target).
- Tier 5 (level 18), finishers on every 4th link when it's full strength: **Earthsplitter** (axes, mace, two-handed,
  bludgeons: shockwave, half the blow to foes round the target, thrown up), **Executioner** (any weapon: double damage
  to a foe under 30%), **Rend** (swords, knives, spears: 1.5 damage a second for 5 s).
- Tier 6 (level 21), active skills on **Y**: **Charge** (shoulder charge about 8 blocks, steered by where you look;
  first foe takes 1.5x attack damage, is shoved and staggered; 12 s), **War Cry** (sneak+Y, or Y if it's the only one:
  foes within 10 weakened and slowed 6 s, you and nearby players Strength I 8 s; 45 s).
- Tier 7 (level 24): **Bladestorm**, every 8th link you whirl and hit every foe round you for the full blow.
- Area blows only touch foes (monsters, and anything targeting you), never bystanders or other players, so a fight in
  town doesn't become a crime.
- The tree screen pages: Strength's "Combat Arts >" button (bottom right) shows tiers 4-7.

## Respec (Andy's answer)
A court wizard unmakes your perks for 5 emeralds + 1 per rank, or drink a bottle o' enchanting (sneak and use it).
Your XP and levels stay; every point comes back to spend again.

## Alignment and gossip (Andy's answer)
Your best pillar, at level 5 and ahead of the others, is your alignment: Survivor, Rogue or Warrior ("Master" at
15). Other players see it over your name tag. Settlements whose people see you learn it, word travels to another
settlement each day, villagers gossip about you, and where you're known for a Rogue pockets take longer to pick.

## Still open
- Packhorse was planned as carrying corpses and blocks without slowing; that lives in Powers/Kitchen, so for now it
  lightens hunger instead. Swap it if you'd rather.
