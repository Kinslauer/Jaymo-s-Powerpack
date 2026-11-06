---
name: skin-tones-climate
description: Villager skin tone by climate (merged PR #64 2026-10-12 23:07Z, Kingdoms 0.5.38 / pack 0.8.77): MCA melanin re-rolled by biome heat
metadata:
  type: project
  modified: 2026-10-12T22:11:40.058Z
---

2026-10-12: Andy asked for darker villagers in hot/desert climates, paler in cold. MCA (our 26.3 jar) already leans
melanin on Biome.getBaseTemperature in Genetics.randomize, but 20% "immigrants" ignore climate. PR #64 adds
`SkinTones` + `mixin/mca/McaGeneticsMixin` (TAIL of randomize) with a firmer lean. Andy (22:53Z): keep MCA's far immigrants (20%) AND
nearby newcomers (15%); they store their homeland (`jaymoskingdoms:origin` attachment) and mention it on Chat 30% of the
time (`McaDialogueMixin` on Dialogues.selectAnswer, chat.topic). MCA dialogue lang args: %1$s player, %2$s ours. Births/conversions skip randomize, so children inherit. Kingdoms folk already had climate faces
(`Climate.skinGroup`: temperate/sunny/cold textures). Barbarians/chiefs have no climate skins.

**How to apply:** tune constants at the top of `SkinTones`. See [[mca-in-pack]].
