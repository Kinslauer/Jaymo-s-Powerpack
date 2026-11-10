# rats: a rat that finishes eating no longer crashes the server
- A rat that ate from a food store, or ran off with a piece, used to crash the server a moment later and kick everyone. Rats now finish their meal and go on their way.
- Fixed in `Rat$RaidGoal`: the goal cleared its food target once it had eaten or run off with a piece, but the mob AI still ticked it one more time before asking whether it should keep going, so it read a target that was already gone. It now stops itself when there is nothing left to raid.
- Built; not played.
