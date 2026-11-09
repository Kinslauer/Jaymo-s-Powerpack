# GeckoLib mobs' animations the right way round: the Skeleton Lord faces forward and holds his staff
- Andy's playtest: the Skeleton Lord's head and feet faced backward while his body faced forward, and his staff floated
  off to one side behind him. The models were right; their animations were turned round. Blockbench projects in the
  free / modded-entity formats (the Workshop models and ours) keep keyframes the Java model way, and
  `tools/bbmodel_geckolib.py` wrote them unchanged, so GeckoLib read every animated rotation's x and y and every
  position's x the other way. The converter now turns those round (Bedrock and GeckoLib projects stay as they are), and
  the four GeckoLib mobs' animation files are rewritten with it.
- The Skeleton Lord's idle, walk, statue and laugh now keep his face, feet and body facing where he walks, with the staff
  in his left hand (it hangs from his hand bone, moved into the grip by its own animation). The same fix turns the silver
  skeleton's, the draugr's and the barrow beetle's moves the right way: swords forward, the draugr's shield raised in
  front, the skeleton's buckler and coffin rise.
- Checked with GeckoLib 5.5.7's own bone math (from its jar) on each mob's file; built. Not seen in game.
