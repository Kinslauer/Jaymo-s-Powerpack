# spectate viewcone shows the NPC's focus, or that it's thinking or stuck
- **What it's focused on** (Andy: "that spectate viewcone should label their current focus, or if they are thinking"): the
  label over an NPC an operator spectates no longer says only its fight senses ("At ease" whenever it had no foe). It now
  reads its AI: the top line is its focus (the foe it's fighting, else the first goal or brain behaviour it's running, else
  where it's walking), or **Thinking: picking what to do next** (no target, no path, nothing running but the always-on
  looking and door goals), **Thinking: re-planning its path** (3 or more new paths in 3 s), or **Stuck** (a path but under
  a block of headway across the ground in 3 s, with "climbing" when it's hanging on vines or a ladder). Under it: its alert
  (the old line, "At ease" now reads "No foe in mind"), the task Jade shows, its brain's activity, the goals and
  behaviours running (by their code names, so they can be looked up), its target and distance, where its path goes and
  whether it gets there (and how far short it ends), and its brain's walk target when that's somewhere else.
- **Drawn too**: its path ahead (white line node to node; the end cross magenta when the path stops short of its goal)
  and its walk target (pale green cross). The top line is white when focused, yellow thinking, orange re-planning, red stuck.
- Only for operators spectating (or any spectator in a dev game), as before. `NpcFocus` (new), `NpcEyes.View`,
  `NpcEyesClient`, lang part `npc-eyes`.
- Built; not played. Which always-on behaviours MCA's people run hasn't been seen: any that show in "Running" every time
  can be added to `NpcFocus.BACKGROUND`.
