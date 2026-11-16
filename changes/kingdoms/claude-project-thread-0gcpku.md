# ghosts seen only by the attuned, wispy tails
- **Who sees the dead** (Andy: "only the attuned and death magicians should ever see the ki ghosts"): the ghost at a grave (`GraveGhost`) is sent only to attuned players and Death Sorcerers (`Mana.seesGhosts`); anyone else never sees it, its motes or its words, and can't lay it to rest. It rises and fades by the nearness of someone who can see it.
- A ghost's voice (out of the body or in Limbo, Simple Voice Chat) is heard only by those who can see the dead (`VoiceMasks.hearsGhosts`, `VoiceRange`).
- **Wispy tail** ("the ghosts should have wispy tails like wisp"): the grave ghost's legs are gone; an 8-link tail tapers from the waist, fading toward the tip, curled back a little and wavering (`GraveGhostTail`, model rebuilt by `tools/gen_ghost_tail.py`).
- Built; not played.
