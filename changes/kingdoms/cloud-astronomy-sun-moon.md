# Astronomy 5: the Moon's orbit, its eight phases, and the Sun's glare

- **The Moon keeps its own hours**: it goes once round the sky in eight days, so it no longer sits opposite the Sun
  every night. A new moon stays beside the Sun and is lost in its glare. A waxing crescent hangs in the west after
  sunset, and a first quarter is high at dusk and sets around midnight. The full moon is up from dusk to dawn, and a
  last quarter rises at midnight and is still up in the morning, even in daylight.
- **Moonlit nights have a rhythm**: the moon's light (the dark nights already follow the phase) now also follows
  whether the moon is up. A first-quarter night is bright until moonset and then goes dark. A last-quarter night
  starts dark and brightens when the moon rises. The full moon lights the whole night, and a new moon's night is pitch
  black from dusk to dawn. Blood, harvest and blue moons still rise where a full moon would and light the whole night.
- **Observing the Moon**: hold your gaze on the moon for about four seconds, under open sky with no rain, and its
  phase goes into your records with the scratch of a quill. Each of the eight phases counts once. You can't see the new
  moon, so you record it by watching the dark night sky on its night. Records stay with your character for the scholar
  journal to read.
- **The Sun's glare**: clicking on the Sun before you know Optics leaves you dazzled. The screen goes dark for a few
  seconds (the same Blindness as our other blinding effects), and until it clears you can't read the sky: no stars are
  discovered and nothing is recorded. Once you know Optics, the click records the Sun instead. The Optics skill comes
  with the scholar journal's skill ladder, which isn't in yet, so for now everyone is dazzled and the Sun can't be
  recorded.
- Our own take on the Astronomy mod's idea (MIT, NeoForge 1.21.1). None of its code or art is used.
- `astronomy/SunMoon` (the orbit as the game's own moon angle, observing, the glare, the records),
  `client/astronomy/SunMoonClient` (the click), `DarkNights` (moonlight while the moon is up), and the star discovery
  pauses while you're blinded (`SkyClient`, `StarDiscovery`).
- Built only. Not played, and no server run. Check in game that the moon shows in the right place for each phase,
  including under shader packs, that moonset darkens the night, and that a click on the Sun dazzles.
