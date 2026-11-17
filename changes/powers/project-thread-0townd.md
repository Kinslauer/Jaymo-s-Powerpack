# Gun handling for the future's guns (TaCZ Additions picks) and blinking while downed
- **Water splash**: a shot that goes into water throws up drops and a ring of ripples where it goes in. A gunner's shot
  into water stops 2.5 blocks under the surface (players' TaCZ bullets keep TaCZ's own water handling).
- **Trail on every shot**: shots that aren't tracers leave a thin grey wisp along their line for a moment.
- **Laser sights**: TaCZ already draws a fitted laser's beam; the End key now switches it off and on (a click), and a
  switched-on laser puts a dot where it points, for everyone. About 1 in 3 of the future's gunners have one, off until
  they take aim, so a dot on the wall comes before the shot.
- **Gun movement, first person**: the gun lags behind when you turn and leans when you step sideways; standing, it
  rises and falls with your breath (harder after sprinting or under half health). Aiming down sights calms both.
- **Tuck**: up against a wall the gun comes back to your chest, muzzle up, and won't fire. WeaponMod's musket,
  blunderbuss and flintlock pistol tuck too and can't be readied while tucked.
- **Long-gun grips by stance** (Andy's note): musket and blunderbuss sit at the hip standing, the low ready in a plain
  sneak, a shouldered high ready in the low crouch, and forward and level lying prone. First person only.
- **Scope sway**: at 4x zoom and up the view drifts in a slow figure eight; prone, kneeling, the low crouch, a sneak or
  resting the gun on a wall top steadies it.
- **Scroll zoom**: aiming down sights, the mouse wheel steps the scope's zoom (TaCZ's zoom key) instead of the hotbar
  or the stances.
- **Recoil by kind**: pistols, SMGs, rifles, machine guns, shotguns and snipers each kick the view by their own share
  of TaCZ's recoil, the view stays where the kick left it, and the gun jumps back in your hands.
- **Downed blinking**: down and awake, your eyes droop shut and open again, slower and longer as you weaken.
- Classes: armory/GunLasers, client/armory/GunHandling, GunLaserClient, MusketSights, wounds/DownedHud,
  mixin/tacz/TaczLaserBeamMixin, TaczRecoilMixin; power/chrono/future/GunGear, FutureGuns.
- Compiled with Gradle; not played. Picture-in-picture scopes were left out (not picked).
