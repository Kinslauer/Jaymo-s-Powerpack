---
status: done
task: reincarnation-full-reset
branch: claude/reincarnation-full-reset
with:
pr: 1201
mods: powers, kingdoms
versions: kingdoms 0.7.676 (no version bump in this PR; release step bumps after merge)
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
Player will see: reincarnation gives a fully fresh life — fresh MCA look and name, villagers forget the player (hearts, memories, relationships, marriage and family ties), a clean party exit with no stale roster, invites or leadership, and no captain remembers having met them (captain journal clean in the new life). Not tested in game; compile-checked only. PR #1201: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1201
- 2026-11-09 18:01 PR #1201 is merged into master; moved to Agents/Done.
