# Killing blows dismember humanoids
- **Every killing hit takes a part off**: a hit struck by hand (or a power's cut) that kills a humanoid mob, or downs it (it would have killed), always takes off the part it landed on: an arm or a leg every time, the head half the time (`Dismember#LETHAL_HEAD_CHANCE`). A beheaded humanoid isn't downed: it dies. A hit on the body takes a random arm or leg (after the chance to cut it in half). The finishing hit on a downed humanoid does the same. Skeletons' parts shatter as before. Armour doesn't guard against it.
- Players, village children, arrows and other shots, and blasts keep their own rules.
- `Dismember#lethalHit`, `#lethalPart`, `#lethalSever`, `#lethalBlow`; called from `Downed` (going down, the finishing hit) and `Wounds#killingBlow`.
- Built; not played.
