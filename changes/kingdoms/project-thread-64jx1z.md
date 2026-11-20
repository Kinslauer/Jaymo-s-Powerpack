# chopping up a felled tree no longer knocks the next tree onto you
- Andy, downed while chopping up a tree he'd felled half a minute before (clip "Clip 2026-11-20 00-37-46 2"): "what hit me here?". It was the standing tree beside it, knocked over onto him by `FelledCompat`'s knock-on.
- Cause: the knock-on reads how fast a Felled tree is moving from Felled's `trunkTop`, which is the tree's middle log. Chopping a log off a tree lying still moves that middle a block or so along the trunk in one tick, which read as the tree moving fast, so any natural tree within a block of its logs went over that way (and our falling trees hit whoever they pass, up to 7 hearts).
- `FelledCompat` now counts the tree's logs; when it has fewer than when last counted, that tick's jump isn't motion and it starts again from where the middle is now. Real falls, leaning trees and blasts knock on as before.
- Built; not played.
