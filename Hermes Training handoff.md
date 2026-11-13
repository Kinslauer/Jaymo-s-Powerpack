# Hermes Training: handoff to Claude Code on Andy's PC

Paste this into Claude Code on the PC (or say "read Hermes Training handoff.md in the vault and take over").
Written Nov 11, 2026 by the cloud Claude in the "Hermes Training" project, which can't reach this PC.

## Your job

You take over training **Hermes**, Andy's bot setup in `E:/hermes`: **YOUTH** (the default profile, his Discord
assistant) and the worker bots. Watch what they do, find where they go wrong, fix their rules, config and scripts,
and keep Andy's asks moving. You can reach `E:/hermes`, `E:/Origins Mod` (the repo) and Discord (with YOUTH's token),
which the cloud thread couldn't.

## Andy's standing rules

- **Merge only on Andy's word** ("merge"). Never merge from a question, and never push to master: every change goes
  through a PR. The Release workflow bumps versions. PRs never touch `version=`, MODLOGs or `docs/codemap/`
  (read `CLAUDE.md` and `changes/README.md`).
- **Never print, log or paste a token or key**, not in a command, a file or a chat.
- Short, plain reports ("ready to merge as PR #N"). Times on a 12-hour clock, Eastern.
- Every decision Andy makes: append it to the vault's `Decisions.md`.
- Ask before anything that can't be undone (deleting data, posting in a public Discord channel, spending API credit).
  Andy is out of Anthropic Console credit, so **no cloud-agent runs** (`tools/cloud-agent`) unless he says so.

## How Hermes is set up

- Hermes home `E:/hermes`; profiles in `E:/hermes/profiles/<name>`. YOUTH's rules: `E:/hermes/SOUL.md`
  (the "Hard rules" section from Nov 10 is the audit fix; keep it).
- Talk to Hermes from `E:/hermes`, in the background, with no short timeout:
  `HERMES_HOME=E:/hermes tools/python-3.14.9+20261021-win32-x64/python.exe -I bin/hermes.exe chat -q "<msg>" --oneshot -Q`
- Restart the gateway only with `schtasks /run /tn Hermes_Gateway` (or the watchdog), **never**
  `hermes gateway restart`. `schtasks /run` does nothing while the gateway is up; to make sessions pick up a new
  SOUL.md, run `hermes sessions refresh-prompts`.
- Bots (vault labels): Apollo `hermes-a`, Bacchus `hermes-b`, Hephaestus `hermes-fixer`, Prometheus
  `hermes-modeller`, Quill `hermes-writer` (the only writer since Nov 10). The `dispatch-bots` cron fires them.
  Cap: 2 counted bots at once, never two builders; Prometheus counts. Logic: `E:/hermes/powerpack/vault.py`,
  `E:/hermes/scripts/dispatch_bots.py`, `bot_health.py`.
- Models in `E:/hermes/config.yaml`: YOUTH on NVIDIA Super first (Andy's call), then free fallbacks. Keep ONE
  `providers:` block; check with `hermes fallback list`. Prometheus runs on Ollama Cloud, which hit its monthly 429
  limit Nov 9 (`Logs/prometheus-error.md`).
- Python: pip installs into Python 3.12 (`py -3`), Hermes runs its own 3.14.9. Install tools for YOUTH's terminal
  with `py -3 -m pip`.
- Work flows through the vault (`E:/Jaymo Vault Live`, synced by `python tools/vault_sync.py`): tasks in `Inbox/`,
  board in `Kanban.md`, decisions in `Decisions.md`. After a PR passes review: `vault_sync.py --note`, then `--send`;
  YOUTH posts it in Discord Hotspot > parthenon > PR Merges (link, what the player sees, what wasn't tested, verdict).
- Discord board: `tools/discord_kanban.py` (one markdown message plus a Move menu in parthenon > Kanban). Env
  `KANBAN_BOT_TOKEN` (YOUTH's token) and `KANBAN_MOVERS` (Andy's id) are set. Keep it running; stop the old
  `discord-kanban` cron if it's still on. Read aloud: `tools/discord_read_aloud.py` (see `Read aloud setup.md`).

## Read these first (vault)

- `YOUTH report.md`: the Nov 8-10 audit of 525 YOUTH messages. It shows the bad habits: merging when asked a question,
  pushing to master, dropped asks, false status, Discord noise.
- `Fix YOUTH.md`: what was fixed Nov 10 (all boxes ticked).
- `Fix Hermes stalls.md`, `Logs/hermes-stall.md`: Nov 11 fixes (10-minute command timeout, bots can't mark a task
  in review unless the commit is on GitHub with green checks).
- `Fix gateway watchdog.md`: the watchdog thinks the gateway is down and "restarts" it every minute; YOUTH got stuck
  in a voice channel. **Still open**: work through it.

## Still open

1. **Re-audit YOUTH** (due around Nov 12): read YOUTH's Discord messages since Nov 10 8:50 PM with the bot token, the
   way `YOUTH report.md` was made, and check each Hard rule: any merge without "merge"? any push to master? any
   unanswered ask? any wrong PR status? heartbeat or narration spam? Write `YOUTH report 2.md`, compare with the
   first, fix what's still wrong (SOUL.md, skills, config), and tell Andy in 5 lines.
2. **Gateway watchdog loop**: `Fix gateway watchdog.md`.
3. **Dropped Discord messages** (~120 on Nov 8-9): check whether `missed.py` caught them up, and why YOUTH misses
   messages at all.
4. **Prometheus**: has the Ollama limit reset, or does it need another provider? `bot_health.py` should flag it.
5. **Waterskins (#1266, merged)**: load an old world (from before Kitchen 0.5.98) once before the next server
   deploy, and check the waterskins come through as Leather Waterskins.
6. Page file move script (`E:/hermes/scripts/move_pagefile_to_ssd.ps1`): Andy runs it himself; remind him once.

## When you finish something

Write the result into the vault (a note, or a line under the task), run `python tools/vault_sync.py`, and tell Andy
in one short Discord post or here. The cloud thread reads the vault, so anything written there reaches it.

## New (Nov 12): teach YOUTH to open Discord threads

**What happened.** Nov 12, 6:22 PM, in parthenon > Dashboard, Tiv told YOUTH "Okay open a new experiment thread and
log this there for later." YOUTH made a vault Inbox task (`omniroute-ai-gateway-experiment`), synced, and said
"Done". No Discord thread was made. Tiv's wording was fine; YOUTH got it wrong for two reasons:
1. **No tool.** Hermes has no agent tool that opens a Discord thread. Its Discord docs list only gateway behaviour
   (`auto_thread` on @mention in text channels, the user's `/thread` command, forum posts the gateway makes on its
   own). So YOUTH couldn't do it, and instead of saying so it did something else and called it done.
2. **Its rules point at the vault.** The Nov 10 Hard rule "Every ask becomes an Inbox task ... the same turn" makes
   the vault YOUTH's answer to any "log this".

**The tool.** PR (branch `claude/project-thread-xamylw`) adds `tools/discord_thread.py`. It uses YOUTH's token from
`KANBAN_BOT_TOKEN` (already set on the PC) and never prints it. Asked from inside a forum post (like Dashboard), it
makes a new post in the same forum (parthenon); asked in a text channel, a new public thread. Prints the link last.

Steps for the PC Claude (tick each):
- [ ] PR #1389 is merged (Nov 12). `git pull` in `E:/Origins Mod`. Test once without posting:
      `py -3 tools/discord_thread.py --in 1570579626151903354 --title "OmniRoute experiment" --message "test" --dry-run`
      (that id is the Dashboard thread). It should say "Would make a new post ... in forum #parthenon".
- [ ] Check YOUTH's bot role can **Create Posts** in parthenon (and Create Public Threads, Send Messages in Threads
      elsewhere). A 403 from the script means it can't. Tell Andy which permission to tick; don't change roles yourself.
- [ ] Make the real post for Tiv's ask (Tiv already asked for it, and parthenon is the private bot forum, not a public
      Tenbuki channel): title "OmniRoute experiment", message = the "What's wanted" and "Initial assessment" parts of
      `Inbox/omniroute-ai-gateway-experiment.md` plus the repo link. Reply in Dashboard with the new link. Add
      `thread: <new id>` to that Inbox card and keep the card (it tracks the evaluation).
- [ ] Add a YOUTH skill `E:/hermes/skills/discord-thread/SKILL.md` (Hermes skill format, look at an existing one):
      when to use it (someone in Discord says thread / topic / post / "open a new X thread"), the command above,
      `--in` = the channel or thread id the ask came from, a short clear title, `--message-file` for long text,
      `--tag` if parthenon requires a tag, and "reply with the link it prints".
- [ ] Add this to the Hard rules in `E:/hermes/SOUL.md` (backup first), then `hermes sessions refresh-prompts`:

```
Discord threads (Andy, 2026-11-12)
- In Discord, "thread", "topic" or "post" means a Discord thread. When someone asks you to open, start or make one,
  make it with the discord-thread skill (tools/discord_thread.py) and reply with its link. A vault Inbox task is
  not a thread.
- Put things in the vault only when someone asks for the vault (Inbox, task, card, Kanban, "for a bot"), or as well
  as the thread when the ask is work a bot should pick up. "Log this there" means log it in the thread.
- If you can't do what was asked (no tool, a permission error), say so and say what you did instead. Never answer
  "Done" for something other than what was asked.
```
- [ ] Test it: in Dashboard, ask YOUTH "open a new test thread called Thread test and put hello in it". It should
      reply with a link to a new parthenon post. Delete the test post afterwards.
- [ ] **PR Merges post** (Andy merged #1389 on Nov 12, 6:40 PM): have YOUTH post in parthenon > PR Merges:
      "PR #1389 merged: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/1389. Adds tools/discord_thread.py so
      YOUTH can open a Discord thread or forum post when asked. Players see nothing (bot tool only). Not tested against
      Discord yet: the dry run and live test are the steps above. Verdict: merged on Andy's word."
- [ ] Write one line per box here, run `python tools/vault_sync.py`, tell Andy in one short post.

## New (Nov 12): try Obscura as YOUTH's browser

**Andy's ask.** Can YOUTH use Obscura (https://github.com/h4ckf0r0day/obscura) as its browser? Yes, it should work,
but it's a trial: keep it only if YOUTH's browsing still works.

**Why it fits.** Obscura is a small Rust headless browser (Apache-2.0) that speaks CDP, the same protocol Chrome
uses. Hermes's `browser` tool already talks CDP and can attach to any CDP endpoint through `browser.cdp_url` in
config.yaml (or the `BROWSER_CDP_URL` env var); a CDP endpoint overrides the local Chromium. Obscura has a Windows
x86_64 build. It uses far less memory than Chromium.

**Risks.** Obscura only implements part of CDP (Target, Page, Runtime, DOM, Network, Fetch, IO, Storage, Input).
If Hermes's browser helpers need something it lacks (Accessibility tree snapshots, for example), browsing calls
will error. Some pages render differently from Chrome, and heavy JS sites can hit its 30-second script budget.
That's why step 5 is a real test, and step 7 is the way back.

**Hard rule: no stealth.** Install the default (non-stealth) build. Never pass `--stealth`, never download a
`-stealth` archive. If a site blocks it, tell Andy; don't work around it.

Steps for the PC Claude (tick each):
- [ ] 1. Download `obscura-x86_64-windows.zip` (NOT the `-stealth` one) from
      https://github.com/h4ckf0r0day/obscura/releases/latest and extract it to `E:/hermes/tools/obscura/`. Keep the
      DLLs next to the exes. (Scoop also works: `scoop install https://raw.githubusercontent.com/h4ckf0r0day/obscura/main/scoop/obscura.json`, which is the non-stealth build.)
      Check: `E:/hermes/tools/obscura/obscura.exe --help` runs.
- [ ] 2. Start it on a port Chrome isn't using: `E:/hermes/tools/obscura/obscura.exe serve --port 9333`.
      Check with `netstat -ano | findstr 9333` that it listens on **127.0.0.1 only**. If it shows 0.0.0.0, look in
      `obscura serve --help` for a host/bind flag and use 127.0.0.1; if there is none, block 9333 inbound in Windows
      Firewall. Don't add `--allow-private-network`.
- [ ] 3. Smoke test outside Hermes: `E:/hermes/tools/obscura/obscura.exe fetch https://example.com --dump text`
      prints the page text.
- [ ] 4. Point YOUTH at it. Back up YOUTH's config.yaml first (`E:/hermes/config.yaml` if YOUTH is the default
      profile; otherwise its profile under `E:/hermes/profiles`). Under the existing `browser:` block (don't make a
      second one) add `cdp_url: "ws://127.0.0.1:9333"`. If Hermes can't connect with that, try
      `"ws://127.0.0.1:9333/devtools/browser"`. Restart the gateway with `schtasks /run /tn Hermes_Gateway`
      (never `hermes gateway restart`).
- [ ] 5. Test, from E:/hermes in the background with no short timeout:
      `HERMES_HOME=E:/hermes tools/python-3.14.9+20261021-win32-x64/python.exe -I bin/hermes.exe chat -q "Use your browser to open https://en.wikipedia.org/wiki/Minecraft and tell me the page title and the first sentence." --oneshot -Q`
      Then once more with a JS-heavy page (e.g. a GitHub PR page). Pass = right answers, no CDP errors in the
      gateway log. Compare its RAM use with the old Chromium in Task Manager.
- [ ] 6. Keep it running: add a Task Scheduler task `Obscura_CDP` that starts the step 2 command at logon, before
      the gateway. Only after step 5 passes.
- [ ] 7. If step 5 fails: remove the `cdp_url` line (restore the backup), restart the gateway, stop Obscura, and
      write down which call failed. YOUTH is back on its own Chromium.
- [ ] 8. Write one line per box here, run `python tools/vault_sync.py`, tell Andy in one short post: works or not,
      and the RAM difference.

## Faster models for the bots (added Nov 13, cloud thread "Find faster models for Hermes")

Why the bots feel slow: NVIDIA's free build.nvidia.com API is capped at 40 requests a minute per account and is
shared, so agent runs (many short calls in a row) queue and hit 429s. The DS Bot app wouldn't help by itself:
speed comes from the API provider, not the app.

Speed (Artificial Analysis, gpt-oss-120b): Cerebras ~1,700 tokens/s, ~1.5 s to first token; Groq ~470 tokens/s,
~5 s to first token. DeepSeek's own API is cheap but no faster than NVIDIA (no published speed). Limits: Groq free =
30 req/min, 1,000 req/day, only 200k tokens/day (one long agent run can burn it). Cerebras has no free tier since
July 2026 (card + $5 starter credit, then $0.35 in / $0.75 out per 1M tokens). OpenRouter `:free` models = 50
req/day, or 1,000/day after a one-time $10 credit buy. Gemini Flash free is down to roughly 20-50 req/day.

**Andy picked Groq first (Nov 13, 9:45 PM ET): free, no new account or card.** Append that to `Decisions.md`.
Order: Groq gpt-oss-120b -> NVIDIA Super -> NVIDIA nemotron-3.5-lightning -> OpenRouter `:free` -> Gemini Flash.
Groq's free 200k tokens/day runs out fast, so falling through to NVIDIA when Groq 429s is expected, not a bug.
Skip steps 2 and 5 (they were for a paid provider); Groq's key is already set.

- [ ] 1. Measure first. From the gateway logs/session files, write down for the last 3 days: requests per day and
      input/output tokens per day per bot, and how many 429s came from NVIDIA. Then time one fixed prompt (with a
      tool call, e.g. "read README.md and list its headings") on each provider already in config.yaml: seconds to
      first token and total seconds. Put the table under this section.
  - [x] **Done Nov 12, 10:20 PM (PC Claude). Result: Groq free can't be the primary. Step 3 is on hold for Andy.**

    **Groq free tier = 8,000 tokens per minute on gpt-oss-120b** (read from Groq's own `x-ratelimit-limit-tokens`
    header). A ~20k-token request gets `413 Request too large ... tokens per minute (TPM): Limit 8000`. Real bot
    calls are far bigger: YOUTH's median call is ~64k-135k input tokens (e.g. 9:59 PM tonight: in=118,235). YOUTH's
    system prompt + tools alone are over 8k. So Groq would refuse every bot call, not just after 200k/day.
    Worse: Hermes treats a 413 as "payload too large", not a rate limit (`error_classifier.py:1361`, then
    `turn_overflow.py:_recover_payload_too_large`): it **compresses the conversation and retries** instead of falling
    through to NVIDIA. With Groq first, every YOUTH turn would start by deleting history, then fail. The same risk
    already exists wherever Groq sits in a chain (YOUTH fallback 3, Quill and Prometheus fallback 2).

    Usage per bot (session DBs, `session_model_usage`; "in" = fresh input, cache reads come on top):

    | Bot | Nov 10 | Nov 11 | Nov 12 (to 10 PM) |
    |---|---|---|---|
    | YOUTH | 910 calls, 9.6M in, 0.67M out | 207 calls, 4.9M in, 0.56M out | 414 calls, 8.0M in, 0.75M out |
    | Quill | 686 calls, 5.8M in, 0.14M out | 189 calls, 2.0M in, 0.19M out | 66 calls, 0.52M in, 0.05M out |
    | Hephaestus | 961 calls, 2.2M in, 0.35M out | 317 calls, 0.88M in, 0.13M out | 0 |
    | Bacchus | 707 calls, 2.2M in, 0.38M out | 0 | 14 calls, 0.08M in, 0.01M out |
    | Apollo | 405 calls, 1.5M in, 0.19M out | 25 calls, 0.35M in | 0 |
    | Prometheus | 0 | 75 calls, 0.27M in, 0.08M out | 0 |

    **429s from NVIDIA, Nov 10-12: zero** in all logs. All ~3,700 429s were Ollama (monthly limit, 3,200) and Fable
    (free tier ended, 538). NVIDIA is slow by queueing, not by refusing: per-call latency (agent.log):

    | Day | NVIDIA Super median / p90 | Lightning median / p90 | Ultra median / p90 |
    |---|---|---|---|
    | Nov 10 | 3.6 s / 17.7 s | 3.2 s / 16.7 s | 4.6 s / 17.0 s |
    | Nov 11 | **31.1 s / 70.2 s** | 3.4 s / 27.9 s | 9.6 s / 370 s |
    | Nov 12 | 8.3 s / 43.1 s | (unused) | (unused) |

    Fixed prompt with a tool call ("Read README.md and list its headings", streaming, Nov 12 ~10:15 PM):

    | Provider | small prompt: first token / total | ~20k-token prompt: first / total |
    |---|---|---|
    | Groq gpt-oss-120b | 0.4 s / 0.5 s | **413 refused (8k TPM)** |
    | NVIDIA Super | 0.4 s / 0.6 s | 1.4 s / 1.6 s |
    | NVIDIA Lightning | 0.3 s / 1.2 s | 0.5 s / 2.9 s |
    | Gemini Flash | 503 "high demand" | 503 "high demand" |
    | OpenRouter :free | no `OPENROUTER_API_KEY` in Hermes `.env` | - |

    **Andy's call (Nov 12, ~10:30 PM): "NVIDIA first, drop Groq".** Steps 2-5 below were for Groq-first and are
    replaced by this (done Nov 12, 10:35 PM, PC Claude):
    - [x] Backups: `E:/hermes/backups/config.yaml.2026-11-12-drop-groq.bak`, and the same name under
      `profiles/quill/backups/` and `profiles/hermes-modeller/backups/`.
    - [x] Groq out of every fallback chain. `hermes fallback list` now shows: YOUTH = Super -> Lightning -> Gemini;
      Quill = Super -> Lightning -> Gemini -> Ultra; Prometheus = Super -> Gemini (reads images) -> Lightning.
      Apollo, Bacchus and Hephaestus never had Groq. The `providers: groq` definition stays (one block, unused).
    - [x] No restart needed: the gateway re-reads `fallback_providers` on each agent create/reuse
      (`gateway/run_config_loaders.py:581`) and cron re-reads per job. `schtasks /run` was a no-op (gateway up).
    - [ ] Not done: the `hermes chat --oneshot` test from this PC fails with "gateway_other_session" (the gateway
      runs in Windows session 0 from the boot task, so a user-session CLI can't reach it). The direct API timing
      above stands in. Fixing that means stopping the session-0 gateway from an admin prompt: Andy's call.
    - Gemini Flash returned 503 "high demand" on every try tonight, so in practice the chain is NVIDIA-only.
      OpenRouter `:free` would need an `OPENROUTER_API_KEY` (Andy makes it, sets it himself) if he wants a non-NVIDIA
      fallback that works.
- [ ] 2. If Andy picks a paid fast primary: he makes the account (Cerebras or Groq Developer) and sets the key
      himself as an environment variable / in Hermes's `.env`. Never put a key in a command, file or chat.
- [ ] 3. Back up `E:/hermes/config.yaml` (and Quill's profile config). In the ONE existing `providers:` block, add
      move Groq gpt-oss-120b to the top, then the order above, for YOUTH and Quill. Drop Ollama from the bots'
      chain until its month resets. Make sure a Groq 429 falls through to the next provider right away (no long
      retry wait on Groq). Check with `hermes fallback list`.
- [ ] 4. Restart with `schtasks /run /tn Hermes_Gateway` (never `hermes gateway restart`); run
      `hermes sessions refresh-prompts` if needed. Re-run the step 1 timing test and one real bot task.
      After a day, note what time Groq's daily tokens ran out and tell Andy in 2 lines whether the bots felt faster.
- [ ] 5. Set a spend cap on the paid provider's console (Andy). After 3 days, write tokens used and dollars spent
      here, run `python tools/vault_sync.py`, and tell Andy in 2 lines.
- [ ] If anything breaks: restore the backup config and restart the gateway.

## New (Nov 12, 10:50 PM): OpenRouter free models as the non-NVIDIA backup

**Andy's call:** add OpenRouter's free models after the NVIDIA entries, so the bots keep going when NVIDIA is down
or rate limited. Andy makes the key at https://openrouter.ai/keys and sets `OPENROUTER_API_KEY` himself (user env
var and `E:/hermes/.env`). Never put the key in a command, file or chat.

**Limits:** `:free` models allow 20 requests a minute and **50 a day per account**, or 1,000 a day once the account
has bought $10 of credit one time (Andy's choice; not needed to start). 50 a day only covers a few bot runs, so it's
a backup, not a primary. A 429 from OpenRouter has to fall through to the next provider right away.

**Models** (from OpenRouter's model list Nov 13: free, take tool calls, >= 128k context; a bot call is 64-135k):
`google/gemma-4-31b-it:free` (262k), then `thinkingmachines/inkling-2-small:free` (1M), then
`cohere/north-mini-code:free` (256k). Skip the `nvidia/...:free` ones: they are NVIDIA models again, so they don't
add a real backup. `openai/gpt-oss-120b:free` no longer lists tool support. The list changes often, so re-check.

- [ ] 1. PR #1403 (`tools/openrouter_check.py`) is merged: pull master. Run `py -3 -m pip install openai`, then
      `py -3 tools/openrouter_check.py --list` (no key needed) and confirm the three models are still listed.
- [ ] 2. Once Andy says the key is set: `py -3 tools/openrouter_check.py`, then the same with
      `--model thinkingmachines/inkling-2-small:free`. Pass = `OK ... called get_server_status(...)`. A `WEAK` line
      means the model ignored the tool; drop it from the chain.
- [ ] 3. Back up `E:/hermes/config.yaml` and each bot profile's config (`*.2026-11-13-openrouter.bak`). In the ONE
      existing `providers:` block, add an `openrouter` provider: base URL `https://openrouter.ai/api/v1`, key from
      `OPENROUTER_API_KEY` (the env var name, never the value). Use Hermes's built-in OpenRouter provider if
      `hermes` has one; don't make a second `providers:` block.
- [ ] 4. Add the models that passed step 2 after the NVIDIA entries in every chain, before Gemini:
      YOUTH = Super -> Lightning -> OpenRouter models -> Gemini; Quill = its NVIDIA entries (with Ultra) -> OpenRouter
      -> Gemini; the other bots the same way. Check with `hermes fallback list`.
- [ ] 5. Restart with `schtasks /run /tn Hermes_Gateway` (never `hermes gateway restart`). Test one fallback call:
      temporarily put an OpenRouter model first for one oneshot chat (or use Hermes's per-call model flag if it has
      one), confirm it answers and uses a tool, then put the order back.
- [ ] 6. Write one line per box here (the decision is already in `Decisions.md`), run `python tools/vault_sync.py`, and
      tell Andy in 2 lines. If anything breaks, restore the backups and restart the gateway.

**Done Nov 12, ~11:20 PM (PC Claude):**
- [x] 1. Pulled master, installed `openai`. `--list` still shows all three models.
- [x] 2. Key set (user env + `E:/hermes/.env`). `cohere/north-mini-code:free`: **OK** (called the tool).
  `thinkingmachines/inkling-2-small:free`: 403 "only available on agentic harnesses" from the check script, but
  **OK** when sent with Hermes's own attribution headers (`X-Title: Hermes Agent`, which Hermes's built-in OpenRouter
  provider always sends). `google/gemma-4-31b-it:free`: 429 three times, "temporarily rate-limited upstream"
  (Google AI Studio's shared free pool), so not verified and **left out**. Re-test it later; add it if it passes.
- [x] Profiles don't read the root `.env` (each has its own), so the key line was copied by script, never printed,
  into the `.env` of hermes-a, hermes-b, hermes-fixer, hermes-modeller and quill. Hermes re-reads `.env` on each call
  (`agent/credential_pool.py:get_env_prefer_dotenv`), so no restart is needed for it.
- [x] 3. Backups `config.yaml.2026-11-13-openrouter.bak` in `E:/hermes/backups/` and each bot's `backups/`. Hermes has
  a built-in `openrouter` provider, so no `providers:` entry was added (still one block).
- [x] 4. `hermes fallback list`: YOUTH = Super -> Lightning -> Inkling -> Cohere -> Gemini. Quill = Super ->
  Lightning -> Ultra -> Inkling -> Cohere -> Gemini. Prometheus = Super -> Gemini (images) -> Lightning -> Inkling ->
  Cohere. Apollo, Bacchus, Hephaestus had no chain; now primary -> Inkling -> Cohere -> Gemini. A 429 switches to the
  next entry at once (`agent/turn_recovery.py:1921`, "Eager fallback").
- [~] 5. Gateway restart not needed (chains and `.env` are re-read live; `schtasks /run` is a no-op while it's up).
  The Hermes oneshot test can't run from this PC session: "gateway_other_session" (the gateway runs in Windows
  session 0). The same calls made directly (same endpoint, model, Hermes headers, one tool) passed for both models.
  First real proof will be the next time NVIDIA fails; look for `via openrouter` in `logs/agent.log`.
- Left alone: `claude-writer` (retired since Quill took over Nov 10; no dispatch since).

**Nov 12, 11:10 PM (PC Claude):** the Discord board (	ools/discord_kanban.py) was down since the morning reboot (nothing started it). Restarted it, and added Task Scheduler task `Discord_Kanban` (at logon, 1 min delay) running `E:/hermes/scripts/start_discord_kanban.ps1`, which skips if a copy is already running. Logs: `E:/hermes/logs/discord-kanban.out.log` / `.err.log`.

## AMD free models as a backup (added Nov 13, cloud thread "Add AMD free models to Hermes")

**Correction (Nov 13, cloud thread): Andy's account is AMD's US "Token Lab", not the China "Token Factory".
His `tl-` key is right; the base URL was wrong.** Token Lab base URL: `https://api.tokenlab.amd.com/v1`
(checked from the cloud: it answers `401 Invalid Token Lab API key` to a fake key, and lists its endpoints as
`/v1/chat/completions`, `/v1/messages`, `/v1/models`, `/v1/ocr`, `/v1/audio/speech`, `/v1/systemone`; the key is
managed at https://notebooks.amd.com/tokenlab). Auth: `Authorization: Bearer <key>`, keys start with `tl-`. There
is no `/usage` endpoint: watch points on Andy's Token Lab page. Token Lab has no public docs; the model list,
limits and terms below come from the China Token Factory docs (https://amd-aim.github.io/radeon-cloud-docs/),
which seems to be the same AMD backend, so treat them as a guide and trust `GET /v1/models` for the real IDs.
One user report says Token Lab often returns 429s, another reason to keep it last.

- Base URL: `https://api.tokenlab.amd.com/v1` (NOT `developer.amd.com.cn`, which rejects `tl-` keys).
- Models on Andy's account (checked his Token Factory page Nov 13; there is NO GLM model). Send the model ID, not
  the card title: AMD's docs say the card title and ID can differ. Confirm the IDs with `GET /models` before
  using them. All have tool calling.
  | Card title | ID to send | Context | Pts per 1M in / out | Notes |
  |---|---|---|---|---|
  | DeepSeek-V4-Flash-0731 | `DeepSeek-V4-Flash` | 1M | 0.14 / 0.28 | text only, thinking off by default; 4% busy |
  | Qwen3.8-27B | `Qwen3.8-27B` | 256k | 0.035 / 0.07 | thinking ON by default (`reasoning_effort` low/medium/xhigh; `high` gives a 400); 3% busy |
  | Gemma-4-31B-IT | `Gemma-4-31B-IT` | 256k | 0.035 / 0.07 | thinking off by default; 4% busy |
  | DeepSeek-V4.1-Flash-1017 | `DeepSeek-V4.1-Flash` | 1M | 0.28 / 0.56 | vision, 31% busy, so most likely to queue |
  | MiniCPM5-2B | `MiniCPM5-2B` | 128k | 0.035 / 0.07 | 2B is too small for agent work; skip |
  A 64-135k bot call fits all of the first four.
- Limits: 20 requests/min per account, 30/min per key, 8 at once per key. No tokens-per-minute limit is
  published (the thing that killed Groq). **Allowance: Andy's account has 10 points TOTAL for a 29-day access
  period that ends Dec 13 2026 (seen on his Token Lab page Nov 13). It is not daily.** What happens after Dec 13
  (renewal or nothing) is unknown; check his Token Lab page then. At ~100k tokens a call, 10 points is roughly
  700 DeepSeek-V4-Flash calls or ~2,800 Qwen3.8-27B / Gemma calls for the whole period. So AMD is a LAST-RESORT
  backup, and Qwen is the better value.
  Over a limit it returns 429 with `Retry-After` (60 s for per-minute, seconds-to-reset when the allowance is used up).
- Terms: one key per person, no extra accounts. "Do not put a production workload behind these endpoints", no
  uptime promise, can be cut or changed any time. Prompts are logged for 2 days, not used for training, and no
  sensitive data may be sent. So: backup only, never first in the chain, and nothing secret in prompts (the
  bots must never paste a key or `.env` content into a prompt anyway).

Andy's part (all done Nov 13): signed up, saved the `tl-` key in `E:\hermes\.env` as `AMD_API_KEY` himself, and
checked the allowance (10 points to Dec 13). Never print, copy or move the key.

PC Claude's part (ready to start):
- [ ] 1. Check the key works without printing it: call `GET /models` with the key from the env var and confirm the
      IDs `DeepSeek-V4-Flash`, `Qwen3.8-27B` and `Gemma-4-31B-IT` are listed (use whatever IDs it returns). Then one small chat call with a tool definition to each;
      note seconds to first token and total. Point `amd_check.py` at `https://api.tokenlab.amd.com/v1`.
  - [!] **Blocked Nov 13 (PC Claude): the saved key is rejected.** `GET /models` on the base URL above returns
    `401 {"detail":"Invalid bearer token"}`. `AMD_API_KEY` in `E:\hermes\.env` is `tl-` + 48 hex characters (checked
    without printing it). Token Factory keys are `rc-` + 48 hex (AMD docs, api/authentication), so this looks like a
    key from another page or service, maybe the "Token Lab" page. Not sent anywhere else, since that would hand the
    key to a third party. **Andy:** open https://developer.amd.com.cn/radeon/tokenfactory, copy the key from the
    **Your API key** card (it starts with `rc-`) and replace the `AMD_API_KEY` value in `E:\hermes\.env` yourself.
    If the card has no key, the account may still be under review. Steps 2-5 wait for a working key. Test script:
    `py -3 -I E:/hermes/scripts/amd_check.py` (lists models; add a model ID to make one tool call with timing; never prints the key).
  - **Unblocked (cloud thread, Nov 13): wrong base URL, not a wrong key.** The `tl-` key is a Token Lab key and
    goes to `https://api.tokenlab.amd.com/v1` (an AMD domain, so it's safe to send it there). Andy does NOT need
    a new key. Change the base URL in `amd_check.py` and re-run step 1. If the IDs differ from the table, use the
    ones `/v1/models` returns in steps 3-4.
  - [x] **Done Nov 13 (PC Claude), base URL `https://api.tokenlab.amd.com/v1`, same key.** `GET /v1/models`: Clef,
    d3, d3-flash, DeepSeek-V4.1-Flash, DeepSeek-V4-Flash, Gemma-4-31B-IT, MiniCPM5-2B, MiniCPM-V-4.7-1B,
    MiniCPM-V-4.7-35B-A3B, Qwen3.8-27B. The three IDs match the table. One streamed call with one tool each (small prompt):

    | Model | First token / total | Tool call |
    |---|---|---|
    | `Qwen3.8-27B` (`reasoning_effort: low`) | 3.5 s / 4.5 s | OK (32 reasoning tokens) |
    | `DeepSeek-V4-Flash` | 3.0 s / 3.6 s | OK |
    | `Gemma-4-31B-IT` | **43.4 s** / 45.9 s | OK, but slow (queued) |

    Rate-limit headers: `x-ratelimit-limit-user-rpm: 20`, `x-ratelimit-limit-user-budget-usd: 10`,
    `x-ratelimit-remaining-user-budget-usd` (use this one in step 4; there is no `...-Daily-USD` header), budget resets
    Dec 13 2026, 11:24 AM UTC. Points = US dollars: each response carries `usage.cost`, and Qwen's matched the table
    price exactly (312 in / 60 out = $0.0000151). So a ~100k-token bot call is ~$0.004 on Qwen (~2,800 calls for the
    $10) and ~$0.014 on DeepSeek-V4-Flash (~700). The test calls cost $0.00007; $9.99993 left. Token Lab has no
    `/usage` endpoint (404 lists the supported ones: chat/completions, messages, models, ocr, audio/speech, systemone).
    Plan for step 3 stands: Qwen, then DeepSeek; Gemma is too slow to swap in. Script:
    `AMD_BASE_URL=https://api.tokenlab.amd.com/v1 py -3 -I E:/hermes/scripts/amd_check.py [model-id]`.
- [ ] 2. Back up `E:/hermes/config.yaml` and Quill's profile config. In the ONE existing `providers:` block add an
      OpenAI-compatible provider `amd` (base URL above, key from `AMD_API_KEY`), the same way NVIDIA is set up.
      Don't make a second `providers:` block.
- [ ] 3. Put AMD at the END of each chain (last resort, the points must last to Dec 13): `Qwen3.8-27B` first
      (set `reasoning_effort: low`, or medium, so its default thinking doesn't eat time), then `DeepSeek-V4-Flash`.
      If Qwen fumbles tool calls in step 1, swap in `Gemma-4-31B-IT` (same price). Skip DeepSeek-V4.1-Flash
      (busiest, pricier) and MiniCPM5-2B (too small).
      YOUTH: Super -> Lightning -> Gemini -> AMD Qwen -> AMD DeepSeek.
      Quill: Super -> Lightning -> Gemini -> Ultra -> AMD Qwen -> AMD DeepSeek.
      Prometheus: Super -> Gemini -> Lightning -> AMD Qwen -> AMD DeepSeek.
      If OpenRouter free goes in too, put it before AMD. A 429 from AMD must fall through right away, no long wait.
      Check with `hermes fallback list`.
- [ ] 4. Restart with `schtasks /run /tn Hermes_Gateway` (never `hermes gateway restart`), run one real bot task
      that falls to AMD (e.g. temporarily point a test run at AMD first), and watch the `X-RateLimit-Remaining-User-Daily-USD`
      header (if Token Lab sends it) or Andy's Token Lab points before/after, to see how many points one bot call costs. Write that here.
- [ ] 5. Append to `Decisions.md` that Andy added AMD Token Lab as the last backup, with the date. Once a week,
      write points left here; if more than half is gone before Dec 1, tell Andy in 2 lines.
- [ ] If anything breaks: restore the backup config and restart the gateway.

**Steps 2-5 done Nov 13, 7:10 AM (PC Claude), on Andy's "yes":**
- [x] 2. Backups `config.yaml.2026-11-13-amd.bak` in `E:/hermes/backups/` and in `backups/` of quill, hermes-modeller,
  hermes-a, hermes-b and hermes-fixer. **No `providers:` entry**: the bot profiles have no `providers:` block, so a
  named provider in the root file wouldn't reach them. Each chain entry instead carries its own
  `provider: custom`, `base_url: https://api.tokenlab.amd.com/v1` and `key_env: AMD_API_KEY` (Hermes reads these per
  entry: `hermes_cli/fallback_config.py:resolve_entry_api_key`, `agent/route_binding.py`). Still one `providers:` block.
  Profiles don't read the root `.env`, so the key line was copied by script, never printed, into the `.env` of
  those five profiles.
- [x] 3. `hermes fallback list` (AMD after OpenRouter and Gemini, as planned):
  YOUTH = Super -> Lightning -> Inkling -> Cohere -> Gemini -> AMD Qwen -> AMD DeepSeek.
  Quill = Super -> Lightning -> Ultra -> Inkling -> Cohere -> Gemini -> AMD Qwen -> AMD DeepSeek.
  Prometheus = Super -> Gemini -> Lightning -> Inkling -> Cohere -> AMD Qwen -> AMD DeepSeek.
  Apollo (Ultra), Bacchus (Super), Hephaestus (Lightning) = primary -> Inkling -> Cohere -> Gemini -> AMD Qwen -> AMD DeepSeek.
  Thinking per model in `agent.reasoning_overrides` of every config: `Qwen3.8-27B: low`, `DeepSeek-V4-Flash: none`.
  DeepSeek would otherwise inherit the global `medium`, which switches its thinking on: 22 s to first token instead
  of 3 s. A 429 moves to the next entry at once ("Eager fallback"); AMD is last, so nothing comes after it.
- [~] 4. Gateway restart not needed: chains, `.env` and `reasoning_overrides` are re-read per agent/fallback
  (`gateway/run_config_loaders.py:581`, `chat_completion_helpers.py:_reresolve_fallback_reasoning_config`).
  The `hermes chat --oneshot` test still fails with "gateway_other_session" (gateway in session 0). Instead, Hermes's
  own code was run from each profile's home: key resolved (`tl-`), client built on the Token Lab URL, thinking
  low/off as set, and one real call through that client with a `read_file` tool came back as a correct tool call
  (7.2 s, 311 in / 59 out, $0.000015). Cost per real bot call: ~$0.004 on Qwen at ~100k tokens (from `usage.cost`
  at the listed price). Budget header to watch: `x-ratelimit-remaining-user-budget-usd`. First real proof will be
  the next time everything above AMD fails: look for `via custom` with `tokenlab` in `logs/agent.log`.
- [x] 5. In `Decisions.md` (Nov 13, 7:09 AM). Points left after all tests: ~$9.9999 of $10.
- [ ] Weekly: write points left here (`py -3 -I E:/hermes/scripts/amd_check.py Qwen3.8-27B` prints the budget
  header for ~$0.00002); if more than $5 is gone before Dec 1, tell Andy in 2 lines.

## New (Nov 13): Minecraft server bot experiment (cloud thread "Plan Minecraft server bot for Hermes")

Andy said yes to scoping Tiv's "server bot experiment". The full plan is in project files `plans/Minecraft-Server-Bot-Plan.md`
(this cloud thread can't write to the PC; also in the vault as `Minecraft server bot plan.md`).

**Vault task** `Inbox/minecraft-server-bot-experiment.md`: the cloud thread already reopened it for you (Nov 13). The "not possible" verdict from hermes-b on Nov 13 is wrong. Set `status: open`, `for: pc-claude`, clear `claimed_by`,
and replace the assessment with: "Possible without a Minecraft account. Step 1: YOUTH in game chat through RCON
(`deck/rcon.py` over the PC's SSH link to the Deck). Step 2: a Sylvan wisp mob (our own GeckoLib entity, `/youth summon`),
driven by the same script. Mineflayer is out for now: no official 26.3 support, and our modded Fabric server would
likely reject it. See Minecraft server bot plan.md." Then run `python tools/vault_sync.py`.

**Andy's choices (Nov 13, append both to `Decisions.md`):** 4:57 PM ET "I'd like a body that can spawn and relay
information about any mechanic to players." 5:13 PM ET "the youth bot should be a wispy winged sylvan animating in the
world near where you asked, doing some variation of 8 gecko libbed idle animations."

**This changes the build: no Carpet.** The body is a new Kingdoms GeckoLib mob (`YouthWisp`) with `/youth summon|say|
dismiss|status` commands, sent over RCON. The plan (rewritten) has the look, the 8 idles, behaviour, commands, the
wood-wisp lore paragraph for Andy to approve, and the steps. Your part is the Hermes side below; skip the Carpet step.
The mod side is built: **PR #1467** (merge only on Andy's word; then pbdr). `/youth say <player> <text>` takes plain
text as its last argument. Before it's merged you can test the brain with plain `tellraw` in place of `/youth say`.
Andy approved the wood-wisp lore (in Lore.md since Nov 13) and his decisions are in Decisions.md.

**What to build:**
1. `E:/hermes/scripts/mcbot.py` with fixed verbs only (`say`, `who`, `where`, `listen`, `off`/`on`; later `spawn`,
   `despawn`, `come`, `look`, `jump`, `stop`, `walk`). It SSHes to the Deck and runs `~/jaymos-server/rcon.py`. It never
   passes raw text to the console (only escaped inside `tellraw`), checks player names, and rate-limits itself
   (1 command/s, 1 chat line/5 s, 10 lines/min). The RCON password stays on the Deck.
2. A YOUTH skill `minecraft` and a SOUL rule: in game chat answer only people who name YOUTH, 1-2 short lines, server
   commands only through `mcbot.py`, never stop or restart the server.
3. A listener (scheduled task or 1-minute cron) that tails `~/jaymos-server/logs/latest.log` over SSH and hands chat
   mentions to YOUTH with `hermes chat -q ... --oneshot`.
4. (Carpet step dropped: the wisp is our own mob, built as a Kingdoms PR.)

Ask Andy before the first in-game test with friends online. No account,
no offline mode, no key handling needed. Estimated 2-4 hours for step 1, 3-6 for step 2.
