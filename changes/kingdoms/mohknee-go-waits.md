# /kingdom mohknee go waits behind the loading screen
- **Fix (watchdog crash)**: `/kingdom mohknee go` made the landing chunk at x 28,000 on the server thread; on the Deck
  it took over a minute and the server shut down (crash 2026-11-13 13:51). Andy: "go waits behind loading". Now the
  land round the western shore is made in the background (`StoryGround.travel`, PORTAL tickets) while you wait
  behind the loading screen ("Mohknee's western shore rises out of the sea"), then you're set down. After two
  minutes without it, you're told to try again and stay where you are. A second `go` while waiting, or while held
  for your story, is refused.
- Built; tested on the Deck server.
