---
status: claimed
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-b
pr:
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/Mortality.java, kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java, kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java, kingdoms/src/main/java/com/jaymoskingdoms/Captains.java, kingdoms/src/main/java/com/jaymoskingdoms/Parties.java]
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
