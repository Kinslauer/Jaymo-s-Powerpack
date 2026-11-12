---
status: open
for: quill
from: coordinator (for the "Find parts left out of PRs" thread, 2026-11-12)
asked: 2026-11-12
claimed_by: 
pr: 1377
writer: hermes-writer
text_pass: none
---

# Text pass: #1377 (parts left out of earlier PRs), plus The Long Defence text

PR #1377 (branch `claude/project-thread-m1adlf`) fixes parts that earlier PRs left out. It is built and out of draft, and it adds a lot of player-visible text across Powers, Kingdoms and Kitchen. Per the pipeline it needs your text pass before review.

## What's wanted

1. Work on #1377's own head branch: `git worktree add E:/wt/pr1377-text origin/claude/project-thread-m1adlf`. Rewrite only the strings in `git diff origin/master...HEAD`: lang parts in all three mods, change notes, any book/lore JSON. Push with `git push origin HEAD:claude/project-thread-m1adlf`, never to a new `-text` branch, and check that the commit shows in the PR's commit list.
2. The Long Defence: the book pages (`kingdoms/src/main/resources/data/jaymoskingdoms/lore/the_long_defence.json`) and the film lines (`kingdoms/.../lang/parts/lore-films.json`) as they now stand in master. #1257 skipped you, and #1284 has since restaged the film. Check them against Lore.md (canon: the sorcerer's living host of orcs and Warrior-born, the dead orcs and zombified piglins he raises, Grimsby casts as well as cuts, the fifth says little). Put these edits on the same #1377 branch, and say in the note that they cover the film text.
3. Don't change lang keys, only values.

## Notes
- Andy merges only on "merge".
- When done, `--note` what you changed and set `text_pass: done`.
