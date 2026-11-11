# shields break by durability, axes wear them faster
Andy (2026-11-11): "The shields break when their durability is gone, and axe hits deal more damage to its durability than swords".
- **Durability only** (`combat/ShieldShatter`): a mob's wooden shield no longer has a hidden strength of 28 blocked
  points. Every shield, a player's or a mob's, breaks when its durability runs out and at no other time. A wooden one
  still bursts into shards when it does, and a mob that lost its shield still reels and takes up a whole spare four
  minutes later.
- **Axes** (`ShieldShatter.axe`, `AXE_FACTOR`): a blow from an axe (any weapon that knocks a shield aside) wears a raised
  shield three times as fast as a sword's of the same weight, wooden or metal, whoever holds it. Weapon guards are untouched.
- Compiled; not played.
