---
status: claimed
for: any
from: Andy (four-mods project thread, 2026-11-15)
asked: 2026-11-16
claimed_by: hermes-a
pr: 
---

# Soften the pixel shadows' edges

**Andy's words:** "the pixel shadows are not very smooth, they very harshly cut from dark to light. can they be smoothed at all?"

**What's wanted:** with shaders off, the sun shadows from PR #1344 fade from dark to light over their edge instead of
cutting hard, while still reading as shadows of blocks. The shade itself stays as dark as now.

**Where to start:** `kingdoms/src/client/java/com/jaymoskingdoms/client/disasters/SunShade.java` (merged in #1344).
Each block top is 8x8 squares (`CELLS`), each fully shaded or not (`Column.shade` bits), drawn as flat black quads at
`SHADE` alpha in `RenderTypes.entityShadow(WHITE)`.

## Notes
- Ideas, builder's choice: per-vertex alpha so each square's corners take the average of the squares round them (a
  smooth ramp across the edge, works across block borders if neighbouring columns are read); or a penumbra by casting
  a few rays per square at slightly spread sun angles and using the fraction blocked as the shade; or a soft-edged
  texture instead of `white.png`.
- Keep it cheap: it rebuilds about 26 columns a tick within 14 blocks.
- Shaders on must still switch it off (`DarkNights.shaderPackOn()`).
- Kingdoms change note `changes/kingdoms/<branch>.md`; no version or MODLOG edits.
