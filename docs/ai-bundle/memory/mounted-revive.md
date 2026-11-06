---
name: mounted-revive
description: PR #814 MERGED 2026-10-31 22:5xZ (Kingdoms 0.7.453 / pack 0.19.7): mounted guards dismount 4 blocks from a downed one, tend on foot, remount after
metadata:
  type: project
---
2026-10-31 Andy: "mounted guards should dismount to revive, right now they just walk into you perpetually".
PR #814 merged (branch claude/project-thread-0caiuy), built in the cloud, not playtested. Kingdoms Downed: riders (Cavalry.mounted) may help; stopRiding at DISMOUNT_REACH 4, horse waits (Downed.waiting, kept from Cavalry spare/lent-poof and caravan remount), remount on untend within REMOUNT_REACH 10; untend clears a WALK_TARGET still aimed at the body (likely cause: helper mounted by Cavalry mid-walk, then dropped with walk target left on you).
Related: [[downed-guards]], [[kingdoms-stables]].
