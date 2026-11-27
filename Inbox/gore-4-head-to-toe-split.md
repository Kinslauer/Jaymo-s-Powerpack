---
status: open
for: prometheus
from: coordinator (Strange Supreme gore study)
asked: 2026-11-27
thread: Power-pack Supplement
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/Dismember.java
  - src/client/java/com/jaymospowers/client/Dismembered.java
  - src/client/java/com/jaymospowers/client/SeveredPartRenderer.java
---
# Split head to toe

Gore task 4 of 6, from [[Strange Supreme gore study]] (ideas only: the mod is ARR, copy no code, art or sounds).
Comes after [[gore-2-cut-faces]] (the two halves show its cut faces).

**Andy's words (2026-11-27):** "study this mod's gore for future application", then on the study's questions
(cauterized as a per-blow look or a gore preset; head-to-toe split from weapons or only powers): "Both, both, and you
choose the order".

**Andy's rules that apply:** blood toned down, no pixel blood; humanoids go down unless beheaded or gibbed; Stoneborn
are never gibbed; royals can be gibbed but are recovered; Rubber-folk survive halving and mend; halved players bleed
out in about 8-10 s; ragdolls drop where they stand (#1020).

**What's wanted:** a body can be split down the middle, head to toe, as well as at the waist. Both, as Andy chose: (a) **weapons**: a hard overhead blow from the same weapons as the waist halving (axes and greatswords, the coordinator's pick: say if you think another weapon fits), on a downed or standing foe, at a low chance like `HALVE_CHANCE`; (b) **powers**: any power cut strong enough (`Dismember#cut`); list which powers can do it. It's a gib: both halves fall where they stand as ragdolls and the body is dead (no downing), and it's never done to Stoneborn; royals are recovered as usual. Rubber-folk split and mend, as with halving. **Bosses are spared** by default (no split, they take the damage only), with a server config toggle to allow it; task 6 uses the same toggle.

**Where to start:** `Dismember#halve` and `halvable` (copy the shape), `SeveredPart`, `Gibs`, the ragdoll drop (#1020).

## Notes
- 
