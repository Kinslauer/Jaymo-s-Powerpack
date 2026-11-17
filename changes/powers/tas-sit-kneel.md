# Take a Seat's poses on our Sit key and every kneel
- Andy: "There seems to be new sit animations on x, move those to our sit button, and change the kneel animation to
  theirs, it happens on the first x press"; then "Replace ours" and "Every kneel".
- **Sitting** (the Sit key, ] by default, or right-clicking a seat): your body now plays Take a Seat's pose for where
  you are and what you hold, in its order: a campfire or furnace you're looking at, a sword, axe, shovel or fishing rod
  in hand, a seat (stair, slab or chair), a fence, a bed, else the ground. Pressing the Sit key again goes round that
  pose's variants (up to four now: the ground's kneel, two ways of sitting back and a lean). Everything else about
  sitting is ours as before: the seat, its facing, getting up, the campfire's healing, the downed wall-sit and the
  midair falling poses.
- **Kneeling**: the low crouch held still, and kneeling to tend someone, use Take a Seat's ground kneel (its first
  press) in place of ours. Tending keeps its own hands at the work. The finishers, web executions and Gravity's crush
  keep their own kneels (they move through them).
- Take a Seat's own key comes off X (where the Power Skill key also is), and its right-click on stairs is turned off,
  so only our Sit key sits you.
- Nothing of Take a Seat's is copied: its animations are played from the mod as installed (through the Player
  Animation Library). Without it, or if one of its animations is missing, our own poses show as before.
- `client.compat.TakeASeatPoses` (new, and `TakeASeatPal` behind it); `MeditatePose`, `CrouchPose` and `TendPose`
  stand down while it plays; `Meditate.POSES` is 4; `KeyConflicts` v10.
- Not built locally (no JDK 25 in the cloud workspace; the PR checks build it). Not played.
