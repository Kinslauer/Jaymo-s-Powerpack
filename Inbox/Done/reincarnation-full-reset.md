---
status: done
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-b
pr: 1201
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/Mortality.java, kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java, kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java, kingdoms/src/main/java/com/jaymoskingdoms/Captains.java, kingdoms/src/main/java/com/jaymoskingdoms/Parties.java]
writer: hermes-writer
text_pass: 
---

# Reincarnating wipes the whole character: stats, skills, relationships

A new life starts with nothing from the last one, including who knew whom.

## What's wanted

Tiv, in the Tasks thread: "EVERYTHING should be reset on a reincarnate, Stats, skills, relationships, I saw in the captain journal after that death that I had known Mile."

## Notes

- Reported case: after a reincarnation, the traveller's captain journal still listed a captain as known ("had known Mile"). Whatever survives that shouldn't.
- `Mortality.wipe` already clears every jaymospowers / jaymoskingdoms / jaymoskitchen attachment, plus inventory, ender chest, XP, effects and spawn. So the leak is either outside those attachments (MCA's own player data: hearts, memories, relationships toward villagers) or something that gets rebuilt during the rebirth flow.
- Known gap from the last reincarnation work: villagers' MCA hearts toward the player were deliberately not reset. Tiv now wants them reset — check `McaBridge`'s player reset and whether relationships, memories and villager-side feelings survive it.
- Verify the reported case before fixing: was the journal read in the new life, or the same life after the death but before rebirth? The clip of the death ends on MCA's "Who are you?" creator, so it reads as a real reincarnation.
- Don't touch the first-two-deaths wake: those are the same character coming round, not a new one. This is about Reincarnate.
- Parties wipe too (Tiv, same thread: "Parties should wipe upon reincarnation too. if a player is reincarnated he leaves the party."). A reincarnated player comes out of their party: off the roster and off everyone's client list (`Parties`, `PartyClient`), with no stale invite or leadership left behind that would pull the new character back in.
- 2026-11-09 17:08 Text pass: rewrote MODLOG entry to player-facing language in change note (changes/kingdoms/claude-reincarnation-full-reset.md); reverted PR's MODLOG addition and version bump; no lang keys or dialogue changed
- 2026-11-09 17:29 Review (head d1a55f3): changes needed. 1) Old-style PR: it edits kingdoms/MODLOG.md and the version in kingdoms/gradle.properties and has no change note, so the Change notes check is red. Run 'py -3 tools/change_note.py convert' (the text pass says it did this, but that commit isn't on the branch: push it). 2) LifeStory.java:2443: McaBridge.forgetPlayer runs after McaCompat.newDestiny has opened MCA's 'Who are you?' creator, and it calls PlayerSaveData.reset() and setEntityDataSet(false), which can wipe what the new character is being built from. Forget first, then launch the destiny: move the three wipes above newDestiny. 3) McaBridge.forgetPlayer only reaches villagers loaded right now (entities in the player's current level); villagers in unloaded chunks or other dimensions keep their hearts and memories. Either forget them when they next load (keep the forgotten UUIDs on the level and clear them in the villager's load) or say plainly in the PR and change note that only nearby villagers forget; the PR body currently says villagers no longer remember the player, full stop. 4) '(0.7.x)' tags in the new comments (Captains.java, LifeStory.java, McaBridge.java, Parties.java); CLAUDE.md forbids version numbers in comments. 5) Indentation: Captains.java:536 onward and Parties.java:460-478 (the closing braces, forgetPlayer, reborn and the kick() line) are one tab too deep. The rest is right: captains forget the meeting (the reported 'had known Mile'), the party is left cleanly with invites cleared, and the first-two-deaths wake isn't touched.
- 2026-11-09 18:01 PR #1201 is merged into master; moved to Inbox/Done.
