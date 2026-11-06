---
name: villager-days
description: Kingdoms 0.6.9 / pack 0.9.20 (PR #125 MERGED 2026-10-13 20:48Z): real tag, toddlers (Families), 19 daily Routines incl. 0.6.10 batch (chores, lunch, dance, hide and seek...), Trespass
metadata:
  type: project
---
2026-10-13 Andy: a child's tag "didn't really do it well", no toddlers seen, villagers should follow more routines and grow suspicious of you in their home.
PR #125 (branch claude/villager-routines-6xmxs5) has new classes `Tag`, `Families` (+McaBridge.families), `Routines` and `Trespass`, plus Theft.Crime.TRESPASS. Cause of no toddlers: MCA's VillageProcreationManager only lets couples conceive below the population threshold, so full villages never do. MCA babies are born as TODDLER age.
Jars are in /mnt/project-files/villager-days/. Merged on Andy's "Merge" 2026-10-13 20:48Z, unplaytested.
**How to apply:** take the next free version before merging. Routines skip workers, guards, Tag players and Trespass watchers, so add any new "busy" state to Routines.free.
