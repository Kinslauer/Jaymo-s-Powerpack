---
status: in-review
task: lanterns-05-patrol-law
branch: claude/lanterns-05-patrol-law
with: Claude Code (PC)
pr: 1764
mods: powers, kingdoms
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/power/zfighter/PatrolLaw.java (new)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (causes, fight/countArrest split, menu, init)
  - src/main/java/com/jaymospowers/JaymosPowers.java (attachment list line)
  - kingdoms/src/main/java/com/jaymoskingdoms/Theft.java (wantedUntil)
  - kingdoms/src/main/java/com/jaymoskingdoms/Captures.java (one hook line)
  - kingdoms/src/main/java/com/jaymoskingdoms/PowersCompat.java (patrolHandedOver at the end)
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_patrol_law.json
  - changes/powers/lanterns-05-patrol-law.md, changes/kingdoms/lanterns-05-patrol-law.md
---
# lanterns-05-patrol-law

**Task:** Lanterns patch 5: Patrol warrants for crooked officers, Patrol arrests by handing crooks over, a partner patrolman.

## Notes
- Lore: "The Patrol's own law" (new) paragraph added after Relief runs, for Andy to approve.
- Partner is our own follow logic in PatrolLaw, not Kingdoms' Followers orders screen.
- Arrests count when a hogtied crook is handed to a ruler for its bounty (Bounties' existing claim), not to guards.

## Done
Crooked officers get a Patrol warrant (a rank lost per crime, a patrolman sent to bring them in). Handing a hogtied crook to a ruler counts as an arrest. From Officer, a partner patrolman follows, fights beside you and rides along. CI passes; not played.
