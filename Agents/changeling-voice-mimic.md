---
status: running
task: changeling-voice-mimic
branch: t3/changeling-voice-mimic
pr: 2248
mods: kingdoms
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ChangelingLures.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BorrowedFaces.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ChangelingVoices.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ChangelingVoiceCompat.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Doubles.java
  - kingdoms/src/main/resources/fabric.mod.json
  - E:/Jaymo Vault Live/Lore.md
  - changes/kingdoms/changeling-voice-mimic.md
---
# Changeling voice mimic

Andy assigned this Inbox task to Apollo. Use Simple Voice Chat for short, in-memory voice clips and positional playback by changeling kidnappers (shapeshifters).

## Plan
- [ ] Understand the existing VoiceRange.java SVC plugin pattern
- [ ] Create ChangelingVoices.java as a new SVC server plugin (beside VoiceRange.java)
- [ ] Implement in-memory voice clip buffering for players (session-only, never saved to disk)
- [ ] Implement opt-out respect for voice chat
- [ ] Hook into shapeshifter behavior to play friend's voice clips as locational audio
- [ ] Add change note under changes/kingdoms/changeling-voice-mimic.md
- [ ] Build and test

## Notes
- Need to understand: How does a shapeshifter know who a player's "friends" are? (MCA relationships, party members, kingdom members?)
- Need to understand: When should a shapeshifter play a voice clip? (When hunting a lone player? When near a vault?)
- The task says "buffer a few short clips of each player's real mic audio" - need to decide clip duration and count
- Players opted out of voice chat are never recorded
- Silent when voice chat is not installed or the friend never spoke
- Must be own remake, nothing copied from Gigarachne

## Done
What a player will see, and what wasn't tested.

Codex took over from Apollo on Andy's instruction, November 26. Inspecting the missing player-stalking hook before implementation. Owns additionally ChangelingVoiceCompat.java and fabric.mod.json for optional SVC wiring.
- 2026-11-26 06:07 No sign of work for 30 min: marked stalled (whoever ran it stopped).

Kingdoms build passed. PR 2248 opened; no in-game or multiplayer voice tests run. CI pending.

Codex resumed build and PR completion on November 26 at 2:50 PM. Existing implementation preserved.
