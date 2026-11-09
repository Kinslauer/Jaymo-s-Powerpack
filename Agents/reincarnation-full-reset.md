---
status: running
task: reincarnation-full-reset
branch: claude/reincarnation-full-reset
with:
pr:
mods: powers, kingdoms
versions:
started: 2026-11-09
owns:
  - src/main/java/com/jaymospowers/core/wounds/Mortality.java
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Captains.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Parties.java
---
# Reincarnating wipes the whole character: stats, skills, relationships

**Task:** Make reincarnation wipe everything from the previous life — stats, skills, relationships, MCA hearts/memories/villager feelings — and remove the reincarnated player from their party, so no captain journal knowledge or party roster survives into the new life.

## Plan
- Read Mortality.wipe, LifeStory rebirth flow, McaBridge player reset, Captains/Parties/PartyClient
- Find what survives a reincarnate (MCA hearts, memories, party roster/invites/leadership)
- Wipe it in the rebirth flow (not the first-two-deaths wake)
- Build, pr_checks, commit, push, open PR, set in-review

## Notes
- Reported case: captain journal still listed "had known Mile" after reincarnation
- Don't touch first-two-deaths wake: same character coming round, not a new one

## Done
What a player will see, and what wasn't tested.
