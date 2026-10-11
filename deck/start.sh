#!/bin/bash
# Starts the playit tunnel in the background and the Minecraft server here. Type "stop" to shut the server down;
# the tunnel closes with it. Friends join at pgsql-jvc.tun.ply.gg
cd "$(dirname "$0")"

if [ ! -x jdk/bin/java ]; then
	echo "Java isn't set up yet: run ./setup.sh first."
	read -rp "Press Enter to close."
	exit 1
fi
if ss -ltn 2>/dev/null | grep -q ':25565 '; then
	echo "The server is already running."
	read -rp "Press Enter to close."
	exit 1
fi

PLAYIT_PID=
if [ -s playit-secret.txt ]; then
	pkill -x playit 2>/dev/null
	./playit --secret "$(head -n1 playit-secret.txt | tr -d '\r\n ')" > playit.log 2>&1 &
	PLAYIT_PID=$!
	echo "playit tunnel started (its log: playit.log)."
else
	echo "No playit-secret.txt: the server runs, but only on this network (no tunnel for friends)."
fi

# keep the Deck awake while the server runs
INHIBIT=
if command -v systemd-inhibit >/dev/null; then
	INHIBIT="systemd-inhibit --what=sleep:idle --who=jaymos-server --why=Minecraft_server"
fi

$INHIBIT jdk/bin/java -Xms2G -Xmx6G -jar fabric-server-launch.jar nogui

if [ -n "$PLAYIT_PID" ]; then
	kill "$PLAYIT_PID" 2>/dev/null
fi
read -rp "The server has stopped. Press Enter to close."
