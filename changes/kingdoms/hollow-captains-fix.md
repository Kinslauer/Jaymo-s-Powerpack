# hollow captains: per-player ladders, 12 per player, own ladders
- Each player now has their own ladder of 12 Hollow captains (was 6 world-wide).
  A captain is added to a player's ladder when a Hollow they killed is promoted;
  a captain who killed multiple players appears on multiple ladders (intersecting).
  While a player is offline, their Hollow keep walking and the ladder is kept in
  the world. A captain due back (fallen, will return) keeps their place on the
  ladder; the count only drops when they're gone for good.
- Bug fix: `died()` no longer lets the count exceed the limit when a captain is
  due back (the body is removed from walking but the ladder spot is reserved).
- Tuning numbers moved to `static final` constants: `MEND_THRESHOLD` (2.0F),
  `PATH_REFRESH_TICKS` (10), `RETREAT_RADIUS` (8), `RETREAT_ATTEMPTS` (3),
  `RETREAT_SPEED` (1.1).
- `HollowCaptains` (per-player attachments: `PLAYER_COUNT`, `PLAYER_WALKING`,
  `PLAYER_LADDER`; new helpers `playerCount`, `playerWalking`, `addToLadder`,
  `removeFromLadder`, `findPlayerWithCaptain`), `Captains.slew` (adds captain
  to the killer's ladder), `Rivals` (ladder helper).
- Built; not played.
