# Deck server handoff (power-pack-admin → reviewer Claude)

Written 2026-11-15 after the new-world deploy. The full history is in `Agents/Done/power-pack-admin.md`.

## The job

Keep the Jaymo's Powers server on the Steam Deck stable:
1. Deploy when Andy asks.
2. Watch the log for watchdog kills, exceptions and crashes.
3. Fix each crash on a branch with a change note, open a PR, and merge it yourself once CI passes.
4. Redeploy, keeping the world, and retest.

You're done when the server has run 30 minutes with Andy logged in and no crash. Stop and report if two fix rounds in a row make no progress. Keep messages to Andy short, and log what you do in `Agents/Done/power-pack-admin.md`.

## Where things stand (2026-11-15, 5:45 PM)

- **Server:** up on a NEW world (made at 2:44 PM). It runs the same code as Powers 0.24.18, Kingdoms 0.9.52 and Kitchen 0.6.30, and Andy's CurseForge game matches.
- **Pregen:** Chunky finished radius 2500 (99,225 chunks) at 5:33 PM.
- **Old world:** kept on the Deck as `~/jaymos-server/world-old-20261115-144334`. Never delete a world without asking Andy.
- **Watchers:** none running. Andy said "stop for now".
- **Not done yet:** the 30-minute run with Andy logged in, on this world.

## Fixes merged today

- **#1612:** the Stone Age recipes turned off 48 vanilla recipes but not their unlock advancements, so the server wouldn't start. It now has a `fabric:false` override in `src/main/resources/data/minecraft/advancement/recipes/<cat>/<name>.json` for each of them. The scrap loot `minecraft:chain` became `iron_chain`. **Lesson: every `data/minecraft/recipe/*.json` that is `fabric:false` needs its advancement disabled too.** A future PR that adds one without the other will stop the server booting.
- **#1614:** in the Dynamic Waters port, `HydrologyManager.leftDry` and `drainedByRavines` called each other forever (StackOverflowError, so chunks were left without terrain). A per-thread visiting set now stops the loop. The port's source is `ports/dynamicwaters`, built by `gradlew buildDynamicWaters` into `E:/dynamicwaters-port-build/libs`.

## Open items

- **16 chunks without terrain:** X 2230–2440, Z 2510–2550, made before #1614. They haven't been checked since. Look at them in-game or offline. **Don't `forceload` them:** that blocked the main thread for 60 s and the watchdog killed the server (5:03 PM).
- **Duplicate mobs:** that kill left 8 mobs saved twice and caused 2 ConcurrentModificationExceptions in `PersistentEntitySectionManager.updateChunkStatus`, all on the pregen's outer ring. They're harmless and one-off, so ignore them unless they show up away from that ring.
- **Tick profile:** never analysed. It comes from the old world (2026-11-14, tick average about 45–54 ms with Andy on). `/debug` zip: `~/jaymos-server/debug/`. JFR: `/tmp/tick.jfr`, which may be gone after a reboot.
- **Older follow-ups from earlier sessions:**
  - 24 kept `isLoaded` sites;
  - BURNED, SKYFALL and SENTENCED starts;
  - WindAether's arrival chunk;
  - FarmhandHome.

## How to

- **SSH:** `ssh -i ~/.ssh/jaymos_deck -o BatchMode=yes deck@10.0.0.164`. Without `-i` it's refused. The server lives in `~/jaymos-server`.
- **RCON:** on the Deck, `cd ~/jaymos-server && python3 ./rcon.py "<command>"`. Examples: `list`, `chunky progress`, `save-all`. Commands that wait on the main thread time out.
- **Deploy, keeping the world** (pulls, builds, copies, restarts, about 15 min): run `deck\deploy.ps1` in PowerShell, then `deck\update-game.ps1` to update Andy's CurseForge instance.
  - New world: `deploy.ps1 -NewWorld -Pregen 2500`. Pregen sends Chunky commands through RCON.
  - Run both in the background. If `update-game` fails on `git pull` (a fetch race), run `git pull --ff-only` and run it again.
- **Starting the server by hand:** `systemd-run --user --collect bash -c "cd ~/jaymos-server && exec konsole --workdir ~/jaymos-server -e ./start.sh"`. `setsid` or `nohup` over ssh dies silently.
  - To check it's running, use `pgrep -f "java.*fabric-server-launch"`. A plain `pgrep -f fabric-server-launch` matches your own ssh command and says "alive" when it isn't.
  - Boot takes about 2 min until `Done (` appears in `logs/latest.log`.
- **After a restart, Chunky may sit at 0 cps.** Fix it with `chunky pause`, then `chunky continue`.
- **The crash watcher** is a Monitor with a 30-minute timeout; re-arm it each time it expires:
  ```
  ssh -i ~/.ssh/jaymos_deck -o BatchMode=yes -o ServerAliveInterval=15 deck@10.0.0.164 'cd ~/jaymos-server; tail -n0 -F logs/latest.log | grep --line-buffered -E "Exception|Crash report|crash-reports|Watchdog|A single server tick took|Stopping server|joined the game|left the game|Done \(|\[Chunky\] Task finished|Error upgrading chunk" | grep --line-buffered -v -E "^\s*at |^\|--|Entity is already tracked"'
  ```
- **Harmless noise:**
  - During boot: `Error loading class: ...` (Sodium, Starlight, client renderers, Create and so on) and Subtle Effects `NoClassDefFoundError: LocalPlayer`.
  - "Can't keep up" right after `Done`.
  - Jade's client-plugin line.
  - `|-- fabric-crash-report-info` in the mod list.
- **Crash reports:** `~/jaymos-server/crash-reports/`. The client's crash reports are in Andy's CurseForge instance `crash-reports/`.
- **Fix flow:**
  1. Branch from `origin/master`. Add a change note in `changes/<mod>/<branch>.md` (a `# title` line, then the text). Never touch `version=`, the MODLOGs or `docs/codemap/`.
  2. Push, `gh pr create`, wait with `gh pr checks <n> --watch`, then `gh pr merge <n> --merge`.
  3. The Release workflow bumps the version about a minute after the merge. Then deploy.
- **Boot-test before merging:** build with `gradlew.bat build -x test`. The jar lands in `E:/jaymospowers-build/Origins-Mod/libs/`. `scp` it over `~/jaymos-server/mods/jaymos-powers-<ver>.jar` with the server stopped, then start it.

## Andy's preferences

- Server runs at 8 GB, client at 8 GB, with a 60 s watchdog.
- Chunky pauses while players are on.
- Keep-world deploys when master moves, unless he asks for a new world. He asked earlier not to keep world backups, but ask before deleting `world-old-*`.
- He may say "ill join myself". Otherwise he has asked for the game to be launched and joined again after each crash.
- Write times as 3:45 PM, never 15:45.
