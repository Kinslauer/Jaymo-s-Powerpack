# Boxing: the block
- **Fist gear blocks on right click**, through the pack's weapon guard (the block, and the parry in its first ticks).
  What it stops is the gear's own, more with each step up: Hand Wraps 50%; cestus 55 / 60 / 65% (leather, iron,
  steel); bracers 65 / 75 / 85%. A sword's guard stops 60%.
- **A boxer's block costs by the blow**, whatever its weight: one feather icon, an icon and a half in the Arrow Slit.
  Better gear takes less: cestus 95 / 90 / 85% of that, bracers 90 / 80 / 70%. Without a style, fist gear blocks at the
  usual cost.
- **The combo breaker** no longer counts a punch that ran into a raised guard from the front.
- Classes: `combat/Guard` (two small hooks: the gear's share in the guard's component, the boxer's cost when a blow is
  blocked), `boxing/BoxingGear` (`blockShare`, `blockCostShare`), `boxing/Boxing` (`blockCost`).
- **Not built locally and not played.** Untested in particular: that fist gear raises the weapon guard at all (it
  should, being a plain weapon), and that every other weapon's guard is unchanged.
