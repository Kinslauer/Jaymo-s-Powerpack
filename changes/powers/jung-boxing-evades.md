# Boxing: evades, strong attacks, blur and the combo breaker
- **Evades:** while boxing, the Dodge key (Left Alt) is the style's evade in place of the roll. Arrow Slit slips left
  or right or bobs straight in, Long Guard pulls back, Bastion rolls the shoulder in place (or slips aside). A blow
  that would land within 0.4 s of the press misses, and the next evade is ready 0.7 s after it. Two evades cost a feather icon at level 10, one each at
  level 1.
- **Perfect evade and strong attacks:** an evade pressed inside the guard's perfect-parry window (5 ticks, 7 when
  locked on) earns the next punch: the uppercut out of a bob (4), the pull counter out of a pull (3, a block more
  reach and a hard shove), the roll straight out of a shoulder roll (5). A shoulder roll that isn't perfect lets half
  the blow through and earns a 2.5 straight. A strong attack costs one icon at every level.
- **Blur and daze:** those strong attacks blur a player's sight for 1, 1.5 or 2.5 s (a pale haze under the HUD; a key
  swaps it for a dark frame for players who get motion sick), not again for 5 s. Mobs are dazed for as long, the
  undead never, a boss 1.5 s at most.
- **Combo breaker:** six punches landed in a row on a player or a boss and they are shoved back; that boxer can't
  reach them for a second, and their blur clears.
- A click that comes too early is kept and thrown in its turn. A gassed boxer gets Mining Fatigue and can't evade.
- Classes: `boxing/BoxingEvades` (new), `boxing/Boxing` (the punch takes strong attacks, the breaker, the queue),
  `client/boxing/BoxingClient` (new: the evades' movement, the blur), `client/stamina/DodgeClient` (the Dodge key
  hands over to boxing). `docs/boxing.md` has the numbers and test steps.
- Not in it: **the blocks** (right click with fist gear), which need their own patch through `combat/Guard`; the
  breath puff, poses and idles; teachers; the HUD.
- **Not built locally, not booted, not played.** The haze is a wash of colour, not a true blur.
