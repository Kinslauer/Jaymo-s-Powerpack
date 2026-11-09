# Blood pools grow, drag trails as wide as the body

- **Blood pools as it gathers** (Andy: puddles "should increase in size the more it accumulates"). Blood landing on or at
  the edge of a pool of the same blood (rain of blood, a body bleeding on one spot, repeated hits, drips) now soaks into
  it: the pool spreads out, lies thicker and darker the bigger it gets (up to a cap), and lies fresh again, instead of
  many small puddles stacking. A pool grown into another of the same blood runs together with it into one. Holds for
  the server's lasting stains and the client's splash pools alike; fewer decals lie about as a result. Heritage colours
  only pool with their own (magma keeps its glow).
- **Drag trails like the rest of the blood** (Andy: "a large body will drag and a single tiny trail of blood will be
  there"). A dragged or crawling body, and a Kitchen carcass dragged behind a player, now leaves a smear as wide as its
  hitbox (a man's broad, a cow's wide, a chicken's thin) made of overlapping blotchy pieces, with drops shaken off beside
  it, a denser blot where it turned, and a pool where it lay still. Dragging back over the same trail darkens and widens
  it rather than stacking pieces. A downed body's trail still thins as it weakens.
- **Rain spreads and smears too** (Andy: "rain should do the same smearing and spreading"). Rain puddles now really
  grow while the rain keeps falling (they used to shrink back to their first size), lie a little deeper as they grow,
  and puddles whose edges meet run together into one bigger puddle between them. Blood the rain falls on spreads wider,
  runs out one way into a smear and thins to a watery tint as it washes away, rather than only shrinking.
- Tuning: `BloodTrail.TOUCH` / `GROW` / `MAX_SCALE` / `DRAG_*`, `BloodSmears.TRAIL_WIDTH` and the step, drop, turn and
  stop constants, `BloodPuddle.THICK_*` / `POOL_*` / `DRAG_ALPHA`, `BloodClient.POOL_*`, `BloodPuddle.RAIN_*` / `WET_POOL_MAX`, `WetClient.RAIN_TOUCH`.
