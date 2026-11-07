---
name: war-alert
description: Kingdoms 0.7.65 WarAlert (PR #261): whole realm on alert in a war: bells, sandbags block, civilians hide, guards at sandbags
metadata:
  type: project
---
2026-10-15 Andy: "the ENTIRE kingdom should be on alert during a war, ringing bells, putting up sandbags, and civilians hiding before the raid party arrives."
PR #261 MERGED 2026-10-15 16:1xZ (pack 0.11.73 / Kingdoms 0.7.65), not playtested. `WarAlert`: alert from declaration (warmup included) to peace for every faction whose Wars.top is in a war;
bell peal then toll every 2 min; alarm every 2 s (real bell swung) while `WarParties.incoming` != null; villagers carry
sandbags (5x5 front walls toward nearest enemy seat, 3x3 back), saved in level attachment `war_sandbags`, taken down at peace;
civilians hide via `Frights.shelter`; idle guards/archers stand behind front bags. New `Sandbags` block (raised=true drops nothing).
See [[kingdoms-war-tempers]].
