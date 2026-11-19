# final deaths cut straight to black
- Andy (2026-11-19): "deaths like this should cut straight to black. i shouldnt see the enemies despawned. only hearing nothing and the long wait for the respawn screen", then "the cut to black is only for a final death".
- All of this is only for a death that ends the character (`Mortality.FINAL`); every other death plays as before.
- **No film.** A film after death (a Kingdoms captain's scene over your body, the shot round an executioner) is cut the moment the death is known to be final (`UltimateCinematic.deathFilm`, its tick), so the death screen's black comes at once. The captain is still raised in the world. Films after other deaths stay.
- **The long black.** The death screen stays all black for 5 s (`DeathScreenBlackMixin.HOLD_TICKS` 100, buttons can't be clicked yet), then comes up over the old 2.5 s fade.
- **Silence at once.** Every sound is cut the moment you die for good (not eased out over half a second), the downed heartbeat and the other black-screen cues too; only the death screen's button clicks are heard (`BlackoutHush`).
- **No world after Respawn on the way down.** One sent below by their weight is knocked out again on the tick they're back on their feet, not up to a second later (`Karma`), so the camp that killed them doesn't show in between.
- Built; not played.
