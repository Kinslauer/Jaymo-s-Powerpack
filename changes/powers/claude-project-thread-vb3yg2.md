# the rest of The Aether II - young, glows, saddlebag, cloud skiff
- **Young animals have their own shapes.** Moa chicks, kirrid kids (one per zone), burrukai calves and taegore piglets use
  The Aether II's baby models and baby animations (LGPL-3.0, like the grown ones) with newly painted skins, not a grown
  animal at half size. The other young are unchanged.
- **Lit eyes and marks.** Glitterwings' and shroudwings' marks, the Slider's eye once it wakes, the detonation sentry's
  eyes, and the sentry golem's (and Sky Warden's) eyes and core now show in the dark.
- **Moas differ.** Each moa has its own beak and leg colour (four, plus its kind's own), apart from its feathers. A tamed
  aerbunny wears a red collar with a brass tag.
- **Moa Saddlebag** (new item: five beast pelts, a string and a chest). Used on a saddled moa it hangs behind the saddle
  and holds two rows; sneak and use the moa to open it. The bag and its load drop if the moa dies.
- **Cloud Skiff** (new item: two cold aerclouds over three skyroot planks). Use it on a block to unfold it. It hangs in
  the air where you leave it and flies where you look: forward to go, jump to climb, sneak to get off. It seats two, takes
  a lead, and a blow from a player folds it back into the item. **Only the just can ride it**: with karma under 25 you sink
  through the cloud, and a rider whose karma falls below that is dropped.
- **Sentry golems fidget.** One left standing looks around or checks itself over now and then.
- **Zephyrs keep away from a lit ambrosium campfire** (12 blocks), so a camp with one burning is not gusted off its island.
- **The Slider goes for whoever has hurt it most** this fight, not just whoever is nearest.
- **The floor of the sky woods is alive** (Andy picked "Living things" from The Aether II's world pieces).
  - **Leaf piles** for every kind of Aether leaves. Trees shed them on the ground below; a shed pile rots away in time, greens
    bare aether dirt under it and now and then leaves a rotshroom in the dark. Piles you place yourself stay.
  - **Mulch.** A sapling or a picked berry bush with a leaf pile beside it grows much sooner, and uses the pile up.
  - **Rotshrooms eat leaf piles.** After three they fruit into a cluster: use it to pick two, or leave it to seed another close
    by in the dark. **Roasted Rotshroom** (new food) from a furnace, smoker or campfire.
  - **Moss creeps.** A moss carpet on plain holystone near water and out of the full sun turns the stone mossy and spreads to
    plain holystone beside it. It stops when it runs out of bare stone. Keep moss carpets off a damp plain-holystone floor you
    want kept clean.
  - **Aerbunnies eat berry bushes** down to the stems and breed on them (never past six in one place).
- Code: `client/aether/mob` (baby models, `CloudSkiffModel`, `AetherAnimalRenderer` overlay/glow/babies), `client/aether/dungeon/DungeonRenderer`,
  `aether/mob/{CloudSkiff,CloudSkiffItem,Moa,AetherAnimals}`, `aether/monster/Zephyr`, `aether/dungeon/Slider`;
  `aether/{ForestFloor,LeafPileBlock,SheddingLeavesBlock,RotshroomBlock,CreepingMossBlock}`;
  skins by `tools/gen_aether2_skins.py`, item art by `tools/gen_aether_animals.py`. Docs: `docs/aether.md`.
- Compiles, and all 38 models bake and pose headless. Not played: the skiff's flight feel and speed, the saddlebag menu and the
  rider's seat height are untested in game.
