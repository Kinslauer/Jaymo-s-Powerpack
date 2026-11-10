# Hardrock patch 5 - food that doesn't keep
Andy's TFC Hardrock picks (ideas after TerraFirmaCraft, our own code and art, `tools/gen_fresh.py`):
- **Food rots**: food you carry is stamped with the day it was made (`jaymoskitchen:made`; food made the same day still
  stacks) and shows a freshness bar, green to red, with Fresh, Going stale or Nearly off on its tooltip. Raw meat keeps
  a week, raw fish 4 days, cooked meat and fish 2 weeks, fruit 2 weeks, vegetables and bread a season (21 days), soup 5
  days, anything else 2 weeks (`fresh/Freshness`). Rotten meat turns to rotten flesh, anything else to the new Spoiled
  Food, which goes into organic compost. Golden food, honey, dried kelp, candy and the like never rot
  (`#jaymoskitchen:keeps`). Creative players' food doesn't age.
- **Preserving**: salt in your hand and food in the other, use: it's salted (one salt to 8 food). A dish smoked well (good
  or perfect) in the smoker's minigame comes out preserved too. Preserved food keeps four times as long.
- **Cold cellars**: a chest or barrel somewhere dark and out of the sky, and below height 40 or within 2 blocks of ice,
  packed or blue ice or an icebox, ages its food half as fast (counted each time it's opened).
- **Food groups**: grain, fruit, vegetables, protein and dairy (`stomach/FoodGroups`; a Kitchen dish counts every group its
  ingredients do). Four or more groups in your stomach's slots at once add two hearts, three add one, and two or more
  meals of one group alone take a heart off. Five pips beside the stomach slots light up for the groups you've eaten, and
  a food's tooltip names its groups. This adds on top of Survivalist health and every other bonus.
- **Cheese** (`fresh/Cheese`): three buckets of milk and salt make a Cheese Wheel. Set down in a cold cellar it ages over
  about three days (young to aged, it darkens). Use it with an empty hand for a slice, four to a wheel: Cheese (3 food),
  or Aged Cheese (6 food, never goes off) once it's aged. An uncut wheel picks back up young; a cut one breaks.
- **Tired fields** (`farm/TiredSoil`): farmland keeps three nutrients, for grain, roots (carrots, potatoes, beetroot) and
  everything else. Each ripe harvest wears that one and lets the other two recover a step, so rotating crops pays. After 3
  harvests of one kind more than the soil has recovered, that kind grows at half speed, after 6 at a quarter. Fertilizer
  (bone meal or manure) rests the soil at once.
- The clay oven and greenhouse in the plan already exist (Kitchen's oven, Seasons' `#jaymoskitchen:greenhouse_roof`).
- JEI info pages on Spoiled Food, salt and the cheese wheel.
- Compile-checked; not played.
