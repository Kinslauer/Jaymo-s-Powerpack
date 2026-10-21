# The skill tree: Survival, Stealth, Strength

Andy, 2026-10-13: "can you add a system involving stealth that upgrades your chances to steal the more you do it
successfully? we could make a skill tree maybe, pillaring Survival, Stealth, and Strength?"

## Where it lives
In **Jaymo's Kingdoms** (`kingdoms/`, `Skills` + client `SkillScreen`). Stealth works on Kingdoms' own systems
(pickpocketing, theft, witnesses), and Kingdoms doesn't depend on Powers, so the tree sits beside them. It's open to
every player whatever their power: it never touches a power's abilities or mastery, and it has its own key, **K**
(Powers keeps R/G/V/B, C, Z, H; Equestrian has N and J).

## How every pillar works
- Each pillar has its own XP, earned by doing what it's about, and levels 0-15 (level l → l+1 takes 30 + 15·l XP);
  Strength and (Kingdoms 0.7.101) Stealth go on to 25 for their second pages.
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

### Hoods (Kingdoms 0.7.101, with Powers 0.14.3)
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

### Hidden blade (Kingdoms 0.7.101)
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

## Freerunning (Stealth's second page, Kingdoms 0.7.101, with Powers 0.14.3)
Andy picked "Full freerunning" (2026-10-20). Stealth now goes to level 25 (`Skills.STEALTH_MAX_LEVEL`; alignment still
counts only to 15, and the pocket practice only the first 15 levels). Kingdoms owns the perks (`Skills.Perk`, appended
last) and the page ("Freerunning >" in the tree); the moves are Powers' (`freerun/Freerun`, client
`client/freerun/FreerunClient`, numbers at the top of `Freerun`), which reads the ranks by reflection on
`Skills.rank(Player, String)` (none without Kingdoms) and syncs them to every client for the poses. The server checks
every move (the rank, the wall or bar or obstacle really there). Ledge grab and mantle stay open to everyone. Every
move costs hunger (0.3 exhaustion; holding on 0.008 a tick, climbing 0.3 a block) and keeps your speed; a cling, a hang
and a wall run stop a fall counting (a vault or a kick doesn't). None of it while flying, gliding, swimming, riding,
carrying or carried, crawling, meditating, on a ledge, or in a web slinger's own parkour (web users keep theirs).

```
     Climber (lvl 3, 3)            Freerunner (lvl 3, 2)
            |                              |
   Sure Footing (lvl 8)          Leap of Faith (lvl 8, 2 pts)
            |                              |
   Bar Swing (lvl 12, 2 pts)     Wall Runner (lvl 12, 2)
```
12 points in all; with page 1's 15 and 25 levels you can't have everything.
- **Climber** (3 ranks): jump at a tall wall holding forward and Jump (any solid wall but ice, glass, leaves and
  `jaymospowers:freerun_no_climb`; one whose top you could catch with a jump is the ledge grab's) to cling. Forward
  climbs 0.05/0.07/0.09 blocks a tick, strafe shimmies (0.06), Sneak lets go. The grip lasts 3/6/10 s
  (`GRIP_TICKS`), then you slip down (0.07 a tick) for 3 s and fall off. Reaching the top, the ledge grab catches the
  edge and pulls you up and over while you hold forward.
- **Freerunner** (2 ranks): 1, a sprinting jump at something waist-high (up to 1.5 blocks: fence, wall, block, with
  room over it) vaults it at full speed (one of the web vaults' four looks). 2, wall kick: in the air beside or
  facing a wall, a fresh Jump kicks off it (up 0.5, out 0.45, half your speed along it kept), each wall face once until
  you land; also off a cling or a wall run.
- **Sure Footing**: on narrow tops (`jaymospowers:freerun_narrow`: fences, walls, gates, panes, bars, chains, end and
  lightning rods) you walk and sprint at full speed with your arms out to balance, and take 75% less knockback.
  Without it (only with Kingdoms loaded) a narrow top is walked: no sprinting, 30% slower (`UNSTEADY_SLOW`, Andy's call).
- **Leap of Faith** (2 pts): from any height, landing in a hay bale, water or leaves (`freerun_soft_landing`) takes
  no fall damage: Andy's accepted exception to "fall damage always applies". Falling (fast) looking at least 50° down,
  you dive head first (the wind's dive pose).
- **Bar Swing** (2 pts): jump (Jump held) under a fence, iron bar or horizontal chain whose underside is at most 0.9
  above your head (`freerun_bars`) to catch it and hang, swinging (10 s at most). Jump lets go forward the way you
  look (0.9 forward, 0.5 up); Sneak drops.
- **Wall Runner** (2 ranks): a sprinting jump alongside a wall runs along it for 1.5/2.5 s, light on gravity (the web
  run's speeds), once per wall face till you land; then you drop, or kick off it with Freerunner 2.
- XP: a freerun move gives Stealth +1, at most every 20 s (Powers tags the player `jaymospowers_freerun`; Kingdoms
  takes it off and pays, `Skills.freerun`). A hidden blade leap that lands on its mark gives +6.
- Poses: the web's vault, cling (hand over hand, or Not Enough Animations' ladder climb), wall kick and wall run poses;
  a new arms-out balance (`BalancePose`) and a bar hang, swinging from the hands (`BarHangPose`).

## Shadows: mob senses (Stealth's third page, Kingdoms 0.7.103)
The ideas of Dynamic Stealth (MIT, 1.12.2), written afresh for 26.3 in `Senses` (+ `LivingEntityVisibilityMixin`,
client `SensesClient`). Numbers are `static final` constants at the top of `Senses`. The tree screen's page button now
cycles: Stealth -> Freerunning -> Shadows -> back (tiers 0-3, 4-7, 8-11).
- **Sight.** How far a hostile or neutral mob notices you = follow range x vanilla's visibility (invisibility, mob
  heads) x Powers' Darktide veil (both already in `getVisibilityPercent`) x `Senses.factor`: light at your eyes 0.25
  (dark) to 1.0; sneaking 0.6 (also Soft Step and heritage); standing still 0.8; outside its 120 degree view cone 0.3
  (not within 2 blocks); hood 0.75 (`Identities.HOOD_SIGHT`); glowing or burning 1.5; never below 0.05. A mob already
  after you with you in sight sees you at full range. Creative/spectator players, bosses and the kingdoms' folk are
  untouched.
- **Losing track.** 100 ticks without line of sight and the mob drops you (neutral ones stop being angry), walks to
  where it last saw you (up to 10 s), looks about for 5 s ("searching"), then gives up. A flagless goal counts; a
  MOVE+LOOK goal walks. State is the non-persistent attachment `Senses.TRACK`. Bosses (wither, dragon, warden, elder
  guardian, `c:bosses`) and `Folk` are exempt.
- **Gauge.** An eye above the crosshair (`textures/gui/awareness_eye.png`, drawn by `tools/gen_items.py`): fills from
  the bottom as the nearest noticing monster closes from its full follow range to where it spots you, opens red when
  one targets you, half-closes (amber) while one searches. A 1-byte S2C payload (`Senses.Gauge`) every 5 ticks, only on
  change.
- **Unaware blows.** A melee hit on a hostile or neutral mob that isn't after you and is behind its view cone (or is
  searching for you) is a sneak attack, x2 (Sneak Attack: x2.5, x3). Never on players. The hidden blade's full-strength
  stab on an unaware one is an assassination, x10 like the crippled stab (not stacking), with Assassinate; without it,
  only the sneak attack. Both hook in `LivingEntityHurtMixin` after `HiddenBlade.dealt`.
- XP: sneak attack +1 (at most every 20 s), assassination +3.
- Perks (tier, column, level, ranks x cost): Shadowed (8, 0, lvl 5, 3x1: -10% sight per rank, in proportion to how dark
  it is where you stand); Quiet Step (8, 2, lvl 5, 2x1: sneaking 0.5, then 0.4); Slip the Net (9, 0, lvl 10, 1x1, needs
  Shadowed: lose track in half the time); Sneak Attack (9, 2, lvl 10, 2x1, needs Quiet Step); Assassinate (10, 1, lvl 15,
  1x2, needs Slip the Net and Sneak Attack). 10 points; Stealth tops out at 25 so with the other pages not everything fits.

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
