# Fix: worn fist gear floats off the arms with Fresh Animations

- (Jung) With Fresh Animations (its player extension, through Entity Model Features) in the pack, worn wraps, cestus,
  bracers and the Fang Gauntlets floated in front of a boxer while the arms were somewhere else: during a punch, a
  block, or any time the plain guard was off. The gear is drawn where our pose puts the arms, but Fresh Animations
  moved the arms again as the body was drawn, and it was only told to step aside while the plain guard was up.
- It now steps aside whenever fist gear is worn (`PoseTakeover.posed`: `FangGauntletsClient.worn`, was `guarding`), so
  the arms stay where the gear is. A boxer's arms and legs then move by the game's and our own poses, not Fresh
  Animations', while the gear is in hand.
