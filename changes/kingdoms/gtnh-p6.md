# GT New Horizons picks, patch 6 - marked monsters, mother and flying spiders, turned villagers' trades, punji stakes
- **Marked monsters** (`MarkedMonsters`): a hostile monster may rise marked with 1 to 4 marks, at a chance by the world
  tier of the players near it (1% at Hearth up to 20% at Legend, more marks at higher tiers): Vengeance (a quarter of a
  melee blow goes back to the striker), Regen, Sapper (its blows drain food), Webber (webs you where you stand), Ghastly
  (a fireball now and then), Blastoff (throws you up), Ninja (now and then steps out behind you), Sticky (knocks your
  weapon out of your hand to the ground near by), Rust (your armour wears faster), Storm (rarely, lightning near you,
  under open sky, after a crackle of warning). No words name a mark: each shows by a faint aura of its own typed
  `Fx` mote, haze or spark and its own sound; three or more marks bring a boss bar (`BossBars`, only for those who
  struck it). A little more loot and experience for each mark. Never on bosses, captains, villagers and folk, thralls,
  or anyone's own beast, and never on a spawner's mobs (no marked farms). Storm and Ghastly marks only turn on players,
  and a Ninja steps behind only a foe it can see.
- **Captains** (Andy: "give these to captains"): the ten marks are also `Captains.Trait` strengths, rolled with the
  others (a heavy-footed or iron-clad one is never a Ninja, one who fears fire never Ghastly), named in the journal and
  told under questioning in each dialect; they act through the same code from `CaptainWays.step`.
- **Mother spider** (`wild/SpiderKinds`, `SpiderMinds`): now and then a temperate spider is a mother, full grown and
  slower, her brood on her back (a wolf spider's way); killed, she bursts into 3 to 6 tiny, quick spiderlings that go
  for her killer and drop nothing.
- **Flying spider**: in forests and taiga, a jumping spider that sits up in the canopy and watches; prey below and near,
  it leaps down on a dragline, and the line hauls the prey up to 4 blocks off the ground before it lets go. Skins for
  the mother, spiderling and flyer from `tools/gen_spider_kinds.py`.
- **Turned villagers remember** (`TurnedVillagers`, MCA only): an MCA villager who turns keeps their trade's trick:
  fishers cast a rod and reel you in, fletchers, guards, archers and hunters shoot their old bow from a quiver that
  runs out (`MobAmmo`; the arrows can be picked up), farmers throw their hoe once (it lands as the hoe). Plain
  zombies, the old dead and skeletons get nothing new.
- **Punji stakes** (`creatures/PunjiBlock`): whittled from 4 sticks (or 4 bamboo) with a knife in the grid, the knife
  handed back worn (`creatures/WhittlingRecipe`, JEI's crafting page). Whatever walks into them is slowed and stabbed at
  each step, a fall onto them hurts as a stalagmite does, and mobs go round them when they can. Diggers set them in
  their pits (in place of pointed dripstone) and along a new bolt-hole tunnel out of each warren's chamber, and are
  never stabbed by them (the bolt-hole is dug only where it breaks into no water and takes no chest); barbarian camps are founded with patches of them round the outside of the palisade.
- Built; not played.
