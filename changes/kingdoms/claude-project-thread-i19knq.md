# Start searches read the land from the seed first, like /locate
- **Faster starts**: a life story's search for a place to stand (the wild, camps, realms, wrecks, monoliths, the death ring) now reads land that isn't made yet straight from the world seed, the way /locate does: the generator's own column of blocks shows whether a spot will be sea, lake or dry ground without making the chunk. Only spots the seed says are dry land are made, in at most 2 chunks a search (`FORESEEN_CHUNKS`, was up to 6 blind ones). Before, a ring that fell half in the sea made chunks of open water, tried again, and could wait out the whole minute before falling back.
- A whaler's harbour walks in from the sea by the seed too, making only the shore's own chunks (it used to make every chunk of water on the way, on the server thread), and a new monolith is never tried for out at sea.
- The log says how many columns each search read and how long it took ("A start's search read N columns of land not made yet from the seed (M dry) in X ms").
- Classes: `LifeStory` (`worthMaking`, `foreseenDry`, `FORESEEN_CHUNKS`), `StoryGround` (`shore`, `monolith`).
- Dynamic Waters rivers are carved after the seed's column, so a spot in one still shows up only once its chunk is made (the search then tries the next).
- Built; not played.
