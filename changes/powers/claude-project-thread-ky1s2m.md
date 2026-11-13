# Whirlwind spin: dizzy, then out cold if held too long
- Andy: "the double axe spin should make you pass out if you hold it too long". The spin is the RPG Series' Whirlwind
  (`rpg_series:whirlwind`), the held skill of the double axe and every other big axe (battleaxe, war axe, great axe,
  minotaur axe). It channels for up to 8 s.
- **Getting dizzy** (`core/wounds/SpinDizzy`): each tick of spin adds 1 to your dizziness; each tick not spinning takes
  1 off. From 3 s of spin (`WARN` 60) the warning signs start and grow to their worst at 6 s (`LIMIT` 120): the view
  sways side to side and up and down and rolls, walking weaves aside and slows, past halfway you stagger even standing
  still, and a woozy warble (the nether portal's hum, louder and lower as it gets worse) plays in your own ears every
  2 s (`client/wounds/SpinDizzyHud`, `FaintInputMixin`, `ScreenShakeMixin`).
- **Out cold**: still spinning at 6 s, the spin stops (no last blow) and you drop out cold for 10 s through
  `Downed.knockOut`, the same knock-out as a thrown rock (3 presses of sneak bring you round sooner). Nobody can start
  a Whirlwind while down (`SpellEngineCompat`). The dizziness stays at its worst while you're down and wears off over
  about 6 s once you're up, so you get up still swaying, and spinning again at once puts you back down fast.
- Players only: Spell Engine runs held casts only for players and spell summons, so no villager, guard or mob ever holds
  the spin. Not in creative or spectator.
- Built; not played.
