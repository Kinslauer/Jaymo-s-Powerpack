---
name: network-protocol-error
description: "Connection Lost: Network Protocol Error" on join = client packet-handler exception; usually a friend's mods folder not matching the server
metadata:
  type: reference
  modified: 2026-10-14T07:09:26.896Z
---
"Network Protocol Error" with an "Open Report Directory" button means the CLIENT threw while handling a packet (ClientCommonPacketListenerImpl.onPacketError); the report is `<instance>/debug/disconnect-<date>-client.txt` and names the cause.
- 2026-10-12: Andy's own game had it from leftover Alex's Mobs (Missing element jukebox_song alexsmobs:thime) after the mod left the server.
- 2026-10-14: friends got it on join; report confirmed stale Alex's Mobs + Citadel + Distant Horizons jars left in their mods folder (pack updates don't delete removed mods). Fix: delete them. No code change.
- Andy's CurseForge instance: C:\Users\toopo\curseforge\minecraft\Instances\Jaymo's Powers (debug, logs, mods readable via folder tools once granted).
- Dev runServer needs the CameraPreset preLaunch entry removed temporarily (client class missing in dev server); Kingdoms isn't in root dev runtime: copy kingdoms/build/libs jar into run/mods. See [[cloud-playtest-setup]].
