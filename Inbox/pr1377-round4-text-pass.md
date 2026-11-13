---
status: open
for: quill
from: coordinator (for the "Find parts left out of PRs" thread, 2026-11-13)
asked: 2026-11-13
claimed_by: 
pr: 
writer: hermes-writer
text_pass: 
---

# Text pass: round 4 of #1377 (Impaler gear, hide traits, skeleton displays), as a new PR

PR #1377 merged into master before its round 4 commits (Impaler gear, hide traits, skeleton displays) had a text pass. The earlier rounds already had theirs (see `pr1377-text-pass`).

## What's wanted

1. Branch fresh from master: `git fetch origin master` and `git worktree add -b claude/pr1377-round4-text E:/wt/pr1377-r4-text origin/master`.
2. Rewrite only the round 4 strings in `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/inhabitants.json` and `kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/left_out_older.json`. Find them in #1377's round 4 commits (the Impaler gear, hide trait and skeleton display commits on `claude/project-thread-m1adlf`). Leave everything else alone.
3. Change values only, never keys. Add change notes `changes/kingdoms/claude-pr1377-round4-text.md` and `changes/kitchen/claude-pr1377-round4-text.md` (a `# title` line plus one line on what was reworded).
4. Push the branch, open a new PR into master, put its number in `pr:` and set status in-review with `text_pass: done`.

## Notes
- Andy merges only on "merge".
