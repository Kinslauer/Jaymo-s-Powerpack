---
status: in-review
task: Venom head only, adapted to our existing symbiote (child of codex-reference-dinos-venom)
branch: t3/venom-head-adaptation
with: codex-reference-dinos-venom (parent), dinosaur track (separate)
pr: 2080
mods: powers
versions:
started: 2026-11-24
owns:
  - src/client/java/com/jaymospowers/client/power/web/WebBigHead.java
  - src/client/java/com/jaymospowers/client/power/web/WebBigHeadMesh.java
  - src/client/java/com/jaymospowers/client/power/web/WebHulk.java (head hooks only)
  - src/client/java/com/jaymospowers/client/power/web/WebSymbioteLayer.java (head hook only)
  - src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_bighead*.png
  - tools/gen_web_bighead.py, tools/recover_symbiote_head.py, tools/data/bighead_*
  - changes/powers/venom-head-adaptation.md
---
# Venom head, adapted to our symbiote

**Task:** Andy's Venom HEAD ONLY adaptation from his own Symbiote 1.20.1 jar (Desktop `Reference Mods`), fitted onto our
existing big body. No full body, no unrelated abilities. The dinosaur track owns dinosaurs and core registration; I touch
neither.

## Ownership (Andy's confirmation, relayed by the parent thread 2026-11-24)
Andy explicitly confirms the Symbiote 1.0.1 and Amber Age jars are his own mods, made with Claude/GPT, and authorizes
reusing them in the pack despite their All Rights Reserved metadata. Recorded here; the parent adds it to Decisions.md.
That lifts the "nothing copied from any other mod" line the older symbiote tools carry, for this head only.

## Plan
- Recover the head from the jar (no .bbmodel/Gecko JSON: the model is built in code, `FormGeometry.venom()`; recovered
  from the bytecode, 78 parts / 214 cubes, head + jaw + fang parts used).
- Our big body (`WebHulk`) keeps its body; only its head masses are swapped for the recovered head, scaled to our frame,
  with our jaw/teeth/tongue behaviour driving it.
- Keep the plain suit and the Consume maw as they are.

## Notes
- Lore placement is a `(new)` paragraph in Lore.md for Andy to approve.

## Done
PR #2080 (first push b656f1168a; follow-up 5131a0593f at Andy's request: "the head looks great, but now the color is off, can you add a preview of the rest of the body, and add the tounge that flicks in and out periodically").
- The big suit (WebHulk) wears the recovered head (head + jaw + fang parts + the four tongue segments, 80 cubes, 6x texture, white/red twins, eye glow); body, plain suit and Consume's maw unchanged. Fangs always show (as in the original).
- Colour: the head's dark and red texels are matched to the body texture's colours by brightness rank (tools/gen_web_bighead.py, match_palette); numbers now agree (dark median 13,13,17 on both).
- Tongue: the original's segments, in the jaw, shot out on WebHulk.flick (every 140 ticks per wearer, 12-tick flick, then the pause; licks after a meal); the old drawn-on big-body tongue (WebHulk.submitTongue) is removed.
- Previews, in `E:/Jaymo Vault Live/Assets/`: bighead-fullbody.png (true colours), bighead-fullbody-lifted.png, bighead-heads.png / -lifted.png (original | old | new), bighead-tongue.png (in / out / back, front and side), bighead-tongue.gif (one flick + the pause). All made by tools/preview_bighead.py from the generated Java and the real textures.
Not checked: any game run (glow, head pose while crawling/climbing/executions, tongue in motion, SCALE/OFFSET constants; the preview stands the body straight, the game bends elbows and knees). Lore.md has a `(new)` paragraph "Its own face" for Andy to approve. Decisions.md left to the parent.
