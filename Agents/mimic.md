---
status: running
task: mimic
branch: claude/mimic
with: hermes-b (initial investigation)
pr:
mods: Powers
versions:
started: 2026-11-08
owns:
  - art/mimic.bbmodel
  - art/mimic-render.png
  - art/mimic-CREDITS.md
  - src/main/resources/assets/jaymospowers/geckolib/models/entity/mimic.geo.json
  - src/main/resources/assets/jaymospowers/geckolib/animations/entity/mimic.animation.json
  - src/main/resources/assets/jaymospowers/textures/entity/mimic.png
  - changes/powers/claude-mimic.md
---

# Mimic: replace the way echos function

**Task:** Inbox task mimic (E:/Jaymo Vault Live/Inbox/mimic.md). Andy (2026-11-08): "prometheus can pick up the mimic, and tell him I want that to replace the way the echos function". A mimic feature that replaces the existing echos mechanic; details to be confirmed with prometheus (Discord thread 1569108674721357906, Tiv).

## What "the echos" are (investigation, all in E:/wt/mimic = origin/master)

- Powers Chrono echoes: `com.jaymospowers.power.chrono.ChronoEcho` + `ChronoEchoes`. One echo owed per Chrono level-up (mastery 2-4, ascension 5), appears 5-25 min later near the owner, one echo per Chrono at a time. Wears the owner's skin and holds what they hold; walks the owner's recorded routes, mines what they mine, places what they place, attacks every living thing near it (owner included). Outside the Nether any hurt shatters it and it returns 1-10 min later; in the Nether it can die for good (and corrupts the owner); in the Future it can die too, opening the future to its owner. When the owner ascends to Timeless the echo takes orders: standby / harvest / guard / loop / record. Files: `ChronoEcho.java`, `ChronoEchoes.java`, `ChronoMemory.java`, `ChronoDirectives.java`, `ChronoPower.java` (onLevelUp owes the echo; onLost fades all), lang `entity.jaymospowers.chrono_echo` = "Echo", power tab "Echo Orders".
- Kingdoms Quiet One "echo strike": spirits move repeating melee blows `ECHO_DELAY=10` ticks later at `ECHO_SHARE=0.5` damage (`kingdoms/.../spirits/QuietOne.java`). Probably not what's meant, but unconfirmed.

Existing (unrelated) "Mimic" mobs: Powers Aether dungeon mimic (sentry crate that's alive, `mimic_spawn_egg`) and Kingdoms chest mimic.

## Open questions (to confirm with prometheus / Tiv; do not guess)

1. Which "echos" are meant — Chrono echoes, the Quiet One echo strike, or both?
2. What should the mimic be (mob, item, spawn egg?), and what does "prometheus can pick up the mimic" mean mechanically?
3. Exactly what does the mimic take over from echos — spawning on level-up, behaviour, the orders screen, Nether/Future death, ascension?
4. What replaces echos for Chrono players (curse, Stopped Clock, Timeless orders)?

## Plan

- Wait on prometheus confirmation via Andy/Tiv (Discord thread 1569108674721357906).
- Once confirmed: design the mimic feature, implement in Powers, `sh ./gradlew build`, `python tools/pr_checks.py`, open PR, set vault task `status=in-review`.

## Done

- Worktree E:/wt/mimic on branch `claude/mimic` (from origin/master 5c9f5f3b9).
- Read ChronoEcho, ChronoEchoes, ChronoMemory, ChronoPower; lang parts/chrono.json echo keys.
