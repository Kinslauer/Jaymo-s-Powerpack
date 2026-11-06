---
name: nightfall-plague
description: Nightfall Plague (ARR Forge mod) remade in Kingdoms nightfall/ package; PR #369 MERGED 2026-10-21 09:50Z (Kingdoms 0.7.129 / pack 0.15.37)
metadata:
  type: project
---
2026-10-21 Andy: "i need this added to our vampires" (Nightfall Plague, CF 1462657, ARR, Forge/NeoForge only). Remade as our own in kingdoms/src/main/java/com/jaymoskingdoms/nightfall/: Atrophy + Immunity effects, data-driven brewing (26.3 brewing is recipe JSON), Night Bleeder (AbstractIllager) / Blightwing / VampireBat (extend Bat), Callings on our vampires (block hand-in, emerald trades, raw beef = Vampires.feedBeef), Relics items, Nightmares + Nightfall event (Nightfalls: clock skips dawn->dusk via clockManager().setTotalTicks; miasma fog MiasmaFogMixin). Art/data: kingdoms/tools/gen_nightfall.py. Test jar /mnt/project-files/nightfall/.
Kept ours on two clashes (told Andy, no answer yet): Immunity doesn't cure turned vampires (priest quest stays); plague-dead villagers rise as bats, not vampires.
PR #369 MERGED 2026-10-21, not playtested. Next free: Kingdoms 0.7.130 / pack 0.15.38. Related: [[vampires-werewolves]], [[vampires-strike-back]].
