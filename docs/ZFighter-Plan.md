# Z Fighter: the full build (Andy, 2026-10-14: "add all of it")

Everything below is our own code, art and names, built from DragonMineZ's ideas (GPL-3.0, Forge 1.20.1, so it can't be dropped into the pack). Each stage is pushed to PR #134 and built in the cloud. Andy merges when he says "merge".

## Stage 1: the fighter himself (Powers, Z Fighter only)
- **Rage.** Before he has ever gone Super, at 25% health or less in a fight, a rage meter fills. When it's full he bursts into Rage: fists 40% harder and 25% tougher, no ki cost, 15 s. Every Rage adds hardship.
- **Spirit Bomb, past Super 3.** Learned on the training screen once Super 3 is 25% mastered (20000 TP). In Super 3, hold C and raise your hands: the sphere above you grows from every living thing within 48 blocks (animals, villagers and willing players, each of whom gets a prompt) and from your own ki. Let go and it falls where you aim, its size and damage set by what it gathered. It only hurts what you're fighting (monsters, and players and creatures that attacked you); everything else is shoved aside. Huge crater with mob griefing on. 3 minute cooldown.
- **Technique loadout.** New techniques bought with TP, picked on the training screen for G (quick) and V (charged):
  - G slot: Ki Blast, Ki Disc (a cutting disc that passes through), Homing Volley (5 small bolts that seek), Solar Flash (blinds everything facing you), Ki Barrier (a dome for 3 s that stops projectiles).
  - V slot: Ki Wave, or Ki Bomb (a big slow ball that bursts).
- **Hermit Beans.** A rare crop (seeds from the Tower in stage 5; until then a rare find in jungle chests). One bean fully heals, fills hunger and ki, and clears bad effects. Grows slowly.
- **Training Weights.** Wristbands and boots you wear: you're slower and jump less, and you earn TP 1.5x (2x for heavy weights).
- **Training together.** Friends who are Z Fighters within 32 blocks get 25% of the TP you earn.

## Stage 2: gravity training with Gravity users (after Gravity PR #135 merges, so the two don't clash)
- A Gravity user can place a **Immense Field**: a dome of heavy gravity, 10 blocks across, for 60 s, at the block they aim at. The key combo is picked once #135's keys are settled.
- Inside, everyone is heavy (slower, low jumps, faster falls). A Z Fighter inside trains at 2x TP and 2x form mastery (3x with weights on), and his hits count as heavy for mastery.
- The Gravity user earns mastery for every minute a Z Fighter trains in their field.
- Any Z Fighter pinned by Crush or a Gravity Well also gets the 2x while it lasts.

## Stage 3: training activities
- **Sparring dummy.** Place a dummy and it takes your shape and fights back (it copies your fists, Ki Blast and Flash Step, at your power level). TP and mastery from fighting it; it never kills you (it stops at half a heart).
- **Training post.** A block with four short minigames on a small screen, each paying TP by score: Rhythm (hit keys on the beat), Ki Control (hold a meter in a moving band), Shadow Boxing (block and punch prompts), Precision (hit shrinking targets).

## Stage 4: lineages and the Great Ape
Picked once when the power awakens (respec with the Writ of Rebirth):
- **Earthling:** the training-born. TP 1.25x, Limit Break half price and gentler on health.
- **Warrior-born:** has a tail. Defeats grow him twice as much. Under a full moon, if his tail is out, he becomes the **Great Ape**: 3x size, huge strength, breath beam, raging. Super forms as now.
- **Verdant:** regenerates, can stretch his arms for reach, has a Giant form instead of Super, and can heal others.
- **Frost-born:** evolution forms (Second, Third, Final) that cost no ki to hold, then a Golden form past Final.
Each lineage has its own skin tint and aura colour.

## Stage 5: the world
- **Seven wish orbs.** Scattered across the world (one per region, buried in shrines). A **Wish Radar** points to the nearest. Gather all seven at night to summon the **Eternal Dragon** (our own design): one wish from a menu (great power and TP, revive a fallen friend on the spot, a hoard of emeralds, Rage unlocked early, a new lineage). Then the orbs scatter for 3 days.
- **The Lookout:** a floating temple high over the world. Its keeper blesses you and holds the door to the **Time Chamber**, a white endless dimension where TP comes 2x but food drains 2x.
- **The Tower:** a tall spire to climb; at the top, a hermit master who sells Hermit Beans.
- **The Verdant World:** a green-sky dimension with Verdant villages and traders, reached through the Lookout.

## Stage 6: Kingdoms tie-ins
- **Masters.** Rare MCA elders (like court wizards) teach a technique or a form step, if your alignment from the K skill tree suits them (good masters: 61+; dark masters: 40 or less).
- **Tournaments.** Capitals hold a tournament now and then: a ring, bracket fights between strong fighters (players and champions), prizes in TP and emeralds.
- **Raids and world bosses.** A rogue Z Fighter or a tyrant lands and attacks a kingdom; whoever helps beat it shares a large TP prize.
- **Fusion.** Two Z Fighters do the fusion dance together (both press Z then C within 2 s, facing each other). One body for 60 s, the leader steers, stats added together and a little more. The other watches from inside.

Andy's earlier rulings stay: "no foe fells him twice" lasts only for the rematch, fists only, eats a lot.
