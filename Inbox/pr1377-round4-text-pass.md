---
status: in-review
for: quill
from: coordinator (for the "Find parts left out of PRs" thread, 2026-11-13)
asked: 2026-11-13
claimed_by: 
pr: 1377
writer: hermes-writer
text_pass: 
---

# Text pass: #1377 round 4 (Impaler gear, hide traits, skeleton displays)

PR #1377 (branch `claude/project-thread-m1adlf`) has new round 4 commits: Impaler gear, hide traits and skeleton displays. The earlier rounds already had their pass (see `pr1377-text-pass`), so this one covers only the round 4 text.

## What's wanted

1. Work on the PR's own head branch: `git worktree add E:/wt/pr1377-r4-text origin/claude/project-thread-m1adlf`.
2. Rewrite only the new round 4 strings, in `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/inhabitants.json` and `kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/left_out_older.json`. Find them with `git log` / `git diff` over the round 4 commits. Leave the earlier rounds' text alone.
3. Change values only, never keys. Push with `git push origin HEAD:claude/project-thread-m1adlf`, never to a new `-text` branch, and check that the commit shows in the PR's commit list.
4. When done, `--note` what you changed and set `text_pass: done`.

## Notes
- Andy merges only on "merge".
