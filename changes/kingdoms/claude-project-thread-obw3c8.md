# class items show up in JEI under the class's name
- **Class item tags** (Andy: "all items related to classes should get some type of tag so typing the class name in jei
  gets you all the related items"): fifteen item tags, `jaymoskingdoms:class/<class>` (shown as "Cleric's Gear" and so
  on), one for each class. Each holds the class's casting focus, the weapons it is trained with (`ClassPerks.trainedAs`:
  a Thief's light blades, a Warlord's two-handed weapons, a Paladin's swords and bludgeons...), the armour made for it
  (the Paladin's, Warlord's, Ranger's and Hedge Mage's sets, Templar and Reverent plate, the Scoundrel's wrappings, the
  RPG Series' robes and armours), and what only it uses: scrolls a Cleric, Druid or Warlock can learn
  (`ClassCasters.ALLOWED`; a Mage gets every scroll), tomes, the Rider's tack and lances, the Tinkerer's stations and
  gadgets, a Monk's fist gear, the weapon schools' masks. Other mods' items are optional, so a missing mod drops out.
  `kingdoms/tools/class_item_tags.py` writes the tags (run it again after changing a list; it reads the scroll lists from
  `ClassCasters`).
- **JEI search** (`client/compat/ClassJei`): every item in a class's tag gets the class's name as a JEI search alias, so
  typing `cleric` (or `ranger`, `tinkerer`...) in JEI's search lists all of them beside anything named so. The alias is
  the class's name key, so it follows the game's language. `$class/cleric` finds the tag itself. Needs JEI's "Search
  Extra Ingredient Names" option, which is on by default.
- A loading hint says so.
- Built; not played (JEI's search not tried in game).
