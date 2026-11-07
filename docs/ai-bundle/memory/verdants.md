---
name: verdants
description: Verdants (Z Fighter Verdant lineage + Verdant World villagers) have antennae and green skin; PR #179 (pack 0.10.78 / Kingdoms 0.7.23, MERGED 2026-10-14 21:2xZ)
metadata:
  type: project
---
2026-10-14 Andy: "verdant world villagers should all be verdants, and all verdants should have antennae and some shade of green skin."
PR #179: Kingdoms `Verdants` marks any Villager loading in jaymospowers:verdant_world with a synced float shade (stays Verdant if it leaves); `McaVerdantSkinMixin` (MCA SkinLayer.getColor) + `client/VerdantLook` antennae. Powers `ZFighterVerdantLook`: Verdant-lineage players get antennae and their skin's warm pixels repainted green (UUID shade), hand too.
Antennae: 7 shapes (SHAPES table, picked by UUID), added on Andy's "add several antennae variants". Tune greens/thresholds in the constants at the top of those classes. See [[zfighter]], [[skin-tones-climate]].
2026-10-14: PR #190 MERGED (pack 0.10.93 / Kingdoms 0.7.27): Verdant World off MCA's destiny (start-up village pick) screen via McaDestinyMixin on DestinyLocationResolver.isDimensionAllowed. To leave the Verdant World: right-click the Verdant Gate by the arrival spot.
