# Fire is vanilla fire again
- **Fire goes out when you hit it, burns out and spreads by the vanilla rules again**, and the fire-spread gamerule
  (`fire_spread_radius_around_player`) counts. Peterwolf's Forest Fire was taking over every fire as one of its
  "incidents": it put the fire back when you punched it, kept it alight for ever, ignored the gamerule and burnt grass
  blocks to dirt. Our port no longer lets it take fires over (`FireBlockMixin` removed; port 1.1.11-26.3-jaymo.3,
  `PORT.md`), and any fire an older version left burning is put out when the server starts.
- Its kit still works on ordinary fire (the hose and sprinklers put it out), and its wind follows ours.
- Tested on a plain 26.3 server: fires lit on grass and planks made no incident, and the grass fire burnt out by
  itself; a fire incident started by command was gone after a restart. Not played in the full pack.
