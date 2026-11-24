---
status: stalled
task: Keep-world deploy of current origin/master to the Deck, then watch latest.log; fix crashes in PRs, merge on green CI, redeploy (Andy, 2026-11-24)
branch: 
with: local desktop session (PC)
pr: 
mods: 
versions: 
started: 2026-11-24
owns:
  - deck/deploy.ps1 (local, untracked)
---
# Deck deploy + crash watch (2026-11-24)

**Task:** check deploy.ps1 builds current origin/master, run the keep-world deploy, watch the Deck log for crashes and fix them.

## Notes
- deploy.ps1 checked: switches to master, `git pull --ff-only origin master`, then builds. Local master was 91 behind.

## Done
- 2026-11-24 18:18 No sign of work for 30 min: marked stalled (whoever ran it stopped).
