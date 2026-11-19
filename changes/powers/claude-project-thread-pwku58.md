# no Animal Dictionary on first join
- Alex's Mobs Continued no longer hands every new player its Animal Dictionary (Andy: players shouldn't spawn with it).
  `compat/AlexsMobsCompat` turns its `giveBookOnStartup` flag off once the server is up and as each player connects. The
  book can still be crafted and read; players who already have one keep it.
- Built; not played.
