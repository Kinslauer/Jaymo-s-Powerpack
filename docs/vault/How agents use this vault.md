# How agents use this vault

The vault is `docs/vault/` in the repo, and Andy opens the copy in the main checkout: `E:\Origins Mod\docs\vault`.
It has two kinds of notes:

- **Reference notes** ([[Home]], `Context/`, `Templates/`, this note) are in git. They change through PRs like code.
- **Live notes** are in `Live/`, which git ignores. There's only one copy: the main checkout's
  **`E:\Origins Mod\docs\vault\Live\`**. Every agent writes there directly, by that absolute path, never in its own
  worktree's copy. That way the [[Board]] shows an agent the moment it starts, and Andy has one place to look.
  - `Live/Agents/`: one note per agent. Finished ones move to `Live/Agents/Done/`.
  - `Live/Decisions.md`: the decision log ([[Decisions]]).
  - `Live/Open PRs.md`: every open PR ([[Open PRs]]), written by `python3 tools/vault_prs.py`. Run it after you open
    or merge a PR, and the project thread runs it whenever it reviews.

Writing into `Live/` is the **only** thing an agent does in the main checkout: no git commands there, no other files.
Cloud sessions can't reach this PC; they put the same details in their PR body instead.

## Your own worktree, always

Every agent, Hermes included, works only in **its own git worktree** on its own branch
(`git worktree add ../<name> -b claude/<name> origin/master`, or the app's worktree).

- Never switch branches in a checkout someone else might be using (the main `E:\Origins Mod` folder included).
- Never run `git checkout -- .`, `git restore .`, `git clean -f` or `git reset --hard`: they throw away unsaved work,
  yours or another agent's, with no undo. On 2026-11-06 Hermes lost unsaved work exactly that way.
- To set work aside, make a WIP commit on your own branch instead.

## When you start

1. Read [[Home]] (versions, open PRs, what's next), then `CLAUDE.md`.
2. Read every note in `E:\Origins Mod\docs\vault\Live\Agents\` whose `status` is `running`, `in-review` or
   `blocked`. Their `owns:` lists are the files they're editing. **Don't edit a file another agent owns.** If you
   need one, ask Andy (or that agent's session) first.
3. Make your agent note: copy `Templates/Agent.md` to `E:\Origins Mod\docs\vault\Live\Agents\<branch name without
   claude/>.md` (in Obsidian: new note, which lands in `Live/Agents/`, then *Insert template*). Fill in the task,
   branch, the files you'll own, and `status: running`.

## While you work

- Add a file to `owns:` before you start editing it.
- Each time Andy decides something (a ruling, a tuning number, "yes do it", "no, leave it"), append an entry to
  `E:\Origins Mod\docs\vault\Live\Decisions.md` using `Templates/Decision.md`: date, which agent, the decision in
  Andy's words, where it applies.
- Lasting facts (a 26.3 API gotcha, a standing rule) also go in `docs/AI-HANDBOOK.md` or `docs/HANDOFF.md` through
  your PR, so they're in git too.

## When you finish

1. Set your note's `status` to `in-review`, fill in `pr:` and the version numbers you took, and write a short
   "Done" line: what a player will see, and what wasn't tested.
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
