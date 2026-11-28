# Shift + Left Alt zooms again, and never dashes in Combo Clash's combat mode
- **Andy: "zooming was not working last i played".** The zoom is Shift + Left Alt, held (Alt alone with a fishing line
  out). It drives OK Zoomer through its own Zoom key, which is left unbound. `SneakZoom` held that key down only at the
  end of each tick, and OK Zoomer reads it at the start of the next one, so anything that let go of it in between
  (a key event, a focus change) dropped the zoom. It is now held down again at the start of the tick too, in a phase that
  runs before OK Zoomer's (`SneakZoom.BEFORE_ZOOMER`). Sneak is read off the keyboard as well as its mapping, the way Alt
  already was. OK Zoomer's key is looked for again until it's found, instead of only once.
- **Combo Clash's combat mode:** its dash is on the same Left Alt, and it fired on Shift + Alt as well. Its dash now
  holds off while Alt is the zoom's: Shift held with it, or a fishing line out (`SneakZoom.ownsAlt`, read by
  `ClientCombatMixin`, which wraps the dash key's `consumeClick` in its `tickStart`). Our own dodge reads the same test.
- If Shift + Alt is held and no zoom comes of it, `latest.log` now says why once, on a line starting `Zoom:` (just off a
  wall or bar, in a freerun move, a web yank, or OK Zoomer's key missing).
- The double-tap Shift zoom stays removed (Tiv asked for that on 2026-11-08, #1186).
- Built; not played. The exact thing that let go of the key in Andy's game wasn't found (his last log shows no error).
