---
name: mastery-pacing
description: Power ability mastery pacing (3 days/ability, 1 day floor, field half day), Gravity owner sparring, shadowbox combos; PR #253 (pack 0.11.65)
metadata:
  type: project
  modified: 2026-10-15T15:16:13.767Z
---
2026-10-15 Andy: mastery slower: up to 3 in-game days per ability, use/meditation down to 1 day per skill, Gravity's Immense Field boosts everyone except its owner, who shadowboxes too and must spar in his field; hip-tip kick + side kick alternating, 6 more combos unlocking with abilities.
PR #253 MERGED 2026-10-15 (pack 0.11.67; #248 merged as 0.11.65), not playtested. Mastery: 60 pts/ability, 1 time pt/min, practice capped at 2 per time pt (bank 6); field = practice x2 + extra allowance (half a day). 15:21Z Andy: owner earns mastery ONLY shadowboxing next to another shadowboxing user (to make Z Fighters train with Gravity users). Built as: Gravity user shadowboxing pays practice only with a shadowboxer within 5 blocks (Meditate.partnered); in his own field that also gives the field boost. Time and ability use still count (needed to unlock the field at all).
Reading I chose: "gain a power" = each ability unlock. Combos in core/Shadowbox (1,2,4,6 at 1-4 abilities); pose MeditatePose (hip tip via quaternion around model y=12).
