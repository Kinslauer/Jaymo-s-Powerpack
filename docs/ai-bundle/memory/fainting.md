---
name: fainting
description: Passing out from hunger/thirst + villager wakers (PR #1028 MERGED 2026-11-05 ~16:55Z, Powers 0.20.112 / Kingdoms 0.7.594 / hint 118): my readings of Andy's ask
metadata:
  type: project
  modified: 2026-11-05T15:58:03.481Z
---
2026-11-05 Andy: pass out at low hunger/thirst; "fov closing" as it drops; letterbox shuts, lose control mid-walk, fall on face, ragdoll; view stays in head.
PR #1028 (branch claude/project-thread-qcpcrt), core/wounds/Fainting + client/wounds/FaintHud + FaintInputMixin. Built, not played. Readings I chose:
- "FOV closing" = a dark tunnel from the edges (no FOV modifier), from 6 food or water, deepest at 0.
- Faints can come at 3 or less: 1/240 to 1/40 a second, x2 sprinting/swimming. 4 s swoon (letterbox shuts, keys held as they were, slowing and weaving, head droops), face-first tip via LivingRagdolls.knock, held limp 13 s via KitchenCompat ragdoll hold, a blow wakes, 3 s wake, 60 s cooldown.
- Skipped while downed/carried/Chrono window/left for dead/adrenaline/riding/asleep; counted in OutOfPlay (villagers stay away).
Related: [[adrenaline]], [[thirst-temperature-weathering]].
Follow-up same PR (Powers 0.20.112 / Kingdoms 0.7.594): Kingdoms FaintWakers. Andy: villagers near you wake you and hint, tell + give a drink, or steal. My readings: nearest rememberer within 16 in sight; by temperament; tell gives Kitchen clean water +6 (food +4 if hunger); thieves (villagers only) do NOT wake you, take one stack into their inventory (pickpocketable). Bubbles only.
