---
name: otectus-jewellery-locks
description: Otectus patches 4 (jewellery, /home/user/jewel) and 6 (locks, /home/user/locks) built by workflow; versions and push plan
metadata:
  type: project
---
23:50Z Andy "continue": patches 4 jewellery (/home/user/jewel, branch jewellery-work, Powers 0.19.82) and 6 locks (/home/user/locks, branch locks-work, Kingdoms 0.7.492) building via workflow wf_90a18aee-59a from origin/master eb3ef5b6. Planned local versions: fibre 0.19.80/0.7.489, coins 0.19.81/0.7.490/Kitchen 0.5.55 (master Kitchen is 0.5.54). Push order waits on #872.
See [[otectus-mods-fit]], [[coins-replace-emeralds]].
00:27Z #887 (bug sweep) takes Powers 0.19.80 / Kingdoms 0.7.489; renumber fibre/coins/jewellery/locks after it. Worktree base eb3ef5b6 still compiles (pre-#885).
01:12Z both built, reviewed (2 fix rounds), merged master c15635c1, booted Done, committed: jewel 9698871a (Powers 0.19.84 / Kingdoms 0.7.493), locks 7d7625b5 (Kingdoms 0.7.494 / Powers 0.19.85). Coordinator reserved Kingdoms 0.7.493 for coins and 0.7.494+ for Tidings roost thread, so renumber all at push. Open questions to Andy: key ring and coin purse share the belt slot; locked loot chests now slow-break instead of unbreakable. Smith jewellery orders pay emeralds (coins merge must convert). Decision card: push coins/jewellery/locks as own PRs now vs wait for #872.
01:40Z PRs open: #893 jewellery (branch claude/otectus-jewellery, Powers 0.19.85 / Kingdoms 0.7.495), #894 locks (claude/otectus-locks, Powers 0.19.86 / Kingdoms 0.7.496). Subscribed. Andy approved own branches for these ("Yes, three PRs now").
- 2026-11-04 07:35Z: both merged. #893 as Powers 0.19.87 / Kingdoms 0.7.496 (hints 82-83); #894 as Powers 0.19.89 / Kingdoms 0.7.497 (hints 84-85; 0.19.88 went to another thread). Open questions to Andy still: key ring vs purse sharing the belt slot; locked loot chests slow to break, not unbreakable.
- 2026-11-04 07:35Z Andy: "ok for both" = keep key ring and coin purse sharing the belt slot; locked loot chests stay slow to break (4-20x), not unbreakable. Settled.
