---
status: stalled
task: The Long Defence on GeckoLib actors, recorded to a tape and replayed
branch: claude/project-thread-9yvqn9
with: claude-cloud
pr: 1284
mods: kingdoms
versions: 
started: 2026-11-11
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/films/LoreFilmClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/films/FilmTape.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/films/FilmActorRenderer.java
  - kingdoms/src/main/java/com/jaymoskingdoms/films/FilmActor.java
  - kingdoms/src/main/java/com/jaymoskingdoms/films/Moves.java
  - kingdoms/tools/gen_film_actor.py
  - kingdoms/tools/humanoid_moves.py
  - kingdoms/src/main/resources/assets/jaymoskingdoms/films/long_defence.tape
---
# The Long Defence on GeckoLib actors and tape

**Task:** Andy: run every film actor's model through GeckoLib (animations that match gameplay or realistic one-off moves, reusable later), and capture the film in the engine once and replay it.

## Notes
- PR #1284 is built on #1268 at 938baad3 and replaces it. FilmPose is deleted; anything new in the film that shows or sounds must go through a stage op in LoreFilmClient so it reaches the tape, and the tape must be re-recorded (`/kingdomfilm record long_defence`, or `-Djaymoskingdoms.filmRecord=true`), then copied to `assets/jaymoskingdoms/films/`.
- Ported from #1268's later commits: forest fire (flames are tape events IGNITE/FLAME/QUENCH), 860 ticks.
- Slingshot glide ported (moves `sling`, `glide`; END 756, FILM_TICKS 886). The cloak rule is built on the model: solid cape at rest, ribbon bones while at work, winding to part and to form (`FilmActor.cloak`, tape event CLOAK).
- The imps are in the film (four, `LoreFilmClient.imps`, tape event CHARGE, `Imp.stageCharge`); master with #1286 is merged into the branch.

## Done
- Film plays from the shipped tape; watched in a dev client, live and replayed. Not seen in the full pack; no sound in the dev client.
- 2026-11-11 13:30 No sign of work for 30 min: marked stalled (whoever ran it stopped).
