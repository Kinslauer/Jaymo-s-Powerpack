# the life aim page is a torn scrap on rough starts, headed by who you are
- Andy: "your starting journal entry should resemble a scrap of paper ... only ever a book if you have a start within a
  town or something less rough. it should also read out your class, heritage, name, and then the reason".
- The aim page that opens as you land is now a single torn scrap of parchment, burnt dark at its ragged edges (our own
  texture, `textures/gui/journal/scrap.png`; client `ScrapScreen`, closes on Esc or a click) on every road but the
  town ones. Down on your luck in a rich city, the family farmhand and sworn to a realm (at its keep) keep Minecraft's
  book page (`LifeAims.BOOK_ROADS`); the cells and the eviction are in a town but rough, so they get the scrap.
- Both open with a "Who You Are" heading and "You are <name>, a <class> of <heritage> heritage." (no class yet: "of
  <heritage> heritage, with no calling yet"), then the aim as it read before. The name is the one picked in the
  creator (the username if none). Lang part `aim-scrap.json`.
- Still shown on screen only, as since the book left the pack: no item.
- Built by CI; not played.
