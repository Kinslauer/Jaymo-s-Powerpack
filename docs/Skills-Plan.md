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

### Hoods (Kingdoms 0.7.98, with Powers 0.13.36)
Andy: "id like any hooded costumes to add to stealth, while not completely concealing identity like it does now".
Powers tags a player whose costume mask is a Hood or a Peaked Hood (head piece on, face showing) with
`jaymospowers_hooded:<key>`; Kingdoms reads only the tag (`Identities.hoodKey`, `hooded`, `hoodSight`).
- Seen less far: witnesses see a hooded player at 0.75 of the usual range (`Identities.HOOD_SIGHT`), on top of
  sneaking and Soft Step: a crime's witnesses (`Theft.witnesses`, 16 blocks: 12 hooded, 6 hooded and sneaking), house trespass
  (`Trespass.SEE_RANGE` 12: 9) and good deeds (`Deeds.WITNESS_RANGE` 16: 12).
- A fragile mask: to a settlement that doesn't know the hood, a hooded player is the hood's own identity, "a hooded
  figure" (its reputation, crimes and wanted time, like a mask). Unless recognised: when a crime is seen (anything put
  through `Theft.commit`: thefts, pickpocketing, assault, murder, arson, trespass...), a witness within 6 blocks
  (`Identities.HOOD_KNOWN_RANGE`), facing the player (the same 0.2 gaze as a mask coming off) and not in the dark
  (`Heritage.dark`: night outdoors, or light 7 or less) sees the face under the hood. The settlement then knows who
  wears it, and the crime is the player's. Seen from further, from behind or in the dark, it stays on the hooded
  figure. A pickpocket's mark that turns on them counts as facing them. Golems don't recognise anyone.
- Lowering the hood where a settlement's people see it gives it away like taking off a mask (once per settlement);
  pulling it up gives nothing away. A paranoid king's guards stop hooded strangers too ("lower that hood").
- MCA's villagers still know a hooded friend: only a full mask hides you from their memories (`MASKED_AS`).

### Hidden blade (Kingdoms 0.7.98)
Andy: "They also need a hidden blade weapon, craftable at all times but usable only by those with sufficient stealth
skill. It enables jumping from height and converting your fall damage into damage dealt to the enemy, leaving them
crippled for the duration, and hidden blade stabs deal 10x damage when attacking crippled enemies."
`HiddenBlade`, numbers at its top.
- The item (`jaymoskingdoms:hidden_blade`): a leather bracer with a spring blade. Iron durability (250), 4 attack
  damage, 2.2 attacks a second (a dagger; Better Combat dagger moves, in `light_blades`). Crafted any time:
  ` I ` / `LNL` / ` S ` (iron ingot, leather, iron nugget, string).
- Needs Stealth level 5 (`BLADE_LEVEL`). Below it the spring jams: its blows do 1 damage and there is no leap.
- Leap: falling at least 4 blocks (`LEAP_MIN_FALL`) onto a living thing with the blade in either hand, you take no
  fall damage; what you would have taken (vanilla's: blocks fallen less safe fall, times the fall damage
  multiplier, rounded up) is dealt to it as your blow (crimes, witnesses and kill credit as usual), and it is
  Crippled for 6 s, a second more for every 2 blocks past 4, at most 20 s (`CRIPPLE_BASE_TICKS` 120,
  `CRIPPLE_STEP_TICKS` 20, `CRIPPLE_MAX_TICKS` 400). A miss is an ordinary fall. Caught in the air each tick (the
  fall swept from last tick's feet to 0.6 below), and again on landing (anything standing where you land).
- Crippled: 70% slower, no jumping, a player can't sprint (`CRIPPLE_SLOW`, `CRIPPLE_JUMP`); it limps with little
  marks of pain and a crippled player sees how long is left. Kept with the creature (saved), so an unloaded chunk
  doesn't cure it.
- The stab: every full-strength blow (a swing charged 90%) with the blade in the main hand at a Crippled foe deals
  10x (`CRIPPLED_STAB`), after the class perks and the Combat Arts.
- The assassin who comes for rulers now draws a hidden blade (his blows and stab unchanged).

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
- Weapon Forms (lvl 2, second root, Kingdoms 0.7.30): every weapon's full Better Combat combo. Without it a weapon
  has only its first two plain swings (our twirl/spin/flip flourishes skipped). Andy, 2026-10-15: "weapon combo
  animations are unlocked within the strength skill tree. simple one or two attacks otherwise. like most mobs. some mobs
  will be more experienced like storied guards or kings." Mobs (`MobCombat`) do the same; `WeaponForms.experienced`
  gives the whole combo to leaders (kings, lords, chiefs), a ruler's escort, assassins, the two wealthiest realms'
  guards, folk grown 12+ days, and illager captains.

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
