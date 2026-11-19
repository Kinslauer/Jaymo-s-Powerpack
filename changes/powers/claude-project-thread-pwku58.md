# no Animal Dictionary on first join; UI tips as you meet things
- Alex's Mobs Continued no longer hands every new player its Animal Dictionary (Andy: players shouldn't spawn with it).
  `compat/AlexsMobsCompat` turns its `giveBookOnStartup` flag off once the server is up and as each player connects. The
  book can still be crafted and read; players who already have one keep it.
- **UI tutorial as tips** (Andy picked tips as you meet things over a walkthrough): the first time a part of the screen
  shows, a tip card says what lives there: left of the hotbar (body), right of it, around the crosshair, over the hotbar,
  top left, then how to move HUD parts (/hudmove); the character menu the first time it's open; the journal key the
  first time you carry the traveller's journal. Once per player, one at a time, none in the first 10 seconds of a
  session. `client/hud/UiTips`, lang `parts/ui_tips.json`; `TipCard.busy()` added. Powers keep their own tips.
- Built; not played.
