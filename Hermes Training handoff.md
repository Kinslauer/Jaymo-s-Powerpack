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

- [ ] 1. Check out PR #1403 (`tools/openrouter_check.py`) or wait for it to merge. Run `py -3 -m pip install openai`, then
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
- [ ] 6. Write one line per box here, append the decision to `Decisions.md`, run `python tools/vault_sync.py`, and
      tell Andy in 2 lines. If anything breaks, restore the backups and restart the gateway.
