# Boxing: every punch winds up, shorter with level

- (Jung, outside Wind) Every punch now winds up before it lands: click, the arm draws back, and the blow lands a
  moment later: 0.75 seconds at level 1, a little less each level, 0.35 seconds at level 10 (Andy: "shorter wind up
  with level"; `Boxing.WINDUP_SLOWEST`, `WINDUP_FASTEST`, `windupAt`). It lands on the one it was aimed at if they are
  still in reach then; step out during the windup and it hits air. The punch's stamina is spent as the windup starts.
- The next punch can't start until the last has landed. The windup shrinks in step with the punch speed, so the old
  speed table still holds: every 0.8 seconds at level 1, about three a second at level 10. A click during a windup is
  still kept and thrown next. Strong attacks (uppercut, pull counter, roll straight) wind up the same way.
- The windup shows: a jab or cross draws the arm back and coils the body the other way before it snaps out; a hook
  lifts the arm out wide as the body coils back; an uppercut drops the fist and dips. The pose starts on the click
  and is at full reach as the blow lands. First person draws the punching arm back the same way. Everyone watching is
  told each punch's windup, so the pose times right for every boxer.
- A boxer knocked down or killed during the windup throws nothing.
- Not played. The timing is worth a feel test: it changes how boxing fights.
