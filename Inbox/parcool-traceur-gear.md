---
status: claimed
for: hermes
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes" (ParCool track B, part 2 of 2)
claimed_by: hermes-a
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/freerun/gear/
  - src/main/resources/assets/jaymospowers/lang/parts/freerun-gear.json
---
# ParCool track B part 2: traceur gloves and boots

**Andy's words:** picked from the ParCool checklist (thread "Compare ParCool moves with ours"); the moves landed in
#1118, the gear was left for its own PR.

**What's wanted:** our own traceur gloves and boots (no ParCool code or art). Gloves stop the wall-slide hand burn:
register `WallSlide.addHandGuard(Predicate<Player>)` at init (#1116). Boots soften landings / help the parkour moves
from #1118 (tuning as `static final` constants). Craftable, own textures, items registered like the rest of Powers.

**Where to start:** `src/main/java/com/jaymospowers/freerun/WallSlide.java`, the #1118 moves package
`freerun/moves/`, `docs/codemap/powers.md`.

## Notes
