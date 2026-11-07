---
name: weapon-executions
description: Basic weapon executions for everyone (Wild Slam / Stabbed Through) and neck snap only at the head; PR #472 (pack 0.15.146 / Kingdoms 0.7.183)
metadata:
  type: project
  modified: 2026-10-24T01:14:04.116Z
---
2026-10-24 Andy: basic execution with weapons for all players, faster than the bare-hand neck snap: wild slam with anything blunt (bows, fishing rods and other tools heavily damaged), blades stab through then struggle to pull out, axes slam; neck snap only when near the head of a humanoid.
PR #472 MERGED 2026-10-24 01:16Z (branch claude/project-thread-1lf1sr), built in the cloud, not playtested. In core/finisher/Finishers: Kind.SLAM (kills at tick 28) and Kind.IMPALE (kills at 12, then tugs and pulls free at 40, using the `after` beats). Without the Executioner perk every weapon plays basic(); with it the unique finishers stay, except an unloaded bow or crossbow slams. Improvised tools lose 12% durability a blow (IMPROVISED_WEAR). Neck snap: Finishers.atHead/headAt, HEAD_REACH 1.25; a downed crawler's head is ahead, one on its back has it behind. Test jar: /mnt/project-files/weapon-executions/.
Related: [[downed-guards]], [[accurate-hitboxes]], [[combat-arts]].
