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
- JEI info pages on Spoiled Food and salt.
- Compile-checked; not played.
