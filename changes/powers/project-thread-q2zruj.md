# out of your body while left for dead
Andy: "the ghost should also be how the player character appears while in limbo, so you can kind of haunt your friends";
then, limbo being the choice on the final death screen: "the ghosts you implemented can stay, as long as they can still
enter limbo if they don't come to."
- **Out of the body** (`core/wounds/OutOfBody`, `PlayerGhost`): lying left for dead, once the black has lifted and until you hold sneak
  to come to, you are out of your body as the ghost from the graveyards: the black screen is gone, your view hangs over
  your body, and the moving keys drift it the way you look (forward and back along your gaze, so looking up rises), 0.2
  blocks a tick, up to 48 blocks from the body. Blocks stop it. Everyone else sees a pale see-through figure, lit from
  within and bobbing, turning its head as you look. Nothing touches it, it touches nothing, and no mob sees it.
- The body stays where it fell and everything about it is as before: it can be carried (the ghost is drawn along at the
  end of its leash), brought round somewhere safe, or you hold sneak to come to. Any of those ends the ghost.
- The screen is tinted a little blue instead of the grey of the downed. One line of chat says you can move.
- A life that ends instead of coming to goes on as before: the ghost is gone the moment the wait ends, the view is back
  in the body, and the final death screen follows with its choices (Limbo, once that has merged, among them).
- Kingdoms reads where each ghost is (`OutOfBody.ghostAt`) for the ghost's voice.
- Lore: "(new) Out of the body" under The dead in Lore.md, for Andy to approve.
- Compiled (`compileClientJava`); not played. Unseen in game: how the camera feels, whether another mod that takes the
  camera or redraws the first person view argues with it, the ghost's look on a player-shaped model.
