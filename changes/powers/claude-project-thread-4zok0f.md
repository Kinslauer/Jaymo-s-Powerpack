# Downed: a blow knocks you out, red blows kill, no tossing, fire kills after 6 s
- **A blow on the ground knocks you out**: anyone downed, player or mob, who takes a blow (a swing, an arrow, a bite, a
  blast) after going down is laid out cold for 30 seconds. Sneak mashes you round sooner as from any knock-out, and a
  friend can still revive you. Andy: "make the downed players and mobs go unconcious after a hit, then 3 hits while
  downed kills them, or any execution or unblockable (red) attack. this should change how fights end, as of now i get
  tossed around for 30s". `Downed#HIT_OUT_TICKS`.
- **Three blows from going down**: the blow that put someone down no longer counts toward the three that finish them,
  so it is three blows after they fall, as the earlier note meant. One blow of three hearts or more still kills.
- **Red blows kill the downed**: a blow too heavy to parry (the red tell) finishes anyone downed at once. Executions
  already did.
- **No more being thrown about**: blows don't knock a downed body back. A blast still moves it. `DownedStillMixin`.
- **Fire**: falls and bleeding still don't count as blows (Andy: "falls and bleeding dont count, fire knocks unconcious
  instantly ... they die after burning for 6s"). Anyone downed who catches fire is laid out cold at once, and dies after
  burning for 6 seconds; put out for 2 seconds and the count starts again. `Downed#BURN_KILL_TICKS`.
- Built on CI only; not played.
