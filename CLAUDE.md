# Jaymo's Powers (and Kingdoms, Kitchen)

Minecraft 26.3 Fabric mods for the user's friends' modpack. **Read `docs/HANDOFF.md` first**: what each folder is,
how to build, hard-won 26.3 API facts, the user's preferences, and what to do next.
- **Project vault** (`docs/vault/`, Obsidian): read `docs/vault/Home.md` first (versions, open PRs, what's next), then
  follow `docs/vault/How agents use this vault.md`. Live notes go straight into the main checkout, never your worktree:
  make your agent note in `E:/Origins Mod/docs/vault/Live/Agents/` from `Templates/Agent.md` (branch, PR, files you
  own) when you start, update it when you finish, never edit a file another running agent owns, and append each of the
  user's decisions to `E:/Origins Mod/docs/vault/Live/Decisions.md`.

- Root: Jaymo's Powers (`./gradlew build`). `kingdoms/`: Jaymo's Kingdoms. `kitchen/`: Jaymo's Kitchen. Each is its
  own Gradle project (JDK 25).
- Finding code: grep `docs/codemap/<powers|kingdoms|kitchen>.md` first (one line per class: path, what it does, main
  methods), then open only what you need. Regenerate it with `python3 tools/codemap.py` after adding classes.
- Kingdoms lang is split into `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/*.json`, merged at build
  (there is no source `en_us.json`). Put a new feature's keys in its own part. A branch that still has the old
  single file: on the merge conflict keep its en_us.json, then run `python3 tools/lang_split.py` (moves its keys into parts).
- MODLOGs hold only recent entries; older ones are in `docs/modlog/`. Append new entries at the end as before.
- Check every Minecraft API against the 26.3 sources (`./gradlew genSources`) before using it.
- Keep each power in its own package and `lang/parts/<power>.json`; list every class that holds an attachment in
  `JaymosPowers.registerAttachments()`.
- Bump the version on every release. Never commit `Serverfiles/` (it holds a secret).
- Build to check work; don't launch long dev client/server runs unless the user asks.
- `docs/Plan-0.8.md` (built and merged) is the example of splitting a patch into tracks (4 tracks: Gravity, Arsenal, Chrono, Web; open questions at the end).
- Default keys: R/G/V/B = abilities 1-4, C = ultimate, Z = mode, H = menu, ' = costume mask. "G+B" means G, then B (e.g. Crush
  released inside your own Gravity Well).
- Tuning numbers live in `static final` constants at the top of each class; change balance there.
- Optional mods (Kitchen, WeaponMod) only through a compat class that checks they're loaded (`KitchenCompat`).
  Don't copy other mods' code or art unless their license allows it.
- The symbiote never feeds on anything living: kill, grab with tendrils, carry as a corpse, then feed. Every
  autonomous symbiote action affects the player's screen.
- Big patches run as parallel agent tracks, one per power, so they don't edit the same files; 2 at a time, since
  4 at once hit usage limits.
- Workflows: read-only scan stages use the `scanner` agent (Haiku 5.5); edit stages use the `editor` agent
  (Sonnet 5.5), each in its own worktree.
- Every finding gets an independent verifier agent before it's reported. A fix loop stops after two rounds
  with no progress.
- Gemini second opinion: `.mcp.json` runs omni-ai-mcp 4.8.2 via `tools/gemini-mcp.sh` (needs `GEMINI_API_KEY` as a plain
  environment variable; a network secret breaks it because Gemini rejects the Bearer header the proxy adds). Use only its
  text/code tools (`ask_gemini`, `gemini_code_review`, `gemini_challenge`, `gemini_analyze_codebase`) as an extra verifier;
  its answers are advice to check against the code. Media, agent and OpenRouter tools are denied in `.claude/settings.json`.
