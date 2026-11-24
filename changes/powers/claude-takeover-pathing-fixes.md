# Takeover pathing fixes: long tunnels, safe shafts, no stray weapon arm
- **Long walks keep digging**: on a long way to its prey, a takeover now keeps track of where you are along the whole path (it only looked at the first few steps before), so it still tears into the next wall far along a tunnel, and its zips and resent paths start from where you really are.
- **No more blind holes**: digging straight down under you, it first checks there's ground to land on within 3 blocks under the hole, with no lava, fire or water on the way; if not, it goes round instead of dropping you.
- **The weapon arm ends with the takeover**: a weapon it was forming on its own melts back when the takeover ends (it lets go, you break free, you die or it leaves), instead of striking later.
- **The red pounce after rejoining**: the pounce and gap-jump timers reset when you join a world, so a stale one no longer blocks the red one's pounce.
- **No back and forth**: when it gives up on prey it can't reach, it won't turn back to that prey later in the same takeover, so two out-of-reach creatures can't hand it to each other forever; out of reachable prey, it lets go.
- Classes: WebTakeoverPath, WebSymbiote, WebSymbioteClient, WebMorph, WebRedHunt.
- Built by CI; not played.
