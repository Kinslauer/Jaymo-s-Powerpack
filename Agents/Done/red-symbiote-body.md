---
status: stalled
task: Red strain gets the slender body from Andy's symbiote jar; humanoid hosts fight in the strain's form; the red one hunts within 500 blocks
branch: claude/red-symbiote-body
with: codex-reference-dinos-venom (parent); stacks on venom-head-adaptation (#2080)
pr: 2096
mods: powers
versions:
started: 2026-11-24
owns:
  - src/client/java/com/jaymospowers/client/power/web/WebSlender*.java, WebHostBody.java (new)
  - src/main/java/com/jaymospowers/power/web/WebRedHunt.java (new)
  - body/tendril hooks in WebSymbioteLayer, WebSuitGrowth, WebHulk (limbs/tendril tables; #2080's head untouched), WebTendrilLayer, WebMorphLayer, WebSymbioteWingsLayer, WebGooGlobs, WebBehemothClient, mixin/WebLivingEntityRendererMixin
  - WebInhabit, WebBehemoth, WebPower (hunt + black-needs hooks)
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
- Andy mid-task (2026-11-24): tendrils a cage not a cape; "exaggerated hunch ... more curled while using tendrils ... slight ragdoll on movement" and "arms and knees should bend too" (done: WebSlender.hunch, ELBOWS, THIGHS, CROUCH_DROP); then "retain them all ... abundance ... differ in size thickness and length ... stay on the back/rear and shoulders, never out the front" (done: TENDRIL_REST + 14 STRANDS); "adapt them well to the climb and walk" (walls walk from slender roots; low two skitter on the ground at a run); "when done, black should get a similar treatment" (done: WebHulk.TENDRIL_REST/STRANDS).
- Black-strain update via parent: kills only for its needs, seeks a web host; no 500-block hunt.
- Question for Andy: a red host wastes away in 20-90 s today, too short for a 500-block hunt. Default taken: while it hunts it wastes far slower, and each kill (fed on as a corpse) buys it more time.

## Done
PR #2096 (draft, stacks on #2080). The red strain wears the slender body (from Andy's jar) wherever the big body would be; both bodies wear a crown of tendrils + strands off the back/shoulders; red: deep hunch, rears with tendrils high while they work, pounces, weapon arm grows from the forearm, skitters on the ground at a run, walls walked from its own roots; black: wide low crouch when upright. Taken humanoids are drawn in their strain's body. Red mob hosts hunt within 500 blocks with bounded chunk tickets; black/white lash and slam only for their needs and drop a mob for a symbiote-less web slinger. Review workflow: 2 confirmed bugs, both fixed. Lore (new) paragraph "The red one's own shape, and its hunt"; Decisions.md entry added.
Not tested in game: anything in motion, hunt/tickets, taken mobs' held items/armour, WebSuitWrapLayer and WebSymbioteHand still big-body sized, red web slingers don't hunt (only mob hosts).
- 2026-11-24: rebased onto master after #2080 merged. Andy's rulings: a red-bonded web slinger under bond 30 is seized for the red hunts as stronger takeovers (WebSymbiote.RED_HUNT_*: 12% at a check with nothing near, struggle counts half, ends on kill/escape/bond 30/3 min, no tickets for players); lore paragraph approved, one (new) sentence added for his OK.
- 2026-11-24: merged as #2096 (squash) on Andy's word, head 1aee4d0e17 green on all checks.
