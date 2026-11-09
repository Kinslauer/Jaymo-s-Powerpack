---
name: vault-review
description: Review the PRs that agents finished from the vault Inbox (status in-review), send failing ones back, and ask Andy before handing passing ones to the coordinator. Run it on a loop with /loop 15m /vault-review.
---
# Vault review

You are the reviewer in the vault pipeline (see `docs/vault/Pipeline.md`). Hermes picks up Inbox tasks and opens PRs,
you review them, and a PR that passes goes to the coordinator (the Claude project thread Andy reads) only after Andy
says yes. Never edit code, comment on GitHub, push or merge here.

All task changes go through `python tools/vault_sync.py` (it syncs before and after), never by editing the files.

1. `python tools/vault_sync.py --reviewer-seen` (tells the coordinator you're here; if you skip rounds for 30 minutes
   it reviews in-review tasks itself), then `python tools/vault_sync.py --review-queue` (in-review tasks the writer bot has already given a text
   pass; ones still waiting for it aren't listed). If it prints nothing, reply only "Nothing to review." and stop.
2. For each task, oldest first:
   1. `python tools/vault_sync.py --show <task>`: Andy's words, what's wanted, the `pr:` number, earlier review notes.
   2. `gh pr view <pr> --json number,title,body,headRefName,headRefOid,mergeable,statusCheckRollup` and
      `gh pr diff <pr>`. If a note already says `Review (head <first 7 of headRefOid>)`, this head was reviewed: skip it.
   3. Review it:
      - Does it do what Andy asked, all of it, the way he asked? Anything missing is a change request.
      - `CLAUDE.md` rules and the "Before you open a PR" checklist in `docs/AI-HANDBOOK.md`: a change note
        (`changes/<mod>/<branch>.md`) for each mod changed and no edits to `version=`, MODLOGs or `docs/codemap/`
        (send back an old-style PR with "run `py -3 tools/change_note.py convert`"), lang in its own `lang/parts/` file, tuning numbers as constants, optional mods only through compat
        classes, no copied art or code.
      - PR checks must be green (`statusCheckRollup`). Still running: skip the task until the next round.
      - Real bugs: read the code around each change (`git fetch origin <headRefName>`, then
        `git show origin/<headRefName>:<path>`) and check every Minecraft API against the 26.3 sources. Only report
        what you can point at a line for.
   4. Verdict:
      - **Changes needed:** `python tools/vault_sync.py --note <task> "Review (head abc1234): <numbered list, each
        with file:line and what to change>"`, then `python tools/vault_sync.py --set <task> status=changes`. Hermes
        picks it up again. If this is the third review and the same problems are still there, use `status=blocked`
        and say why in the note: the coordinator takes it to Andy.
      - **Passes:** `python tools/vault_sync.py --note <task> "Review (head abc1234): pass. <one or two sentences:
        what a player will see, what wasn't tested>"`, then `python tools/vault_sync.py --send <task>`. That is the
        step Andy approves. If he says no, leave the task and note "Andy held it". `--send` sets `status: ready`,
        which puts the card in the Kanban's **Needs Andy** lane (a Claude thread's own note in `Agents/` goes there
        too once its `status:` is `ready`). Then have YOUTH (Hermes's Discord bot) post the PR in Andy's Discord,
        forum **parthenon**, post **PR Merges**: the link, what a player will see, what wasn't tested, the verdict.
3. Finish with one line per task you looked at: task, PR, verdict.
