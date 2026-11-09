---
status: done
task: xeno-ultimates-patch-2
branch: hermes/xeno-ultimates-2
with: 
pr: 1160
mods: powers
versions: 
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/power/xenoform/
  - src/client/java/com/jaymospowers/client/power/xenoform/
  - tools/ultimates/
---

# Xeno-Band Ultimates patch 2: finish and open the PR

**Task:** Finish the seven Xeno-Band Ultimates (Elder Wyrm, Copper Storm, Ironpour, Worldmaw, Brinecrown, Lodestar, Nightfall), fix the ~11 review issues in the Ultimate classes against 26.3 APIs, and open the PR.

## Plan
- Create worktree E:/wt/xeno-ultimates-patch-2 from origin/claude/project-thread-aniy94, branch hermes/xeno-ultimates-2
- Merge master in; revert gradle.properties, MODLOG.md, docs/codemap/ to master; add changes/powers/hermes-xeno-ultimates-2.md
- Review seven Ultimate*.java classes for real bugs with file:line; check 26.3 APIs against genSources
- sh ./gradlew build, python3 tools/pr_checks.py, commit, push, open PR, set task in-review

## Notes
- Branch is ~91 commits behind master; made under old rules (edits gradle.properties, MODLOG.md, docs/codemap/)
- Third review found ~11 issues never fixed — review the seven Ultimate classes again

## Done
What a player will see, and what wasn't tested.
- 2026-11-08 20:27 PR #1160 is merged into master; moved to Agents/Done.
