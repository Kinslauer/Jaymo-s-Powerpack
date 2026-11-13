# Wind: the Spiral Dive goes down head first, spinning

- (Jung) The Spiral Dive (tap X in the air, looking steeply down) looked like a belly flop: the diver lay about flat
  and turned like a pinwheel. It now tips right over within a third of a second and goes down head first, arms
  together ahead like a spear's point, legs together behind, spinning about its own length like a drill.
- Why it lay flat: the dive borrowed the Zephyr's upright spin, and on top of it a falling Wind user's slow tip
  forward (which only reaches head-down at over twice the dive's speed) was still running. The dive has its own
  pose now (`WindMoves.DIVE`, drawn by `WindMovePose`), and that falling tip is off while it lasts.
- Nothing about the dive's speed, steering, landing ring or cooldown changes. Third person and other players only;
  first person is as it was.
- Not built locally; not played: the tip and spin are set by numbers at the top of `WindMovePose`.
