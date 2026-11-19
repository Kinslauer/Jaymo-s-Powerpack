# An escape isn't known to the camp until they see you gone
- **Escaping your chains**: picking the shackle, slipping it, breaking the chain or sawing the rope no longer sets the whole camp on you that moment. The camp only hunts you, for the rest of the day as before, once one of its people sees you loose (in front of them, in sight, within 32 blocks) or comes within 12 blocks of your empty chain with a clear view of it. The one who notices shouts it (a speech bubble, in orc or goblin speech for their camps). If nobody notices within the day, the camp lets it go.
- The "hunted" line now comes when you're seen, not when the chain comes off.
- `StoryHard.free` puts the runaway in `LOOSE`, and `StoryHard.loose` (each second) raises `LifeStory.startHunt` on sight (`Exposure.sees`) or on the empty chain (a clear sightline). The "Escaped captive" start still begins with the hunt on, as it always has.
- Built; not played.
