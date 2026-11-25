# The Nightmare haunts from the dark instead of acting as an enderman
- **The Graveyard's Nightmare** no longer works like an enderman. The stare that angered it, its walk-up chase, its melee goal and both enderman teleports (and their sound) are gone. It now hunts any player who comes within 24 blocks in sight, or anyone who hurts it, one blow at a time (`NightmareHaunt`, `mixin.graveyard.NightmareHauntMixin`):
  - **Dissolve** (0.6 s): it stops and comes apart into ink and smoke with a soul's sigh, then is gone: unseen, silent and unhurtable.
  - **Lurk** (3.5-7 s, 2.5 s longer after you struck it): it waits 10-18 blocks off. While it is near, your breath shows cold, a whisper comes from where it waits, and about half the time it lets you glimpse it 9-14 blocks off at the edge of sight, gone the moment you look straight at it.
  - **Gather** (1 s): it picks a dark spot 2.6-3.6 blocks behind or beside you (at least 55° off where you look). Smoke and ink draw in there, a moan rises from that spot, and you hear your heart hammer. That is the warning.
  - **Wind-up** (0.5 s): it stands there, whole and hittable, and swings its own attack. Its blow reaches 3.4 blocks. Step back and it misses (a whoosh). A hit chills you with a 2.5 s Darkness pulse. Hit it first and it breaks apart before the blow lands.
  - **Recover** (0.8 s): it stays a moment longer, still hittable, then dissolves again.
- **Light wards you.** It only gathers where the light is 7 or less. It can't reach you while you stand in light 12 or more (sky or block, as it is right now) or hold a torch or lantern. After 30 s warded in one hunt it gives up, steps back into sight where it waited, and won't start another hunt for 20 s. Shots still go through it; only steel up close bites, and only while it is there.
- Players who can see invisible things (occult Exposure, Initiated) see it waiting while it lurks.
- Built; not played.
