---
name: kingdoms-living-economy
description: Andy's Kingdoms "living economy" ask (2026-10-11) and its 3-stage split; stages 1-2 and living trees merged in PR #22 (Kingdoms 0.5.4) 2026-10-11
metadata:
  type: project
  modified: 2026-10-11T17:16:05.482Z
---

2026-10-11: Andy asked for sleep fast-forwarding kingdoms, visible gathering (mining, tree farms, crop farms, butchering),
every villager with a visible profession, kings leaving the keep by day with 3-8 guards and returning at night, builders
prioritising mines (covered entrances into rock or cliff faces, stopping at caves), then tree farms, animal and crop farms,
then housing and defences, the largest village building for the king, a farm for a farmer, a chest-room building for the
builders, and generated castles of 60+ blocks with several floors.

Split into stages:
1. PR #22 (draft, branch claude/kingdoms-living-economy-81vctt, Kingdoms 0.5.1): `Nights` (sleep catch-up), `Royals`
   (king's round and escort), `Farmers` (FARMER role id 8), `Citadel` (61x61 castle). Only compiled.
   Also `Wardens` (king under 3 guards stays in; may offer a player a warden post; king's death wipes their rep).
2. Same PR, 0.5.2: `Mines` (builders dig a cliff/shaft mine first, stop at caves), `Gatherers` (MINER id 9,
   WOODCUTTER id 10), tree_farm and pen blueprints first in build order; farmers keep pens.
   0.5.3 (2026-10-11): playtested on the dev server (seed 8675309 plains village at -304,-320); mine, gatherers, tree
   farm, pen, sleep catch-up all seen working; fixes listed in MODLOG 0.5.3. Not yet checked: citadel, warden offer.
   0.5.4: Andy asked for Dynamic Trees (no Fabric 26.3 build, MIT) and chose "rebuild our own": `Trees` +
   `SaplingBlockMixin` (staged growth, whole-tree felling, self-seeding); woodcutters use it. Growth/woodcutter seen in game.
3. PR #24 (MERGED 2026-10-11, same branch, Kingdoms 0.5.5, 2026-10-11): `Homes` (working folk sleep in beds at night, new
   house brings a villager) and `Jobs` (unemployed villagers sent to Kingdoms job sites; workers turn storage into goods).
   Playtested: sleeping, a trade taken, butcher cooking, farmer hand-in. Not seen: smith, house newcomer.

**Why:** Andy wants kingdoms to feel alive and the economy to be gathered visibly.
**How to apply:** #22 merged 2026-10-11; stage 3 merged as PR #24; read kingdoms/MODLOG.md's 0.5.1 and 0.5.2 sections first.
