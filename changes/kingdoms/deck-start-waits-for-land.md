# A start waits for its land instead of making it (Deck crash 10:12)
- **Fix (server froze, watchdog crash)**: setting out on a road (a village start, and any start whose place wasn't
  made yet) no longer makes chunks on the server thread. `LifeStory.reach` now only looks at land already made; the
  land a start looked at and found missing (up to 6 chunks a try, `LifeStory.Wanted`) is made in the background
  (`StoryGround.ahead` with a chunk list, PORTAL tickets) while the player waits behind the loading screen, and the
  start tries again, looking at that land first. The chosen start is waited for up to 3 tries, the wild after it up to
  5; the last try makes do as before. Deck crash 2026-11-13 10:12: Hendrikus's owed village start fell back to the
  wild and made chunks 600-1200 blocks out, holding the server 60 s.
- Only while a start is tried: outside one (a harbour's shore, a trader's second town, camps, captors, shelters)
  `reach` still makes a few chunks as before. A monolith start's search looks at the land made for it first too.
- Built; tested on the Deck server by rejoining with the owed start.
