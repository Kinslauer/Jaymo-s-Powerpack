---
name: distant-horizons-chunky
description: DH removed at Andy's request 2026-10-13 (PR #113, pack 0.9.6); earlier: Andy's DH "not working" (2026-10-13) was a 256-chunk LOD radius + out-of-memory; Chunky added for fast gliding (PR #94, pack 0.8.109)
metadata:
  type: project
  modified: 2026-10-13T05:09:36.253Z
---
2026-10-13: Andy's log showed Distant Horizons 3.3.4 loaded and talking to the server's DH; the LOD radius was 256 chunks
and the client ran out of memory. Advice given: radius 64-128, Show Generation Progress on, 8-10 GB RAM in CurseForge.
Andy offered to drop DH; kept it (only way past vanilla's 32 chunks). For loading while gliding fast, Chunky Fabric
1.5.3 (CF 433175/8036332, Modrinth 4Eotm6ov, dev: false) merged in PR #94 as pack 0.8.109; Andy runs
`/chunky radius 5000` + `/chunky start` in the server console once.
**Why:** server-side generation with heavy worldgen is what lags fast gliders.
**How to apply:** DH is OUT: 2026-10-13 14:04Z Andy asked to remove it; PR #113 (merged 14:16Z, pack 0.9.6) drops it from packMods and adds mod id distanthorizons to removedIdsFromProfile. Don't re-add unless Andy asks. Chunky left in 0.8.118.
2026-10-13 06:12Z: Deck server (Xmx6G, ~4 GB free) hung on new-world start at "Selecting global world spawn" as DH
started its server-side world-gen queue (0.8.111, before village levelling). Chunky had no task. Suggested test: turn
off DH server distant generation in config/DistantHorizons.toml; awaiting Andy's result.
