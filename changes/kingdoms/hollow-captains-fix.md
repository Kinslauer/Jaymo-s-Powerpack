# hollow captains: per-player ladders, 12 per player, own ladders
- Each player now has their own ladder of up to 12 Hollow captains (was 6 across the whole world).
  A captain joins a player's ladder when a Hollow they killed is promoted — promotion is kill-only, as before.
  A captain who killed players for more than one person walks on more than one ladder, and captains from different ladders can meet and fight together.
  While a player is offline, their Hollow keep walking and their ladder is kept. A captain due back keeps their place on the ladder; the count only drops when they're gone for good.
- Bug fix: the captain count no longer goes over its limit when a captain due back falls (their body leaves the walking but their ladder spot stays reserved).
- Tuning is in named constants at the top of the class: the 2.0F mend threshold, the 10-tick path refresh, and retreat (8-block radius, 3 attempts, 1.1 speed).
- Built; not played yet.
