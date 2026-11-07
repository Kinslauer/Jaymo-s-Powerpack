---
name: kingdoms-arson
description: Kingdoms 0.5.20 (PR #41 merged 2026-10-12): lighting a fire in a settlement is arson; seen, the townsfolk mob you until you hit back
metadata:
  type: project
  modified: 2026-10-12T07:08:11.607Z
---
2026-10-12 Andy: "if villagers see you lighting a fire it is as if you stole, and everyone within the vicinity
civilians included attack you until you hit back". PR #41 merged on Andy's "merge pr" as Kingdoms 0.5.20.
`Theft`: Crime.ARSON (medium) from flint and steel / fire charge, checked a tick later; a riot of grown villagers
(vanilla + MCA) and civilian folk within 24 blocks punches (1 dmg, crowd capped at one blow per 10 ticks); hitting
any rioter scatters them, guards keep hunting. Firefighting skips rioters.
Playtest gotchas: superflat spawns endless slimes (they killed the tester, not the crowd); a paranoid ruler doubles
reputation loss; kill folk/golems first or the guards' javelins kill the tester. See [[cloud-playtest-setup]].
