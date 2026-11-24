---
status: stalled
task: Red strain gets the slender body from Andy's symbiote jar; humanoid hosts fight in the strain's form; the red one hunts within 500 blocks
branch: claude/red-symbiote-body
with: codex-reference-dinos-venom (parent); stacks on venom-head-adaptation (#2080)
pr:
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
-

## Done
(pending)
- 2026-11-24 14:31 No sign of work for 30 min: marked stalled (whoever ran it stopped).
