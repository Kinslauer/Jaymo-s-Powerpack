# Context

Shared ground truth for Jaymo's Powerpack. This is the single source all three agents read before starting work. Update it when a build rule, version, or standing ruling changes.

## Project

- **Repo:** `Kinslauer/Jaymo-s-Powerpack` (branch `master`)
- **Mods:** Jaymo's Powers (root), Jaymo's Kingdoms (`kingdoms/`), Jaymo's Kitchen (`kitchen/`)
- **Game:** Minecraft 26.3 Fabric (unobfuscated/Mojang names, no mappings)
- **Pack version:** 0.21.46 (`gradle.properties`)
- **JDK 25**, Gradle wrapper 9.7.1 (`sh ./gradlew build`; `gradlew` not executable in git)
- **Cloud build:** Maven Central returns HTTP 429 → use Google Maven mirror via `~/.gradle/init.d/mirror.gradle`, `JAVA_HOME=/opt/jdk25 sh ./gradlew build`
- **Server:** Steam Deck `~/jaymos-server`; pbdr (pull → build → deploy → restart) runs there

## Build rules (from CLAUDE.md / AI-HANDBOOK.md)

- Check every Minecraft API against the 26.3 sources: `sh ./gradlew genSources`
- Never build `ItemStack`s in static fields/initialisers ("Components not bound yet" crash)
- `packageModpack`, `runClient`, `runServer` only on Andy's PC/Deck
- Build to check work; don't launch long dev client/server runs unless asked
- Never commit `Serverfiles/` or `playit-secret.txt` (secrets); never accept API keys pasted in chat

## Code conventions

- Tuning numbers: `static final` constants at the top of each class
- Each power: own package, `lang/parts/<power>.json`
- Every `AttachmentType` class listed in `JaymosPowers.registerAttachments()`
- Optional mods (Kitchen, WeaponMod) only through a compat class (`KitchenCompat`)
- Edit Java with Python scripts asserting each replaced snippet occurs exactly once; never PowerShell `Set-Content` (adds BOM)
- Regenerate codemap: `python3 tools/codemap.py`
- Kingdoms lang in `lang/parts/*.json` (merged at build; no source `en_us.json`)

## Versions / branches / merges

- Bump `version` on every release in each project's `gradle.properties`; bump only changed mods
- Before merging, bring `master` into the branch and take next free version numbers then
- MODLOG conflict: keep `master`'s file, append only your branch's entry
- Andy merges only when Andy says "merge" or "merge pr" — never merge on your own
- One PR per feature; PR body says what a player will see

## Agent workflow

- Read-only scan (cheap model) → edit agent (mid model, own worktree, build to check)
- Independent verifier per finding; fix loop stops after 2 rounds no progress
- Max 2 parallel tracks

## pbdr (deploy on Steam Deck)

- Pull master, build, deploy jars to `~/jaymos-server`, restart on same world
- Never commit/push from Deck; never delete a world (new world sets old aside as backup)
- Patch notes: player-facing, bold lead word, no class names → `modpack/patch-notes-<pack>.md`

## User preferences

- Bare "More" = add more ideas in same direction; don't ask what it means
- "X does Y" = often a feature description; if ambiguous, keep current behaviour and ask
- Bare video clip = diagnose what it shows
- Controls: R/G/V/B = abilities 1–4, C = ultimate, Z = mode, H = menu, `'` = costume mask
- Fall damage always applies outside creative (exceptions: controlled descents <0.5 b/t, Gravity Weightless)
- Symbiote never feeds on anything living: kill, grab with tendrils, carry as corpse, then feed; every autonomous symbiote action affects the player's screen

## Open issues

- Invisible orc (#988): root cause unconfirmed; grab `logs/latest.log` if it recurs
- Takeovers still drag the camera toward targets
- Aether sky hide on the minimap unconfirmed (look for "Aether veil:" log line)
