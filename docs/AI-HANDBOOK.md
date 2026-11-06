# AI Handbook: working on Jaymo's Powers with any assistant

This file is for any AI coding assistant (Claude, Gemini, Codex, Cursor, a local model) that picks up this repository.
It gathers the rules, habits and workflows that grew up over many Claude sessions, so another assistant can carry on
without asking Andy everything again. Read it together with `CLAUDE.md` (the short rules) and `docs/HANDOFF.md`
(what each folder is, how to build, hard-won 26.3 API facts).

Written 2026-11-06, at Powers/pack 0.21.40, Kingdoms 0.7.642, Kitchen 0.5.84.

## 1. What to read, in order

1. `CLAUDE.md`: the short project rules. Everything in it applies to every assistant, not only Claude.
2. `docs/HANDOFF.md`: folders, build, 26.3 API facts, Andy's preferences.
3. This handbook: standing rulings and workflows.
4. `docs/codemap/<powers|kingdoms|kitchen>.md`: one line per class (path, what it does, main methods). Grep it
   before searching the code, then open only what you need. Regenerate with `python3 tools/codemap.py`.
5. `MODLOG.md`, `kingdoms/MODLOG.md`, `kitchen/MODLOG.md`: recent design log. Older entries are in `docs/modlog/`.
6. `docs/ai-bundle/`: the exported project memory (one file per topic), the reference notes and the modpack readout.
   See `docs/ai-bundle/README.md`.

Skills and workflows the Claude sessions used live in `.claude/`:

- `.claude/skills/pbdr/SKILL.md`: pull, build, deploy, restart the Steam Deck server (section 5).
- `.claude/agents/scanner.md` (read-only scan agent) and `.claude/agents/editor.md` (edit agent in its own worktree).
- `.claude/workflows/audit-fix.js`: audit files, refute false positives, patch each finding, cross-check the diffs.
- `.claude/settings.json` and `.mcp.json`: the Gemini second-opinion server (section 8).

These are plain markdown and JavaScript. To use them elsewhere, paste the skill text into the other tool's rules file
(Cursor `.cursor/rules/`, Gemini CLI `GEMINI.md`, Codex and most others `AGENTS.md`). `AGENTS.md` and `GEMINI.md` at the
repo root already point here.

## 2. The project

- Owner: Andy (GitHub `Kinslauer`). Private repo `Kinslauer/jaymo-s-powerpack`, default branch `master`.
- A Minecraft 26.3 Fabric modpack for Andy's friends' SMP server: **Jaymo's Powers** (repo root), **Jaymo's Kingdoms**
  (`kingdoms/`) and **Jaymo's Kitchen** (`kitchen/`). Each is its own Gradle project (JDK 25). Kitchen is built from
  source (its jar in `libs/` is what the pack ships).
- The server runs on Andy's Steam Deck. Friends get `modpack/Jaymos-Powers-Modpack-<pack>.zip`.
- Andy plays with friends and sends many feature and tuning requests, often in the middle of other work. Most merged
  work since about #941 has been built but not yet played; treat "merged" as "compiled", not "tested in game".

## 3. Building

- JDK 25 and the Gradle wrapper. `gradlew` isn't executable in git: run `sh ./gradlew build`.
- Root `sh ./gradlew build` builds Powers. Run it in `kingdoms/` and `kitchen/` for those mods.
- **Cloud build setup** (Andy's standing approval, 2026-10-11: "i approve the mirror period"): Maven Central returns
  HTTP 429 from cloud containers, so builds go through the Google Maven mirror.
  1. Install Temurin JDK 25 (from api.adoptium.net) into `/opt/jdk25`.
  2. Add `~/.gradle/init.d/mirror.gradle` that swaps `repo.maven.apache.org` for
     `https://maven-central.storage-download.googleapis.com/maven2/`.
  3. `JAVA_HOME=/opt/jdk25 sh ./gradlew build`.
- If the root build fails because `libs/jaymos-kitchen-*.jar` is older than `kitchen/gradle.properties`' version,
  build `kitchen/` and swap its jar into `libs/` for the build. Don't commit the swap.
- Andy's account needs trusted devices, so nothing can run on Andy's PC from a cloud session.
- `gradlew build` does not check mixin targets. Check them against the 26.3 jars (`javap` on the loom-cache jars).
- A build doesn't catch startup crashes. For any change that adds items, entity types or registries, boot
  `runServer` once before calling it ready, and say plainly if you didn't. Never build `ItemStack`s in static fields or
  initialisers: 26.3 crashes at startup with "Components not bound yet". Use `Item`s or `Supplier<ItemStack>`.
- Don't launch long dev client/server runs unless Andy asks (a short boot check is fine).
- `packageModpack`, `serverMods`, `profileMods` only make sense on Andy's PC or the Deck.

### 26.3 facts that keep coming up

Check every Minecraft API against the 26.3 sources (`sh ./gradlew genSources`) before using it. Beyond HANDOFF.md:

- Mouse buttons start at 1.
- Recipe categories are only building, redstone, equipment, misc.
- Loot tables use `modifier`/`condition` objects with a `type`.
- Payload types must be registered before their receivers.
- "Network Protocol Error" on join usually means a client packet handler threw an exception.
- Every class holding an attachment type must be listed in `JaymosPowers.registerAttachments()`.
- CurseForge file lists: `www.curseforge.com/api/v1/mods/<id>/files`.

## 4. Versions, branches, PRs and merging

- **Bump the version on every release**: `version` in `gradle.properties` (pack/Powers), `kingdoms/gradle.properties`,
  `kitchen/gradle.properties`. Bump only the mods you changed (the pack version follows Powers).
- **Versions collide constantly** because several branches run at once. Right before merging, bring `master` into the
  branch and take the next free version numbers then, not when you start. Tell anyone else working in parallel which
  numbers you took.
- On a MODLOG merge conflict, keep master's file and append only your branch's own entry at the end.
- Kingdoms lang lives in `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/*.json` (merged at build; no
  source `en_us.json`). Put a new feature's keys in its own part. If an old branch still has a single `en_us.json`,
  keep it on the conflict, then run `python3 tools/lang_split.py`.
- Story hints (loading-screen tips): `StoryHoldScreen.HINTS`, keys `gui.jaymoskingdoms.story.hint.N`. New features
  add a hint; hint numbers collide like versions.
- **Andy merges only when Andy says "merge" or "merge pr".** Never merge on your own. Merge one PR at a time, never in
  parallel. One PR per feature; each PR body says what a player will see.
- Never commit `Serverfiles/` or `playit-secret.txt` (they hold secrets). Never accept API keys pasted in chat; ask
  Andy to revoke any that get pasted.

## 5. pbdr (pull, build, deploy, restart)

"pbdr" means pull master, build the pack, deploy the jars to the Deck server and restart it on the same world.
"pbdr new world" (or "new map") does the same but sets the old world aside as a backup and starts fresh. Never delete
a world. The full step list is in `.claude/skills/pbdr/SKILL.md`; it must run in a session on the Deck itself.
Andy asking for pbdr is the go-ahead for every step, including stopping the server.

- Server folder: `~/jaymos-server` on the Deck. Jars also go to Andy's CurseForge profile and
  `Desktop\Serverfiles\mods` when built on the PC.
- Every pull ends with player-facing patch notes (what changed since the last pull, in game terms, no class names),
  saved beside the friends' zip.
- Voice chat: playit tunnel `malls-harrington.tun.ply.gg:44082` to local UDP 24454. Friends join at
  `pgsql-jvc.tun.ply.gg`.
- Andy's Deck logs end up in `E:\Origins Mod\modpack\deck-logs`.

## 6. How Andy likes to work

- A bare "More" means: add more of your own ideas in the same direction. Don't ask what Andy means; build a few
  extras, list them briefly, keep them tunable.
- A one-line "X does Y" is often a feature description, not a bug report. If it's ambiguous, keep current behaviour
  and ask.
- A bare video clip means: diagnose what it shows.
- When Andy shares a link or video, check `docs/ai-bundle/references/Reference-Notes.md` first (grep the URL or file
  name) and reuse the entry. After reading a new one, add a 2-4 line entry: what it is, licence/loader/version, what we
  took, verdict, which PR.
- New features must work with old ones, especially villager chat interactions and the SevTech-style progressive world
  generation.
- Tuning numbers live in `static final` constants at the top of each class. Change balance there.
- Keep each power in its own package and `lang/parts/<power>.json`.
- Optional mods (Kitchen, WeaponMod) only through a compat class that checks they're loaded (`KitchenCompat`).
- Don't copy other mods' code or art unless the licence allows it. Many mods Andy likes are ARR and are remade from
  scratch instead (section 7).
- Big patches run as parallel tracks, one per power or area, so they don't edit the same files. Two at a time; four at
  once hit usage limits.
- Patch notes for testers: short bullets, bold lead word, no internal names. Keep out what Andy cut before (most
  power/lore detail, Aether, wind dragons, Nether faction, Xenoform, Flame Avatar, Giants, vampires/werewolves,
  temperaments, ruler/guard detail, mod lists) unless asked.

## 7. Andy's standing rulings

These are game-design decisions Andy made. Keep them unless Andy changes them.

**General**
- Mob griefing is always on (Kingdoms `PackRules` enforces it).
- No slash commands for player features; use UIs. No discovery chat text; sounds and speech bubbles are fine.
- Usernames only in commands; MCA names in play.
- Clothes only from the tailor. No punching trees. Gold and diamond gear off. MCA dyed hair and the Homosexual trait off.
- Fall damage always applies outside creative (except controlled descents slower than 0.5 b/t, and Gravity's
  Weightless).

**Kingdoms and villages**
- Kingdoms only come from Epic villages. Grim keeps and citadels (Winterhold etc.) are only fiefs or allied small
  villages.
- A villager's attitude must stay consistent through a conversation: anything a villager says or does toward a player
  respects `Introductions.wary(player, villager)`.
- Each villager wants something tied to their personality. Special NPCs (Judge, Keeper, masters) get only relevant
  options; the Judge reset is always available.
- Animals in houses, shops or the speakeasy get shooed, and killed after 30 s. Herd animals flee nearby killings.
- Tidings only through a Pigeon Roost: rare, big news. Culture coins everywhere; emeralds are a gem. Seven patron gods
  plus The Truth. Gem sockets.
- Everyone has friendly, wary and fight lines for orcs. Invisible raiders, if intended, need a killable caster.
- Only five bloods are human: Heartlander, Nightborn, Earthling, Stormborn, Dawnborn (`Faiths.human`). Others get
  Prejudice degree 1 (Ember/Frost/Rubber/Divine) or 2 (End/Deep/Dragon); patrons via `Faiths.lean`.
- Items beyond a player's world tier or knowledge are hidden from JEI and the recipe book until reached or held.

**Downed, death and bodies**
- All humanoids go down instead of dying, unless beheaded or gibbed. Executions: hold use on a downed foe. Righteous
  guards arrest instead (jail cells in kingdoms and fiefdoms); jail escapes must be realistic, no teleporting.
- Royals are never left dead (recovered and buried) but can be gibbed. Only Stoneborn are never gibbed.
- Downed bodies stay in the world until they come to; players can carry them. No input means no respawn roll, a
  "being carried" notice, and revival at safety. Live animals can be picked up; some (snakes) bite.
- The first two deaths always wake up after a black screen (in the wild, kidnapped, or taken in by a village). The
  final death's button is "Reincarnate", which wipes absolutely everything and stays black until ready. The death screen
  cuts to black (no red) and slowly fades into scenario text.
- Ragdolls drop where they stand; first person follows the head.
- Chrono's cheat death is R on the death screen, with no prompt or hint anywhere. Chrono players are never told.
- Zombie players vanish in heaven. Corpses flop with their eyes closed.
- Dismemberment: blades 30% on overkill (skeletons x3), blunt never cuts; Grim Hand +40% per rank; explosions gib by
  proximity. Code in `core/Dismember.java` and `core/wounds/Wounds.java`.
- Blood is toned down, no pixel blood; skeletons chip bone. Rubber-folk survive being halved and mend; other halved
  players bleed out in about 8-10 s.
- Captives: captors stay calm until the captive misbehaves. Collar chain or rope is visible and breakable.
- Cutscenes: no FOV modifiers, the player is invulnerable, the speaker stops to talk.

**Powers and combat**
- The symbiote never feeds on anything living: kill, grab with tendrils, carry as a corpse, then feed. Every
  autonomous symbiote action affects the player's screen.
- Takeover bodies face the travel direction; the camera stays on the look direction.
- Food hearts are kept after nourishment ends but never heal back.
- Werewolves: bites curse players too; the beast holds and shows nothing; the lean wolf form is earned by taming the
  curse (sneak+U); a ritual off the full moon gives mostly-human wolfblood.
- Armour: 30% durability but absorbs more; broken armour stays on but protects nothing; no anvil or Mending repairs in
  combat.
- Every melee weapon parries and has a skill bar move.
- Only one tool or weapon is shown worn on the body.
- Valor and Bio-built levelling are about 4x slower than they first were.
- Knowledge levels: boss kills unlock Lv1 anvils, Lv2 smithing, Lv3 enchanting, Lv4 scroll spells/Storm Arts, Lv5 End
  eyes; tiers need levels.

**Starts**
- Open starts: Rock bottom, Family farmhand (village start, living in the barn with a farming family), Heritage
  specifics, Banished, Shipwreck, and Let fate decide (random from all, even locked, with a % table).
- Locked starts, each with its own deed and advancement: City, Wild, Sworn, Sentenced, Nobodies, Burned. The Bio-built
  heritage is earned.

**Controls** (defaults)
- R/G/V/B = abilities 1-4, C = ultimate, Z = mode, H = menu, ' = costume mask. "G+B" means G, then B.
- Zoom: tap then hold Shift, or Shift+Left Alt (Shift+Alt ends only on Alt release). Roll: Left Alt.
- Ledge grab: Sneak+scroll down climbs 0.7-1.95 high blocks, bellies into 1-high gaps, vaults fences; empty-hand
  right-click grabs a ledge you can see over.
- Right-click a foe with a weapon = guard; sneak+right-click talks.
- If zoom fails on Windows, check the Alt+Shift language-switch shortcut.

**Mods that are out**
- ARR mods remade ourselves instead of shipped: Palladium, Fisk's, Weather2, Physics Mod, Village Life, Legends
  Superheroes, Perception, Apotheosis art, Viltrumite, Courier Owls, Werewolves: Become a Beast art.
- Don't re-add Alex's Mobs/Citadel or Distant Horizons. Can't use Create Aeronautics, ParCool, Nature X, Simple Clouds,
  Sleeping Overhaul 2, Real Camera.

## 8. Agents, verification and the Gemini second opinion

These habits came from running many Claude agents at once; they work for any multi-agent setup.

- Read-only scan stages use a cheap, fast model (the `scanner` agent). Edit stages use a mid-size model (the `editor`
  agent), each in its own git worktree.
- Every finding gets an independent verifier before it's reported. A fix loop stops after two rounds with no progress.
- `.claude/workflows/audit-fix.js` is the template: audit batches of files, refute each finding, patch survivors in
  worktrees, verify each patch, then cross-check all diffs together.
- Gemini second opinion: `.mcp.json` runs omni-ai-mcp 4.8.2 through `tools/gemini-mcp.sh`. It needs `GEMINI_API_KEY`
  as a plain environment variable (a proxy network secret breaks it because Gemini rejects the Bearer header). Only its
  text/code tools are used (`ask_gemini`, `gemini_code_review`, `gemini_challenge`, `gemini_analyze_codebase`); media,
  agent and OpenRouter tools are denied in `.claude/settings.json`. Treat its answers as advice to check against the
  code. On the free tier only the flash models answer.

## 9. Open issues to remember

- The invisible orc (#988): root cause unconfirmed. If it happens again, grab `logs/latest.log` before restarting.
- Takeovers still drag the camera toward targets; Andy may want it free.
- The Aether sky hide on the minimap is unconfirmed (look for the "Aether veil:" log line).
- `docs/Plan-0.8.md` holds the plan for the next Powers patch track set (Gravity, Arsenal, Chrono, Web).

## 10. What doesn't carry over from the Claude app

The Claude setup used a project "coordinator" that started one thread per task, shared memory between threads, and
merged on Andy's word. Another assistant won't have that. Run one task at a time: give it this handbook, `CLAUDE.md`
and the relevant memory file from `docs/ai-bundle/memory/`, and keep the same version and merge rules. When you learn
a lasting fact (a ruling, a gotcha), add it to this handbook or the matching memory file so the next assistant has it.
