# Lock-on names the part only with Called Shots; what hurts you is said in your own voice
- **Lock-on**: the name of the body part you're aimed at ("Torso" and the rest) shows under the bracket only once you've
  learned Called Shots (the skill-tree perk that lets you scroll the aim between parts). Before that, no label at all
  (`LockOnClient.marker`).
- **What hurt you, said as you'd say it**: the line under the crosshair for a hurt with no attacker is the character
  reacting, not a label: "That BURNS!" instead of "Hurt: fire", "Too hot... I'm cooking", "So thirsty...", "I'm bleeding
  out", "Something's moving under my skin", "Freezing...", "Can't breathe!", "Need food...", "What was that?!" and the
  rest (`HurtCause`, `lang/parts/hurt_cause.json`). Same timing: once per cause, again after 10 seconds. A cause without
  a line of its own reads "Ugh... <cause>!".
- **Wounds in your own voice**: "Left arm's bleeding! I need to bind it, or stitch it", and the same for broken,
  sprained, festering, rotted and both arms broken (`lang/parts/wounds.json`).
- Built; not played.
