# Sound fades with the screen as you go down

- Andy: "When everything goes dark as you go down your audio should quiet down and blank out too". The game's sound now
  follows the screen: a little quieter while down and greyed, quieter still once passed out, and silent while the screen
  is black (out cold, a death turned away, a life waiting for the new character, a fainting's letterbox shut, the death
  screen). It comes back as the view does (`BlackoutHush`, `BlackoutHushSoundMixin`).
- Kept over the black at full volume: menu clicks (the death screen's buttons), the heartbeat while down or swooning, the
  breath as you wake, and a turning's groan.
- Sound Physics Remastered and our own footstep/soundscape sounds fade with it (only each sound's gain is scaled).
