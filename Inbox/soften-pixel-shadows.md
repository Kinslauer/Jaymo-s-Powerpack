---
status: in-review
for: any
from: Andy (four-mods project thread, 2026-11-15)
asked: 2026-11-16
claimed_by: hermes-a
pr: 1644
writer: hermes-writer
text_pass: done
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
- 2026-11-16 06:18 Text pass: PR #1644 claude/soften-pixel-shadows. Rewrote changes/kingdoms/soften-pixel-shadows.md to house format: what the player sees, class touched (com.jaymoskingdoms.client.disasters.SunShade), not played in game; 'sun/moon' -> 'sun and moon'; added trailing newline. SunShade.java contains no player-visible strings. Built kingdoms (BUILD SUCCESSFUL), python tools/pr_checks.py passed, committed 5b8e20d0f and pushed to the PR branch claude/soften-pixel-shadows (fast-forward, not force-pushed).
