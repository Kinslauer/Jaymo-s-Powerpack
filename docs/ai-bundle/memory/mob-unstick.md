---
name: mob-unstick
description: Pack-wide stuck-pathing fix (Kingdoms Unstick + PathNavigationMixin), merged PR #62, Kingdoms 0.5.36 / pack 0.8.72
metadata:
  type: project
  modified: 2026-10-12T20:31:43.111Z
---

2026-10-12: Andy said mobs too often get stuck pathing and asked about AI Improvements (Modrinth). Verdict: AI
Improvements is NeoForge-only (no Fabric) and a performance mod (head-turn caching, goal toggles), so it can't help.
No Fabric 26.3 pathing-fix mod exists. We built our own: `Unstick` in Kingdoms, run from `PathNavigationMixin` at the
end of PathNavigation.tick, for every mob: hop at 1.5 s no headway, recentre + fresh path at 3.5 s, drop walk at 7 s;
our Folk (out of combat, not raiders/caravans) puff to their goal at 12 s. PR #62 merged 2026-10-12 as Kingdoms 0.5.36 / pack 0.8.72.

**Why:** vanilla's stuck timer resets on every moveTo, and goals re-path every few ticks, so it never fires.

**How to apply:** tune numbers at the top of `Unstick`; new walking code for Folk doesn't need its own puff fallback.
Related: [[kingdoms-workers-mca]].
