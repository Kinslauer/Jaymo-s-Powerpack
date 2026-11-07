# Context

Shared ground truth. Read before starting work. Update when a rule or version changes.

## Project

- Repo: `Kinslauer/Jaymo-s-Powerpack`, branch `master`
- Mods: Jaymo's Powers (root), Kingdoms (`kingdoms/`), Kitchen (`kitchen/`)
- Game: Minecraft 26.3 Fabric (unobfuscated, no mappings)
- Pack version: 0.21.46 (`gradle.properties`)
- JDK 25, Gradle 9.7.1: `sh ./gradlew build`
- Cloud: Google Maven mirror via `~/.gradle/init.d/mirror.gradle`, `JAVA_HOME=/opt/jdk25 sh ./gradlew build`
- Server: Steam Deck `~/jaymos-server`; pbdr (pull → build → deploy → restart)

## Build rules

- Check every API against 26.3 sources: `sh ./gradlew genSources`
- No `ItemStack`s in static fields/initialisers
- `packageModpack`, `runClient`, `runServer` only on Andy's PC/Deck
- Build to check work; no long dev runs unless asked
- Never commit `Serverfiles/` or `playit-secret.txt`; never accept API keys in chat

## Code conventions

- Tuning: `static final` at top of each class
- Each power: own package, `lang/parts/<power>.json`
- Every `AttachmentType` class in `JaymosPowers.registerAttachments()`
- Optional mods only through compat class (`KitchenCompat`)
- Edit Java with Python scripts asserting each replaced snippet occurs once; never PowerShell `Set-Content`
- Regenerate codemap: `python3 tools/codemap.py`
- Kingdoms lang in `lang/parts/*.json` (merged at build)

## Versions / branches / merges

- Bump `version` on every release in each project's `gradle.properties`; changed mods only
- Before merging, bring `master` into the branch; take next free version numbers then
- MODLOG conflict: keep master's file, append only your branch's entry
- Andy merges only on "merge" / "merge pr" — never merge on your own
- One PR per feature; PR body says what a player will see

## Agent workflow

- Scan (read-only, cheap model) → edit (mid model, own worktree, build to check)
- Independent verifier per finding; fix loop stops after 2 rounds no progress
- Max 2 parallel tracks

## pbdr (deploy on Steam Deck)

- Pull master, build, deploy jars to `~/jaymos-server`, restart on same world
- Never commit/push from Deck; never delete a world
- Patch notes: player-facing, bold lead word, no class names → `modpack/patch-notes-<pack>.md`

## User preferences

- Bare "More" = add more ideas in same direction; don't ask what it means
- "X does Y" = often a feature description; if ambiguous, keep current behaviour and ask
- Bare video clip = diagnose what it shows
- Controls: R/G/V/B = abilities 1–4, C = ultimate, Z = mode, H = menu, `'` = costume mask
- Fall damage always outside creative (exceptions: controlled descents <0.5 b/t, Gravity Weightless)
- Symbiote never feeds on anything living: kill, grab with tendrils, carry as corpse, then feed; every autonomous symbiote action affects the player's screen

## Open issues

- Invisible orc (#988): root cause unconfirmed; grab `logs/latest.log` if it recurs
- Takeovers still drag the camera toward targets
- Aether sky hide on minimap unconfirmed (look for "Aether veil:" log line)
