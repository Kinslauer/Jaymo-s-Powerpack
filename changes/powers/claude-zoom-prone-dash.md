# Shift + Left Alt zooms for real, and no dash while prone
- **Andy's clip, "here is zoom, dash and prone broken".** Holding Shift + Left Alt turned OK Zoomer on, but the world
  never zoomed. You can see OK Zoomer on: its "zoomed interface" blew the HUD up past the edges of the screen, and
  Kingdoms' stealth meter grew large and spread apart. But the field of view stayed the same for the whole hold.
  Something that runs after OK Zoomer's change to the camera's field of view put the plain field of view back. Which mod
  does it is not proven. TaCZ's Fabric port sends the world's field of view through its own smoothing filter, so it is
  the main suspect.
- **The zoom is our own now** (`SneakZoom`). It is the same gesture, Shift + Left Alt held (or Alt alone with a fishing
  line out), and it stops when Alt is let go. It zooms 4 times, like OK Zoomer's default. It eases in over 8 ticks and out
  the same way. It is applied after everything else that changes the field of view (`ZoomFovMixin`, priority 3000), and
  it multiplies, so the sprint widening and a punch's kick still show through. The mouse turns slower by the same amount
  while zoomed (`ZoomMouseMixin`). It doesn't press OK Zoomer's key any more, so the HUD stays where it is, and the
  stealth meter fades out as the view zooms, as it was made to. OK Zoomer's mouse-wheel zoom steps no longer apply to this
  zoom. OK Zoomer's own key, still unbound, works as before for anyone who binds it. No zoom during your own Ultimate film.
- **No dash while prone** (Andy: "you shouldnt be able to dash while prone"). Lying prone, from the Crawl key, the stance
  wheel, the held roll's prone, a leg blow or being downed, Left Alt throws no dash, roll, dive or flip
  (`DodgeClient`). Combo Clash's dash holds off too, and the press is spent (`ClientCombatMixin`). The server refuses a
  dodge or a Combo Clash roll from someone prone (`Dodge.canDodge`, `Dodge.start`, `Dodge.clashRoll`). A leg blow's roll
  back up onto your feet still works.
- **Smoother (Andy: "the transition needs to be smoother").** The clip showed two sudden jumps. The zoom now eases in
  and out instead of the HUD jumping. The jump from lying flat straight into a dash roll is gone, because there's no dash
  from prone. The crouch-into-prone animation (`ProneTransition`) is not changed.
- Built in CI; not played.
