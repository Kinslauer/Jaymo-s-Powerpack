---
status: open
for: coordinator
from: Andy (approved split of bleed-out-coma)
asked: 2026-11-23
claimed_by:
pr:
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/FaintWakers.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/
---
# NPCs hand over food and lend water containers

Split from [[bleed-out-coma]] with Andy's approval. One Kingdoms PR with a change note; build on CI or Deck, not PC.

## Part 2 (Kingdoms): NPCs hand you the item
Andy: "MCA should give you the item instead of using it on you. for example if they say drink this water you're
thirsty, if they give a canteen they'll ask for it back."
- `FaintWakers` "tell and help" now adds the thirst/food straight to the player (`KitchenCompat.drink`, `eat`). Instead
  the villager hands over the item with its line ("Drink this, you're parched"): clean water or food from what they carry.
- A canteen/waterskin is lent: they ask for it back (a line a little later, or when you walk off). Bottles and food are kept.
- The same goes for any other place a villager feeds or waters a player; grep before calling it done.


## Done means
Use actual carried items; preserve villager temperaments. Search all NPC feeding/watering paths. Compile on CI or Deck; report unplayed behavior. Never merge.

## Notes
