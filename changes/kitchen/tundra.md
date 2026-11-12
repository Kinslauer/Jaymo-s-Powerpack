# tundra autumn - the tundra's grass turns with the year
- **Tundra seasons** (Andy picked Klei_Wright's tundra, `docs/Tundra-Plan.md`): the grass of Powers' tundra and lichen
  woodland follows our calendar: straw in early spring, green-gold through the short summer, orange then red through
  autumn, dull straw in winter. Only the colour changes; they are too cold to be temperate, so nothing else about the
  seasons applies there.
- `Seasons.tundra` knows the two biomes by their base temperatures (-0.4 and -0.45, which nothing else uses), so the
  colour lookup needs no registry; `SeasonTints.TUNDRA_TINT` holds the twelve colours.
- Compiled; not played.
