# sleeping sets your broken bones
- Tiv: "sleeping should set your broken bones. after hitting the sleep button and waiting, longer per break". A
  broken arm or leg no longer needs a splint, a cleric or a priest to be set: sleep in a bed does it. The splint stays
  for when there's no bed to be had.
- **How long**: each break takes 10,000 ticks of the clock asleep (`Breaks.SLEEP_SET_TICKS`). The times add up, one
  break after another: a whole night in bed (about 11,000) sets one break, two breaks take two nights, three take
  three. What a night doesn't finish carries over to the next sleep, even after logging out. A leg is set first,
  since two broken legs leave you crawling. The time is measured on the clock, so a night that's skipped or run by as
  Kingdoms' timelapse counts in full. A player sleeping while the others stay up takes the full night in real time.
- **Only real sleep counts**: Kingdoms' bed rest (lying down by day, sneaking into bed, or lying there before you
  press Sleep) sets nothing. Sleep at night (a bed used at night, or the Sleep button from rest) and the counting
  starts once you're fully asleep, the same rule as a sleep ending Battered or a chamomile sleep healing you. The
  morning wakes you as usual: the sleep doesn't run longer, a second night is needed.
- **A set bone shows as splinted**, as if a splint had been put on: the slats show on the limb, the break keeps only a
  quarter of its slowness and weakness, and it counts as tended when the game checks whether you're patched up (what
  lets natural healing run at full speed). The splint comes off as the bone knits, as before. Messages: "Your Left leg
  has set in your sleep"; on waking with a break still to go, "Your Right arm is 40% set: sleep in a bed again to
  finish setting it". The "broken" message now says a splint or sleep in a bed will set it.
- **The downed bed rest** (hauling yourself into a bed while downed) still sets every break after a minute, now
  through the same `Breaks.bedSet`, so those breaks show splinted too instead of hanging untended.
- `Breaks`: `SLEEP_SET_TICKS`, the saved `SLEPT` attachment, `bedSet`, the sleep counting (each tick while
  `isSleepingLongEnough`, and on `EntitySleepEvents.STOP_SLEEPING` for the morning a skipped night jumps to).
  `DownedStruggle` calls `Breaks.bedSet`. Lang: `wounds.slept_set`, `wounds.sleep_setting`.
- Built; not played.
