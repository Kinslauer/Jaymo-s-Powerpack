# Wisp: ghost tail, and a Phase you hold
Ideas from Yo Fadda's Danny Phantom mod (All Rights Reserved: nothing taken, our own code; no new art).
- **Ghost tail** (`WispTail`): below the waist Wisp is now one tapering tail of 8 links in place of the block of tatters.
  Hanging in the air it curls back like a comma and wavers. Moving, it streams out behind, opposite to where you are going
  (back at a run, down on the way up, up on the way down, off to the side of a strafe), and a turn swings it wide. Every link
  follows the one above it a moment late, so a stop or a turn runs down the tail and the tip arrives last. The ki glow
  bends with it. Everyone sees it on every Wisp.
- **Phase is held** (R, `Wisp`, `WispPhaseMixin`): hold R for up to 6 s and no block stops you: walk through walls and
  doors, Jump to rise through a ceiling, Sneak to sink through the floor (the floor holds until you sneak; bedrock and
  barriers always hold). You float while phased, as on B. Nothing hurts you and your blows hurt nothing. Let go inside a
  block and you are put out at the nearest clear spot within 3 blocks, or where you last stood clear; run out of time inside
  one and that costs 2 hearts. Cooldown 1.5 s plus three quarters of the time you held it. It replaces the old blink through
  a wall up to 2 blocks thick.
- **Phased look** (`WispPhaseClient`): a phasing Wisp thins to almost nothing over dimmer ki, and flickers through its last
  second. Your own screen takes a pale wash that pulses as time runs out; with your eyes inside a block you see out of it
  and the world beyond is still drawn (vanilla and Sodium).
- Turning into Revenant ends a held Phase (Revenant keeps Wraithwalk).
- Built and compiled; not played. To check in game: the tail's direction on strafes and turns, walking and sinking through
  blocks with no rubber-banding, what you see from inside a wall with Sodium, and being put out when Phase ends inside stone.
- **Overshadow** (Sneak+G): slip into the creature you look at within 5 blocks and walk it as your own for 8 s. Your
  movement keys steer it (forward, back, strafe, sprint, jump), it faces where you look, and your swing strikes through it.
  G again lets go; 20 s cooldown. Revenant's Possession steers the same way now.
- **Ghost Ray** (Sneak+B, hold): a pale light gathers in your hand; let go for a bolt along your aim. A tap reaches 20
  blocks for 2 hearts; a full 1.5 s hold reaches 32 for 6 and is much wider. 1 to 3.5 s before the next, by how long you held.
- **Dread Wail shows and hits**: rings of pale light roll out 9 blocks ahead of you; creatures in that cone are thrown back,
  glass in it shatters (up to 32 blocks and panes), and you are slowed and weakened for 2.5 s afterwards.
- **Ghost breath** (`WispBreath`): a thin cold breath leaves a Wisp's mouth every couple of seconds while anything undead,
  or another Wisp, is within 14 blocks, walls or not. Nothing explains it.
- **Float on the move**: floating Wisps and Revenants tip forward with speed, roll into their turns and bring their arms
  out ahead at full speed, head lifted to see. The tail is never still: hanging, its curl tightens and lets out, and every
  few seconds the top flicks aside and the flick runs down to the tip.
- **Change rings for every alien** (`XenoChangeRings`): whenever anyone changes form (into an alien, between aliens, or
  back) two rings in their band's glow open at the waist and sweep up over the head and down to the feet. The band's flash
  still goes off as before.
- To check in game as well: steering a possessed creature (zombies, villagers, something four-legged), the ray's charge
  feel, and whether the rings read at the flash's brightness.
