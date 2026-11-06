---
name: paranoid-guards
description: Every king's guards treat players by his temper (Retinue class: paranoid drive-out, tyrant kneel/fine, warlord sheathe/salute, noble bread, reveler toss); merged PR #97 as Kingdoms 0.5.63 / pack 0.8.121
metadata:
  type: project
  modified: 2026-10-13T06:09:17.618Z
---
2026-10-13 Andy: "paranoid king's guard will push you away and harass you more, eventually driving you out the city
if you harass or stay around the king too long. suspecting you of an attack".
Built on the existing PARANOID temper (Tempers, PR #29). `Paranoia`: per-player suspicion (in memory, not saved);
25 = 2 town guards dog and shove you (Paranoia.Harass goal on Folk); 60 = 5-minute ban, shoved out to 80 blocks
from the seat. Never hostile unless you attack. Merged PR #97 2026-10-13 on Andy's "merge". Not playtested.
07:00Z Andy: "make a personality pass like this for all types" -> Paranoia renamed Retinue with a per-temper RULES table
(heat close/linger/click, wary, driveOut). Tyrant: sneak = kneel. Warlord: weapon in hand counts. Noble/reveler never ban.
Related: [[kingdoms-war-tempers]].
