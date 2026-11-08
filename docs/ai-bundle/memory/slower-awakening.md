---
name: slower-awakening
description: Andy wants powers to take at least 3 in-game days to awaken, sooner only via a power's own acquisition method (PR #102 merged, pack 0.8.123)
metadata:
  type: project
  modified: 2026-10-13T06:46:39.816Z
---
2026-10-13 Andy: "powers should now take longer to gain, at least 3 days or sooner if the power has acquisition methods".
PR #102 (open): Powers.AWAKEN_MIN/MAX 72000/120000 ticks (3-5 days of survival play); old saved countdowns stretched +48000 once (awaken_slow marker).
No power had its own acquisition method at the time; the only routes are the survival countdown and /power set. Any acquisition method added later should cut the countdown, never bypass the 3-day floor for the passive route.
2026-10-13 07:2xZ: Andy approved callings (core/Callings.java, same PR): Gravity 40+ fall, Chrono clock sleep, Arsenal 5 weapon kinds, Resonance midnight village bell / sonic boom, Darktide dusk-to-dawn at sea. He asked to change Wind and Web; my picks (kill a Breeze; cave spider bite, 1 day off) await his OK.
2026-10-13 07:2xZ: Andy OK'd all (Wind = kill a Breeze, Web = cave spider bite) and said merge. PR #102 merged as pack 0.8.123.
