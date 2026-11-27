---
status: running
task: hunters-road-3-models
branch: claude/hunters-road-3-models
with: 
pr: 
mods: jaymoskingdoms
versions: Kingdoms 0.14.11
started: 2026-11-27
owns:
  - kingdoms/art/grave_gnawer.bbmodel
  - kingdoms/art/bloated_dead.bbmodel
  - kingdoms/art/grave_brute.bbmodel
  - kingdoms/art/bone_burrower.bbmodel
  - kingdoms/src/main/resources/assets/jaymoskingdoms/geckolib/models/entity/*.{geo.json,animation.json}
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/*.png
  - kingdoms/tools/gen_hunters_road_models.py
  - changes/kingdoms/hunters-road-3-models.md
---
# Hunter's Road 3 models: corpse-eaters

**Task:** Make Blockbench models, textures and animations for every new creature in Hunter's Road plan section 3 (Corpse-eaters, rows B1 B5 B6 B7): grave-gnawer, bloated dead, grave-brute, bone-burrower — GeckoLib 5 assets for Kingdoms, ready when patch 3's code lands.

## Plan
- Paint 4 vanilla-style entity textures (64x64) with discrete shade ramps, light from top-left, no per-pixel noise
- Build free-format .bbmodels in kingdoms/art/ (per-face UVs, model faces north, feet at y=0)
- Animate each: idle (loop), walk (loop), plus lunge/leap/burrow/charge (once)
- Convert with tools/bbmodel_geckolib.py -> geckolib/models/entity, geckolib/animations/entity, textures/entity
- Render previews, vision_analyze each, fix until clean; texture_lint + art_audit
- sh ./gradlew clean build, tools/pr_checks.py, change note, PR

## Notes
- Variants from the plan (spined pack leader, spined bloated dead, heavy bone-burrower) are geometry/palette swaps on these bases - noted here, not modelled in this pass unless time allows.
- No gameplay Java: modellers don't write entity, renderer or registration code (that's hunters-road-3's code task).
- Licence: our own vanilla-style designs; nothing copied from The Conjunction of the Spheres.

## Done
What a player will see, and what wasn't tested.
