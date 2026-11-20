# Z Fighter gaps 2: stamina for dash, guard and forms

- **Stamina**: Flash Stepping, raising the guard and going into a form or Limit Break now draw stamina (the pack's feather row, {@link com.jaymospowers.stamina.Feathers}). At zero stamina they cannot be used until it regrows; meditation and rest refill it through the stamina system.
- Costs are hooks only in ZFighterPower.java (guard tick, Flash Step, form entry, Limit Break) while zfighter-aura-rework owns that file; the costs themselves spend from Feathers, which was already there.
- The other gap rows (holding back your power, more Limit Break tiers, fighting styles, unlocked potential) wait on the aura rework merging or on later gap tasks.
- Built; not played in game.
