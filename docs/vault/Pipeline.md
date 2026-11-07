# Pipeline: Inbox to Andy

How work flows without Andy watching each step. He's asked only once, at the end.

1. **Coordinator** (the Claude project thread in the Claude app) turns Andy's request into a task in `Live/Inbox/`
   (`status: open`).
2. **Hermes** checks the Inbox on a schedule, claims the oldest task, does it in its own worktree, opens a PR, then
   sets `status: in-review` and `pr:`.
3. **Claude Code on the PC** runs `/loop 10m /vault-review` (`.claude/skills/vault-review`) and reviews each
   `in-review` PR. A failing one goes back to Hermes (`status: changes`, with the review notes in the task). For a
   passing one it runs `vault_sync.py --send`, **the step that asks Andy**. Saying yes sets `status: ready`.
4. **Coordinator** checks the vault every hour, posts each `ready` PR to Andy with a link and the review, and sets
   `status: sent`. Andy says "merge" there. The coordinator merges, sets `status: done` and moves the task to
   `Inbox/Done/`.

`blocked` at any step means it needs Andy: the coordinator asks him.

## Statuses

`open` → `claimed` → `in-review` → (`changes` → `claimed` → `in-review`)… → `ready` → `sent` → `done`

## Commands (each syncs before and after)

```bash
python tools/vault_sync.py --claim hermes            # take a task (exit 3 = none waiting), prints it
python tools/vault_sync.py --tasks in-review         # list tasks by status
python tools/vault_sync.py --show <task>
python tools/vault_sync.py --set <task> status=in-review pr=1234
python tools/vault_sync.py --note <task> "text"
python tools/vault_sync.py --send <task>             # reviewer only, after Andy says yes
```

## Setting it up on the PC

**Claude Code (reviewer):** open a session in the repo and type `/loop 10m /vault-review`. Leave that window open.
For it to ask only at `--send`, the session needs these in its permissions (Andy adds them: `/permissions`, or
`.claude/settings.local.json`):

- allow: `Bash(python tools/vault_sync.py:*)`, `Bash(gh pr view:*)`, `Bash(gh pr diff:*)`, `Bash(git fetch origin:*)`,
  `Bash(git show origin/:*)`
- ask: `Bash(python tools/vault_sync.py --send:*)` (ask beats allow, so this one always prompts)

**Hermes:** give it a scheduled job (every 10 minutes) with this prompt:

> In the Jaymo's Powerpack repo, run `python tools/vault_sync.py --claim hermes`. If it says "No task waiting", stop.
> Otherwise it printed a task: do it, following `AGENTS.md` and `docs/vault/How agents use this vault.md` (your own
> worktree and branch from origin/master, an agent note in `E:/Jaymo Vault Live/Agents/`, the "Before you open a PR"
> checklist in `docs/AI-HANDBOOK.md`). If the task has review notes (it was sent back), fix every point on the same
> branch and PR. Push, open or update the PR, then run
> `python tools/vault_sync.py --set <task> status=in-review pr=<number>` and stop. If you need an answer from Andy,
> run `python tools/vault_sync.py --note <task> "Question: ..."` and `python tools/vault_sync.py --set <task>
> status=blocked` instead. Never merge.

If Hermes has no scheduler, paste the same prompt whenever you want it to take the next task.
