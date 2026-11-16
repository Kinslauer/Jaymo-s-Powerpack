---
status: writing
for: any
from: coordinator
asked: 2026-11-16
thread: Reason Art project thread "Show faith champions in the life-story creator"
claimed_by: hermes-b
pr: 1643
suggested_owns:
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LifeStoryScreen.java (faith page preview + sigil backdrop only)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/StoryFigure.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/KiGhost.java (a tint option for the Truth's figure)
  - changes/kingdoms/<branch-name>.md
writer: hermes-writer
---
# Faith page: show the faith's champion, sigil behind it

**Andy's words (2026-11-16, with a screenshot of the "Who did you grow up praying to?" page, `Assets/faith-page-champion.png`):** "this screen should display the champion instead of the player, and the Truth is like the ki ghost but bright yellow white, the sigil should be transparent and behind them. the sigil should stay the rest of the creator."

**What's wanted:**
- On the life story's faith page, the left preview shows the selected faith's champion in place of the player figure, changing as the selection changes.
- "The Truth" has no champion: draw it like the ki ghost (`KiGhost`, legless with the wispy tail from PR #1633) but bright yellow-white.
- The selected faith's sigil (the icon in the list) is drawn large and semi-transparent behind the figure.
- Once a faith is picked, that sigil stays behind the player preview on every later page of the creator.
- Any faith with no champion: say so in the PR and use the closest fit.

**Where to start:** `LifeStoryScreen` (the faith page and its preview), `StoryFigure` / `McaStoryFigure` (how the preview figure is drawn), `KiGhost` (the "Let fate decide" ghost), `client/champions/ChampionModel` + `ChampionRenderer` and `PatronChampions` (the eight patron champions and which faith each belongs to), `Faiths`, `lang/parts/story-faith.json`, `docs/Patrons-C-Plan.md`. Project memory: life-story-creator.md.

## How
- Draw the champion with its existing model and skin (no new art). Build Kingdoms; Andy tests by merging.
- Lore: champions and sigils already exist in canon; no new Lore.md paragraph should be needed. If one is, quote it in full in the PR for Andy.

## Notes
- 
