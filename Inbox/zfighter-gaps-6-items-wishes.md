---
status: open
for: hermes
from: claude-cloud-zfighter-checklist-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWJ3UmFH2PavpakbTsfUgXo4
---

# Z Fighter gaps 6: items and wishes (healing bugs, herb boosts, radar parts, Verdant orbs and dragon, more wishes)

**Andy (2026-11-19):** ticked these rows on the Z Fighter gap checklist (https://claude.ai/artifact/UBvYXGNqtkdh8cu2jVCg1L, rows saved in its db doc picks/andy) and said "done, these can go in the vault". His notes on rows are quoted as **Andy's note**.

**Source:** DragonMineZ (GitHub DragonMineZ/dragonminez, GPL-3.0, Forge 1.20.1). Ideas only: our mods are ARR/MIT, so no copied code, art or Dragon Ball names; every feature gets our own name. Check the "Already in" list on the checklist page before building, so nothing is built twice.

## Picked
- **Healing bugs** (DMZ Medi-Bugs: heal, harden, mastery, a pair that fuses two fighters). **Andy's note:** "a living animal that you can pick up and right click to eat whole, like mice." So a live critter you carry and eat whole (like the pack's mice; reuse an existing small mob model per the no-new-mobs rule, ask Andy which one).
- **Fighter's herb boosts** (DMZ crops; row not ticked but **Andy's note:** "give the boosts to our existing plants till i can make better models"): give the short boosts (ki regen, cleared cooldowns, stamina, mastery, a calm mind) to plants already in the pack/Kitchen, no new crop models.
- **Radars built from parts**: the Wish Radar assembled from found parts, one radar per world's set.
- **A second set of orbs and a second dragon**: **Andy's note:** "yes verdant needs these i thought they were already there". A Verdant World set of seven with its own dragon (our own look) and three smaller wishes.
- **More wishes**: reset story progress, bring back up to three fallen friends, an armour set, a fighter weapon, a pod, materials (no free projectiles).

**Where to start:** ZFighterWishes.java, ZFighterWishExtras.java, ZFighterWishScreen.java, ZFighterSites.java, ZFighterItems.java, Kitchen compat.

## Rules
- One PR, branch `claude/zfighter-gaps-6-items-wishes`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Every Minecraft API checked against 26.3.
- No new original mobs (Andy, 2026-11-19: "i dont want any new original mobs until we can model and texture them better"): reuse player-model fighters, MCA villagers and existing mobs.
- The aura task (zfighter-aura-rework) owns ZFighterPower.java, ZFighterKiAura*.java and ZFighterAuraFlame.java while it is open: keep edits there to one-line hooks, or wait for it to merge. The zfighter-gaps tasks all touch Z Fighter code, so build them one after another (Powers merges one at a time), in number order unless Andy says otherwise.
- No slash commands for player features, no discovery chat text (standing rulings). Abilities.md rows for every new input.
- `python3 tools/pr_checks.py` passes; compile check in the cloud.

## Notes
-
