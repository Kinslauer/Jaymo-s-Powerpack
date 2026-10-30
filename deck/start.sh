#!/bin/bash
# Starts the playit tunnel in the background and the Minecraft server here. Type "stop" to shut the server down;
# the tunnel closes with it. Friends join at pgsql-jvc.tun.ply.gg
cd "$(dirname "$0")"

if [ ! -x jdk/bin/java ]; then
	echo "Java isn't set up yet: run ./setup.sh first."
	read -t 60 -rp "Press Enter to close (it closes itself in a minute)."
	exit 1
fi
# one at a time: a double-click started two at once, and the second (failing on the locked world) took the
# tunnel down with it on its way out
exec 9>.start.lock
if ! flock -n 9; then
	echo "The server is already starting or running."
	read -t 60 -rp "Press Enter to close (it closes itself in a minute)."
	exit 1
fi
if ss -ltn 2>/dev/null | grep -q ':25565 '; then
	echo "The server is already running."
	read -t 60 -rp "Press Enter to close (it closes itself in a minute)."
	exit 1
fi

PLAYIT_PID=
if [ -s playit-secret.txt ]; then
	pkill -x playit 2>/dev/null
	./playit --secret-path playit-secret.txt --socket-path "${XDG_RUNTIME_DIR:-/tmp}/playit.sock" > playit.log 2>&1 9>&- &
	PLAYIT_PID=$!
	echo "playit tunnel started (its log: playit.log)."
else
	echo "No playit-secret.txt: the server runs, but only on this network (no tunnel for friends)."
fi

# keep the Deck awake while the server runs
# (only where it's allowed: started from outside the desktop session, e.g. over SSH, systemd-inhibit is refused and
# the server would never start, so it then runs without it)
INHIBIT=
if command -v systemd-inhibit >/dev/null && systemd-inhibit --what=sleep:idle --who=jaymos-server --why=check true 2>/dev/null; then
	INHIBIT="systemd-inhibit --what=sleep:idle --who=jaymos-server --why=Minecraft_server"
else
	echo "(Not keeping the Deck awake: started outside the desktop session. Keep it plugged in with sleep off.)"
fi

$INHIBIT jdk/bin/java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui

if [ -n "$PLAYIT_PID" ]; then
	kill "$PLAYIT_PID" 2>/dev/null
fi
read -t 60 -rp "The server has stopped. Press Enter to close (it closes itself in a minute)."
