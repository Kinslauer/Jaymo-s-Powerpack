# Jaymo's Powers (and Kingdoms, Kitchen)

Minecraft 26.3 Fabric mods for the user's friends' modpack. **Read `docs/HANDOFF.md` first**: what each folder is,
how to build, hard-won 26.3 API facts, the user's preferences, and what to do next.
- **Project vault** (`docs/vault/`, Obsidian): read `docs/vault/Home.md` first (versions, open PRs, what's next), then
  follow `docs/vault/How agents use this vault.md`. Live notes go in the live folder `E:/Jaymo Vault Live/` (outside git;
  it shows in the vault as `Live/`), never in your worktree: make your agent note in `E:/Jaymo Vault Live/Agents/` from
  `Templates/Agent.md` (branch, PR, files you
  own) when you start, update it when you finish, never edit a file another running agent owns, and append each of the
  user's decisions to `E:/Jaymo Vault Live/Decisions.md`. The live folder syncs with the `vault-live` branch
  (`tools/vault_sync.py`, run by hooks); the coordinator posts tasks in its `Inbox/`. Cloud sessions:
  `python3 tools/vault_sync.py --setup` gets it in `docs/vault/Live/`.

- Root: Jaymo's Powers (`./gradlew build`). `kingdoms/`: Jaymo's Kingdoms. `kitchen/`: Jaymo's Kitchen. Each is its
  own Gradle project (JDK 25).
- Finding code: grep `docs/codemap/<powers|kingdoms|kitchen>.md` first (one line per class: path, what it does, main
  methods), then open only what you need. Don't commit it: the Release workflow regenerates it after each merge
  (run `python3 tools/codemap.py` locally if you want your new classes in it, then `git checkout origin/master -- docs/codemap`).
- Kingdoms lang is split into `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/*.json`, merged at build
  (there is no source `en_us.json`). Put a new feature's keys in its own part. A branch that still has the old
  single file: on the merge conflict keep its en_us.json, then run `python3 tools/lang_split.py` (moves its keys into parts).
- **PRs never touch `version=`, the MODLOGs or `docs/codemap/`.** Each PR adds a change note per mod it changes,
  `changes/<powers|kingdoms|kitchen>/<branch-name>.md`: a `# <title>` line, then the text a MODLOG entry has (no
  version). After the merge the Release workflow (`tools/release.py`) bumps the version, appends the MODLOG entry and
  deletes the note. `changes/README.md` explains; `tools/pr_checks.py` enforces it. MODLOGs hold only recent entries;
  older ones are in `docs/modlog/`.
- Check every Minecraft API against the 26.3 sources (`./gradlew genSources`) before using it.
- Keep each power in its own package and `lang/parts/<power>.json`; list every class that holds an attachment in
  `JaymosPowers.registerAttachments()`.
- Every mod change ships a change note (the version is bumped for you at merge). Don't write version numbers in code
  comments (`(0.21.62)`): say what the code does; `git blame` finds the PR. Leave the old ones as they are.
  Never commit `Serverfiles/` (it holds a secret).
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
