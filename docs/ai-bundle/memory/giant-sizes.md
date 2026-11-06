---
name: giant-sizes
description: Giants sized to the Z Fighter Great Ape (30.6 x 10.2) and smash any tree/building in their way, PR #400 (Kingdoms 0.7.152 / pack 0.15.68), 2026-10-22
metadata:
  type: project
  modified: 2026-10-22T01:43:02.624Z
---
2026-10-22 Andy: "make all the giants the same size as warrior born great apes". Great Ape = player 1.8 x 17 = 30.6 blocks. Giants in the pack = the four Aether Giants (Kingdoms remnants/AetherGiant, HEIGHT/WIDTH constants) + vanilla monster.Giant (scale modifier in Monoliths.sizeVanillaGiant). Ogres and Aeolus are not giants.
Andy ruling: "it should knock down any tree, or building. dont get in the way": giants fell natural trees and smash everything breakable they walk into (mobGriefing on), only their own monolith spared.
26.3 note: EntityType.GIANT doesn't exist; use `instanceof net.minecraft.world.entity.monster.Giant`.
PR #400 MERGED 2026-10-22 01:44Z (Kingdoms 0.7.152 / pack 0.15.68), not playtested. Related: [[zfighter]], [[wild-great-ape]].
2026-10-22 03:12Z Andy: "the giants need to be rarer, and they shouldnt activate until after borderlands world tier, the monument will only hum." PR #409 (Kingdoms 0.7.159 / pack 0.15.79): RAISE_CHANCE 0.02, SPACING 2400, Giant rises only for a player at Wildlands+ (Andy: "do wildlands"); otherwise monolith hums. MERGED 03:21Z. The first-Giant-slain reward (raise floor to Borderlands) is now moot.
2026-10-24: Andy pasted a Gemini-made Giant model (no words); taken as "use it". PR #470 (Kingdoms 0.7.182 / pack 0.15.145): titan model ported to Mojang names, texture relaid 256x128 (Gemini UVs overlapped), shards dropped, death sprawl instead of flip. MERGED 2026-10-24 01:02Z. Gemini code Andy pastes uses Yarn names; always port and check UV overlap.
2026-10-24 01:40Z Andy: "these new giants are AWFUL, keep our old models, and the new animations and the shatter". PR #474 MERGED 2026-10-24 22:15Z as pack 0.15.156 / Kingdoms 0.7.190: pre-#470 model/textures/gen_giants.py restored (128x128, crystals), titan walk + flinch kept, two-part death (face-plant fall, then pieces fly out and bounce; server block-particle burst + crash at deathTime 10). No Gemini gear/weapons. Don't bring the Gemini titan back.
