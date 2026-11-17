# Youth thinks out loud
- **Youth** looks busy while an answer is coming, so nobody takes it for frozen (Andy). From the moment you ask (or
  Hermes calls it with `/youth summon`) until the answer arrives, it wanders between four new thinking animations: chin
  in hand with a swinging foot, pacing the air with its hands behind its back, scratching its head, and rolling over to
  hang upside down with its arms folded. Only a short hover between them, never the same one twice running. Three dots
  of light wink on over its head in turn ("..."). The answer stops all of it.
- Saying only its name ("YOUTH!") doesn't start it thinking; a question does.
- Classes: `inhabitants/YouthWisp` (`THINKING` synced, `THINKS`, `nextIdle`, the dots in `tick`). Animations:
  `kingdoms/tools/gen_youth_wisp.py` (ponder, pace, scratch, upside), regenerated `kingdoms/art/youth_wisp.bbmodel` and
  `youth_wisp.animation.json` (model and texture unchanged).
- Built (compileJava); poses checked in a rough render; not played.
