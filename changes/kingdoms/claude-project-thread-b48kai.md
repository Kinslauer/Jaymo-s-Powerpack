# The band's smith is an Ocellan
- **The Ocellan** (Andy: "the race of aliens that build and upgrade the watch ... very small with bug eyes ... robes ...
  and monocles, and are a high class society"): the smith who keeps the star forge is no longer drawn as a man. He is a
  Ocellan, about half a block tall, with the body of Powers' Gremlin: grey, a wide flat head and two big yellow-green
  eyes that shine in the dark and blink. He wears a long trimmed robe with a short mantle and wide cuffs, a monocle on a
  chain over his right eye, and keeps his hands folded in front of him. There are three robes (plum, teal, umber).
- He is still the same villager: his menu, his talk and the reforge are unchanged. He is small now, so aim lower to
  click him. Smiths already in a world change the next time they are loaded.
- `OcellanBody` (new: hides the man's body, draws the Ocellan), `ZFighterFolk.OCELLAN` (which parts are Ocellan; add a part
  there to make another villager one) and `OCELLAN_SCALE`, `VillagerLooks`, `ModelPoses`, `McaLookModels.hideChest`,
  `tools/gen_ocellan.py` (skins in `textures/entity/ocellan/`).
- Compiled (`compileClientJava`) and rendered from the skins outside the game; not played. Not checked in game: that
  every MCA layer on the smith (hair, clothes, held things) is hidden, and how his name tag sits.
