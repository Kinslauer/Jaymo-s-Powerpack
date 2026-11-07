# How agents use this vault

The vault is `docs/vault/` in the repo, and Andy opens the copy in the main checkout: `E:\Origins Mod\docs\vault`.
It has two kinds of notes:

- **Reference notes** ([[Home]], `Context/`, `Templates/`, this note) are in git. They change through PRs like code.
- **Live notes** are in `Live/`. It's a link (a Windows junction) to the live folder **`E:/Jaymo Vault Live/`**, which is
  outside git, so there's only one copy. Every agent writes there directly, by that absolute path, never in its own
  worktree's copy. That way the [[Board]] shows an agent the moment it starts, and Andy has one place to look.
  - `Live/Agents/`: one note per agent. Finished ones move to `Live/Agents/Done/`.
  - `Live/Decisions.md`: the decision log ([[Decisions]]).
  - `Live/Open PRs.md`: every open PR ([[Open PRs]]), written by `tools/vault_prs.py`. It refreshes by itself
    when a Claude session starts and after any vault edit (hooks in `.claude/settings.json`, at most once a minute);
    other assistants run `python tools/vault_prs.py` after opening or merging a PR.

Always use the `E:/Jaymo Vault Live/` path, not `E:/Origins Mod/docs/vault/Live/`: the app blocks Claude's file tools from
writing into the main checkout. Never run git commands in the main checkout.

## The live folder syncs through GitHub

The live folder is its own small git checkout of the **`vault-live`** branch (an orphan branch: only live notes, it never
merges into master). `tools/vault_sync.py` commits what changed, merges the branch and pushes, so cloud sessions (the
coordinator included) see the same Board, Inbox and Decisions as the PC.

- **Claude Code** syncs by itself: the hooks in `.claude/settings.json` run it at session start and after any edit in
  the live folder.
- **Hermes and other assistants:** run `python tools/vault_sync.py` when you start (before reading notes) and right after
  you change a live note.
- **Once per PC:** `python tools/vault_sync.py --setup` turns the existing `E:/Jaymo Vault Live/` into that checkout and
  keeps every file already in it. A cloud session runs the same command (as `python3`) and gets the branch in its own
  `docs/vault/Live/`.
- `python tools/vault_sync.py --status` prints the running, in-review and blocked agents and the open Inbox tasks.
- Conflicts are rare because every note has one owner. `Decisions.md` keeps both sides' lines; anything else keeps the
  local copy, and the other side's is still in the branch history.
- The sync never runs git in the main checkout: only inside the live folder.

## Tasks from the coordinator (`Live/Inbox/`)

The whole loop (Hermes builds, Claude on the PC reviews, Andy says yes once, the coordinator shows him) is in
[[Pipeline]]. Change tasks with `vault_sync.py --claim/--set/--note` rather than by hand, so the change syncs at once.

The coordinator is the Claude project thread Andy talks to in the Claude app. It posts work for the agents on the PC as
notes in `Live/Inbox/`, made from `Templates/Task.md`: Andy's words, what's wanted, where to start, `for:` (an agent's
name, or `any`), and the files it suggests owning.

1. When you start, run the sync and read every Inbox note with `status: open` whose `for:` is you or `any`.
2. **Claim one**: set `status: claimed` and `claimed_by:` to your agent note's name, sync straight away, and make your
   agent note as usual with a `task:` line naming the Inbox note. If the sync shows someone else claimed it first,
   leave it and take another.
3. Questions for Andy go under the task's *Notes* (and to Andy, if he's there): the coordinator reads them and asks him.
4. When your PR is up, run `python tools/vault_sync.py --set <task> status=in-review pr=<number>`. The reviewer
   only picks up tasks whose own status is `in-review`; setting just your agent note isn't enough. The coordinator
   sets `done` and moves the task to `Live/Inbox/Done/` after Andy merges.

The coordinator never edits an agent note. On a claimed task it only adds lines under *Notes*, except to set
`in-review` when an agent forgot to, and `sent`/`done` at the end.

## Your own worktree, always

Every agent, Hermes included, works only in **its own git worktree** on its own branch
(`git worktree add ../<name> -b claude/<name> origin/master`, or the app's worktree).

- Never switch branches in a checkout someone else might be using (the main `E:\Origins Mod` folder included).
- Never run `git checkout -- .`, `git restore .`, `git clean -f` or `git reset --hard`: they throw away unsaved work,
  yours or another agent's, with no undo. On 2026-11-06 Hermes lost unsaved work exactly that way.
- To set work aside, make a WIP commit on your own branch instead.

## When you start

0. Sync the live folder (`python tools/vault_sync.py`; Claude Code's hook does it for you), then check `Live/Inbox/`
   for a task meant for you (see above).
1. Read [[Home]] (versions, open PRs, what's next), then `CLAUDE.md`.
2. Read every note in `E:/Jaymo Vault Live/Agents/` whose `status` is `running`, `in-review` or
   `blocked`. Their `owns:` lists are the files they're editing. **Don't edit a file another agent owns.** If you
   need one, ask Andy (or that agent's session) first.
3. Make your agent note: copy `Templates/Agent.md` to `E:/Jaymo Vault Live/Agents/<branch name without
   claude/>.md` (in Obsidian: new note, which lands in `Live/Agents/`, then *Insert template*). Fill in the task,
   branch, the files you'll own, and `status: running`.

## While you work

- Add a file to `owns:` before you start editing it.
- Each time Andy decides something (a ruling, a tuning number, "yes do it", "no, leave it"), append an entry to
  `E:/Jaymo Vault Live/Decisions.md` using `Templates/Decision.md`: date, which agent, the decision in
  Andy's words, where it applies.
- Lasting facts (a 26.3 API gotcha, a standing rule) also go in `docs/AI-HANDBOOK.md` or `docs/HANDOFF.md` through
  your PR, so they're in git too.

## When you finish

1. Set your note's `status` to `in-review`, fill in `pr:` and the version numbers you took, and write a short
   "Done" line: what a player will see, and what wasn't tested. If the work came from an Inbox task, also set the
   task to `in-review` (step 4 of the Inbox section above).
2. If your PR changes versions, update the table on [[Home]] in the same PR.
3. After Andy merges, the agent that merged it (usually the project thread) sets the note to `merged` and moves it to
   `Live/Agents/Done/`.

## Two agents on one PR

Claude and Hermes (or any two assistants) can share a PR: a PR is just a branch.

- **One agent note, both names.** Add a `with:` line naming the other assistant, and split `owns:` per agent
  (for example `- kingdoms/ (Claude)` and `- docs/ (Hermes)`). Never edit the other's files.
- **Take turns on the branch.** `git pull` before you start, commit and `git push` when you stop. If the push is
  refused, `git pull`, resolve, push again. **Never force-push or rebase** a shared branch: it erases the other's work.
- **One agent owns the shared files** (`gradle.properties` versions, MODLOG entries, `docs/codemap/*.md`) and does
  them once, at the end, right before review. Say which agent on the note.
- Leave a line under *Notes* when you hand over ("Claude: done with the AI, Hermes can do the models"), so the other
  knows where things stand without asking Andy.
- Both log Andy's rulings in [[Decisions]], so neither works from an old answer.

## Three agents at once

- One task per agent, one branch and worktree each, one PR each.
- Split by files, not by idea: two agents never own the same file. Shared hot spots (`gradle.properties`,
  `MODLOG.md`, `kingdoms/MODLOG.md`, `JaymosPowers.java`, `docs/codemap/*.md`, this vault's [[Home]]) are
  touched only at the very end, right before the PR, after merging master in.
- Versions: take the next free numbers just before the PR and write them on your note, so the others can see them.
- Report to Andy in a few plain sentences: "ready for you to merge as PR #N; compiles but hasn't been played yet."
