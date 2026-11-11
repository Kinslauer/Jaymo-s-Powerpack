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
