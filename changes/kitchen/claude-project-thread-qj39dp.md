# meat groups - any venison, game, fowl or fish cooks into the dishes
- **Why**: Andy: "i should be able to make dishes with venison, more than just cooked venison, maybe add a tag to related
  items so not everything needs a specific recipe". Cube Animals' Raw Venison (roe and red deer) went into no dish at
  all: Venison Stew only took Naturalist's. Bear meat, whale meat, shark, tuna steak, reptile meat, bird legs and deep
  fish had no dish either.
- **Meat groups** (`cooking/Step`): an ingredient can name a group, the item tag `jaymoskitchen:cooking/<group>`, and
  anything in the tag does as well as the ingredient's own item. A new meat needs one line in a tag, no dish of its own.
  The ingredient's own item is used up first (beef before venison).
  - Red meat (every dish that asked for beef): beef, both venisons, mammoth, bear, Burrukai rib cut, whale.
  - Pork: porkchop, Taegore. Poultry (was chicken): chicken, duck, drumstick, bird leg, Prismallard leg.
  - Mutton: mutton, Kirrid loin. Small game (was rabbit): rabbit, bushmeat, morsel.
  - White fish (was cod): cod, bass, catfish, piranha, both shark meats, deep fish. Oily fish (was salmon): salmon, tuna steak.
  - Venison (both mods'), drumstick (and bird leg), lizard tail (and reptile meat), clam (and snail).
  - The cooked ones the same way: cooked red meat, pork, mutton, white fish, oily fish.
- **Cutting board** (`delight/Chopping`): any red meat minces, any pork cuts to bacon, any poultry to cuts, any mutton to
  chops. Minced Beef is now "Minced Meat", Beef Patty "Meat Patty".
- **Screens**: the dish list shows a group's meats in turn, a second each; the buttons read "Raw Red Meat", "Raw Poultry",
  "Raw White Fish", "Raw Small Game". The cooking table and JEI list every meat a dish takes (`MinigameRecipe` works its
  ingredients out when first asked, since tags load after recipes are read).
- The raw groups are in `raw_meats` (eaten raw they can make you sick, like beef) and `spit_foods` (they roast on the spit).
- Built (Gradle compileClientJava clean); not played.
