# Contributing as a friend of the pack

This is for players of Jaymo's Powers who have been invited to the repo to work on their own power (Jung on Wind was
the first). It is short on purpose: the full rules are in `docs/AI-HANDBOOK.md`, and this page only says which parts of
it apply to you and where your power lives. If you work with an AI assistant, point it at this file first.

## The deal

- You work on a **branch** and open a **pull request** into `master`. Andy reviews it and is the only one who merges,
  and only when he says "merge".
- `master` has no branch protection turned on, so collaborator access could push to it directly. **Never push to `master`, never merge, never force-push someone else's branch.** Pushing
  to `master` also fires the Release workflow, which bumps versions and commits on its own.
- Stay inside your power where you can. If a change needs another power's or system's files, say so in the PR body.

## Getting set up

1. Accept the GitHub collaborator invite from Kinslauer, then clone:
   `git clone https://github.com/Kinslauer/jaymo-s-powerpack.git`
2. Make a branch off the newest `master`, named after you and the change:
   `git checkout master && git pull && git checkout -b jung/wind-<short-name>`
3. Install **JDK 25** (Temurin). The Gradle wrapper does the rest.
4. Build Powers from the repo root: `gradlew.bat build` on Windows, `sh ./gradlew build` elsewhere. The first build
   downloads a lot. In a cloud session, follow `docs/AI-HANDBOOK.md` section 3 (JDK path, Maven mirror).
5. To try it: `gradlew.bat runClient` starts a dev client with the mod. In game, `/power set @s wind` gives you the
   power; `docs/wind.md` has test steps for every move. `/powerdebug wind ...` has Wind's test commands (`WindDebug`).

You can skip the parts of `CLAUDE.md` and the handbook about the Obsidian vault, agent notes in `E:/Jaymo Vault Live`,
Hermes, pbdr and the Steam Deck. Those are Andy's own machines and bots.

## Where Wind lives

| What | Where |
|---|---|
| Design doc: controls, every move, tuning, test steps | `docs/wind.md` |
| One line per class (grep this before opening code) | `docs/codemap/powers.md`, search `power/wind/` |
| Server side: the power, abilities, modes, moves | `src/main/java/com/jaymospowers/power/wind/` (`WindPower` is the core; `WindMoves` holds your 0.21.27 revamp) |
| Client side: flight, poses, HUD, sounds, effects | `src/client/java/com/jaymospowers/client/power/wind/` (`WindPowerClient`, `WindFlightController`, `WindMovePose`, `WindHud`) |
| Packets | `power/wind/WindNet.java` |
| Mixins | `power/wind/mixin/`, `client/power/wind/mixin/`, listed in `src/main/resources/jaymospowers.wind.mixins.json` and `src/client/resources/jaymospowers.wind.client.mixins.json` |
| Player-visible text | `src/main/resources/assets/jaymospowers/lang/parts/wind.json` |
| Textures, models, animations | `src/main/resources/assets/jaymospowers/` (`textures/`, `models/`, `items/`, `player_animations/`) |
| Recent Wind history | `MODLOG.md` (search "wind"), older in `docs/modlog/` |

Balance numbers are `static final` constants at the top of each class: change them there.

## Before you open the PR

Go through `docs/AI-HANDBOOK.md`, section 4, "Before you open a PR". The short version:

1. Add a change note `changes/powers/<your-branch-name>.md`: a `# <title>` line, then what a player will see, the main
   classes, and what you didn't test. See `changes/README.md`.
2. **Don't** touch `version=` in `gradle.properties`, any `MODLOG.md` or `docs/codemap/`. The Release workflow does
   those after Andy merges.
3. `sh ./gradlew build` passes, and `python3 tools/pr_checks.py` comes back clean.
4. New text goes in `lang/parts/wind.json`, not in code.
5. Andy's standing rulings in handbook section 7 apply to you too. The ones Wind runs into most: no slash commands for
   player features (use keys and UIs), no chat text announcing discoveries (sounds and visuals are fine), and new
   features must work with the old ones.

## Opening the PR

- Push your branch (`git push -u origin <branch>`) and open a PR into `master`.
- Title: what changed, in plain words. Body: what a player will see, and what you did and didn't test in game.
- The **PR checks** action builds all three mods and re-checks the rules above. Fix anything red.
- Then tell Andy. He reviews it with his Claude, which may push small fixes or ask you for changes on the PR, and he
  merges when he's happy. After the merge the version is bumped for you and the change lands in the next pack build.
