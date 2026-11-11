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

## Not built yet

The evades on Left Alt and the perfect evade, the strong attacks, the blocks, blur and daze, the combo breaker, the
early click that queues a punch, tiring and gassed looks, the idles and poses, the cestus and bracers, the teachers,
quests and levelling, the HUD, and the loading-screen hints.

## Things to know

- A punch is instant, like any vanilla hit, so its target gets no incoming-blow glint. The evades patch has to decide
  whether punches get a short wind-up.
- Fist gear is not in Kingdoms' `form_fists` tag, so a fist form never takes right click from the block to come.
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
11. A second player: PvP punches obey the server's PvP setting.
