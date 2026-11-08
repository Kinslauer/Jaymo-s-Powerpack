---
name: jei-cheat-mode
description: Why JEI items couldn't be pulled in creative, and PR #87 (pack 0.8.102) that auto-enables JEI cheat mode in creative
metadata:
  type: project
  modified: 2026-10-13T03:43:39.293Z
---

2026-10-13: Andy asked why JEI items can't be taken in creative. Cause: JEI 31.9 cheat mode starts off every session
(never saved to config), its "Toggle Cheat Mode" key is unbound; only default switch is Ctrl + left-click on JEI's
wrench button. Server defaults already allow creative + ops. Fix: `client/JeiCheats` flips JEI's internal toggle by
reflection on entering/leaving creative. PR #87, pack 0.8.102 (0.8.100 web-whip, 0.8.101 skill-tree were taken).
Merged 2026-10-13 on top of master 0.8.101 (web airbrake); skill tree must re-version. Jar at /mnt/project-files/jei-cheats/.
