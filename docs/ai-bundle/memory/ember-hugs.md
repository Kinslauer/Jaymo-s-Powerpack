---
name: ember-hugs
description: Emberborn too hot to hug without fire resistance (EmberHugs), PR #337 MERGED 2026-10-20 23:5xZ as pack 0.14.4 / Kingdoms 0.7.102
metadata:
  type: project
  modified: 2026-10-20T19:12:46.860Z
---
2026-10-20 Andy: "hugging emberborn villagers and players is impossible without fire resistance."
Reading: failed hug = burned (3 s fire, 2 dmg, knockback) and pull away. Safe = Emberborn, fireImmune, or Fire Resistance.
Kingdoms `EmberHugs` (burned/burn helpers), hooked into McaDialogueMixin main->hug (both directions: Emberborn player burns
villager) and Neighbours HUG/AFFECTION (75% switch to CHAT; else burn, meeting ends, no regard/mood). No player-player hug
exists in the pack; kisses untouched. PR #337 (branch claude/project-thread-6tfe2q), unplaytested. Jar /mnt/project-files/ember-hugs/.
Related: [[emberborn-fire-cold]], [[villager-neighbours]].
