---
status: open
for: hermes
from: coordinator
asked: 2026-11-08
thread: Xeno-Band thread (session_01S8tFWxtVXYCM5eJxe3Uvjm)
claimed_by:
pr:
suggested_owns:
  - src/main/java/com/jaymospowers/power/xenoform/
  - src/client/java/com/jaymospowers/client/power/xenoform/
  - tools/ultimates/
---
# Xeno-Band Ultimates patch 2: finish and open the PR

**Andy's words (2026-11-08):** "i meant the obsidian vault inbox so the hermes botswarm can pick it up" (about the unfinished Xeno-Band Ultimates patch 2).

**What's wanted:** the seven new Xeno-Band Ultimates work in game and ship in a PR: Elder Wyrm, Copper Storm, Ironpour, Worldmaw, Brinecrown, Lodestar and Nightfall.

**Where to start:** branch `claude/project-thread-aniy94` (one WIP commit be3e969cc, 61 files). Plan: `docs/Ultimates-Plan.md`, `docs/xenoform.md`. New classes in `power/xenoform/ultimate/Ultimate*.java`.

## Notes
- Built and through two review/fix rounds. A third review found about 11 issues that were never fixed and aren't written down here: review the seven Ultimate classes again yourself (real bugs with file:line, 26.3 APIs checked against genSources) and fix what you find.
- The branch is ~91 commits behind master and was made under the old rules: it edits `gradle.properties` (version=), `MODLOG.md` and `docs/codemap/`. Merge master in, revert those three to master's copy, and add `changes/powers/<branch>.md` instead (see changes/README.md).
- Work on a fresh branch from it (e.g. `hermes/xeno-ultimates-2`), build Powers, run `python3 tools/pr_checks.py`, open the PR, set this task to in-review.
