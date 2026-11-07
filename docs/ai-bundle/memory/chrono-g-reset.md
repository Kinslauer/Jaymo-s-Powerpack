---
name: chrono-g-reset
description: Chrono G (Stasis Field) also resets all other cooldowns + Overclock water run fix, PR #188 MERGED (pack 0.10.96), 2026-10-14
metadata:
  type: project
---
2026-10-14 Andy: "chronokinesis G should reset all your current cooldowns as well." PR #188 MERGED 22:53Z as pack 0.10.96. Next free: 0.10.97. `ChronoPower.resetCooldowns` clears R/V/B, V phase, Chrono Punch and inventory item cooldowns when G succeeds; G's own cooldown is then set, so it can't loop. Kingdoms combat arts (Y, Shield Throw, sorcery) are NOT reset (other mod, no Powers dependency); add only if Andy asks.
Same PR fixed Overclock's water run (always sank since 0.10.28): in 26.3 a LivingEntity stands on fluid only if canStandOnFluid AND getLiquidCollisionShape (empty except Strider) returns a shape; ChronoWaterRunMixin now returns Shapes.block() while overclocked+sprinting. Not playtested. Jar: /mnt/project-files/chrono-reset/.
