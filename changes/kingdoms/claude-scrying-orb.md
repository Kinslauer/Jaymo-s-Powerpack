# Scrying Orb: watch a far person or place as a living miniature over a crystal orb
- **Scrying Orb** (block): a glass sphere on a stand. An attuned player (one with mana) right-clicks it holding a
  **Lock of Hair** (from the Scrying spell) to watch that person, or a **Sighted Shard** to watch a place. The orb lights
  up and a 1/24-scale miniature of the land round the target hangs over it for 30 seconds: real blocks, a sphere 17
  across and 11 high, turning slowly, dimmer when it's night there. People show as tiny blue blocks, monsters red,
  animals green. Everyone within 16 blocks of the orb sees it, refreshed each second. A watched person gets the Scrying
  spell's illusion save (if it holds, the orb only clouds over) and feels a chill. A place has no save. Its 3x3 chunks
  stay loaded while it's watched and unload by themselves after.
- **Sighted Shard**: sneak-use a plain amethyst shard on a block to mark that spot (its dimension and position). Sneak-use
  a marked shard in the air to clear it.
- **Getting one**: clerics sell the orb, and it's made on the infusion altar from glass, 4 amethyst shards, 2 eyes of
  ender and 2 glass.
- Classes: `ScryingOrb`, `ScryingOrbBlock`, `ScryingOrbBlockEntity`, `SightedShard`, client `ScryingOrbRenderer`.
- Built; not played: the miniature, the figures and multiplayer viewing haven't been seen in game.
