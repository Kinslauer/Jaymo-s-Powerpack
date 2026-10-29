# Hosting the server on the Steam Deck

Everything runs from one folder in Desktop Mode; nothing is installed system-wide.

1. **Unpack.** The chat only takes files up to 30 MB, so the download comes in 14 parts
   (`Jaymos-Server-Deck.tar.gz.part00` to `part13`). Put them all in your home folder, then right-click there →
   Open Terminal Here and run:
   `cat Jaymos-Server-Deck.tar.gz.part* | tar -xz && rm Jaymos-Server-Deck.tar.gz.part*`
   You get `~/jaymos-server`.
2. **Copy `playit-secret.txt`** from `Desktop\Serverfiles` on the PC into `~/jaymos-server`. It isn't in the
   download so the key never passes through the chat. Without it the server still runs, but only on your home network.
3. **Set up** (once): in `~/jaymos-server`, right-click → Open Terminal Here, then run `./setup.sh`. It downloads
   Java 25 and the Linux playit tunnel (about 200 MB) and adds a "Jaymo's Powers Server" launcher to the desktop
   and the app menu.
4. **Start** with the launcher or `./start.sh`. Type `stop` in that window to shut it down. Friends join at
   `pgsql-jvc.tun.ply.gg`, as before.

Keep the Deck plugged in (and on Ethernet through a dock if you can). `start.sh` keeps it from sleeping while the
server runs. The server may use up to 6 GB of the Deck's 16 GB.

**Voice chat (pack 0.17.157+):** Simple Voice Chat's server listens on **UDP port 24454**, which the playit tunnel
for the game (TCP) doesn't carry. Once: on playit.gg add a second tunnel, type **UDP**, local port **24454**. Then, with
the server stopped, open `~/jaymos-server/config/voicechat/voicechat-server.properties` (made on the first start with
the mod) and set `voice_host=` to that tunnel's address with its port (for example `voice_host=something.ply.gg:12345`).
Start the server again. Without this, everyone still plays, but voice says it can't connect.

**Only run one copy of the world.** Don't start the PC's server again: its world is now older than the Deck's.

**Updates:** on each release, Claude sends `Deck-mods-<version>.tar.gz` in parts (`.part00`, `.part01`, ...).
Stop the server, put the parts in `~/jaymos-server`, open a terminal there and run:
`rm -rf mods && cat Deck-mods-*.part* | tar -xz && rm Deck-mods-*.part*`
then start it again.

**Pre-generating the world (once, makes fast flight smooth):** with the server running, type in its window
`chunky radius 2500` then `chunky start`. It generates every chunk within 2500 blocks of spawn in the background;
players can stay on, and it pauses when the server stops and carries on with `chunky continue`. `chunky progress`
shows how far along it is. It takes a few hours on the Deck and the world folder grows by about a GB, so start it before
bed. In `server.properties`, `view-distance=12` and `simulation-distance=8` are a good balance on the Deck: more chunks
are sent ahead of flyers, fewer are ticked.
