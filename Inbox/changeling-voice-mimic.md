---
status: claimed
for: any
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: changeling-voice-mimic
pr: 
suggested_owns:
  - kingdoms new ChangelingVoices.java (Simple Voice Chat server plugin, beside VoiceRange.java)
  - kingdoms BorrowedFaces.java (hook only)
---
# Changeling kidnappers mimic friends' voices

**Andy's words:** "https://www.curseforge.com/minecraft/mc-mods/gigarachne can we use this voice mimic on our changeling
kidnappers?" Answered yes, as our own remake.

Gigarachne is Forge 1.20.1 and All Rights Reserved: no code or assets copied. It records friends' voices and plays them
back to lure you, using Simple Voice Chat. We already run an SVC server plugin (VoiceRange.java), so: buffer a few short
clips of each player's real mic audio (in memory only, never saved to disk, never across sessions), and let a changeling
kidnapper play a clip of a friend's voice as locational audio from its position to lure a lone player. Silent when voice
chat is not installed or the friend never spoke. Players opted out of voice chat are never recorded. Change note under
changes/kingdoms/<branch>.md.
