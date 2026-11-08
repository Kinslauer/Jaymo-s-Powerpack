---
status: done
task: kitchen-recipes-b
branch: claude/kitchen-recipes-b
with: 
pr: 1135
mods: Kitchen
versions: Kitchen 0.5.91 (next free after master's 0.5.90; taken right before the PR)
started: 2026-11-07
owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/Dish.java (5 new entries only)
  - kitchen textures, models, lang keys and recipe data for those 5 dishes
---

# Kitchen: five new baked, sweet and side dishes (Builder B)

**Task:** Add five new dishes to Jaymo's Kitchen (Builder B's half of Andy's "five new recipes each" request): baked goods, desserts, breakfast and side dishes only — no savoury mains, soups or stews (Builder A covers those). Made only from ingredients Kitchen/Powers/vanilla already add; pixel-art textures via gen_assets.py.

**Plan:**
- 5 new enum entries in Dish.java: HONEY_BUN, CHOCOLATE_COOKIE, PANCAKES, FRENCH_TOAST, HASH_BROWNS
- 5 sprite grids in kitchen/tools/gen_assets.py + run it (textures, item models, item json)
- 5 lang keys in assets/jaymoskitchen/lang/en_us.json
- 5 minigame recipe jsons in data/jaymoskitchen/recipe/minigame/
- Bump kitchen/gradle.properties to the next free version right before the PR + MODLOG entry
- sh ./gradlew build + tools/pr_checks.py, commit, push, open PR, vault sync status=in-review

**Notes:** Builder A (kitchen-recipes-a, PR #1131) takes Rabbit Stew, Smoked Duck, Bushmeat Stew, Clam Chowder, Grilled Anglerfish — stay out of mains/soups/stews. Master is Kitchen 0.5.88; Builder A's branch took 0.5.90, so take 0.5.89 now and re-check master right before pushing.

**Done:** (none yet)

## Notes
- 2026-11-08 12:44 PR #1135 is merged into master; moved to Agents/Done.
