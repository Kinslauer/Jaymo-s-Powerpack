---
status: running
task: Red strain gets the slender body from Andy's symbiote jar; humanoid hosts fight in the strain's form; the red one hunts within 500 blocks
branch: claude/red-symbiote-body
with: codex-reference-dinos-venom (parent); stacks on venom-head-adaptation (#2080)
pr: 2096
mods: powers
versions:
started: 2026-11-24
owns:
  - src/client/java/com/jaymospowers/client/power/web/WebSlender*.java (new)
  - src/main/java/com/jaymospowers/power/web/WebRedHunt*.java (new)
  - the red-strain body hooks in WebSymbioteLayer / WebSuitGrowth / WebHulk (body choice only; #2080's head untouched)
  - src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_slender*.png (new)
  - tools/gen_web_slender.py, tools/preview_slender.py (new)
  - changes/powers/claude-red-symbiote-body.md
---
# Red symbiote's slender body and hunt

**Task:** Andy (2026-11-24): keep #2080's combined black suit; put our tendrils, blades and scripts on the narrow,
taller, slender body (from his own symbiote jar) for the red strain; symbiote-taken non-web humanoids fight in these
forms (black big, red slender); the red one mostly stays slender and hunts every living thing within 500 blocks, loading
terrain for pursuit and releasing every ticket when the hunt ends. Movement feel from the second film, nothing copied.

## Plan
- Read the strain, body choice and takeover code; preview the slender body first (Assets/red-symbiote/).
- Slender body as a client body variant driven by the same tendril/blade/execution state.
- Hosts: takeover renderers pick the body by strain.
- Hunt: server-side, bounded chunk tickets, released on every end.

## Notes
- Previews: E:/Jaymo Vault Live/Assets/red-symbiote/ (slender-body.png, -lifted, slender-vs-big.png), tools/preview_slender.py.
- Andy mid-task (2026-11-24): tendrils a cage not a cape; then "retain them all ... abundance ... differ in size thickness and length ... stay on the back/rear and shoulders, never out the front" (done: TENDRIL_REST + 14 STRANDS); "adapt them well to the climb and walk" (walls walk from slender roots; low two skitter on the ground at a run); "when done, black should get a similar treatment" (to do).
- Black-strain update via parent: kills only for its needs, seeks a web host; no 500-block hunt.
- Question for Andy: a red host wastes away in 20-90 s today, too short for a 500-block hunt. Default taken: while it hunts it wastes far slower, and each kill (fed on as a corpse) buys it more time.

## Done
(pending)
