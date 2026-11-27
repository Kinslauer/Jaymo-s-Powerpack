# Hunter's Road 6, part A: toxicity, tolerance, the gauge, five harms, hunter's eyes
- **Toxicity** (`alchemy/toxic/Toxicity`): strong brews (graded Fine and up, or any potion carrying a toxic strength) leave a load in the body. Past half the view is tinged green and the pulse slows; past the harm line (three quarters) it hurts every few seconds, faster near the top; only a full load can kill. No bar: dark veins round the eyes on the skin (everyone sees them). Ordinary load fades in minutes; a monster brew's held load stays till it ends. Milk clears the ordinary load, a golden apple or a healing draught takes some off. Sits beside Corruption without touching it.
- **Tolerance**: a second number. Doses stronger than it raise it slowly; it cuts what doses add, moves the harm line up, and fades slowly.
- **Sweat it out**: a hot spring soak, or sitting still by a lit campfire (low pose or a stool), draws the ordinary load out faster.
- **Toxin gauge** (charm bauble, no bonding): held or worn in the charm slot it shows load, tolerance, the next line and mutagens (none yet).
- **Harms** (`alchemy/toxic/Harms`): toxin, acid, flammable, cripple and hunt as effects (`jaymospowers:harm_*`); hooked into the hurt mixin for cripple and hunt.
- **Hunter's eyes** (`HunterEyes`): bright slit pupils on any skin, for good, earned by a held (monster brew) dose.
- API for part B documented at the top of `Toxicity`. `Baubles.add` is now public.
- Built; not played.

# Hunter's Road 6, part B: the hunter's alchemy
- **Draughts** (`alchemy/hunter/Draughts`, `Draught`): slow mending, grey sight, savagery, keen edge, deep breath, thorned blood, thin venom, plenty and the cleanser, each in three grades (the crucible recipe's `grade`: Plain, Fine, Expert; the Brewer's Alembic distils a draught a grade better). Each adds toxin. The cleanser clears every harm and the ordinary load (the top grade every bad effect) and leaves a monster brew's held load alone, then adds a small load of its own.
- **Monster brews**: marsh fury, grave hunger, pack marrow, fog shroud, imp gallop, troll blood: ten minutes, made from the organ Kitchen carves off a grown monster, with a heavy held load (earns hunter's eyes). Mire hag, grave hag, gnawer leader, fog-wight and imp warrior do not exist yet: patches 3 to 5 add a file to Kitchen's `monster_organs` table for each.
- **Refill** (`Refills`, `AlchemyBenchBlock`): a drunk brew leaves a spent phial that remembers it; strong spirit (`#jaymospowers:strong_spirits`, Kitchen's still) refills three a bottle at the new alchemy bench, on waking, or sitting still at a campfire. Spent casings are there for patch 7.
- **Formulas** (`Formulas`): a brew needs its formula read first (a paper, found in old chests or bought from the wandering trader); a new Formulas tab in the Codex shows what is known.
- **Leans** (`Leans`): every poison leans heat, sea, decay, cold, forest or Nether; learned by taking a lot of it or by the testing needle on a strong creature near death; the Scrying Lens shows it only after. `Leans.leans(player)` is for patch 11.
- **Paste ladder** (`Pastes`): poison paste ground in the mortar, toxic paste in the crucible, pure toxin in the Brewer's Alembic. Kingdoms' five vials are now made from them.
- **Hunter's flask** (`HuntersFlask`): four doses of one potion or draught, each working twice as long (netherite three times); a Refill enchantment fills it overnight for a sleeper at Knowledge level 3.
- **Lab furniture** (`LabFurniture`): vial rack, bell jar, copper scale, loose papers.
- Grey sight outlines the living in the dark for the drinker alone (`DarkSightClient`).
- Reincarnate: the wipe already removes every Powers attachment, so part A's and B's state go with it (no change needed).
- Built; not played.
