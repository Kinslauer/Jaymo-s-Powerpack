# Jaymo's Powers (and Kingdoms, Kitchen)

Minecraft 26.3 Fabric mods for the user's friends' modpack. **Read `docs/HANDOFF.md` first**: what each folder is,
how to build, hard-won 26.3 API facts, the user's preferences, and what to do next.
- **Project vault** (`docs/vault/`, Obsidian): read `docs/vault/Home.md` first (versions, guides) and `Live/Dashboard.md`
  (what waits on Andy, what's stuck), then follow `docs/vault/How agents use this vault.md`. Live notes go in the live folder `E:/Jaymo Vault Live/` (outside git;
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
- **Rules for particular files** are in `.claude/rules/*.md`: each loads (in Claude Code) when you work on the files its
  `paths:` lists: Kingdoms lang parts, a power's package, magic and ki effects, the symbiote, GeckoLib mobs, Blockbench
  animation. Other assistants: read every rule whose `paths:` match the files you're about to edit.
- Words Andy uses for things (and what they're called in the code) are in `docs/vault/Context/Glossary.md`.
- **PRs never touch `version=`, the MODLOGs or `docs/codemap/`.** Each PR adds a change note per mod it changes,
  `changes/<powers|kingdoms|kitchen>/<branch-name>.md`: a `# <title>` line, then the text a MODLOG entry has (no
  version). After the merge the Release workflow (`tools/release.py`) bumps the version, appends the MODLOG entry and
  deletes the note. `changes/README.md` explains; `tools/pr_checks.py` enforces it. MODLOGs hold only recent entries;
  older ones are in `docs/modlog/`.
- **Lore first.** Everything new (features, mobs, items, structures, dialogue, art) must fit the lore canon, `Lore.md`
  in the live vault (`E:/Jaymo Vault Live/Lore.md`; cloud: `python3 tools/vault_sync.py --setup`, then `docs/vault/Live/Lore.md`).
  Read it before planning, place the new thing in it (who made it, where it lives, whose people or faith), and reshape
  anything that doesn't fit, or ask Andy. Add the placement to Lore.md as a `(new)` paragraph for him to approve.
- Check every Minecraft API against the 26.3 sources (`./gradlew genSources`) before using it.
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
- **Living things follow three rules** (Andy, 2026-11-12, from Klei_Wright's tundra; `docs/Tundra-Plan.md`). Every animal
  or plant we add or rework: **sympathy** (it does what the real one does, so knowing the game teaches the real thing),
  **feedback loops** (it changes its surroundings, and those changes come back on it: lichen eats its rock and runs out,
  grazers follow the food, a fed predator stops hunting), **stewardship** (a player who looks after it is rewarded;
  nobody is punished for leaving it alone). Not another mob with a new coat.
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

## Check, don't assume (every agent, every task)

From imisic/claude-obsidian-vault's verification rule (MIT), Andy's pick 2026-11-19. It goes with Andy's ruling
that no part of an ask is left out, cut down or swapped for something easier without saying so.

1. **Prove "done" with a check, not a feeling.** A build, a grep, a test, a lookup in the codemap or the MODLOG: a
   check can't be imagined, an impression can. Before you say a task is finished, go through the ask (the pick list,
   the task note, Andy's words) line by line against what you built.
2. **Never invent.** No class, method, file, number, mod, PR or ruling you haven't seen. If something is unknown, say
   it's unknown; an empty section is better than a made-up one.
3. **Never claim something is missing without searching.** "There's no such class", "nothing handles that", "Andy
   never asked for it" must come from a real search by every likely name (the codemap, grep, Decisions.md, the task's
   notes), not from memory or one try. Saying "missing" too early is the most common mistake, more than inventing.
4. When you couldn't check something (no game run, a file you can't reach), say what you didn't check.

## Coding guidelines (every agent, every task)

From Andrej Karpathy's notes on common coding-agent mistakes, via
[multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) (its `CLAUDE.md`). The project
rules above win where the two differ. For trivial tasks, use judgment.

1. **Think before coding.** State your assumptions. If a request can be read more than one way, say which reading you
   took (or ask) instead of picking silently. If something simpler exists, say so and push back. If something is
   unclear, stop, name what's confusing and ask.
2. **Simplicity first.** The minimum code that solves the problem: no features beyond what was asked, no abstractions
   for single-use code, no configurability nobody requested, no error handling for impossible cases. If 200 lines
   could be 50, rewrite it.
3. **Surgical changes.** Touch only what the task needs. Don't "improve", refactor or restyle nearby code, comments or
   formatting; match the existing style. Mention unrelated dead code instead of deleting it. Remove only the imports,
   variables and functions your own change made unused. Every changed line should trace back to the request.
4. **Goal-driven execution.** Turn the task into checkable goals ("fix the bug" -> a test or check that reproduces it,
   then passes) and verify against them before calling the work done. For multi-step work, state a short plan with a
   check for each step.
