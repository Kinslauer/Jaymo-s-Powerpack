---
name: tier-hiding
description: TierHiding (PR #1068 MERGED 2026-11-06 03:35Z, Kingdoms 0.7.624): items beyond a player's world tier/knowledge hidden from JEI + recipe book until reached or held
metadata:
  type: project
  modified: 2026-11-06T03:27:54.998Z
---
Andy 2026-11-06: "all items that are inaccessible to your world tier should be hidden from JEI and otherwise. you should really have to discover everything." Built as `TierHiding` (Kingdoms), PR #1068 (0.7.624) merged, unplayed..
- Seeds: tags jaymoskingdoms:tier_hidden/<tier|never>, knowledge_hidden/<n>, TierGate.tierOf, Aether namespaces = Borderlands, Knowledge.needed blocks; then carried through every recipe (min over recipes, max over slots, station counts).
- My calls (flag if Andy disagrees): Nether things = Borderlands (One Dimension lets you dig down to it); End stone = knowledge 5; End-city stuff = Legend; anything ever held stays revealed (`tier_seen`); creative sees all; trades unchanged.
- Client: client/compat/TierJei (JEI compileOnly in kingdoms/build.gradle now), ClientRecipeBookMixin.
Related: [[apotheosis-world-tiers]], [[boss-posture-dawncraft]].
