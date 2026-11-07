---
name: golems-quiet-firefighters
description: Kingdoms 0.5.42 (PR #69 merged, pack 0.8.83): iron golems never speak and beat out settlement fires
metadata:
  type: project
  modified: 2026-10-12T23:23:09.283Z
---
2026-10-12 Andy: "iron golems should not be able speak and they should beat the fires out, instead of watching them burn".
MERGED 2026-10-12 23:40Z as PR #69, Kingdoms 0.5.42 / pack 0.8.83 (branch claude/project-thread-qbk3qk): `Speech.say` ignores IronGolem speakers; Theft picks a non-golem witness
to cry out; `Firefighting` sends golems to fires and beats them out with EntityEvent.START_ATTACKING (no water).
Golems' only speech had been the Theft witness cry. Test jar /mnt/project-files/golems/. See [[kingdoms-arson]], [[villager-speech]].
