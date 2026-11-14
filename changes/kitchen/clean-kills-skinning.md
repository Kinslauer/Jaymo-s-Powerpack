# clean kills and skinning in strokes
- Andy: "Yeah 1 and 3" (two ideas left over from a "Carcass Craft cleanroom" zip he sent; nothing is ported from it).
- **Clean kills** (`corpse/CleanKill`): how an animal died decides what its carcass gives. A blade, an arrow or an
  armed mob's weapon leaves it whole, as before. **Mauled** (a wolf, a bear, your own dog: any mob with empty hands):
  the hide is torn, half the skins are lost (a single hide is lost). **Burnt** (died on fire or by fire): no hide.
  **Battered** (a fall, a blast, bare fists, or a weapon in the new item tag `jaymoskitchen:bruising_weapons`: maces,
  hammers, cudgels, staves): a third of the meat is lost, never the last piece. The loss is taken from the body's loot
  when the body is made, so the ground, the butcher block, the hook and butcher villagers all see the same carcass.
  The first click on such a body with a butchering tool only tells you what happened to it
  ("Mauled: the hide is torn"); the next click starts the work.
- **Skinning in strokes** (`corpse/Hanging`): the skin comes off in 3 knife pulls instead of one click, each wearing
  the knife ("Skinning: pull 1 of 3"). A chicken or rabbit takes 2, a big carcass 5, and a flint or bone knife
  (new tag `jaymoskitchen:poor_knives`) 2 more. The skins and scraps still come on the last pull.
- Only animal bodies are judged; monsters, villagers and players are as before. Bodies from before this are clean.
- Compiled with Gradle (compileJava, compileClientJava, processResources); not played.
