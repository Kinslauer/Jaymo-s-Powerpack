# Boxing

Jung's fist styles, approved by Andy as a build on 2026-11-11. The whole design is `docs/Boxing-Plan.md`; this page
says what is built so far, where the numbers are, and how to test it.

**Names.** Andy's brief named the styles Close Guard, Long Guard and Shoulder Guard. Jung has since asked for
**Arrow Slit** (was peekaboo / Close Guard) and **Bastion** (was Philly shell / Shoulder Guard). The code and lang use
Jung's names; Andy has the last word. Changing them back is the three lines in `lang/parts/boxing.json` (the ids
`arrow_slit` and `bastion` are only used by the debug command; saves store a number).

## Built so far: gear and punches

- **Hand Wraps** (`BoxingGear`): two of the `jaymospowers:wrap_cloth` tag (Clean Rag, Kingdoms' Torn Cloth, the
  Kitchen's Hemp Cloth) in the crafting grid. `WRAPS_DURABILITY` 250 punches, mended with string. Without a style
  they are a weak weapon (1.5 damage). Fist gear is the `jaymospowers:boxing_gear` item tag.
- **Styles and levels** (`Boxing`): which style a player boxes in (`STYLE`, told to everyone) and each style's level
  1 to `MAX_LEVEL` 10 (`LEVELS`). `Boxing.learn` only teaches a new style when every known one is mastered. Nothing
  in the game teaches a style yet: that is the teachers' patch. Until then, `/powerdebug boxing` (gamemasters).
- **The punch**: left click on a living thing, with fist gear in the main hand and a style. The server throws it in
  place of the game's own attack. `PUNCH_DAMAGE` 1.5 at every level, reach `RANGE` 2 blocks (a click from further
  out is a punch at the air and still costs), `PUNCH_KNOCKBACK` 25% of the usual shove.
- **Strings**: Arrow Slit throws a one-two then alternating hooks; Long Guard jab, jab, cross; Bastion the right jab
  only. A pause of `STRING_RESET` 30 ticks starts the string again. So far the punches differ only in sound.
- **Speed**: one punch every 16 ticks at level 1, one tick quicker a level, 7 at level 10 (`SLOWEST`, `FASTEST`). The
  attack-speed attribute is set to match, so the attack indicator shows the rhythm. A click up to `EARLY_SLACK`
  2 ticks early still counts and borrows from the next.
- **Hit immunity**: a boxer's punch is never swallowed by the immunity their own last punch left, so levels 8 to 10
  really land faster than every 10 ticks. Other weapons gain nothing.
- **Stamina**: `COST_FIRST` 2 half-feathers a punch at level 1 (one icon), down to `COST_LAST` 0.5 at level 10 (four
  punches an icon); fractions are carried over.
- **Gassed** (Feathers' exhausted state): a punch every `GASSED_INTERVAL` 32 ticks (proposed number), `GASSED_CUT` 45%
  weaker, never more than `GASSED_CAP` 2. The breath puff, the heaving shoulders and the tiring warning are not built.

## Built so far: cestus and bracers

- **Cestus** (damage): a styled punch does more with one on: leather +0.5 (`LEATHER_CESTUS_PUNCH`), iron +1
  (`IRON_CESTUS_PUNCH`), steel +1.5 (`STEEL_CESTUS_PUNCH`). So 2, 2.5 and 3 a punch against 1.5 in wraps. The gassed
  cap of 2 still holds.
- **Bracers** (blocking): leather, iron, steel. They punch like wraps. What they do for a block comes with the blocks.
- **Making them:** the leather ones are sewn round Hand Wraps in the crafting grid (wraps + 2 leather for the cestus,
  wraps + 4 leather for the bracers). The iron and steel ones are forged like other metal weapons
  (`tinkering/WeaponParts`): Cestus Studs (2 ingots) or Bracer Plates (3 ingots), in iron or steel, with a Tool Binding
  at the Tool Station. No gold or diamond.
- Without a style they are weak weapons: cestus 2 / 3 / 3.5, bracers 1.5 / 2 / 2.5. Durability: leather 400, iron 250
  (its material's), steel 600.
- All the art is drawn by `tools/boxing_assets.py` and is placeholder.

## Built so far: evades, strong attacks, blur, the combo breaker

`BoxingEvades` (server), `client/boxing/BoxingClient`, and a hook in `client/stamina/DodgeClient`.

- **The Dodge key (Left Alt) while boxing** is the style's evade, not the roll. The client moves the boxer and sends
  which way was held. Arrow Slit: slip left or right (`SLIP_SPEED` 0.38), else a bob straight in (`BOB_SPEED` 0.3).
  Long Guard: a pull straight back (`PULL_SPEED` 0.6). Bastion: a shoulder roll in place, or a slip to a side.
  `COOLDOWN` 14 ticks. Cost: 2 half-feathers at level 1 down to 1 at level 10 (two evades an icon). A gassed boxer
  can't evade.
- **Missing a blow:** a melee blow or a shot that would land within `EVADE_TICKS` 8 of the press misses. That is shorter than the
  cooldown, so evades can't be chained into lasting immunity.
- **Perfect evade:** within the guard's perfect-parry window of the press (`Guard.PERFECT_TICKS` 5, +2 when locked onto
  the attacker). It earns the next punch inside `STRONG_TICKS` 30:
  - bob: the **uppercut**, `UPPERCUT_DAMAGE` 4;
  - pull: the **pull counter**, `PULL_COUNTER_DAMAGE` 3, reaching `PULL_COUNTER_REACH` 1 block further than a punch and
    shoving 1.8 times the usual knockback;
  - shoulder roll: the **roll straight**, `STRAIGHT_DAMAGE` 5;
  - a side slip: the hook on that side as the next punch (a plain punch, chosen).
- **A plain shoulder roll** (in the window but not perfect) lets `GLANCE` half the blow through and earns a
  `STRAIGHT_WEAK_DAMAGE` 2.5 straight.
- A strong attack costs `STRONG_COST` one icon at every level, and a cestus adds to it.
- **Blur and daze:** a strong attack out of a perfect evade blurs a player for 1 s, 1.5 s or 2.5 s (`UPPERCUT_BLUR`,
  `PULL_COUNTER_BLUR`, `STRAIGHT_BLUR`), not again within `BLUR_COOLDOWN` 5 s of the last one starting. Mobs are
  dazed for as long (`DarktideStatus.stun`); the undead never; a boss `BOSS_DAZE` 1.5 s at most.
- **The blur on screen** is a pale haze over the world, drawn under the HUD (crosshair, incoming-blow cues and bars
  stay sharp), fading over its last half second. It is a haze, not a true lens blur. The "Boxing: blur or vignette" key
  (unbound by default) swaps it for a dark frame; the choice is kept in `config/jaymospowers-boxing.properties`.
- **Combo breaker:** `BREAKER_HITS` 6 punches landed in a row on a player or a boss (a pause of `BREAKER_RESET` 40
  ticks, or them evading one, ends the row) and they are shoved about 1.5 blocks back; that boxer's punches can't
  reach them for `BREAKER_TICKS` 20, and their blur clears.
- **Early click:** a click that comes too early is kept (one) and thrown when the rhythm allows.
- **Gassed** adds Mining Fatigue. `Boxing.tiring` (8 half-feathers or fewer) is there for the looks patch to read.

## Built so far: gear worn on the hands

- Fist gear is **worn, not held**, the way the Fang Gauntlets are (`client/armory/FangGauntletsClient`, through its
  mixins): the held item is hidden and the gear is drawn on the arm, for everyone and in your own first person. With
  the off hand empty it shows on both hands.
- **Hand Wraps**: a wrap round the hand. **Cestus**: the wrap, with a studded band over the knuckles. **Bracers**: a
  guard up the forearm. Sizes are the `WRAP_*`, `KNUCKLE_*` and `BRACER_*` knobs there; the textures are
  `textures/entity/boxing/<item>.png` (from `tools/boxing_assets.py`, placeholder).
- Holding fist gear also puts the arms in the Fang Gauntlets' fists-up guard when nothing is swinging. The styles'
  own idles and poses will replace it.

## Built so far: the block

- **Right click raises a guard with fist gear.** Fist gear is a weapon, so it gets the pack's weapon guard
  (`combat/Guard`): a block while held, and the usual parry in the first ticks of a raise.
- **What it stops** is the gear's own, more with each step up (a sword's guard stops 60%):

  | | Hand Wraps | Leather | Iron | Steel |
  |---|---|---|---|---|
  | Cestus | 50% | 55% | 60% | 65% |
  | Bracers | | 65% | 75% | 85% |

- **What it costs a boxer** (a style, fist gear): by the blow, whatever its weight. One icon (`BLOCK_COST` 2
  half-feathers), an icon and a half in the Arrow Slit (`ARROW_SLIT_BLOCK_COST` 3). Better gear leaves less of it:
  wraps 100%; cestus 95 / 90 / 85%; bracers 90 / 80 / 70%. Fractions are carried over. With nothing left to pay, the
  guard breaks as any weapon's does.
- Without a style, fist gear blocks at the pack's usual cost (by the blow's damage).
- **The combo breaker** doesn't count a punch that ran into a raised guard from the front.
- The styles block alike except for the cost: the share stopped belongs to the gear, not the style.

## Not built yet

The breath puff and heaving shoulders, the styles' own idles and poses (blocks included), the teachers, quests and
levelling, the HUD, and the loading-screen hints.

## Things to know

- A punch is instant, like any vanilla hit, so its target gets no incoming-blow glint: against another boxer an
  evade is a read, not a reaction. Mobs' and weapons' blows do show the cue. Andy to say if punches get a wind-up.
- Fist gear is not in Kingdoms' `form_fists` tag, so a fist form never takes right click from the block.
- Hand Wraps have no Better Combat weapon attributes: they hit the vanilla way, which is what the server hooks.
- Sharpness and other damage enchantments do nothing to a styled punch.
- The Hand Wraps texture is a placeholder drawn for this patch.

## Testing

1. Craft Hand Wraps from two Clean Rags. They show in the Combat creative tab.
2. Hold them with no style: a weak weapon, the usual attack speed.
3. `/powerdebug boxing style arrow_slit`. Punch a zombie from 3 blocks: nothing lands. From inside 2: 1.5 damage, and
   it is barely pushed back. The attack indicator fills in 0.8 s.
4. `/powerdebug boxing level 10`: the indicator fills in 0.35 s, and a held flurry lands about three a second.
5. Level 7, then 8: at 8 the punches land faster than a sword could re-hit the same zombie.
6. Watch the feathers: at level 1 each punch takes one icon; at level 10 four punches take one.
7. Punch until exhausted: punches slow to one every 1.6 s and do under 1 damage, until the row is back to 30%.
8. `long_guard` and `bastion`: same numbers; listen for the cross in the Long Guard's third punch.
9. Empty hand, any style: plain vanilla punches (and Z Fighter's bare hands as before).
10. `/powerdebug boxing status` prints all of it.
15. Hold Hand Wraps, each cestus and each pair of bracers, third person (F5) and first person: the item is not in
    the hand; it is on the hands (both, with the off hand empty; only the main hand with something in the off hand).
16. A second player sees the same. The Fang Gauntlets still look as they did.
17. Right click with Hand Wraps: the guard comes up. A zombie's hit from the front does half; with steel bracers,
    15%. From behind, all of it.
18. With a style, each blocked hit takes one feather icon (one and a half in Arrow Slit); with steel bracers, less.
    Without a style it costs by the hit's damage, as a sword's guard does.
19. Block until the feathers run out: the guard breaks.
20. Two boxers: six punches into a raised guard don't trigger the combo breaker; six clean ones do.
12. Cestus: with a style, a leather, iron and steel cestus punch for 2, 2.5 and 3. Bracers punch for 1.5.
13. Craft a Leather Cestus (Hand Wraps + 2 leather) and Leather Bracers (Hand Wraps + 4 leather).
14. Forge an Iron Cestus: shape Cestus Studs at the Part Builder, cast them, pour iron, then studs + a Tool Binding at
    the Tool Station. The same with steel, and with Bracer Plates.
11. A second player: PvP punches obey the server's PvP setting.
15. Arrow Slit, a zombie swinging: Left Alt with A or D held slips aside and the blow misses; with W, a bob in.
16. Time the bob so the blow would land within a quarter second of the press: crit sparks, and the next punch is a 4
    damage uppercut that leaves the zombie's arms down for a second (a skeleton is not dazed).
17. Long Guard: Left Alt pulls back. Timed right, the next punch reaches from 3 blocks, does 3 and throws the target.
18. Bastion: Left Alt with no direction rolls in place. Timed right: no damage, and a 5 damage straight. A little
    late: half damage taken, and a 2.5 straight.
19. Two players: land a perfect uppercut on the other: their screen hazes for a second, HUD still sharp. Again within
    5 s: no haze. Press the "Boxing: blur or vignette" key (bind it first): the haze becomes a dark frame.
20. Two players: land six punches in a row: the sixth shoves them back and the next second of punches miss.
21. Click twice quickly at level 1: the second punch comes by itself when the first's rhythm is up.
22. No fist gear, or no style: Left Alt is the roll, as before.
