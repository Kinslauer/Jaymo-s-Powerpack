---
status: claimed
for: prometheus
from: coordinator (Strange Supreme gore study)
asked: 2026-11-27
thread: Power-pack Supplement
claimed_by: t3
pr: 
suggested_owns:
  - src/client/java/com/jaymospowers/client/Dismembered.java
  - src/client/java/com/jaymospowers/client/SeveredPartRenderer.java
  - src/main/resources/assets/jaymospowers/textures/ (new cut-face textures)
---
# Cut faces with insides

Gore task 2 of 6, from [[Strange Supreme gore study]] (ideas only: the mod is ARR, copy no code, art or sounds).
Second: the biggest visual step up, and tasks 3, 4 and 5 draw on it.

**Andy's words (2026-11-27):** "study this mod's gore for future application", then on the study's questions
(cauterized as a per-blow look or a gore preset; head-to-toe split from weapons or only powers): "Both, both, and you
choose the order".

**Andy's rules that apply:** blood toned down, no pixel blood; humanoids go down unless beheaded or gibbed; Stoneborn
are never gibbed; royals can be gibbed but are recovered; Rubber-folk survive halving and mend; halved players bleed
out in about 8-10 s; ragdolls drop where they stand (#1020).

**What's wanted:** where a part was cut off (the stump on the body, both ends of a `SeveredPart`, and the top half of a waist halving) the cut face shows a cross-section instead of a flat stump: meat, a ring of bone, and at the torso an organ or two. It stays on the body and on Kitchen's corpse. Each body bleeds its own: skeletons show bone, golems stone or iron, slimes goo, and bodies that don't bleed (`BladeBlood`: Stoneborn, some hollows) show their own material. Modelled or textured faces, never pixel splats; follow docs/Vanilla-Style.md. Shown at every blood setting but Off.

**Where to start:** `Dismembered#stubPart`, `SeveredPartRenderer`, `Dismember#halve`, `BladeBlood` (what each body bleeds), `BloodSettings`, Kitchen's corpse renderer (`jaymoskitchen.missing.*`).

## Notes
- 2026-11-27 17:40 YOUTH delegated this to prometheus (Claude approved): Andy asked for Prometheus on gore 1-4 (via Claude coordinator, 2026-11-27)
- 2026-11-27 18:28 hermes-modeller's claim had no sign of work for 30 min: handed back as open.
