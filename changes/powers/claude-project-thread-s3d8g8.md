# no more getting stuck on a horse after a called-off climb
- **Stuck on a horse**: with Horseman in the pack, clicking a horse sat you in the saddle on your own screen at once,
  while the server was still running the climb. When the climb was called off (the horse shied, you stepped away or
  were hit) the server never had you riding, but your screen kept you on the horse: Sneak did nothing and a wild horse
  never threw you, until you left the game. Now your game never starts the ride itself; you are in the saddle only
  when the server's climb ends (`MountClimbHorseMixin`).
- Tested in a cloud test game with Horseman, Kaffee's Dual Ride and Kingdoms: a climb called off partway leaves you
  standing; a finished climb seats you and Sneak gets you off. The server side is unchanged, so a wild horse throws
  you as before (seen in the same game before the fix, not again after). Not tested on a dedicated server.
