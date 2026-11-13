# Change notes

A PR never changes a `version=` line, a MODLOG or `docs/codemap/`. Instead it adds one small file per mod it changes:

    changes/powers/<branch-name>.md      (Jaymo's Powers, root src/)
    changes/kingdoms/<branch-name>.md    (kingdoms/src/)
    changes/kitchen/<branch-name>.md     (kitchen/src/)

Use your branch name (or a short slug) so no two PRs pick the same file. The file is the MODLOG entry without the
version: a `# <title>` first line, then the same text an entry has always had (what a player will see, the classes,
what wasn't tested). Example, `changes/powers/fishing-tackle.md`:

    # fishing tackle - rods, baits, lures
    - **Rods**: the Cane Pole takes small fish only, but they bite 1.5x as fast...
    - Built; not played.

A bigger version step, only when Andy asks for one: put a line `bump: minor` in the note (0.21.109 -> 0.22.0), or
`bump: major` (-> 1.0.0). The line is left out of the MODLOG. Without it every note is a patch bump.
A patch bump never reaches .100: the release after x.y.99 is x.(y+1).0 by itself, so each mod's minor version steps
every 100 patch releases (`PATCH_ROLLOVER` in `tools/release.py`). A mod already past .99 rolls over on its next release.

A PR that touches two mods adds two notes. After the merge, the Release workflow (`.github/workflows/release.yml`,
running `tools/release.py`) bumps each mod's patch version once per note, appends
`## <version> (<date>): <title> (#<PR>)` plus the text to the end of that mod's MODLOG, deletes the note, updates the
version table in `docs/vault/Home.md`, regenerates `docs/codemap/` and commits to master. `tools/pr_checks.py` (the PR
checks workflow) fails a PR that changes a mod's code without a note, or that changes `version=`, adds to a MODLOG or
edits `docs/codemap/`.

An older branch that still bumps versions or appends to a MODLOG: `py -3 tools/change_note.py convert` moves its
MODLOG additions into notes and puts master's version lines back.
