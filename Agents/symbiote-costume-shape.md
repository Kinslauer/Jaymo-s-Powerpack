---
status: in-review
task: 
branch: claude/project-thread-robe1m
with: claude-cloud
pr: 1287
mods: powers
versions: 
started: 2026-11-11
owns:
  - src/main/java/com/jaymospowers/costume/Costume.java
  - src/client/java/com/jaymospowers/client/costume/CostumeClient.java
  - src/client/java/com/jaymospowers/client/costume/CostumeModels.java
  - src/client/java/com/jaymospowers/client/power/web/WebSymbioteLayer.java
  - src/client/java/com/jaymospowers/client/power/web/WebSymbioteHand.java
---
# Symbiote suit takes a costume's shape

**Task:** Andy: any symbiote user with a costume has the symbiote take that shape for its suit, called on and off like the default suit. That is what the Quiet One's cloak is.

## Plan
- `Costume.symbioteShape/shown`: a bonded host's costume shows only while the Suit flag is on (not under the big suit).
- Client: the costume stands in for the plain suit; forming, it folds over the goo part by part.

## Notes
- Small edits too in WebSymbiote (set, toggleMask, unmasked), WebSuitGrowth (suitLaid), BodyCover.ownArms.
- The film thread (PR #1284) owns The Long Defence; told what this builds.

## Done
- A bonded host's costume shows only while the Suit is called; it folds over the goo as it forms; mask key and Mask chip lift the costume's head piece. Compiled (Gradle, JDK 25), not played.
