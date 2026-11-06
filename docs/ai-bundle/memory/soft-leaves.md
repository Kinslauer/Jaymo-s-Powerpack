---
name: soft-leaves
description: Soft leaves (Realistic Leaves remake, PR #203 0.10.116); PR #284 (0.11.100): mobs path through leaves, spiders hide in them, hard hits crash through
metadata:
  type: project
  modified: 2026-10-16T06:43:06.208Z
---
2026-10-15 Andy asked for The Realistic Leaves Mod (CF 247242, 1.7.10 PML only), remade in Powers `core/Leaves` +
`LeavesStateMixin` (entities pass through any LeavesBlock and are dragged; Jump climbs; falls cushioned; projectiles and
falling blocks collide), `client/LeafRustle`. PR #203 MERGED (0.10.116).
2026-10-16 "villagers get stuck in the leaves": vanilla pathing treats leaves as wall + floor, so sunk mobs were trapped.
Andy rejected making leaves solid for mobs ("breaks immersion"). PR #284 (0.11.100): `LeavesPathMixin` (leaves = OPEN
path type), `core/LeafSpiders` (idle spiders climb into covered leaves, cling, ambush players within 5), crash-through:
a hit that would hurt (fall > safe fall distance, or sideways > 0.6 b/t) breaks the leaves with twig crack + debris.
**How to apply:** keep leaves soft for everyone; don't re-propose the original mod. Wall cling/walk don't grip leaves.
