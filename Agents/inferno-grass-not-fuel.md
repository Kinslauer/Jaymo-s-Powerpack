---
status: running
task: forest fire burning grass blocks
branch: inferno-grass-not-fuel
with: Claude Code (PC side session)
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2026
mods: powers
versions: 
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/core/InfernoPreset.java
  - changes/powers/inferno-grass-not-fuel.md
---
# Fire burns grass blocks

**Task:** Andy: "it seems the forest fire mod is burning grass blocks?" Find which fire code does it and stop it.

## Notes
- Peterwolf's Forest Fire (jaymo.3, on the PC and the Deck) is not it: no FireBlockMixin since #1926, so its
  grass-to-dirt only runs in command-started incidents.
- Inferno registers grass_block and podzol in Fabric's FlammableBlockRegistry (60, 100) always. With scorchedEarth off
  its mixin zeroes the odds with @ModifyReturnValue at RETURN of FireBlock.getIgniteOdds/getBurnOdds, but Fabric API's
  own mixin returns early from those methods for any registered block, so grass keeps burning and checkBurnOut removes it.
- Fix: when Inferno is loaded and its scorched earth is off, set the two registry entries to 0, 0 at server start.

## Done
PR #2026 is open with Auto-fix on; CI was still building when the session ended. Once it merges and the server
restarts, fire on a grass lawn burns the plants on top and goes out, and grass blocks and podzol stay. Not tested in
game.
