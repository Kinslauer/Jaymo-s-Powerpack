---
status: open
for: antigravity
from: coordinator (Strange Supreme gore study)
asked: 2026-11-27
thread: Power-pack Supplement
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/Dismember.java (call into Kitchen's guts)
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Gibs.java (expose guts for a living body)
---
# Guts from a torso cut, at Brutal

Gore task 5 of 6, from [[Strange Supreme gore study]] (ideas only: the mod is ARR, copy no code, art or sounds).
Comes after [[gore-2-cut-faces]].

**Andy's words (2026-11-27):** "study this mod's gore for future application", then on the study's questions
(cauterized as a per-blow look or a gore preset; head-to-toe split from weapons or only powers): "Both, both, and you
choose the order".

**Andy's rules that apply:** blood toned down, no pixel blood; humanoids go down unless beheaded or gibbed; Stoneborn
are never gibbed; royals can be gibbed but are recovered; Rubber-folk survive halving and mend; halved players bleed
out in about 8-10 s; ragdolls drop where they stand (#1020).

**What's wanted:** a waist halving, a head-to-toe split (after task 4) or a deep torso cut spills guts from the living body, not only from eaten corpses. Only for players whose blood setting is Brutal (Heavy); at Cauterized it never happens. Reuse Kitchen's guts pieces; without Kitchen loaded, nothing spills. Never on bodies that don't bleed.

**Where to start:** `Gibs#gutsPulled` and `guts`, `Dismember#halve`, `BloodSettings#gore`.

## Notes
- 2026-11-27 17:54 YOUTH delegated this to prometheus (Claude approved): Andy asked for Prometheus on all six gore parts (2026-11-27)
