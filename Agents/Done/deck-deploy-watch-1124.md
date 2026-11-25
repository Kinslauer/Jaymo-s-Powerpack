---
status: done
task: Keep-world deploy of current origin/master to the Deck, then watch latest.log; fix crashes in PRs, merge on green CI, redeploy (Andy, 2026-11-24)
branch: 
with: local desktop session (PC)
pr: "#2130 (merged)"
mods: 
versions: Powers 0.27.28, Kingdoms 0.12.65, Kitchen 0.6.85 (Deck-built, keep world)
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
- 8:00 PM: PC build abandoned (E: HDD RAID at ~750% disk time, 1.3 GB RAM free, paging). Switched to a Deck build in a visible Konsole (~/pbdr-run.sh): built in 3.5 min.
- remote-deploy.sh is PC-only (untracked), so it has to be scp'd to ~/jaymos-server/.deploy first. Its leftover_starters check matches any process with "start.sh" in its command line (a helper named pbdr-restart.sh made it exit 1 under set -e).
- 8:33 PM: 247 mods swapped in, server starting in Konsole (keep world). The server had been stopped cleanly since 11:03 AM.
- 8:34 and 8:39 PM: watchdog crash loop. Stellarity's one-time End setup (`forceload add 0 0` in the_end, from main.mcfunction until its score is saved) waited on an End chunk that could never finish: "Feature order cycle" end_highlands vs enderscape:void_depths. Cause: Kitchen's Ender's Delight plants (VEGETAL_DECORATION via Fabric) vs Enderscape's barrens_vegetation Lithostitched modifier added after Fabric. #2130 moves the 4 plants to TOP_LAYER_MODIFICATION (2 verifiers ok). Youth's restarter paused during the loop, resumed after.
- 9:12 PM: Deck pbdr of #2130 up (Done 1.7 s), 0 cycle errors. Andy's CurseForge profile updated (our jars + Alex's Caves, Icy's Better Horses, Incendium, Modonomicon, Stellarity, Tectonic; horse-expert, kaffees_dual_ride, ultimate_horseshoes retired .old); friends zip 0.27.28 in modpack/.
- Open, Andy's call: this kept world's End is still Enderscape's new_terrain (enderscape:new_terrain/new_end_cities/new_strongholds enabled in level.dat after stellarity), not Stellarity's. No End chunk was ever saved, so disabling them now loses nothing.
- 2026-11-24 21:29 PR #2130 is merged into master; moved to Agents/Done.
