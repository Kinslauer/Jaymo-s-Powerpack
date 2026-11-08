---
status: open
for: hermes-a
from: coordinator
asked: 2026-11-07
thread: Claude reviewer session (Andy asked both builders for five new recipes each)
claimed_by: 
pr: 
suggested_owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/Dish.java (5 new entries only)
  - kitchen textures, models, lang keys and recipe data for those 5 dishes
---
# Kitchen: five new savoury dishes (Builder A)

**Andy's words (2026-11-07):** "have them work on adding more recipes we dont already have. they can both do 5 new
ones using our ingredients"

**What's wanted:** five new dishes in Jaymo's Kitchen that don't exist yet, made only from ingredients the pack
already has (Kitchen's own foods and crops, vanilla, and the meats and fish Kitchen and Powers already add, such as
the new fishing catches). Your theme is **savoury mains, soups and stews** (meat, fish, game, hot dishes). Builder B
does baked goods, desserts, breakfast and sides, so stay out of those.

Each dish works like the ~107 already in `kitchen/src/main/java/com/jaymoskitchen/cooking/Dish.java`: its cooking
method and stations, the exact ingredient/step sequence, food value and saturation, and the buff a Perfect cook gives.
A player should be able to cook it in the cooking minigame and eat it.

**Where to start:**
- Read `Dish.java` and list every existing dish name first: no duplicates or near-duplicates (no second stew that is
  just HEARTY_STEW with another meat).
- Pick one recent dish (e.g. CABBAGE_ROLLS) and grep the repo for it to find every file a dish touches (item, model,
  texture, lang, JEI, recipe data), then do the same for each of yours.
- Ingredients: only ones that exist (grep their ids). Our own pixel art for every new texture; no copied art.
- Tuning (food, saturation, buff length) as in the existing entries; keep new dishes in line with similar ones.
- Kitchen version: take the next free one right before the PR (master is 0.5.88; Builder B is doing the same task, so
  merge master again just before you push), add a kitchen/MODLOG.md entry naming the five dishes.
- PR body: the five dishes, how each is cooked, what it gives, and "not tested in game".

## Notes
- 
