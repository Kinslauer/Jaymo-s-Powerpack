---
name: no-rainbow-hair
description: Andy turned off MCA rainbow hair and random dyed hair (PR #68 merged, Kingdoms 0.5.43 / pack 0.8.84)
metadata:
  type: project
---
2026-10-12 Andy: "turn off rainbow hair". Done in Kingdoms code, not MCA config: McaTraitsMixin hides Traits.RAINBOW (hasTrait false, dropped from getTraits), McaConfigMixin forces coloredHairChance=0. Rainbow Eyes left on. Andy also had MCA's Homosexual trait hidden the same way (same mixin, same PR). PR #68 merged 2026-10-12 as Kingdoms 0.5.43 / pack 0.8.84. See [[mca-in-pack]].
