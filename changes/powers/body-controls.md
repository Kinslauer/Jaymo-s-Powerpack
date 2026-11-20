# body controls - hold drags, tap loots, sneak revives
- Andy: "right click hold should always be drag, tap can loot. and shift should revive. the binds got confusing". On anyone downed:
  - **Hold use (standing)**: a bar fills ("Taking hold of ...", `Carry.LIFT_TICKS`) and you drag them by the shoulder (`Carry.liftDowned`); a small downed animal is taken up instead. Sneak + right-click while dragging still lifts them into your arms, right-click lets go. No kneel needed first any more.
  - **Drag out of the water**: someone downed lying in water can be taken hold of from anywhere within arm's reach (not just touching), so you can haul them out from the bank.
  - **Tap use**: takes the weapons and arrows from their hands (Jaymo's Kingdoms, now on the tap's release instead of the first press), or else pulls out a blade stuck in them (`Lodged.pullOnTap`). A Xeno-Band is still torn off a downed wearer on a tap.
  - **Sneak + hold use**: brings them round (the revive, food or treat as before). Any sneak works now, not only the low crouch held still.
  - **Fainted players** are shaken awake with sneak + right-click (was a plain right-click).
  - Executions are unchanged: sneak + hold attack.
- `Downed.onUseEntity/revives`, `Carry.liftDowned`, `Lodged`, `Fainting.shake`. Lang: `message.jaymospowers.downed.cant_take_hold`, `bar.jaymospowers.taking_hold` (the old kneel-first message is gone).
- Built; not played.
