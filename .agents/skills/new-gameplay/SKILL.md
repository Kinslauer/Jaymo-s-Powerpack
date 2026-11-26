---
name: new-gameplay
description: Use before you plan, add or rework anything players meet (a feature, power, mob, animal, plant, item, structure, dialogue or art), or port code or art from another mod.
---
# Adding or reworking gameplay

**Lore first.** Everything new must fit the lore canon, `Lore.md` in the live vault (`E:/Jaymo Vault Live/Lore.md`;
cloud: `python3 tools/vault_sync.py --setup`, then `docs/vault/Live/Lore.md`). Read it before planning and place the
new thing in it: who made it, where it lives, whose people or faith. Reshape anything that doesn't fit, or ask Andy.
Add the placement to Lore.md as a `(new)` paragraph for him to approve.

**Living things follow three rules** (Andy, 2026-11-12, from Klei_Wright's tundra; `docs/Tundra-Plan.md`). Every
animal or plant we add or rework has:
- **Sympathy**: it does what the real one does, so knowing the game teaches the real thing.
- **Feedback loops**: it changes its surroundings, and those changes come back on it (lichen eats its rock and runs
  out, grazers follow the food, a fed predator stops hunting).
- **Stewardship**: a player who looks after it is rewarded; nobody is punished for leaving it alone.
Not another mob with a new coat.

**Andy's standing rulings** (`docs/AI-HANDBOOK.md` section 7) decide many design questions. Check them before
choosing how something behaves.

**Other mods.** Optional mods (Kitchen, WeaponMod) are reached only through a compat class that checks they're loaded
(like `KitchenCompat`). Don't copy another mod's code or art unless its licence allows it; when it does, add the
credit.
