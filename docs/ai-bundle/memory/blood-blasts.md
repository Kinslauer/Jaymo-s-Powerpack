---
name: blood-blasts
description: Blood Sorcerer hold-J gather into reservoir + empty-hand blood blast barrage; PR #975 (Kingdoms 0.7.554, hint 99), merged 2026-11-05 (6430097c)
metadata:
  type: project
  modified: 2026-11-05T02:01:26.924Z
---
Andy 2026-11-05: Blood Sorcerers hold J near blood to gather into a reservoir; empty-handed hold J fires it like Z Fighter ki blasts.
PR #975 (branch claude/project-thread-mdlcj6), Kingdoms 0.7.554, hint 99 (lang/parts/blood-blasts.json). Built, not played.
Design: Blood Sorcerer J sent as down/up (`BloodBlasts.Hold`); tap <6 ticks = Imbue on release; sneak+J old keys. Hold = gather (any hand); empty hand also barrage after 12-tick windup, 1 reservoir per blast, never health. Reservoir now synced attachment `Hemomancy.RESERVOIR`, HUD `client/BloodReservoirHud` right of hotbar (Brute's spot).
Andy 2026-11-05: J only collects when looking at a blood source (bleeding foe in sight, stain/grate at the looked-at spot); a gathering hold never fires. Mode is settled at hold start (tick 6).
Related: [[blood-magic-stains]], [[kingdoms-sorcery]].
