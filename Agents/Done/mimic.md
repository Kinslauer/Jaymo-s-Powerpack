---
status: done
task: mimic
branch: claude/mimic
with: hermes-b (initial investigation)
pr: 1158
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

## Confirmed design target

- The original Discord request was ItsJim's "Building a Minecraft Horror Mod that Mimics You" (https://www.youtube.com/watch?v=74QYqhvNHWk), especially the player-movement copying at 0:35. The mimic copies a player's skin, movement and behaviour; its revealed form is a distorted humanoid.
- Andy then said this mimic should replace the way Chrono echoes function. Prometheus owns only the original true-form model, texture and animations; gameplay/AI replacement is separate work.
- No art is copied from the video or Jim's Mine. The model is an original lanky, ashen humanoid with a hinged jaw, clawed hands and restless reveal/walk/attack animations. The copied-player disguise continues to use the player model.

## Plan

- Build an editable GeckoLib Blockbench model facing north with feet at y=0, with idle, walk, run, attack, transform, hurt and death animations.
- Export the GeckoLib 5 geometry, animation and texture assets for Powers; render and inspect front, side and back.
- Run `sh ./gradlew build` and `python tools/pr_checks.py`, open the PR, and set the vault task to `in-review`.

## Done

- Worktree E:/wt/mimic on branch `claude/mimic` (from origin/master 5c9f5f3b9).
- Read ChronoEcho, ChronoEchoes, ChronoMemory, ChronoPower; lang parts/chrono.json echo keys.
- PR #1158: original Mimic true-form `.bbmodel`, 128×128 texture, GeckoLib 5 geometry and seven animations, plus exact front/left/back/right inspection render and original-art credit.
- No immediate player-visible change: behaviour and renderer wiring are separate. Validated 61 fully mapped opaque cubes, 21 unique bones and all animation targets; exact four-view render visually checked. Full `sh ./gradlew build` and `tools/pr_checks.py` passed. Not tested on a live entity in game.

## Notes
- 2026-11-08 20:26 PR #1158 is merged into master; moved to Agents/Done.
