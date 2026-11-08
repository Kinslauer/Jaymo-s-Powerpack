---
name: family-farmhand-starts
description: Andy 2026-11-05 - village start became Family farmhand (mvs:barn / Homesteads), day-one start list, every locked start has own deed + advancement, fate by odds table (PR #1045)
metadata:
  type: project
---
2026-11-05 Andy, screenshot = Moog's Voyager Structures `mvs:barn` (spruce/oak, gambrel roof, no villagers of its own).
- VILLAGE road = "Family farmhand" (`FarmhandHome`): wake by a bed in nearest mvs:barn, else Homesteads farmstead; couple of player's blood moves in if <2 grown; 40 hearts; job farmer. Fallback old village start.
- Andy picked "Yes, lock the rest": day one only Family farmhand, Rock bottom, Banished, Shipwreck, Fate, heritage starts (Verdant, Demon, Afterlife, Lab). Replaces the old "Sentenced/Nobodies/Burned open" ruling.
- "Everything locked should now have its own unlock method and achievment": each lock has a unique deed; advancements `jaymoskingdoms:roads/<road>` (tab Roads of a Life), awarded in RoadLocks.look.
- Fate: `LifeStory.FATE_ODDS` percent table over all starts, ignoring locks.
- PR #1045 MERGED 2026-11-05 ~19:31Z (Kingdoms 0.7.605 / Powers 0.21.6), built, not played. Related: [[reincarnation]], [[heritage-life-story]].
