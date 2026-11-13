# A start waits at most a minute for its land
- Andy: "taking far too long, maybe after a minute it should just give the fallback". A start waiting for its land
  (`LifeStory.Wanted`) now gives up after 60 s of real time in all (`LAND_WAIT_MS`; real time, since the server
  falls behind while land is made), and goes ahead with what's made: the start if its land is ready, else the wild,
  else where they stand. Each background make (`StoryGround.ahead` with a chunk list) ends at that deadline too.
  On the Deck each wait took 2-3 minutes (village start, tries 1-3).
- Built; tested on the Deck by rejoining with an owed start.
