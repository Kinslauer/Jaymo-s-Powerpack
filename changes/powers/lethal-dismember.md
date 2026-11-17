# Killing blows dismember humanoids
- **Killing hits take parts off**: a hit struck by hand (or a power's cut) that kills a humanoid mob, or downs it (it would have killed), may take off the part it landed on. Blades: the head 50%, an arm or a leg 25%. Daggers and short blades: the head 75%, a limb 12.5%. Blunt weapons and fists break the part instead (a head is dazed), and sever it only 5% of the time. Armour over the part lowers every chance as it does for other cuts. A beheaded humanoid dies instead of going down. A hit on the body picks an arm or a leg (after the chance to cut it in half). The finishing hit on a downed humanoid rolls too. Skeletons' parts shatter. Tuning: `Dismember#LETHAL_*`.
- Players, village children, arrows and other shots, and blasts keep their own rules.
- `Dismember#lethalHit`, `#lethalPart`, `#lethalSever`, `#lethalBlow`, `#shortBlade`; called from `Downed` (going down, the finishing hit) and `Wounds#killingBlow`.
- Built by PR checks only; not played.
