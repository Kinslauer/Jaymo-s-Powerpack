# Wild garlic drives out parasites; the Great Ape steps over the ground instead of sticking
- **Wild garlic clears a parasite** (Andy: "another way to clear parasites ... a player can actually reach"). Wild garlic,
  the common forest herb, now eats like sweet berries: used on soil it's planted, used in the air it's eaten (even on a
  full stomach, as a remedy). Each clove takes a third off a parasite from foul water (Jaymo's Kitchen's), so three
  clear a fresh one. The first ones say "The raw garlic burns going down. The parasite squirms."; the last says "The
  garlic drives the parasite out." Hot chocolate and milk still clear it at once. Its tooltip and JEI page say so.
  `Herbal#garlicEaten` (tuning: `GARLIC_PURGE_TICKS`, `GARLIC_FOOD`), `KitchenCompat#purgeParasite` (finds the effect
  by id, no Kitchen classes), `Afflictions#consumed`, `Herbs`.
- **The Great Ape no longer gets stuck** (Andy's clips: an ape crawling through a forest and pinned on a hillside in a
  village). At 30 blocks tall he still stepped up only a player's 0.6 of a block, so every one-block rise, path edge
  and house wall stopped him and he could only hop. He now steps up to 6 blocks more as he grows, and still while he
  shrinks back; the wild ape (a Warrior-born with no Z Fighter power) 5, like a villager's ape. Tuning:
  `ZFighterLineage.APE_STEP` / `WILD_STEP`; `ZFighterPower#refresh`, `ZFighterLineage#attributes`.
- The voices for the ape and the xeno forms are Jaymo's Kingdoms' (its own note).
- Built; not played. Not tested: eating garlic with Kitchen's stomach, the parasite messages, how the ape's step feels
  on a hillside and in a village, a tall step on the server (the attribute is synced, so client and server should
  agree).
