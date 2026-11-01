# More healing: herbs, washing rags, rest, healers, prayer, brews

Andy (2026-11-01): "we need more healing methods and items". He picked all ten ideas offered, and added:
- "make the herbs common so you have an early method to treat wounds"
- "allow an inefficient method of purifying rags, to prevent festering, maybe involving water"

Everything stays medieval (no modern medicine, see `docs/Wounds-Plan.md`) and keeps the rule that you never heal to
full without help: self-care stops at 60% of a part (`Stomach.SELF_HEAL_CAP`), while another's care (a healer, a cot
in an infirmary, a brew someone made) can go past it.

## Where it lives
- **Jaymo's Powers, `core/wounds/`** (with the rest of the kit): herbs, poultices, rag washing, the satchel, campfire
  rest, hot springs, sickbed foods. Herbs are ours, not Kitchen's, so poultices work in any pack.
- **Jaymo's Powers, `alchemy/`**: two healing brews.
- **Jaymo's Kingdoms**: paid healing from town healers, the infirmary cot, the shrine. They reach Wounds through
  `PowersCompat`.
- Tuning numbers are `static final` constants at the top of each class. Every new route shows in JEI.

## 1. Wild herbs (common, early)
Five herbs grow wild as small bushy plants, common enough that a new player finds some on the first day:

| Herb | Grows in | Used for |
|---|---|---|
| Comfrey (knitbone) | plains, meadows, forest edges, riverbanks | knitbone poultice |
| Yarrow | plains, meadows, savanna, hills | yarrow powder |
| Plantain | almost every grassy biome | burn salve |
| Wild garlic | forests, dark forests, swamps, taiga | garlic poultice |
| Chamomile | plains, meadows, flower forests | chamomile tea |

- Worldgen: scattered patches like vanilla flowers, in every temperate biome, a patch every few chunks.
- A plant has 4 growth stages. Right-click a grown one to pick 1-3 herbs (like sweet berries); it drops back a stage
  and regrows. Breaking it gives the plant back. The herb itself plants a new one on grass, dirt or farmland, so
  herbs can be farmed in a garden.

## 2. Poultices and powders (all crafted from herbs)
Used like the rest of the kit (use: body screen; sneak + use: worst part; hold on another: kneel and treat).

- **Yarrow powder** (1 yarrow -> 2): packed into a cut, stops bleeding on a part at once (not a stump). The early
  bandage: no wool needed.
- **Knitbone poultice** (2 comfrey + leaves -> 2): on a broken arm or leg, the break knits twice as fast for 10
  minutes, and the part mends a little. Works best over a splint.
- **Burn salve** (2 plantain + leaves -> 2): heals burns: puts out fire, heals the part over time (fire and lava hits
  land on the body), eases frostbite.
- **Garlic poultice** (2 wild garlic + leaves -> 2): cleans a part like honey or spirits, and that part can't fester
  for 10 minutes.

## 3. Washing rags (slow, but it works)
A rag is dirty and may fester. Two ways to get a **clean rag**, which dresses like a rag but never festers:
- **Wash it in water:** hold use with rags while standing in or facing water. One rag every 4 seconds, and a quarter
  of the time it comes out still dirty. Slow and wasteful on purpose.
- **Boil it:** use rags on a water cauldron with fire, a campfire, magma or lava under it. 20 seconds a batch, up to
  4 rags per water level, always clean.

## 4. Healer's satchel
Leather and string. Holds up to 64 medicine items (bundle-style, medicine only). Carried anywhere in your inventory:
treatments take a quarter less time, and when the stack in your hand runs out the next one comes from the satchel.

## 5. Campfire rest
Sit or sneak still within 3 blocks of a lit campfire: every few seconds the most hurt part heals a little, up to 60%,
and pain eases. Never sets a bone. Cosy embers on the screen edge while it works.

## 6. Hot springs
Small worldgen pools of water over magma in mountains, taiga and snowy biomes, with steam rising. Soaking in one (or
any water with magma right under it) eases pain, warms frostbite, cures the dazed effects, and heals parts slowly up
to 60%.

## 7. Sickbed foods
- **Bone broth** (bowl, bone, any herb, water bottle): a small heal spread over the worst parts, and it cools a
  festering fever for a while.
- **Chamomile tea** (bowl, 2 chamomile): calm sleep; the next sleep heals every part twice the usual share. Eases pain
  like willow-bark tea but weaker.
- **Posset** (milk, sugar, bowl): cures nausea and poison, a small heal.

## 8. Healing brews (Alchemy)
- **Bone-knitting elixir:** sets and knits your worst broken limb over two minutes.
- **Draught of vigour:** every part heals over a minute, past the 60% cap (it's another's craft).
Both are made in the alchemy cauldron with herbs and Alchemy essences, and unlocked by research.

## 9. Paid healing in town (Kingdoms)
Talk to a town healer (cleric villagers, the Restoration healers) and pay emeralds to be treated fully: bleeding
stopped, breaks set and knitted, festering cleaned, parts healed to full. The price grows with how badly you're hurt.
Severed limbs can't be paid back.

## 10. Infirmary cot (Kingdoms)
A low cot (wool, planks, sticks). Sleeping in one heals like a bed, and if it's an infirmary (a healer villager works
within 16 blocks, or a brewing stand or a Restoration caster is near) the sleep sets and knits breaks and heals parts to
full.

## 11. Shrine (Kingdoms)
A small stone altar with candles. Hold use to pray for 5 seconds: once a Minecraft day, a small heal on every part and
festering cleared. Crafted (stone bricks, candles, gold nugget).
