---
name: pbdr
description: Use when Andy says "pbdr" (pull, build, deploy, restart) or "pbdr new world", or asks to update, redeploy or restart the Jaymo's Powers server on the Steam Deck.
---

# pbdr: pull, build, deploy, restart

"pbdr" = **p**ull master, **b**uild the pack, **d**eploy the jars to the server, **r**estart it on the **same world**.
"pbdr new world" (or "new map") = the same, but the old world is set aside as a backup and the server starts a fresh
one. Never delete a world. Andy asking for pbdr is his go-ahead for every step below, including stopping the server
(kicking whoever is on).

The server lives in `~/jaymos-server` on the Steam Deck (see `deck/README.md`). This skill is written for a Claude
Code session **on the Deck**, started inside this repository's checkout. Run every step yourself; don't hand Andy
commands to type. Keep him posted with short status lines and finish with one summary (versions, same/new world,
tunnel and voice status, where the friends' zip is).

Paths below: `REPO` = this checkout (the session's working directory), `SRV` = `~/jaymos-server`.

## 1. Pull

```bash
cd "$REPO"
git restore libs/ 2>/dev/null; git clean -fq -- 'libs/jaymos-kitchen-*.jar'   # undo step 2's local Kitchen swap
git checkout master && git pull --ff-only origin master
```

If the checkout has other local changes, stop and tell Andy what they are instead of discarding them. Report the
versions: `version` in `gradle.properties` (pack / Powers), `kingdoms/gradle.properties`, `kitchen/gradle.properties`.

## 2. Build (the server keeps running meanwhile)

The Kitchen jar the pack ships is the one in `libs/` (`jaymos-kitchen-*.jar`). If `kitchen/gradle.properties` has a
newer `version` than that jar, build Kitchen first and swap it in (a local change only; step 1 undoes it next time):

```bash
export JAVA_HOME="$SRV/jdk"            # the Temurin 25 JDK that deck/setup.sh downloaded
(cd kitchen && sh ./gradlew build)
rm libs/jaymos-kitchen-*.jar && cp kitchen/build/libs/jaymos-kitchen-<kitchen version>.jar libs/
```

Then build everything and package the pack (builds Kingdoms and the TRMT port too):

```bash
JAVA_HOME="$SRV/jdk" sh ./gradlew build packageModpack
```

This makes `modpack/mods/` (every server jar) and `modpack/Jaymos-Powers-Modpack-<pack>.zip` (for friends). On the
Deck the CurseForge-profile and `Serverfiles` steps skip themselves; those folders are on Andy's PC, so mention that
his own CurseForge profile isn't updated by a Deck pbdr. If the build fails, stop before touching the server and
show Andy the error (a broken master is fixed in a PR, not on the Deck).

## 3. Stop the server

Check whether it's running: `ss -ltn | grep -q ':25565 '`. If it is:

1. Warn players: `cd "$SRV" && ./rcon.py "say Server restarting for an update in 30 seconds"`, wait 30 s.
2. `./rcon.py stop`. If RCON is off (`enable-rcon=true` missing in `server.properties`), send SIGTERM instead:
   `pkill -TERM -f fabric-server-launch.jar` (Minecraft saves the world on SIGTERM).
3. Wait until the java process is gone and port 25565 is closed (up to ~60 s) before going on. Never `kill -9` a
   server that is still saving.

## 4. Deploy

```bash
cd "$SRV"
rm -rf mods && cp -r "$REPO/modpack/mods" mods
```

The mods folder is replaced whole so jars that left the pack leave the server too.

**New world only:** set the old one aside, named like the earlier backups:

```bash
LEVEL=$(grep '^level-name=' server.properties | cut -d= -f2); LEVEL=${LEVEL:-world}
mv "$LEVEL" "world-backup-$(date +%Y-%m-%d-%H%M)"
```

## 5. Start

Start it detached so it outlives this session:

```bash
cd "$SRV" && setsid nohup ./start.sh > server-console.log 2>&1 < /dev/null &
```

(`start.sh` starts the playit tunnel too. Started outside the desktop it may say it can't keep the Deck awake; that's
fine, the Deck stays plugged in with sleep off.) Then watch `logs/latest.log` until `Done (` appears (a few minutes
on the Deck). If it crashes instead, read the log and `crash-reports/`, report the cause, and don't loop restarting.

## 6. Voice chat host (once, after the first start with Simple Voice Chat)

Simple Voice Chat writes `config/voicechat/voicechat-server.properties` on its first start. Friends reach it through
a separate playit UDP tunnel, so it must advertise that tunnel:

- `port=24454` (the local UDP port the tunnel points at; the default)
- `voice_host=malls-harrington.tun.ply.gg:44082`

```bash
F="$SRV/config/voicechat/voicechat-server.properties"
grep -qx 'voice_host=malls-harrington.tun.ply.gg:44082' "$F" && grep -qx 'port=24454' "$F"
```

If both are already set, skip this step. Otherwise stop the server as in step 3, edit those two lines in place
(`sed -i 's/^voice_host=.*/voice_host=malls-harrington.tun.ply.gg:44082/; s/^port=.*/port=24454/' "$F"`, or add the
line if it's missing), and start it again as in step 5. Don't touch the rest of the file.

## 7. Check and report

- `logs/latest.log` reached `Done (` with no mod errors. The Jade line about a client-only plugin being skipped is
  known and harmless.
- The tunnel: `playit.log` shows it connected; friends join at `pgsql-jvc.tun.ply.gg`.
- Voice: `ss -lun | grep -q ':24454 '` and `voice_host` set as above.
- Tell Andy: pack / Kingdoms / Kitchen versions, same world or new world (and the backup's name), tunnel and voice
  status, whether anyone was kicked, and that friends need `modpack/Jaymos-Powers-Modpack-<pack>.zip` from the Deck
  checkout (the PC makes the same zip when it builds).

## Don'ts

- Never commit or push from the Deck, and never commit `Serverfiles/` or `playit-secret.txt` (they hold secrets).
- Only one copy of the world runs: never start the PC's old server.
- Don't change `server.properties` or other configs beyond what's above unless Andy asks.
