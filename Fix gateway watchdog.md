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
  `py -3 tools/discord_read_aloud.py --stop`). A reader started before PR #1280 has no pid file, so --stop misses it.
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
