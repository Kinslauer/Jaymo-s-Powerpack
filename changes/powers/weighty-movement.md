# weighty movement - the walk carries weight
- Andy (2026-11-24, with a clip of a player hauling a greatsword about): "Can our movement have an increased weight to the
  animation like this?" Every player's walk, theirs and everyone's in view, now moves like a body with weight
  (`client/moving/BodyWeight`):
  - **The torso swings on the hips.** It lags back a moment as you set off, leans into the walk and the run, and when
    you stop it carries on forward past upright and settles back instead of snapping straight.
  - **Each step lands:** the body sinks as a foot comes down and rises over the planted leg, the hips rock over the leg
    taking the weight, the shoulders turn against the hips, and a slow walk's knee gives under the weight and folds as it
    swings back (the run's own knee fold is kept).
  - **Landings squash:** coming down from a jump or a drop the body sinks onto bent knees, tips forward and comes back up
    with a small rebound, deeper the harder the landing. In first person your view sinks with it (the field of view is
    never touched). Off the ground the legs tuck up a little.
  - **Heavier gear, heavier body:** plate armour (by the armour load) and a two-handed weapon or polearm in hand make it
    all slower and deeper.
- It gives way to every pose of ours (crawl/prone, dodge rolls, ledges, parkour and wall runs, falls, slides, carries,
  downed, swimming, riding, flight): standing still it does nothing, so still bodies keep their stances. Under the armed
  stance only the arms are left alone. Your own body seen in first person only sinks, never tips. Off in low-end mode.
- Built; not played.
