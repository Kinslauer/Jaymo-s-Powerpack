# character creator for a skipped life story, with presets

Andy: "people who skip the story go to a new character creator that they can choose everything explicitly except their
power (only affinity) and this can also be saved as a preset like the costumes work now."
- **Skipping the story opens a character creator** (`LifeStoryScreen`, `Draft.explicit`, `Field`). "On second thought..."
  (now on the who-are-you page too) no longer rolls a random life and reads it back: it opens one page with a row for each
  thing the story decides. Click a row to step it on, right click to step back: who you are, heritage (every blood open to
  you, earned ones you have woken included), its look, who raised you (and their realm for a noble house), your childhood,
  class (all of them) and its first spell, element, patron, oath or sworn foe. Each row says what it gives (+1 Might, the
  look's trait). Under them are the flaws, the hard-road switches and the ideal, bond and flaw, as on the story page.
- **The power is not picked, only its affinity.** The Affinity row is the story's "Then one day..." answer (a spider's
  bite, a watch that ticked backwards, "Nothing, yet"). It never names the power; that still wakes on its own after the
  usual wait.
- What the story had answered before the skip is kept; the rest starts at random, and **Randomize** rolls every row again.
  A row that a new heritage can't have (a noble house for a Bio-built) gives way to the first one it can. Back returns to
  the story. The creator is made in silence (no narrator), and the start is still chosen on the road page after MCA's
  creator.
- **Presets** (`CharacterPresets`, `config/jaymoskingdoms-characters.json` on your own computer, up to 32). Type a name and
  Save preset; "Load a preset" steps through the saved ones (right click back); Delete removes the one named. A preset
  keeps every row, the flaws, switches and sparks, and the start you then take on the road page, which is picked for you
  next time (you can still turn to another). It is kept by name, so it loads in any world or server: a realm that world
  doesn't have, or a blood not woken there, falls back to what the creator had.
- A preset does not keep MCA's face, hair, body and name: those are still made in MCA's creator after it.
- No server change: the creator sends the same packet the story page does, so the server checks it the same way.
- Built with Gradle (`compileClientJava`). Not played. The skip ask's second line is new text and has no narrator clip
  (the old `skip_desc` clip is no longer played).
