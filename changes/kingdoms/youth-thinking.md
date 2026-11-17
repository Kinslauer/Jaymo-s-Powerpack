# Youth thinks out loud, "youth, look!"
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
- **"youth, look!"** (Andy): the wisp flies to whatever your crosshair is on (a mob or a block, up to 48 blocks off),
  circles over it and asks "This?". Say yes (yes, yeah, yep, that one, mhm...) or just ask a question, and the ask goes
  to Hermes with what it is: `[youth-ask] <name> <question> (pointing at: <id> "<name>" at x y z)` ("what is this?"
  when you only said yes, or what you said after "look"). It thinks there until the answer comes, then flies back. Say
  no and it looks again at wherever you point now. Pointing at nothing: "Look at what? Point me at it!". With no answer
  in 30 seconds it comes back.
- Classes: `inhabitants/YouthWisp` (`looked`, `show`, `hoverShown`, `Shown`). Lang: `youth_wisp.look.*`, `look_miss`.
- **Youth leaves sooner** (Andy): with nothing to do it goes home after 15 seconds (was 2 minutes). Waiting on an
  answer it still waits up to 2 minutes, then says "be right back". After "youth, look!" it waits 15 seconds for a yes.
- **Send it off** anytime: "go away", "bye", "thanks", "leave", "go home", "nevermind"... (with its name, or alone while
  it's beside you). It says goodbye ("Off I go! Call if you need me.") and goes.
- **Say "youth" aloud** (Andy): on the dedicated server, Youth hears its name in Simple Voice Chat and comes ("I heard
  you! Type your question in chat."). Only the name; questions are still typed. It uses Vosk (Apache 2.0), an offline
  recogniser listening for that one word: on the server's first start it fetches Vosk, JNA and Vosk's small English
  model (about 70 MB, hash-checked) into `config/jaymoskingdoms/youth-ears/`; nothing is added to the mod jar or
  clients. An empty file `config/jaymoskingdoms/youth-ears/off` turns it off. If anything fails it logs why once and
  Youth just doesn't hear voices. Logged as `[youth-call] <name> voice`.
- Classes: `inhabitants/YouthEars` (new), `inhabitants/YouthWisp` (`calledByVoice`, `BYE`, `ANSWER_TIMEOUT`), `VoiceRange`
  (feeds it the decoded voice). Lang: `youth_wisp.bye.*`, `youth_wisp.voice.*`.
- The voice part is compiled only; it hasn't run (Vosk not loaded or tried on speech here).
- **"Youth can hear you"** (Andy): while you talk in voice chat, a small line at the top of the screen says whether
  Youth is listening ("Youth can hear you" in its leaf-green, or grey "Youth can't hear voices here" when the ears are
  off or failed, or in singleplayer). It fades a moment after you stop. `YouthEars.Hearing`, drawn in
  `client/inhabitants/YouthWispRenderer`. Lang: `hud.jaymoskingdoms.youth_hearing.*`.
