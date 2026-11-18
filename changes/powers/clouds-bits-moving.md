# moving about - late jumps, auto walk, pull riders off, pet-safe swings (clouds/bits patch 4)
- **Late jumps**: a jump pressed up to 4 ticks (2 in heavy armour, 15+ armour points) after walking off a ledge still
  jumps; holding jump under a low ceiling keeps jumping instead of waiting after each bump (`client/moving/LateJumps`).
- **Auto walk**: an unbound key ("Auto walk (toggle)") walks you forward until you press forward or back, open a
  screen or die (`client/moving/AutoWalk`, `AutoWalkInputMixin`).
- **Pull riders off**: sneak and right-click a mount, cart or boat to take its passengers off; never the driver, you,
  or a carried body (`moving/PullRiders`).
- **Don't hit your own pets**: your melee and weapon sweeps skip your own tamed animals (anything ownable that you own,
  horses included); sneak and the blow lands (`moving/PetSafe`). Kingdoms' companions are in its own note.
- Built; not played.
