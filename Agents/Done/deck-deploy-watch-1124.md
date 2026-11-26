---
status: done
task: Keep-world deploy of current origin/master to the Deck, then watch latest.log; fix crashes in PRs, merge on green CI, redeploy (Andy, 2026-11-24)
branch: 
with: local desktop session (PC)
pr: "#2130 (merged)"
mods: 
versions: Powers 0.27.29, Kingdoms 0.12.67, Kitchen 0.6.85 (Deck-built, keep world)
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
- 9:47 PM: Deck pbdr to Powers 0.27.29 / Kingdoms 0.12.67. Then, on Andy's word, disabled enderscape:new_terrain, new_end_cities, new_strongholds over RCON and restarted (Done 9:59 PM): the End now has Stellarity biomes (/locate hallowed_tundra works), 0 cycle/watchdog errors. The End's centre chunks made at 9:12 PM keep Enderscape terrain.
- Profile updated to 0.27.29 (matches the pack except server-only EasyAuth); modpack/mods and the 0.27.29 friends zip copied to E:\Origins Mod\modpack. Youth's watchdog is on; Claude's watcher stopped.
- Gotcha: `pgrep -f "java.*fabric-server-launch"` inside an ssh `bash -c` matches its own command line; check /proc/<pid>/cmdline starts with java.
- 10:45 PM and 11:12 PM watchdog crashes: explorer maps made on the server thread (downed wandering trader's offers via Droppable Villager Trades; Kingdoms RoamingCaravans trader offers) ran vanilla's chunk-making structure search. #2148 (4 rounds, 2 verifiers each): Kingdoms redirects ExplorationMapFunction, Alex's Caves cabin maps, LabyrinthCompass and RuinFolk to Kingdoms Scout (PoolPick winner, underground column check, placement cut first). Merged on green CI incl. pack boot test.
- 11:41 PM Andy's client disconnected (Network Protocol Error) on jaymospowers:web_meeting_scene: WebMeetingClient added its Figure without an entity id. #2152 sets FIGURE_ID. Merged.
- Open: server "Invalid entity rotation: NaN" spam from 11:26 PM near Colette's village (follows Andy's loaded chunks); source not found by reading; proposed a one-shot stack-trace diagnostic, waiting on Andy. Large red suit: no red-specific block found; likely silent refusals on the 2nd Suit pick or first person; asked Andy for a debug line.
- 12:11 AM Nov 25: Deck pbdr Powers 0.27.39 / Kingdoms 0.12.82 / Kitchen 0.6.87 up; profile updated (tectonic retired per #2138); modpack/mods + 0.27.39 zip copied.
- 12:44 AM Nov 25: NaN diagnostic (#2156) deployed (Powers 0.27.41); Andy's profile on 0.27.41. 12:48 AM: server stopped cleanly for the night at Andy's request; he'll pbdr in the morning. Symbiote non-web-host rulings (Decisions.md 1:00 AM) building in workflow wf_e3b51313-bd0, branch symbiote-nonweb-hosts.
- Nov 25 overnight: PR #2158 (symbiote in hosts without Web: big body at 60, fights a sensed slinger first then binds or leaves-and-pursues, suit can't come off by choice (mask at most, always a fight), trust debug command) merged after 3 workflow rounds + my compile fix (type-use @Nullable on java.util.UUID) + final verifier ok + green CI. For Andy to confirm (in the change note): big body at 60 has no idle/time-out withdrawal; the bell/bottle still pour it off without a fight; lockout strips the suit for a minute; a slinger fight is an ordinary hold. Server is stopped; Andy will pbdr in the morning.
