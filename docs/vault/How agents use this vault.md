# How agents use this vault

The vault is `docs/vault/` in the repo. It's plain markdown, so agents read and write it with their normal file tools;
Andy opens the same folder in Obsidian. Every agent's worktree has its own copy, so **notes reach master through PRs**
like code does. Keep edits small so they merge cleanly.

## When you start

1. Read [[Home]] (versions, open PRs, what's next), then `CLAUDE.md`.
2. Read every note in `Agents/` whose `status` is `running` or `in-review`. Their `owns:` lists are the files they're
   editing. **Don't edit a file another agent owns.** If you need one, ask Andy (or that agent's session) first.
3. Make your agent note: copy `Templates/Agent.md` to `Agents/<branch name without claude/>.md` (in Obsidian: new
   note in `Agents/`, then *Insert template*). Fill in the task, branch, the files you'll own, and `status: running`.
   Commit it as the first commit on your branch, so it's on your PR from the start.

## While you work

- Add a file to `owns:` before you start editing it.
- Each time Andy decides something (a ruling, a tuning number, "yes do it", "no, leave it"), append an entry to
  [[Decisions]] using `Templates/Decision.md`: date, who/which agent, the decision in their words, where it applies.
- Lasting facts (a 26.3 API gotcha, a standing rule) go in `docs/AI-HANDBOOK.md` or `docs/HANDOFF.md`, not only here.

## When you finish

1. Set your note's `status` to `in-review`, fill in `pr:` and the version numbers you took, and write a short
   "Done" line: what a player will see, and what wasn't tested.
2. If your PR changes versions, update the table on [[Home]] in the same PR.
3. After Andy merges, the next agent (or the project thread) sets the note to `merged` and moves it to
   `Agents/Done/`.

## Three agents at once

- One task per agent, one branch and worktree each, one PR each.
- Split by files, not by idea: two agents never own the same file. Shared hot spots (`gradle.properties`,
  `MODLOG.md`, `kingdoms/MODLOG.md`, `JaymosPowers.java`, `docs/codemap/*.md`, this vault's [[Home]]) are
  touched only at the very end, right before the PR, after merging master in.
- Versions: take the next free numbers just before the PR and write them on your note, so the others can see them.
- Report to Andy in a few plain sentences: "ready for you to merge as PR #N; compiles but hasn't been played yet."
