---
status: paused
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
- 2026-11-24 6:20 PM: not stalled. The deploy was building Kingdoms (~45 min cold on the PC) when Claude Code killed it for low memory. Nothing reached the Deck. Hermes was left suspended; resumed by hand. Waiting on Andy to rerun.
