---
status: done
task: Andy asked for an example of the attached hand-built starts idea.
branch: codex/written-start-example
pr: 2177
mods: kingdoms
started: 2026-11-25
owns:
  - tools/last_lantern.py
  - kingdoms/src/main/resources/data/jaymoskingdoms/structure/written_starts/last_lantern.nbt
  - kingdoms/src/main/resources/data/jaymoskingdoms/written_starts/last_lantern.json
  - kingdoms/src/main/resources/data/jaymoskingdoms/tales/the_last_lantern.json
  - docs/LastLantern.md
  - changes/kingdoms/codex-written-start-example.md
  - E:/Jaymo Vault Live/Assets/last-lantern/
---
# The Last Lantern example

## Plan
- A second Hearthkeeper way-hut with a covered porch and cellar, using PR #2153.
- Check the template, tale and container contents; render the actual blocks; build Kingdoms.
- Open a separate dependent PR without editing Claude's branch.

## Notes
- Worktree: E:/codex-worktrees/written-start-example.
- Base: 7a9c15a6e9d3ba8ed9ab474138494e864e8013da (PR #2153).
- PR: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2177 (draft, targets the dependency branch).
- Tale checker: 82 files, no problems. PR checks: 6 changed files, passed. Template blocks, fixed supplies, markers, ladder and spawn checked; previews inspected.
- Test-world datapack: E:/Jaymo Vault Live/Assets/last-lantern/last-lantern-example.zip. Requires PR #2153; overrides both shipped start entries to select this example.
- Kingdoms Gradle build passed: 7 tasks executed, Java 25. Not played or deployed.

## Done
The saved shelter, fixed supplies, gate and five-step tale are on draft PR #2177. Template, all tales, PR checks and Kingdoms build pass. Exterior, room and cellar block previews inspected and shown to Andy. The example datapack selects this build for both shipped entries in a test world; it requires PR #2153. No Java changes, merges or deployment. In-game placement and progression remain untested. The vault sync was busy with another sync when retried; this live note is saved locally.
- 2026-11-25 07:33 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-25 13:24 PR #2177 is merged into master; moved to Agents/Done.
