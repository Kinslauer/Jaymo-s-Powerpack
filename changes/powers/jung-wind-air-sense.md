# Wind: airflow sense
- **Airflow sense (Jung):** a Wind user with at least one ability unlocked feels the air that moving things stir.
  Round the crosshair a faint pale streak shows on the side each moving creature or player is on (ahead is up, behind
  is down), brighter the faster and nearer it is, thicker for something big, fading over about a second once it stops.
  It feels through walls, in the dark and the unseen, but gives a side only: no outline, no distance, no height.
- **What hides from it:** standing still or sneaking (it needs 0.08 blocks a tick; a sneak is about 0.065).
- **Reach:** 12 blocks with one ability unlocked, up to 24 with four. Half that with no sky overhead, three quarters in
  a dimension without weather, a quarter more in a thunderstorm, where the streaks wander up to 20 degrees off true.
  In the rain or the cold it loses soft movement (slow mobs). Under water it feels nothing.
- Always on and free: no key, no hunger cost, no text. Client side only (`WindAirSense`, run from `WindPowerClient`):
  no packets and no server code, so it only knows what the client is already told about.
- **For Andy to rule on:** in PvP a Wind user will know which side a walking or running player is on within 12 to 24
  blocks, through walls. Sneaking and standing still beat it.
- Not in it yet: a hunger cost, the Gale Barrier muffling it, an active pulse, tracking.
- **Not built locally and not played.** The session that wrote it could not reach the build's download hosts; the PR
  checks are its compile. `docs/wind.md` has the numbers and test steps.
