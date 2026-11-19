# Droppings: dung lands on the ground, piles up, and differs by animal
- Andy: "the dung should drop on ground and be visible and pile high like offal and rocks do", then "can different land
  animals drop different piles? so rangers have a new way to track?"
- **Droppings on the ground**: an animal's manure lands as droppings at its feet. If droppings are already there, the new
  pat goes on top. Up to 8 pats fit in a block: they spread out first, then heap up.
- **Each animal leaves its own kind**, shaped like the real thing:
  - cow pats: cows, mooshrooms, musk oxen
  - horse apples: horses, donkeys, mules, war elephants
  - pellets: sheep, goats, rabbits, llamas, camels, armadillos, moose, serow, shrine deer, great goats
  - lumpy logs: pigs, hoglins, wild boar
  - bird splashes with white caps: chickens, cranes
  - twisted scat full of fur and bone: wolves, foxes, cats, ocelots, polar bears, pandas, coyotes, feral wolves,
    raccoon dogs

  Kingdoms beasts and other mods' deer and boar go by tag (`jaymoskitchen:droppings/*`). Manure set down by hand is a
  plain dung heap.
- **They age**: fresh droppings crust over within minutes and dry out over most of a day, turning paler each time. Flies
  hang over fresh droppings, fewer once they're crusted, and none once they're dry. Dry droppings under the open sky
  weather away a pat at a time, while a heap under a roof keeps.
- **Wild animals leave them too**: any grown animal near a player now and then leaves droppings on open ground, at most
  once every three days. Tamed pets don't. Fed or bred farm animals still go once a day. A fed animal's manure drops
  loose where there's no room or on a nitre bed, which works it in by itself.
- **Reading spoor** (with Jaymo's Kingdoms): a Ranger, or anyone with Read the Ground, can sneak and right-click
  droppings with nothing in either hand. The hint line says what left them ("Deer droppings", or by their look after a
  restart) and how old they are. If the animal that left them is still about, its trail shows to them, as far and as old
  as the best tracker reads. Rangers earn class experience for it now and then. Anyone else takes a pat as usual.
- Manure in hand sets down a dung heap or adds to whatever droppings are there. Sneak to put it beside them instead.
  Right click takes one pat back, a punch drops one manure a pat, you can walk through them, and they pop off if the
  ground under them goes. Scavengers leave them alone. Composting, tanning, fertilizer and nitre beds work as before.
- Code: `farm/ManurePileBlock` (kinds, ages, spoor), `farm/Manure` (who drops what, where). Textures, models, blockstate
  and the outline table come from `tools/gen_manure_pile.py`.
- Built; not played.
