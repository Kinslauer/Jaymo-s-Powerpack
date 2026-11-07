---
status: claimed
for: hermes
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes" (ParCool track B, part 2 of 2)
claimed_by: hermes-a
pr: 1122
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
- 2026-11-07 12:42 Review (head 038cf3b): changes needed. Good: the gloves are a wrist bauble registered with WallSlide.addHandGuard (TraceurGloves.java:37), recipes, own item textures, lang in its own part, version 0.21.54 (0.21.53 is fishing's), checks green. To do: 1) The boots don't help the parkour moves, though the task asks for it and the MODLOG claims it: TraceurBoots adds JUMP_STRENGTH (TraceurBoots.java:45), but the long jump and charge jump set the player's speed directly from fixed numbers (LongJump.java:27 BOOST/MIN_SPEED/MAX_SPEED/UP, ChargeJump.java:29 FULL_UP; the client throws the body in RunMovesClient), so jump strength never reaches them; it only lifts a plain jump. Give the boots their own bonus constants at the top of TraceurBoots (e.g. LONG_JUMP_BONUS, CHARGE_JUMP_BONUS) and apply them where those leaps are computed when the player wears the boots (feet slot); for the safety tap, raise its cut (LandingRoll.SAFETY_CUT) while worn. Say in the MODLOG exactly what the boots change. 2) The item descriptions never show: freerun-gear.json adds traceur_gloves.desc and traceur_boots.desc, but the gloves pass List.of() as their lines (TraceurGloves.java:34) and the boots are a plain Item with no tooltip, so nothing reads those keys. Show them as tooltips (see how other BaubleItems pass their lines) or drop the keys. 3) Worn boots are invisible: the EQUIPPABLE component (TraceurBoots.java:50) has no asset id, so the boots draw nothing on the player's feet. Give them an equipment asset with our own worn texture, like the other wearable gear in Powers. Then build, pr_checks, push, set status=in-review.
