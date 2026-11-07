---
name: story-figure-fa-pause
description: Why the Bio-built road preview kept breaking (Fresh Animations' flying pose during story stasis); PR #845 pauses EMF on you while the story screen is up
metadata:
  type: project
  modified: 2026-11-01T12:08:26.670Z
---
2026-11-01 Andy: "bio built start still glitches on the preview, you said it was fixed". The 0.7.455 fix (#821) was checked in a test game WITHOUT Fresh Animations.
Cause: during character creation the player hangs in StoryStasis, flying; FA's player pack reads abilities.flying, so EMF played its flying pose on the road page's figure of you (drawYou draws the real LocalPlayer). The parts were torn apart and the figure leant out of the frame.
Fix (PR #845 MERGED 2026-11-01 12:3xZ, Kingdoms 0.7.472 / pack 0.19.48): StoryFigure.drawingYou registered as an EMF pause condition in EmfBladePause.
**How to apply:** check any GUI/figure fix of the player in a test game with EMF+ETF+FA (temp localRuntime of the compileOnly modrinth ids in kingdoms/build.gradle; FA zip from www.curseforge.com/api/v1/mods/1281029/files/8245230/download into run/resourcepacks + options.txt). A temp `/kingdomsdev roadtest` (heritage + story with the road empty + send LifeStory.Open) reaches the road page fast. Keep these out of git. See [[cloud-playtest-setup]], [[fresh-animations-player]].
