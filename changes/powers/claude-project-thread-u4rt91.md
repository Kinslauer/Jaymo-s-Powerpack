# Swinging while locked on no longer crashes the game

- Locked onto a foe (zombies, 2026-11-25), a swing for a body region the weapon has no attack of its own for uses a stand-in swing (`RegionSwings.made`). That stand-in carried no trail particle list (null), and Better Combat 3.2.2 checks the list on every upswing (`SlashParticleUtil.trailParticlesFromAttack`), so the client crashed. The stand-in now carries an empty list, which lets Better Combat draw its own trail for the stand-in animation.
- No other code in the pack builds Better Combat attacks; the newer gore code (gnawing, bite gibs, body tugs) was checked for the same kind of null and needed nothing.
- Built; checked against the crash log; not played.
