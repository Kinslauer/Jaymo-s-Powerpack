# The Astronomer's Journal: record readings, grow the Scholar pillar, study the sky

- **The Astronomer's Journal** (crafted from a book and a spyglass): an open book of sepia ink on aged paper, with
  faint construction diagrams (the celestial sphere at latitude 45°, armillary rings, a quadrant) and margin notes in a
  working hand. Three parts: **Record**, **Skills** and **Study**. Scroll or click the arrows to turn pages.
- **Readings at the armillary sphere are written in it.** Press *Record reading* at night, under open sky, with the
  journal on you. The server takes the reading at the sphere, from the stars you have discovered, and writes down
  what is new: each discovered star's altitude and bearing (8 at most a reading, highest first), the latitude from
  Polaris (once), the longitude (once per 0.25° band, 500 blocks east or west, from Astrometry rank 4) and the sky's
  yearly drift (once in each of its 12 months). Only something not yet in the journal counts, so repeating a reading
  earns nothing. When a reading fails, the sphere's screen says why in one line: no journal, not the Overworld,
  daylight, still dazzled by the Sun (or blinded), clouds, no open sky above the sphere, or none of your stars above
  the horizon.
- **The Scholar, a fourth pillar of the skill tree.** The scholar side had no ladder to carry this, so astronomy goes
  into the skill tree's own progression instead of a ladder of its own: each new measurement is Scholar experience
  (a star's place 6, the latitude 40, a longitude band 25, a month 30), up to level 20, with a point every level and a
  bonus point every fifth, as the other pillars have. Its points are spent in the journal on **Astrometry** (20 ranks,
  1 point each): every rank makes readings finer, rounding them to 1° / (1 + rank), down to 0.048° at rank 20. A
  court wizard's unmaking or a bottle o' enchanting returns Scholar points like any others. The Scholar is not drawn
  on the K web (whose three-way layout is unchanged), and adds nothing to character level, attribute points,
  Survivalist hearts or alignment titles: it is study, not training. The character menu lists its level beside the
  other pillars.
- **Study pages** teach the sky's model with its units, formulas and worked examples (Vega, Sirius, Polaris): the
  celestial sphere, hour angle and the altitude/bearing formulas, the sidereal time from the world clock, latitude from
  the pole star, longitude from the sidereal time, the sky's year, precision and what counts, and a page of where the
  model stops being the real sky (flat world, no refraction or precession, the game's own Sun and Moon, linear
  brightness, simple light pollution). No story or narration; the quoted tuning numbers follow the constants.
- **Sky fix (needed for the readings to agree with the sky):** the star catalogue gives right ascension in hours, and
  the sky used it as degrees, squeezing every star into 24° of the sky's turn. Both the drawn sky and the readings now
  convert it (×15) and share one model (`astronomy/Celestial`).
- **Armillary screen fixes:** its text had no alpha and drew invisible; its clock now reads the world's clock (6:00 at
  sunrise, as the sky uses it) instead of the game-tick count; the client no longer sends its own reading as JSON.
- Classes: `astronomy/AstronomersJournal` (item, open payload), `astronomy/Measurements` (the journal's record, the
  server-side reading, tuning at its top), `astronomy/Celestial` (the shared sky model), `astronomy/StarPositions` (the
  catalogue on the server), `client/astronomy/AstronomersJournalScreen` and `AstronomersJournalClient`; `Skills`
  (the `SCHOLAR` pillar, the `ASTROMETRY` perk, `Pillar#web`), `Stats`, `Renown`, `SkillWeb`, `SkillScreen`,
  `ArmillaryNet`, `ArmillaryScreen`. Lang in `lang/parts/astronomy-journal.json`. Art from
  `art/astronomy/generate_journal.py`.
- **Loading-screen hint fix:** the unique-starts hint and the training-weights hint were both number 125, which
  stopped Kingdoms building; the unique-starts one is now 126.
- The idea of a measuring journal with a skill ladder and study pages comes from Jubileu_13's Astronomy mod (MIT);
  nothing of its code, text or art is used.
- Not in this change: telescope readings (the telescope has art and a rig but no gameplay yet), the Altazimuth
  Mount and Optics skills (Optics, which lets you look at the Sun unharmed, still reads as unlearned), and showing
  the Sun and Moon observations in the journal. The armillary sphere itself still has no recipe, blockstate or item
  model, so outside creative it can't be had yet.
- Built; not played, and no dev server or client was started. Unchecked in game: the book's layout and legibility at
  each GUI scale, the readings and their failure lines, the Learn link, and the corrected star field.
