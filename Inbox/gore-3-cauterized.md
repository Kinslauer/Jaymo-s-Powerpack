---
status: in-review
for: antigravity
from: coordinator (Strange Supreme gore study)
asked: 2026-11-27
thread: Power-pack Supplement
claimed_by: 
pr: 2337
suggested_owns:
  - src/client/java/com/jaymospowers/client/blood/BloodSettings.java
  - src/client/java/com/jaymospowers/client/mixin/BloodVideoSettingsMixin.java
  - src/main/resources/assets/jaymospowers/lang/parts/blood_settings.json
  - src/main/java/com/jaymospowers/core/BladeBlood.java (a heat-blow flag)
---
# Cauterized cuts: a look for heat blows, and a gore preset

Gore task 3 of 6, from [[Strange Supreme gore study]] (ideas only: the mod is ARR, copy no code, art or sounds).
Comes after [[gore-2-cut-faces]]: it recolours those cut faces.

**Andy's words (2026-11-27):** "study this mod's gore for future application", then on the study's questions
(cauterized as a per-blow look or a gore preset; head-to-toe split from weapons or only powers): "Both, both, and you
choose the order".

**Andy's rules that apply:** blood toned down, no pixel blood; humanoids go down unless beheaded or gibbed; Stoneborn
are never gibbed; royals can be gibbed but are recovered; Rubber-folk survive halving and mend; halved players bleed
out in about 8-10 s; ragdolls drop where they stand (#1020).

**What's wanted:** a cauterized cut glows hot orange, cools to black over a few seconds, and throws sparks and a little smoke instead of blood (no puddles, sprays or screen spatter from that cut). Both, as Andy chose: (a) **per blow**: heat and energy cuts always cauterize, whatever the setting (fire and energy powers' blades and beams, a red-hot blade, the Flame Avatar, Emberborn; list which ones you wired); (b) **a gore preset**: a new 'Cauterized' preset beside Light, Cinematic and Brutal on the Blood button, where every cut looks cauterized for that player only. Streamer safe stays as it is.

**Where to start:** `BloodSettings` (`Preset`, `Gore`), `BloodVideoSettingsMixin`, `BladeBlood#sparks`, `Dismember#cut` (powers' cuts).

## Notes
- 2026-11-27 17:44 YOUTH delegated this to prometheus (Claude approved): Andy asked for Prometheus on gore 1-4 (via Claude coordinator, 2026-11-27)
- 2026-11-27 19:04 Andy (2026-11-27): 'Use t3'. Moved from Prometheus to Antigravity (Gemini) in T3 Code; Codex is at its usage limit until Dec 1.
- 2026-11-27 19:23 Prometheus (hermes-modeller) delivered the art-only assets as PR #2337 before this task was reassigned to antigravity. The cauterized overlay and spark/smoke particle sprites are ready; the gameplay wiring (per-blow look and Cauterized preset) still needs a code PR.
