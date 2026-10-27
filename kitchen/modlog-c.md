- **Farmer's Delight's larder, cooked through our minigames (Track C).** 35 new `Dish` entries, appended after
  `STUFFED_PUMPKIN_FEAST`; their art, pie and platter models are FD's (MIT).
  - Pot (STIR; furnace, smoker, pot on the hob or over a fire): melon juice, tomato sauce, bone broth, noodle soup, onion
    soup, pasta with meatballs, pasta with mutton chop, mushroom rice, vegetable noodles, squid ink pasta, dumplings (2 a
    batch), glow berry custard, dog food; baked cod stew (SEQUENCE).
  - Oven: stuffed potato, pie crust, sweet berry and honey cookies (6 a batch), and three pies (apple pie, sweet berry
    cheesecake, chocolate pie).
  - Spit: roasted mutton chops, steak and potatoes. Pan: barbecue stick, egg, chicken and bacon sandwiches, mutton wrap.
  - Board (CHOP): nether salad, melon popsicle, salmon roll and cod roll (2 a batch), kelp roll, and two feasts: the
    gleaming salad (4 servings, glows, `Feast.GLEAMING_SALAD`) and the rice roll medley (8 servings, `MedleyBlock`).
  - Not dishes: milk bottle (4 from a bucket, clears effects), ham, pumpkin slice, cake slice, pie slices, kelp roll
    slice, gleaming salad (the serving), horse feed (crafted). New class `delight/DelightDishes`.
- **Pies are blocks.** `delight/PieBlock`: four slices; right-click eats one where you stand, a knife cuts one off. The
  pie keeps the quality it was baked to (`FeastBlock.QUALITY` is shared), and so do its slices.
- **Cuts** (`delight/Chopping`): cake into 7 slices, pumpkin pie into 4, pumpkin into 4, melon into 9, kelp roll into 3,
  ham into 2 porkchops and a bone, smoked ham into 2 cooked ones, and any of our pies into 4 slices.
- **Ham:** a pig (half the time) or hoglin killed with a knife drops a ham, a burning one a smoked ham
  (`LootTableEvents.MODIFY_DROPS`, as FD does: tags aren't loaded when loot tables are).
- **Dog food and horse feed:** dog food (a dish) on a tamed wolf heals it fully and gives speed, strength and resistance for
  5 minutes (twice as long if Perfect, nothing but the heal if burnt, charred or undercooked); horse feed on a tamed horse,
  donkey, mule or llama gives speed II and jump boost.
- **Steps:** 26 new ingredients (before `STIR`, so only the actions' ordinals moved). A `Step` can now name an
  alternative item: `TOMATO` and `ONION` are the Kitchen's own (looked up by id, no link to the crops track) with
  Pantry's as well; `Step.items()` lists what counts, `Kitchen` counts and takes any of them, and `MinigameRecipe`
  shows them all. Dishes and `Step`s share static-import names, so a step named like a dish gets a different name
  (`CRUST`, `SAUCE`, `RICE_COOKED`, `ROLL_SALMON`, `ROLL_COD`).
- **More than 63 dishes at a station:** `Payloads.OpenKitchen.stocked` is a `BitSet` (`writeBitSet`/`readBitSet`)
  instead of a `long`. Every other dish and step number on the wire was already a VarInt.
- **Dish batches and remainders:** `Dish.batch()` (cookies, dumplings and rolls come out several at a time),
  `Dish.remainder()` (bowl or bottle), `Dish.pie()` and `Dish.placeable()` (feast or pie).
  `Quality.apply` makes a bottled dish a drink.
- 26.3 notes: `FriendlyByteBuf.writeBitSet`; `Player.drop(stack, bool, Prediction)`; `LivingEntity.getLootTable()` is an
  `Optional<ResourceKey>`; block loot pools use the singular `"condition"`.
