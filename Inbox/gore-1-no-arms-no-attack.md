---
status: claimed
for: antigravity
from: coordinator (Strange Supreme gore study)
asked: 2026-11-27
thread: t3 mcp:d1eafde0-5f2c-40dd-a1a7-566531ded9c2
claimed_by: t3
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/Dismember.java (a small hook, or a new mixin)
---
# A body with no arms can't attack

Gore task 1 of 6, from [[Strange Supreme gore study]] (ideas only: the mod is ARR, copy no code, art or sounds).
First of the six: small and stands alone.

**Andy's words (2026-11-27):** "study this mod's gore for future application", then on the study's questions
(cauterized as a per-blow look or a gore preset; head-to-toe split from weapons or only powers): "Both, both, and you
choose the order".

**Andy's rules that apply:** blood toned down, no pixel blood; humanoids go down unless beheaded or gibbed; Stoneborn
are never gibbed; royals can be gibbed but are recovered; Rubber-folk survive halving and mend; halved players bleed
out in about 8-10 s; ragdolls drop where they stand (#1020).

**What's wanted:** a mob or NPC that has lost both arms can't make a hand or weapon attack any more. First check whether it already can't (the coordinator didn't find a rule for it in `Dismember`; say what you found). If it still attacks, stop it. Biters (zombies) may still bite and anyone with legs may still kick, at low damage. One arm left: attacks as now.

**Where to start:** `Dismember` (`missing`, the arm bits), `Wounds`/`PartStrain` (the 20% arm rules), the mob's melee goal.

## Notes
- 2026-11-27 17:34 YOUTH delegated this to prometheus (Claude approved): Andy asked for Prometheus on gore 1-4 (via Claude coordinator, 2026-11-27)
- 2026-11-27 18:43 Andy (2026-11-27): 'Use t3'. Moved from Prometheus to Antigravity (Gemini) in T3 Code; Codex is at its usage limit until Dec 1. Prometheus's finding: Dismember.java has no rule stopping a mob with both arms gone from attacking, so the fix is still needed.
