---
status: in-review
task: lanterns-04-crime-word
branch: claude/lanterns-04-crime-word
with: Claude Code (PC)
pr: 1763
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/CrimeWord.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/Theft.java (commit split into lawKnows; register line)
  - kingdoms/src/main/java/com/jaymoskingdoms/Recollections.java (gossipLine hearsay lines)
  - kingdoms/src/main/java/com/jaymoskingdoms/Approaches.java (free(): runners)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/crime-word.json
  - changes/kingdoms/lanterns-04-crime-word.md
---
# lanterns-04-crime-word

**Task:** Lanterns patch 4: the witness runs to tell, hushing the witness, gossip house to house, word down the road.

## Notes
- Stacked on lanterns-01 (#1751, which also changes Theft#commit).
- JaymosKingdoms.java is owned by dialogue-letterbox-pov, so CrimeWord registers from Theft.register().

## Done
A witness with no guard in earshot runs to tell one; catch them to plead, pay or stare them down. Crime hearsay spreads house to house and voices in gossip; heavy crimes reach road-linked realms days later. CI passes; not played.
