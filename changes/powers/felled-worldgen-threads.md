# Felled's cut-log list is only written on the server thread (Deck crashes 18:22, 19:03, 20:17)
- **Fix (server crashes)**: Felled notes every log placed or taken away from a hook on every block change, and world
  generation's worker threads (C2ME) ran that hook all at once on a list made for one thread. With Chunky
  pre-generating the new world the list broke: "Index -1 out of bounds" in its set (crash 2026-11-13 20:17), and twice
  an autosave copying the broken list ran the heap out of memory (18:22, 19:03). A compat mixin
  (`FelledCutLogsMixin`, only with Felled installed, its code untouched) lets the note be taken on the server's own
  thread only: blocks world generation places aren't logs anybody cut.
- Built; tested on the Deck server.
