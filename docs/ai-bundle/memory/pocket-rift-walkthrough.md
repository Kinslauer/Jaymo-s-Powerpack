---
name: pocket-rift-walkthrough
description: Arsenal Z opens a standing rift ahead you walk into (no instant teleport); V on a void gate closes it (PR #181, pack 0.10.75)
metadata:
  type: project
  modified: 2026-10-14T21:03:30.563Z
---
2026-10-14 Andy asked that Arsenal's Z spawn the portal in front so you choose to walk through, and asked how to remove void gates (there was no way). PR #181 MERGED 2026-10-14 21:2xZ (pack 0.10.75): Z/B opens a rift 2.5 blocks ahead (in or, inside the pocket, out); Z again closes it; open 10 s, +10 s after owner passes, friends follow; the in-rift closes when the owner comes back out. Hold V 0.6 s on your own void gate (within 1.5 blocks of aim) closes it; tap V places. Rifts and gates are drawn as portals (ArsenalPortalRenderer, Portals payload): nether swirl normally, End starfield when corrupted (Andy's "nether or end depending on your status"). Builds; not playtested. Builds on [[arsenal-pocket-dimension]]. 2026-10-16 Andy: "should work for the pocket dimension storage base too" (ambiguous; asked on a card). Andy chose "Both": door pad holds a standing walk-through portal out while the room is occupied, AND void gates into the pocket (corrupted Sneak+V looking down >=70 deg opens the pocket rift; a void gate fixed in your own room is the arrival point for rifts into it). PR #275 MERGED 2026-10-16 03:1xZ as pack 0.11.92.
