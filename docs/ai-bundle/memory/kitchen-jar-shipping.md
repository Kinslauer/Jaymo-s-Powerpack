---
name: kitchen-jar-shipping
description: Which Kitchen jar the pack ships; libs/ held a stale 0.5.57 that silently dropped Kitchen fixes (heaven zombies)
metadata:
  type: project
  modified: 2026-11-05T08:07:09.696Z
---
Until PR #991 (pack 0.20.80, 2026-11-05) every pack task (modpackMods, curseforgePack, serverMods, profileMods) shipped `libs/jaymos-kitchen-*.jar`, committed at 0.5.57 while kitchen/ source was 0.5.68. Kitchen fixes after 0.5.57 only reached a build when the jar was swapped by hand (pbdr step 2). Andy's "zombie players still reanimate in heaven" came from this: Kitchen 0.5.61's Other World no-bodies rule was missing, and Powers' reflection registration failed with no log line.
**Why:** a Kitchen fix that "doesn't work" in play may simply not be in the jar that was shipped.
**How to apply:** since #991 the pack tasks run `buildKitchen` and ship kitchen/build/libs (E:/jaymos-kitchen-build/libs on Andy's PC) at the source version. libs/ is now only compiled against and used in the dev game. If a Kitchen fix seems missing in play, first check which Kitchen version the server log loaded. Related: [[cloud-build]].
