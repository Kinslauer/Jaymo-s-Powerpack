---
status: in-review
task: Deaths cut straight to black (Andy's clip 2026-11-19)
branch: claude/project-thread-12j9fn
with: cloud Claude, Reason Art
pr: 1878
mods: powers
versions:
started: 2026-11-19
owns:
  - src/client/java/com/jaymospowers/client/mixin/DeathScreenBlackMixin.java
  - src/client/java/com/jaymospowers/client/wounds/BlackoutHush.java
  - src/main/java/com/jaymospowers/core/UltimateFilm.java
  - src/main/java/com/jaymospowers/core/wounds/Karma.java
---
# Deaths cut to black

**Task:** deaths cut straight to black, silent, long wait, then the respawn screen; no world (enemies) seen after death.

## Done
No film after death (captain gloat scene was the 7.5 s of world), 5 s silent black then 2.5 s fade-up, silence at once,
wicked descent knocks out on the respawn tick. Compiles; not played.
