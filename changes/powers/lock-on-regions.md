# Lock-on body regions; middle mouse swaps to the one on your crosshair
- **Body regions**: while locked on, scroll down moves your aim down the target's body and scroll up moves it up: head, torso, arms, legs (animals and spiders: head, torso, legs; flyers: head, torso, wings; bosses and other bodies without parts: torso only; a cut-off head, both arms or both legs drop out of the list). A new lock starts on the torso; a swap keeps your region if the new target has it.
- The camera eases onto the region, the lock-on bracket sits on it (smaller off the torso) with the region's name under it.
- Your own blows, arrows, throws and power cuts on the locked target land on that part (`LockOnRegions.aimedPart` in the wounds' `HitRouting`, `Dismember.aimedPart`), so its part takes the damage, breaks and can come off. Of a pair, the arm or leg nearer you (or the one still on); an animal's front or hind legs by which end you're at. Falls, blasts, fire and magic still spread as before.
- On top of that: an aimed head hit does 25% more damage; an aimed leg hit slows 2 s (Slowness II from 6 damage); an aimed arm hit weakens 2 s (Weakness II from 6 damage); a wing hit slows. Tuning at the top of `LockOnRegions`.
- **Swapping targets**: scrolling no longer moves the lock sideways. Middle mouse while locked swaps to whatever living thing is right on your crosshair (within 0.3 blocks of its box), or lets go if nothing else is. Turning the camera yourself pauses the lock's easing for half a second so you can line the next one up.
- Sneak + scroll stays the stance's and the ledge grab's; a Z Fighter holding Z still scrolls flight; scroll picks hotbar slots when not locked.
- `LockOn.Packet` now carries the region (`Packet(int)` kept for Kingdoms' Battle Stance).
- Built; not played.
