# the Reaper's risen are undead, and players rise too
Andy: "players can be reanimated too, and the shamans should still be undead so they get the player zombie buffs."
- **Players' bodies.** Death's Company now raises a player's body with the rest: it gets up as Kitchen's zombie wearing
  their face (`KitchenCompat.riseNow`), carrying what was left on the body, and fights for the Reaper's keeper. When its
  time is up it is put down as a kill, not turned to ash, so what it carried of theirs is dropped. A headless player's body
  still never rises. Reap does not raise a player it kills; only the Company does.
- **The risen are undead** (`Thralls.risenDead`, `Thralls.bind`). A shaman still gets up as that shaman, but it is one of the
  undead now: Restoration's healing treats it as it treats a zombie, and it gets what a risen player has: it sees 64 blocks,
  has at least 2 armour, and hits for at least 3.
- Built with Gradle. Not played. "The player zombie buffs" is read as a risen player's sight, armour and blow; other code that
  asks the entity type's undead tag (smite, sunlight) does not see a risen villager as undead.
