---
status: in-review
task: soften-pixel-shadows
branch: claude/soften-pixel-shadows
with:
pr: 1644
mods: kingdoms
versions:
started: 2026-11-16
owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/disasters/SunShade.java
---
# Soften pixel shadows' edges

**Task:** Make the pixel shadows from PR #1344 fade smoothly from dark to light at their edges instead of cutting hard, while keeping the shade itself as dark as now. Shaders on must still switch it off.

## Plan
- Implement per-vertex alpha for shadow quads: each corner's alpha is the average of the 4 cells sharing that corner
- Keep merged quads for performance but compute 4 corner alphas per quad
- Apply same approach to wall shadows if feasible
- Ensure shader pack check still disables shadows

## Notes
- Current rendering uses `flat()` with single color per quad
- Need new vertex method that takes 4 colors (one per vertex)
- Cell grid is 8x8 (CELLS=8), each cell is a bit in `column.shade[surface]`
- Edge fading at camera range (EDGE=4) should remain separate

## Done
- Added `cornerShade()` to compute average shade of 4 neighboring cells at each grid corner
- Expanded shadeQuads from 6 to 10 floats/quad to store 4 per-corner shade factors
- Added `flatSmooth()` for per-vertex color quads
- Modified `render()` to compute per-vertex alpha = SHADE * dark * sky * cornerShade * edge(vertex)
- Change note added at `changes/kingdoms/soften-pixel-shadows.md`
- Build and PR checks pass
- PR #1644 opened