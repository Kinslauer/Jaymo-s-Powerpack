---
status: in-review
task: player-corpse-loot-ash-piles
branch: claude/player-corpse-loot-ash-piles-b519e2
with: Claude Code (PC side session of local_a2db0385)
pr: 2032
mods: kitchen, kingdoms, powers
versions:
started: 2026-11-23
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/ (CorpseEntity, CorpseLoot, CorpseLootMenu, PlayerCorpses, ZombiePlayer, Hanging, AshPileBlock)
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/CorpseLootScreen.java
  - kitchen/src/main/java/com/jaymoskitchen/mixin/PlayerMixin.java
  - kitchen/tools/gen_ash_piles.py, kitchen ash_pile blockstate and ash_mound models
  - kingdoms Harness.java, HiddenBlade.java, KitchenCompat.java (keepOnBody)
  - src/main/java/com/jaymospowers/baubles/Baubles.java, power/web/WebShooters.java, power/web/KitchenCompat.java
  - changes/<kitchen|kingdoms|powers>/player-corpse-loot-ash-piles.md
---
# Player corpses keep their loot; fire on dirt; ash piles

**Task:** players' bodies keep everything (loot screen on right click, bursts only when gibbed; limb armour shed with the
limb); fire still burning dirt; ash piles mounded, punch to dust, more for bigger bodies.

## Notes
- Fire on dirt: the Deck runs Powers 0.26.62; the grass-fuel fix (#2026) is in 0.26.63. Nothing else in the pack burns
  ground (Inferno only grass/podzol; Forest Fire jaymo.3 has no FireBlockMixin; Calamities wildfire only places fire).
- The clips named in the brief (06-56-00, 07-03-47) aren't on disk; 07-23-50 (Limbo) and 07-32-32 (badlands walk) show
  no fire or ash.
- Player items live in CorpseEntity's `hotbar` list (now 41 inventory + 24 handed-over slots), so rebirth/Limbo
  (takeHotbar), Powers' symbiote spill (field by name), zombies and gibs all carry the whole kit.

## Done
- PR #2032 (Auto-fix on). Built by CI only; nothing seen in game.
