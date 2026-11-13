# stored positions wait for their land (Deck watchdog)
- **Nothing to see in play**: code that reads a saved position (a road's goal, a web, a door, a board, a camp's rotting blocks, a siege's banner and keep, a cab's stand, a shooing's door, a mine site, a lair's ground) now waits until that chunk is made and loaded, instead of making the server thread wait on world generation. Nothing is dropped: the record is kept and retried on a later tick.
- Classes: `Emigrants` (putBack no longer forces a chunk), `BeastLairs`, `SpiderKinds`, `DoorPounding` (expiry runs first, with no world read), `Bounties`, `Cabs`, `BaneHolds`, `Strays` (a door that can't be shut yet is shut later), `Camps`, `Mines`.
- Built; tested on the Deck server.
