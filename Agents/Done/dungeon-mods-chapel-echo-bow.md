---
status: stalled
task: 
branch: claude/project-thread-53idu7
with: Claude (cloud thread "Find more dungeon mods to add")
pr: 1417
mods: powers
versions: 
started: 2026-11-12
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterChapel.java
  - src/main/java/com/jaymospowers/power/zfighter/PracticeDummy.java
  - src/main/java/com/jaymospowers/power/arsenal/EchoBow.java
  - src/main/resources/data/jaymospowers/structure/darkened_chapel.nbt
  - tools/church_of_sin_port.py
  - tools/gen_votive_panels.py
  - tools/gen_echo_bow.py
---
# Dungeon mods, the Darkened Chapel, the target dummy and the Echo Bow

**Task:** Andy asked for Re:Animal, The Graveyard, the Church of Sin (as the place the corrupted meet the demon
master), the MmmMmmMmmMmm target dummy and Simply Bows' Echo Bow as an Arsenal weapon.

## Plan
- The Graveyard and Re:Animal have 26.3 Fabric builds: packMods, unchanged, with their libraries.
- Church of Sin (MIT, Forge 1.20.1): its template ported as the Darkened Chapel; own paintings and books.
- Target dummy (CC0, no 26.3 build): its ideas added to our Practice Dummy.
- Echo Bow (Simply Bows forbids ports): our own bow, a rare Arsenal pull.

## Notes
- Touches small parts of build.gradle (packMods), ZFighterDemonRealm.talk, ZFighterPower.init, ArsenalPower.init,
  ArsenalPool.build.
- Compiles; not run in game (a cloud session can't boot the server).

## Done
- 2026-11-13 06:31 No sign of work for 30 min: marked stalled (whoever ran it stopped).
