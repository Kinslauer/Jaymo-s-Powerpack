# deaths cut straight to black
- Andy (2026-11-19): "deaths like this should cut straight to black. i shouldnt see the enemies despawned. only hearing nothing and the long wait for the respawn screen."
- **No film after you die.** A Kingdoms captain's scene over your body (and the shot round a mob executioner) no longer plays to the dead player: `UltimateFilm.afterDeath` does nothing (`PLAY_AFTER_DEATH`). The captain is still raised in the world.
- **The long black.** The death screen stays all black for 5 s (`DeathScreenBlackMixin.HOLD_TICKS` 100, buttons can't be clicked yet), then comes up over the old 2.5 s fade.
- **Silence at once.** The moment you die every sound is cut (not eased out over half a second), the downed heartbeat and the other black-screen cues too; only the death screen's button clicks are heard (`BlackoutHush`).
- **No world after Respawn on the way down.** One sent below by their weight is knocked out again on the tick they're back on their feet, not up to a second later (`Karma`), so the camp that killed them doesn't show in between.
- Built; not played.
