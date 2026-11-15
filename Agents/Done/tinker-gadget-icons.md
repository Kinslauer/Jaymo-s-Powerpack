---
status: merged
task: tinker-gadget-icons
branch: claude/tinker-gadget-icons
with: 
pr: 1611
mods: kingdoms
versions: 
started: 2026-11-15
owns:
  - kingdoms/src/main/resources/assets/jaymoskingdoms/textures/item/ (the 19 mimic gadget PNGs only: lift_rig, gust_bellows, grapple_spinner, rewind_watch, frost_projector, smother_pump, sonic_horn, parry_gauntlet, chain_hook, growth_band, ki_blaster, blink_coil, climbing_claws, flame_spitter, bellows_cannon, beam_lens, jaw_rake, hissing_charge, potion_sling)
  - tools/tinker_gadget_icons.py
---
# Tinker gadget icons

**Task:** Redraw the Tinkerer's 19 mimic gadget icons by hand in the vanilla style (Inbox task tinker-gadget-icons). Andy handed it to Claude (cloud session) on 2026-11-15: "Can you take this over?"

## Plan
- Paint each icon as a 16x16 pixel grid (brass, copper, iron, one hint of what it copies), replace the PNGs in place.
- Render all 19 side by side at hotbar size and check each reads apart.

## Notes
- Change note: changes/kingdoms/tinker-gadget-icons.md. tools/gen_tinker_mimic.py left alone.

## Done
A player sees 19 distinct hand-drawn gadget icons in the hotbar and the Tinkerer's Notebook instead of the flat placeholders. Checked side by side at hotbar size on light and dark slots; not seen in game.
