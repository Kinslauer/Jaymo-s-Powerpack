# karma - a life is weighed when it ends
Andy: "a karmic alignment system, meaning your actions are weighed against you after death ... If you were evil you
reincarnate in the nether, one last chance to continue your life, however it will be extremely difficult. Or if you were
just you end up in the otherworld, allowed the chance to respawn instead of reincarnating, so good karma pays off. (You
will still have to fight in the tournament as a non z fighter)".
- **The weight** (`core/wounds/Karma`): a hidden score per life, -100 to 100, never shown. Up: monsters (+0.15), the
  undead (+0.25), bosses (+4), outlaws (+0.3), getting the fallen up (a player +5, a villager +3), a good name with lawful
  settlements (Kingdoms). Down: killing a villager (-8, a child -14), a player (-12; +2 if they were wicked), someone
  else's tamed animal (-4), and whatever costs a settlement's good name. Crossing into just (25) or wicked (-25) shows one
  vague line above the hotbar. The Judge will say how your page reads (five answers, no numbers).
- **When a life ends for good** (the death-by-cause roll is lost, or a timer runs out), it is weighed first:
  - **Just**: you wake in the Other World with a halo, Z Fighter or not. The fortune teller takes no coin from you; win
    the tournament of the dead and she sends you back as you were (to where you fell), at the cost of half your weight.
    One who never trained is lent a Borrowed Blade (an iron sword) and meets spirit fighters with 60% of their health.
    Not won when the Other World lets go (30 minutes): the life is over and you are born again.
  - **Wicked**, once in a life: the screen stays black ("You don't wake. You sink.") and you wake in the Nether at 30%
    health and 6 food, 140 to 220 blocks from a lit portal (the nearest one standing within 128 blocks of the spot under
    where you fell, or one built there). Every 45 seconds a line says which way its cold draught blows from. Piglins and
    everything hostile within 24 blocks come for you every 5 seconds. A death down there is final, with no roll and no
    wake. Leave the Nether alive and the life goes on as it was.
  - **Anything between**: born again, as before.
- A new life starts at 0. `/karma [player]` and `/karma <player> set <weight>` for ops.
- Rides on `death_by_cause`; the Other World needs `afterlife=true`. Lore: "The weighing" under The dead in Lore.md.
- Compiles (Gradle); not played. The numbers are first guesses.
