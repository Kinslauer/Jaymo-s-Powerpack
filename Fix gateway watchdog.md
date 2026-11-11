# Fix gateway watchdog (for Claude Code on Andy's PC)

Problem (from "Read aloud setup.md"): since about 11:29 PM Nov 10, `E:/hermes/logs/gateway-watchdog.log` says
"restarted the gateway (it wasn't running)" every minute, while the gateway was up (PID 16940 at 11:31 PM, Discord
connected as YOUTH, answering messages).

Likely cause (guess, the script isn't in the repo): at 11:29-11:31 PM `hermes gateway restart` finished an old,
unfinished Hermes update, and that update **re-registered the Hermes_Gateway scheduled task**. The watchdog's
"is it running?" check probably looks for something the old task had (a process name or command line, a pid file,
or the task's own Running state) that the new task no longer matches. Also, "Fix YOUTH.md" found that
`schtasks /run` does nothing while the gateway is up (the task ignores a second run), which would explain why it
"restarts" every minute without the check ever coming back true. If the new task allows parallel runs, or the
watchdog starts the gateway some other way, each minute could start another YOUTH login.

## 12:43 AM: YOUTH stuck in voice
At 12:42-12:43 AM Tiv said "stop" and "stop reading" in parthenon > Dashboard. YOUTH said nothing was reading, but
YOUTH was still in Hotspot > General voice (50+ min). Each message got one reply, so no sign yet of two gateways both
answering. Do this before step 1:
- List every process holding YOUTH: `Get-CimInstance Win32_Process | ? { $_.CommandLine -match 'discord_read_aloud|gateway' } |
  select ProcessId,CreationDate,CommandLine`. Stop every `discord_read_aloud` process (also run
  `py -3 tools/discord_read_aloud.py --stop`). --stop reads one pid file, so a second reading started over it isn't stopped by --stop.
- If YOUTH is still in voice after that, it's a gateway's own voice connection: note which gateway PID, and in step 2
  keep only one gateway. Check the Hermes config for a Discord voice feature and say what it is set to.
- Fix YOUTH's "is it reading?" answer: it should check for a running discord_read_aloud process, not only the pid file.

## Steps

Times on a 12-hour clock. Never print or log a token or key. Restart the gateway only with
`schtasks /run /tn Hermes_Gateway`, never `hermes gateway restart`.

1. Pause the watchdog first: create `E:/hermes/scripts/watchdog.off`.
2. Count the gateways now. List every process whose command line has `gateway` and `hermes`
   (`Get-CimInstance Win32_Process | ? { $_.CommandLine -match 'hermes' -and $_.CommandLine -match 'gateway' } |
   select ProcessId,ParentProcessId,CreationDate,Name,CommandLine`). Also `hermes gateway status`.
   If more than one real gateway is running, keep the one that is connected to Discord (the newest log lines in the
   gateway log come from it) and stop the others with `Stop-Process`. Note the PIDs and start times you found.
3. Read `E:/hermes/scripts/gateway_watchdog.ps1` and the last ~100 lines of `gateway-watchdog.log`. Find the exact
   check that says "not running" and why it's false now. Compare with the task:
   `schtasks /query /tn Hermes_Gateway /xml` (look at Actions > Exec and Settings > MultipleInstancesPolicy) and
   `Get-ScheduledTask Hermes_Gateway | select State`.
4. Fix the check so it finds the real gateway however it was started (by the task or by hand), for example:
   the gateway's own pid file plus `Get-Process -Id`, or `hermes gateway status`, or the command-line match from
   step 2. Keep the 1 minute interval and the ~3 min freeze tolerance from Nov 9. Before it restarts anything, it
   must check a second time ~20 s later (disk stalls on E: can make one check fail) and never restart while any
   gateway process is alive. Keep the Discord webhook alerts (HERMES_ALERT_WEBHOOK), with 12-hour times, and log
   only when the state changes (down, restarted, back), not every minute.
5. Make sure the task can't run twice: MultipleInstancesPolicy = IgnoreNew
   (`$t = Get-ScheduledTask Hermes_Gateway; $t.Settings.MultipleInstances = 'IgnoreNew'; Set-ScheduledTask $t`).
   Back up the old script first (`E:/hermes/backups/gateway_watchdog.ps1.2026-11-11.bak`).
6. Test: run the watchdog once by hand with the gateway up: it must log nothing or "running". Then delete
   `watchdog.off`, wait 3 minutes, and check the log has no new "restarted" lines and there is still exactly one
   gateway process. Don't stop the gateway to test the down path unless Andy says so.
7. bot_health.py reads gateway-watchdog.log: check it still reads the new log lines correctly.
8. Write what you found (the real cause, how many extra gateways there were) and what you changed under
   "## Done" below, then run `python tools/vault_sync.py` in E:/Origins Mod.

## Done

Claude Code on Andy's PC, Nov 11, 12:39-12:46 AM.

**12:43 AM YOUTH stuck in voice (done first, ~12:47 AM):**
- **Processes:** `discord_read_aloud|gateway` matched nothing. The gateway and the reader both hide their command
  lines from a normal shell because they run elevated.
- **What kept YOUTH in voice:** reader 21480, started 12:06:49 AM from Hermes's shell for Tiv. Its temp folder
  `read_aloud_3zmgtysb` was created at 12:06:53 AM, and it was on Lore paragraph ~162 at 12:42 AM.
- **Gone now:** by 12:47 AM it had ended (Andy, or the reading finished), and `--stop` said "Nothing is being read."
- **Not a gateway:** Discord's API says YOUTH has no voice state now (404 Unknown Voice State), so no gateway holds a
  voice connection of its own.
- **Hermes voice settings:** the Hermes config has no Discord voice-channel setting. The gateway's `/voice` join feature
  exists in its code, but no `gateway_voice_mode.json` exists, so it has never been turned on.
- **Why YOUTH said "nothing is reading":** `--stop` only read `~/.discord_read_aloud.pid`, which my test runs had
  overwritten and then deleted.
- **Fix (PR #1281, `claude/read-aloud-find-every-reading`):**
  - each reading also writes `~/.discord_read_aloud/<pid>`;
  - `--stop` and the new `--status` find every reading by those files, the old pid file and a scan of python command
    lines;
  - `--stop` names any reading it can't end (elevated);
  - a new reading stops every older one.
  - Tested with two dummy readers: one found only by command line, one only by its pid file.
- **YOUTH's instructions:** `E:/hermes/skills/media/discord-read-aloud/SKILL.md` and `E:/hermes/SOUL.md` now say: run
  `--status` before answering "are you reading?", never say nothing is reading without it, and pass on a "Could not
  stop process N".
- **Until #1281 merges:** `--status` isn't on master yet, so YOUTH gets an "unrecognized arguments" error from it.

**Real cause:** the watchdog's check (`gateway_watchdog.ps1`) looked for a `python.exe` whose command line has
`gateway run`. The gateway that `hermes gateway restart` started at 11:31 PM (PID 16940, launcher 10176) runs
elevated, and Windows gives a non-elevated script a blank command line for an elevated process. So the check never
matched, the script ran `schtasks /run Hermes_Gateway` and logged "restarted" every minute (73 lines from 11:29 PM to
12:39 AM). Each run did nothing: the task already has MultipleInstancesPolicy = IgnoreNew, the task's run exited with
result 0, and it started no second gateway. The re-registered task wasn't the cause.

**Extra gateways: none.** One gateway the whole time (16940, started 11:31 PM, `hermes gateway status` agrees).
- **The other YOUTH login:** the second YOUTH login that broke the read-aloud voice was **not** a gateway. It is
  python PID 21480, a `discord_read_aloud.py` that Hermes started at 12:06:49 AM for Tiv ("can you read the lore in
  the vault?").
- **It's still reading:** it is still reading the whole Lore note (paragraph ~162 at 12:42 AM).
- **I couldn't stop it:** it's elevated, so Stop-Process gets "Access is denied" from Claude's shell. Andy needs to end
  it from an admin Task Manager.

**Changed:**
- **Backup:** `E:/hermes/backups/gateway_watchdog.ps1.2026-11-11.bak`.
- **New check:** `gateway_watchdog.ps1` now treats the gateway as running if any of these is true:
  - a pid in `gateway.pid`, `gateway_state.json` or `state/gateway.lifecycle.json` is a live `python.exe` (whose command
    line, when readable, says `gateway run`). This works for elevated processes;
  - any `python.exe` command line has `hermes` + `gateway run` (gateways started by hand from a normal shell).
- **Second check:** if neither finds it, it checks again 20 s later and only then runs the task. It never restarts while
  a gateway process is alive.
- **No repeat restarts:** after a restart it waits 10 min before trying again, so a slow start isn't restarted every
  minute.
- **Logging:** it logs only state changes: `down ... restarted it at h:mm AM`, `still down after 10 min: restarted it
  again`, and `gateway is back up (h:mm AM, after about N min)`.
- **Alerts:** the Discord webhook alerts (HERMES_ALERT_WEBHOOK) are unchanged: same texts, 12-hour times. The file is
  still UTF-8 with BOM, so Windows PowerShell reads the emoji.
- **Stale state:** I deleted the stale `gateway_watchdog.state` from 11:29 PM. Otherwise the first run would have posted
  a "back up after 0 min" alert an hour late.
- **Task settings:** the Hermes_Gateway task already had IgnoreNew, so I didn't change it. The watchdog task still runs
  every minute.
- **Freeze tolerance:** there was no "~3 min freeze tolerance" in any script. The 20 s second check plus "never restart
  a live process" stand in for it.

**Tests:**
- **By hand:** a run with the gateway up found 16940 in 1 s and logged nothing.
- **Watchdog back on:** with `watchdog.off` deleted, the watchdog ran several times from 12:41 to 12:44 AM. There were no
  new log lines, Hermes_Gateway didn't run, and there was still exactly one gateway.
- **Down path:** not tested (that needs stopping the gateway; Andy didn't ask).
- **bot_health.py:** `watchdog_restarts()` (regex `^(\S+ \S+) (.*restart.*)$`) counts both new restart lines and
  skips the "back up" line, so it still reads the log correctly.

**Left over:**
- `hermes gateway status` warns "Scheduled Task registration predates the current template (launcher arguments
  differs)". The fix is `hermes gateway install`; I left it alone (not part of this task).
- The read-aloud script lost track of 21480 because a second reading overwrote and then removed the PID file.
  PR #1280 (a new reading stops the old one first) helps only when the old one isn't elevated.
