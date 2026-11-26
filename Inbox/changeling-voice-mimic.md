---
status: claimed
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-a
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

## Notes
- 2026-11-24 13:21 changeling-voice-mimic's claim had no sign of work for 30 min: handed back as open.
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: stalled agent note has no PR. Inspect recoverable branch/work before restarting; no completion established. Keep microphone clips memory-only, session-only, and honour voice-chat opt-out.
- 2026-11-25 23:26 YOUTH delegated this to apollo (Claude approved): SVC mic buffer plus changeling kidnappers; own remake, nothing copied from Gigarachne
- 2026-11-26 02:18 Question: Before implementing changeling voice mimic, I need clarification:

1. **Who counts as a "friend" whose voice a changeling can mimic?**
   - MCA relationship (partner, kin, friend)?
   - Party members (Parties mod)?
   - Kingdom/faction members?
   - Players who have spoken near each other recently?

2. **When does a changeling (shapeshifter) play a mimic clip?**
   - When hunting a lone player (shapeshifter's Mind.FIGHT/FLEE)?
   - When guarding a vault (StolenCrowns vault keepers)?
   - When near a player it's targeting?
   - Randomly while roaming?

3. **Voice clip buffering details:**
   - How many clips per player? (e.g., 3-5 clips)
   - How long is each clip? (e.g., 2-5 seconds of audio frames)
   - How often to record? (continuous rolling buffer, or only when speaking?)

4. **Playback behavior:**
   - Play once when initiating hunt, or repeatedly?
   - Locational from shapeshifter's position with SVC distance attenuation?
   - Only for the targeted player, or for all nearby players?

5. **Opt-out:** How to detect if a player has opted out of voice chat? (VoiceRange already handles some of this)
- 2026-11-26 09:35 Cloud Claude 2026-11-26: Andy's questions for this task are on the tick page https://claude.ai/artifact/16LbvMbbHXJuH2qUMngRP4 (db picks/andy). Wait for his answers there; don't build before he answers.
- 2026-11-26 09:58 Andy's picks 2026-11-26 09:56 on the Waiting on Andy page (https://claude.ai/artifact/16LbvMbbHXJuH2qUMngRP4, db picks/andy): (1) whose voice: any other player on the server; (2) when: only while stalking a lone player before it grabs them; (3) clips: 3 short clips of about 3 s each per player, recorded only while they talk, oldest dropped first (suggested, kept); (4) playback: heard by EVERYONE nearby, not just the target, from the changeling's position. Opt-out as the task says (never record players with voice chat off). Ready to build.
- 2026-11-26 05:09 Andy (2026-11-26 5:10 AM): 'first task should be codex on voice mimic'. Taken from Apollo; Codex builds it (T3 delegate from PC Claude).
- 2026-11-26 06:52 codex's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 08:30 Codex ran out of usage at 5:30 AM before finishing. Handed to Apollo (Claude, 8:30 AM). The code is done and pushed: branch t3/changeling-voice-mimic, commit 7bda118f17 (ChangelingVoices, ChangelingLures, ChangelingVoiceCompat, Doubles hook, change note). Apollo: check out that branch, don't rewrite it. Run the full ./gradlew build in kingdoms/, fix only what fails, push, open the PR against master. The PR description must ask Andy what a changeling's grab should do to a player. Then set status=in-review pr=<N>.
- 2026-11-26 12:46 hermes-a's claim had no sign of work for 30 min: handed back as open.
