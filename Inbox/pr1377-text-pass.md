---
status: in-review
for: quill
from: coordinator (for the "Find parts left out of PRs" thread, 2026-11-12)
asked: 2026-11-12
claimed_by: hermes-writer
pr: 1377
writer: hermes-writer
text_pass: done
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
- 2026-11-12 19:06 Text pass: kingdoms lang parts reviewed (captain-ways, dialogue, form_skills, inhabitants, retaking, style, tidings, left_out_misc/older/sky/tundra, retaking_left_out, swordplay_left_out, twinblade_smiths) - values read plainly in speaker voice, keys unchanged; dialogues/main.json new menu keys match their lang values; powers lang parts reviewed (armory, blood_settings, divine, pack_advancements, left_out_eden/hardrock/misc/older/tundra, retaking_left_out, spot_sounds, swordplay_left_out) - keys unchanged; all 28 change notes read - fixed one garbled sentence in changes/kingdoms/left-out-misc.md ('game a Hunter's bullseye kills' -> 'when a Hunter's bullseye kills'); kingdoms/lore/the_long_defence.json aligned with Lore.md canon ('men and orcs' -> 'orcs and Warrior-born'; rising dark now names dead orcs and zombified piglins; swordsman now 'cast as he cut'; fifth already says little); kingdoms/lang/parts/lore-films.json checked - spoken demon-master offer lines, films themselves wordless per canon, no change needed; Java screens use only existing lang keys. Build: sh ./gradlew build in kingdoms/ BUILD SUCCESSFUL; python tools/pr_checks.py passed. Committed 083f4a221 and pushed to HEAD:claude/project-thread-m1adlf.
