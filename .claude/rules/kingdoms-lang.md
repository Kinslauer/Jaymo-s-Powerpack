---
paths:
  - "kingdoms/src/main/resources/assets/jaymoskingdoms/lang/**"
  - "tools/lang_split.py"
---
# Kingdoms lang parts

Kingdoms lang is split into `kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/*.json`, merged at build
(there is no source `en_us.json`). Put a new feature's keys in its own part. A branch that still has the old
single file: on the merge conflict keep its en_us.json, then run `python3 tools/lang_split.py` (moves its keys into parts).
