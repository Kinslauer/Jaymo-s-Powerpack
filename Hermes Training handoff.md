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
- [ ] After Andy merges the PR, `git pull` in `E:/Origins Mod`. Test once without posting:
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
- [ ] Write one line per box here, run `python tools/vault_sync.py`, tell Andy in one short post.
