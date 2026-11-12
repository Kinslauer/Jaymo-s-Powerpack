---
status: running
task: Build Andy's 100 picks from the Gamma and Thematic picklist, big suit first
branch: claude/project-thread-akompj
with: Claude (cloud thread "Pick Gamma and Thematic ideas for powers")
pr: 
mods: powers
versions: 
started: 2026-11-12
owns:
  - src/main/java/com/jaymospowers/power/web/WebFury.java
  - src/main/java/com/jaymospowers/power/web/WebBrute.java
  - src/main/resources/assets/jaymospowers/lang/parts/web_brute.json
---
# Gamma and Thematic picks

**Task:** Andy picked 100 of 105 rows on the Gamma and Thematic picklist (https://claude.ai/artifact/WxBbmV7ogPkWqFA6aKvTsW). Build them as a stack of PRs: the symbiote's big suit, then aliens, Z Fighter and elemental powers, Rig and gear, Kingdoms and the world.

## Plan
- PR 1: big suit (21 rows).

## Notes
- Both mods are All Rights Reserved: ideas only.
- The page's Save never reached the db; picks known from Andy's screenshots only.

## Done

- 2026-11-12: PR 1 (big suit, 21 rows) is up as #1346 on claude/project-thread-akompj, compile-checked only. Next: aliens (Xenoform rows except xen_size) once #1346 merges, restarting the branch from master.
