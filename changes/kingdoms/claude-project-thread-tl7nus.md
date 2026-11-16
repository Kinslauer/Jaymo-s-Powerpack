# throne golem faces its realm
- **Throne golem facing**: a realm's throne golem now kneels into its throne facing the realm's seat, so whoever sits in it looks over the town. It used to get a random direction, often its back to the town.
- The client now draws a folded throne the way the server turned it (`TownGolem.tick`), instead of a body turn left over from the spawn packet, so the seat you see is the seat you sit in.
- `Golemworks.build`, `TownGolem.tick`. Throne golems already standing keep their old facing; only new ones are turned. Not played.
