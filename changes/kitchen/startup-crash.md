# rats: a rat that reaches food no longer crashes the server
- A rat that got to a store of food, sniffed at it and finished its meal no longer takes the whole server down a tick later. The world goes with it, so everyone gets kicked and the server has to be started again.
- Fixed in `Rat$RaidGoal`: the goal cleared its food target once it had eaten or run off with a piece, but the mob AI still ticked it one more time before asking whether it should keep going, so it read a target that was already gone. It now stops itself when there is nothing left to raid.
- Built; not played.
