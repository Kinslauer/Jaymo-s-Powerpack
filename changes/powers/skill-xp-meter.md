# experience meter - mastery and Z Fighter training show on it
- Andy, of Kingdoms' experience meter (the bar that drops in at the top of the screen when a skill gains experience,
  burns along to the new amount and slides back out): "feed em".
- **Mastery**: your power's mastery shows on the meter ("Wind Mastery") as the way to its next ability and, once all
  four are learned, to the next rank ("Wind Adept"). An ability learned or a rank reached flashes the bar; the big
  fanfare they already had stays.
- **Z Fighter**: Training Points show as the way to the next stat level ("Training 142 TP"), with a small toast and
  the low chime when there is enough for one; each form's mastery shows as its own bar ("Super 42%"), the god form's
  too.
- These trickle in (a mastery point a minute, a Training Point every twenty seconds), so a track waits until it has
  moved 4% of its bar, or reached a level, and then shows all of it at once: the meter doesn't drop in for every point.
- `client/hud/MeterFeed` watches what is already synced and queues gains in Fabric Loader's object share
  (`jaymos:skill_meter`) for Kingdoms' `SkillMeter`. Without Kingdoms nothing shows.
- Compiled; not played.
