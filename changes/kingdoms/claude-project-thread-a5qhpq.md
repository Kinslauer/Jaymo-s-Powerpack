# no floating trees: unsupported wood falls
- **Trees don't float any more** (Andy's burnt birch wood clip): whenever something holding wood up goes, by any cause
  (a log burned, blown, broken or chopped out, the ground dug from under a trunk), the logs beside it are looked at once
  the tick is over. A piece of joined logs that touches nothing solid but other wood and leaves comes down: with leaves
  left on it, or more than 3 logs, it drops as a falling tree (tumbles to its heavier side, crashes, strikes what it
  lands on, then lies as a fallen tree to chop up); 1 to 3 bare logs fall like sand and land as logs, hurting a little.
- **Builds stay up**: a piece with any player-placed log or any stripped log is never brought down, and logs in a
  building touch its planks, stone or earth, which hold them. Leaves, vines, fire, cocoa pods and bee nests hold nothing up.
  Pieces over 200 logs, or running into unloaded land, are left alone.
- Logs already floating in old worlds only come down once something next to them changes.
- `LooseWood` (new), `LooseWoodMixin` (ServerLevel block changes), `FallingTree.drop`; `Trees.leavesOf` now package-wide.
- Built; not played.
