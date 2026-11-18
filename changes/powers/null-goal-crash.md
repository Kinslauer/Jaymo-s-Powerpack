# A null goal no longer stops the server
- `NullGoalMixin` (on `Mob.serverAiStep`): if a goal selector's tick throws the NullPointerException from a null goal, that creature's goals skip the tick and a warning naming the creature type is logged once. Any other exception still propagates.
- Not built or played; CI compiles.
