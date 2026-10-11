#!/bin/bash
# One-time setup on the Steam Deck (Desktop Mode): downloads Java 25 and the playit tunnel into this folder.
# Nothing is installed system-wide and no password is needed. Run it from this folder: ./setup.sh
set -e
cd "$(dirname "$0")"

if [ ! -x jdk/bin/java ]; then
	echo "Downloading Java 25 (Temurin)..."
	rm -rf jdk jdk.tar.gz
	curl -fL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
	mkdir jdk
	tar -xzf jdk.tar.gz -C jdk --strip-components=1
	rm jdk.tar.gz
fi
jdk/bin/java -version

if [ ! -x playit ]; then
	echo "Downloading the playit tunnel..."
	curl -fL -o playit "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-amd64"
	chmod +x playit
fi

chmod +x start.sh setup.sh
# a launcher in the app menu and on the desktop
LAUNCHER="[Desktop Entry]
Type=Application
Name=Jaymo's Powers Server
Comment=Starts the Minecraft server and the playit tunnel
Exec=bash -c 'cd \"$PWD\" && ./start.sh'
Icon=applications-games
Terminal=true
Categories=Game;"
mkdir -p ~/.local/share/applications
echo "$LAUNCHER" > ~/.local/share/applications/jaymos-server.desktop
if [ -d ~/Desktop ]; then
	echo "$LAUNCHER" > ~/Desktop/jaymos-server.desktop
	chmod +x ~/Desktop/jaymos-server.desktop
fi

echo
if [ ! -s playit-secret.txt ]; then
	echo "One thing left: playit-secret.txt is missing (it isn't in the download, to keep it out of the chat)."
	echo "Copy it from Desktop\\Serverfiles on your PC into this folder, then start the server."
else
	echo "All set. Start the server with ./start.sh or the 'Jaymo's Powers Server' launcher."
fi
