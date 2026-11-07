---
name: combat-animations
description: Own Better Combat animations + combos (quarterstaff twirls), PR #107 merged as pack 0.8.116; how the animation JSON axes really work
metadata:
  type: project
  modified: 2026-10-13T06:55:33.007Z
---
2026-10-13: Andy asked for more/better Better Combat animations, "i wanna spin the quarterstaff". merged PR #107 as pack 0.8.116: tools/combat_animations.py writes 5 player animations (staff_twirl_front, staff_whirlwind, staff_twirl_overhead, polearm_whirl, dagger_flip) and combo_* weapon_attributes presets that Armory weapons parent. Quarterstaffs are held by the middle (STAFF_DISPLAY in armory_assets.py). Test jar: /mnt/project-files/combat-animations/. Not seen in game yet. 2026-10-13 follow-up PR #115 merged as pack 0.9.9: vanilla swords/axes (data/minecraft/weapon_attributes, OTHER_WEAPONS in the script) get combo_sword/combo_axe with sword_twirl_slash, sword_whirl, axe_flip_chop; Sora's Daggers use combo_dagger.

**Hard-won:** on `rightItem`, legacy "roll" spins the item round the forearm, "yaw" only turns it about its own length (PAL swaps Z/Y), "pitch" flips it end over end. A frame's easing applies to the stretch after it. Spins must end on whole turns and snap to 0 (a 0.05-tick keyframe), else they unwind on return. Head and arms follow torso. Check animations by loading them with PAL's PlayerAnimatorLoader.GSON in a tiny Java harness (classpath: PAL jar + its nested jars + gson/fastutil/joml/slf4j from the gradle cache).
