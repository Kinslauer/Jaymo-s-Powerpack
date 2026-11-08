---
name: kingdoms-stables
description: Kingdoms 0.5.27 stables + taverns (PR #49 merged 2026-10-12 09:14Z, pack 0.8.62); both "mix by size"
metadata:
  type: project
  modified: 2026-10-12T08:09:42.580Z
---
2026-10-12 Andy asked for a stable in the builder list, mockups first. Three designs mocked on the cloud dev server
(screenshots in /mnt/project-files/stables/): row stable (13x7), coaching yard (13x13), aisle barn (11x15).
Andy chose "mix by size" 2026-10-12 08:05Z: villages build the row stable from 18 people,
kingdoms/fiefs the yard or barn (50/50) from 28; finished stables get 2 horses (Gatherers.STABLE_HORSES).
Branch claude/kingdoms-stables-scng9a, PR #49 merged on Andy's "merge pr". Seen in game: builders put up a coaching yard, horses arrived.
`/kingdom project preview <building> <n>` is the quickest way to mock designs (register with minSize 9999 so builders skip them).
Taverns (Andy asked 2026-10-12 07:38Z; rule: "only wealthy kingdoms with room to spare will build taverns"): alehouse, coaching inn, beer-garden tavern; only a KINGDOM, Wealth WEALTHY, 26+ people, nothing else left to build (Planner.eligible); Andy picked mix by size: alehouse under 48 people (TAVERN_GRAND), then inn or beer garden; stools are wooden slabs with vanilla 26.3 Cushion entities (Cushions.seat, right-click to sit; Andy asked "make the seats able to be sat in... using cushions"); same PR. Screenshots /mnt/project-files/taverns/.
Related: [[kingdoms-caravans]], [[cloud-playtest-setup]].
