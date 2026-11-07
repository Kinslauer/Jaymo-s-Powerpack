---
name: darktide-wind-fixes
description: Andy's 2026-10-13 rulings on Darktide shadows/dash and Wind fire + carried-player drops + dismemberment (PR #122, pack 0.9.27 / Kitchen 0.4.37)
metadata:
  type: project
  modified: 2026-10-13T19:55:54.770Z
---
PR #122 MERGED 2026-10-13 21:22Z ( pack 0.9.27, Kitchen 0.4.37, master incl. disasters merged in). Andy's rulings:
- Darktide shade leaves no corpse when it dies (Kitchen skips bodies tagged `jaymoskitchen.no_corpse`).
- Risen shadow summons never TARGET their master, but their arrows/explosions/etc. CAN still hurt the master (no damage guard).
- Up to 15 stored shadows (MAX_SHADOWS).
- Riptide Dash: landing it = no cooldown; missing = 5 s; activating a flurry = 1 minute.
- Every Wind ability puts out fires (`WindDouse`); Stormbound lightning still ignites.
- Wind carrying a player in flight can drop them, chance falls with mastery, NEVER once fully mastered.
- Dismemberment (core/Dismember): katana G slash is aimed, 20% sever on humanoid mobs; other blades 5% on humanoid mobs and all players. Headless zombies/skeletons fight on (skeleton arrows wild), other mobs die or stagger; beheaded players watch from their head. Kitchen corpses keep lost parts via `jaymoskitchen.missing.<part>` tags.
- Charged punch 2-10 dmg, 0.9 radius; daggers follow hands in R overhead hold.
- 2026-10-14: each Riptide Dash/flurry spends a Combat Roll charge, landed flurry refunds it; cooldowns kept. PR #130 MERGED 2026-10-14 01:20Z (pack 0.10.1); Andy: keep miss/flurry cooldowns too.
Related: [[darktide-resonance]], [[darktide-shade]].
