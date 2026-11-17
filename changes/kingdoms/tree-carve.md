# chopping carves the trunk
- Andy: "Our mod does NOT have cracks that deepen ... i want the trunk to carve out, retaining the interior texture and
  all". Each axe stroke on a tree now cuts a bite out of the log where you're looking: a notch about ten pixels wide
  that goes about three pixels deeper with every stroke, until it's three quarters through. The bark stays on what's
  left of the outside; inside the cut you see the log's own end grain (rings and heartwood), slightly darker. Strike
  somewhere else on the log and a second notch starts there. Woodcutters' strokes carve the side facing them.
- The chop meter, the chips, how many chops a tree takes and how it falls are unchanged. The cracks that were meant to
  deepen with each chop are gone; a log nobody has cut still shows the game's cracks while an axe is at it.
- Logs chopped before this update have no cut saved, so they stand whole until the next stroke.
- The carving (the 16x16x16 wood volume, the shape of a bite and the face mesher) is ported from **Immersive Tree
  Chop** by Litewer, MIT (`docs/licenses/Immersive-Tree-Chop-MIT.txt`). Nothing else of that mod is used: not its
  falling trees, player animation or sounds. One change of ours: a cut stops three quarters through a log
  (`WoodVolume.CUT_LIMIT`), so a tree never hangs on two slivers.
- `WoodVolume` (new): the wood left in a log, `carve`, `faces`. `ChoppedLog.Entity` keeps and syncs it;
  `ChoppedLog.carve`. `Trees.carve` finds where the stroke lands. `ChoppedLogRenderer.carved` draws it. Tuning:
  `WoodVolume.BITE_*`, `CUT_LIMIT`, `ChoppedLog.CARVE_POWER`, `ChoppedLogRenderer.INSIDE_SHADE`.
- Not built locally (no JDK 25 in the cloud workspace; the PR checks build it). The carving and meshing were run on
  their own outside the game (six strokes, mesh area checked against the wood's surface). Not played: untested are how
  the carved log is lit beside its neighbours, the texture direction on sideways logs, and saving and reloading a cut.
