# Guards only see a drawn blade in front of them; sneak right up behind anyone
- **The blade stance is seen, not heard**: a guard (or any villager) now reacts to your drawn, crouched sword stance
  only when it faces you (the view cone `Senses` gives humanoids). Andy's clip: an orc guard with his back to him
  warned "Halt. Sheathe that blade" the moment he drew. `Menace` checked only a clear line between them, not which way
  the guard was looking. Drawing a weapon still makes no sound mobs hear.
- **Sneak up for the takedown**: the two blocks behind a mob where it noticed you anyway now count only while you're
  walking. Sneaking, you can get right up behind anyone (Andy: "while not sneaking, if you sneak you can get right up
  for the takedown"). `Senses#close`.
- Built on CI only; not played.
