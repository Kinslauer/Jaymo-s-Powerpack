# survival picks: free riding camera, plants and flames in the wind
- **Free riding camera** (Andy's pick "free riding camera"): in a boat, a raft or at a ship's helm your look is no
  longer turned with her each tick, so looking around isn't dragged or juddered as she swings (`BoatFreeLookMixin`,
  `ShipClient`; MC-122731 and MC-259512, our own fix from the idea only). In a boat you still can't look further round
  than vanilla's 105 degrees off the bow. Walking a ship's deck you're still carried round with it. Mobs aboard, free
  rein in the saddle and Shoulder Surfing are unchanged.
- **Plants in the wind** (pick "wind sway"): the pack's Interactive Foliage already sways grass and plants on the GPU
  and leans them in rain; now our wind (the one that fills sails) counts as its rain when it blows harder, and a gale
  as its thunder, so plants bend and gust with the wind in dry weather too (`FoliageWindMixin`, `WindSigns`). Costs
  nothing extra on the Steam Deck. Its shader always blows plants west; that's its own and isn't changed.
- **Flames in the wind**: flames off fires, campfires, torches and candles lean along the wind under open sky
  (`FlameWindMixin`), half as far as the smoke drifts.
- Not changed: lanterns and chains (Fancy World Animations sways them on its own, not with our wind).
- Built by CI only; not played: none of it seen in game.
