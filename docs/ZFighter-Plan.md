# Z Fighter: the full build (Andy, 2026-10-14: "add all of it")

Everything below is our own code, art and names, built from DragonMineZ's ideas (GPL-3.0, Forge 1.20.1, so it can't be dropped into the pack). Each stage is pushed to PR #134 and built in the cloud. Andy merges when he says "merge".

## Stage 1: the fighter himself (Powers, Z Fighter only)
- **Rage.** Before he has ever gone Super, at 25% health or less in a fight, a rage meter fills. When it's full he bursts into Rage: fists 40% harder and 25% tougher, no ki cost, 15 s. Every Rage adds hardship.
- **Spirit Bomb, past Super 3.** Learned on the training screen once Super 3 is 25% mastered (20000 TP). In Super 3, hold C and raise your hands: the sphere above you grows from every living thing within 48 blocks (animals, villagers and willing players, each of whom gets a prompt) and from your own ki. Let go and it falls where you aim, its size and damage set by what it gathered. It only hurts what you're fighting (monsters, and players and creatures that attacked you); everything else is shoved aside. Huge crater with mob griefing on. 3 minute cooldown.
- **Technique loadout.** New techniques bought with TP, picked on the training screen for G (quick) and V (charged); a newly learned one (bought, or taught by a master) goes on its key at once, and works there even before that key unlocks:
  - G slot: Ki Blast, Ki Disc (a cutting disc that passes through creatures and blocks for 60 blocks, stopped only by obsidian-hard, unbreakable or block-entity blocks), Homing Volley (5 small bolts that seek), Solar Flash (blinds everything facing you), Ki Barrier (a dome for 3 s that stops projectiles).
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

## Stage 6: Kingdoms tie-ins (built: Abilities rows 261-265)
- **Masters.** Rare MCA elders (like court wizards) teach a technique or a form step, if your alignment from the K skill tree suits them (good masters: 61+; dark masters: 40 or less).
- **Tournaments.** Capitals hold a tournament now and then: a ring, bracket fights between strong fighters (players and champions), prizes in TP and emeralds.
- **Raids and world bosses.** A rogue Z Fighter or a tyrant lands and attacks a kingdom; whoever helps beat it shares a large TP prize.
- **Fusion.** Two Z Fighters do the fusion dance together (both press Z then C within 2 s, facing each other). One body for 60 s, the leader steers (they swap at 30 s), stats added together and a little more. The other watches from inside.

Andy's earlier rulings stay: "no foe fells him twice" lasts only for the rematch, fists only, eats a lot.

## Training slowdown (the user: "Z Fighter scaling should be decreased across the board... take longer and more training, and diversified methods")
- **Weaker:** every stat's full-level bonus is a third of what it was (Strength +3.35 fists, Toughness -10%, Vitality +6.7 health, Ki Power +25%, Energy +50 ki); forms keep a third of their bonus over 1.0 (Super fists +20% / ki x1.17 / speed +8%, Super 2 +33% / x1.27 / +12%, Super 3 +53% / x1.4 / +15%); mastery's power +5% (was 15%); Limit Break +10/20/33% fists, +3/7/10% speed; Rage +25% fists; corruption +5%; each foe that beat you +0.1 fists. Punch cap 7, rising to 9 (was 8 to 11). Ki Wave 8-18, Ki Bomb 10-20, Ki Disc 6, Spirit Bomb 20 + 0.25 per energy; hold-combo finishers 1.4-1.6x fists.
- **Slower shop:** every TP source pays a quarter (hits 0.5, kills 0.5 + 1/16 of max health, 1 per 20 s, 1 per 80 blocks, shadowboxing 0.25 a second, drills 0.5 a point, sparring, tournaments, raids, wishes, the Keeper, masters). Stat levels cost 30 + 15 per level bought. Form mastery comes at half the rate.
- **Stats train themselves** (`ZFighterGrowth`, attachment `zfighter_growth`): each stat's own kinds of training fill its bar; a full bar is a level (same cap of 50, not counted in the shop's price). Every method tires through the in-game day (each 12 points it gives halve the next, down to 5%), so varied training is fastest. The methods and their rates are in Abilities row 221. `/powerdebug zfighter train <method> <points>` and `train newday` test it (survival).
- Existing characters keep every level they bought; each level just gives the new, smaller amount.

## Ape rage (pack 0.15.148)
Andy: "what does an ape takeover look like to you, add that and lemme see". `ApeRage` (client `ApeRageClient`): a Z
Fighter's Great Ape builds rage (0.6/s x (1 + primal/50), +1.2 per damage taken, +6 per kill, +25 when crippled). Full,
the ape throws a fit: the wild ape's berserk walk takes the wheel (anything alive in sight, players and villagers too)
for 10 s + 0.2 s per primal; hammer movement keys to break it (15 presses + primal/4, the bar drains). Win: rage back to
30, primal -4. Ride it out: rage 50, primal +3. Primal (kept) also grows +5 a change, +1 a minute as the ape, +1 per
person killed in a fit; at 100 a fit lasts until the moon lets go (60 presses). HUD: rage bar under the crosshair area,
fit bar while it has you. Debug: `/powerdebug zfighter ape primal <n>`, `/powerdebug zfighter ape fit`.

## Super 4 (pack 0.15.148)
Andy: "If you are able to fight it off successfully multiple times give access to a new form hidden until you do, a super
4, combining the primal ape look with the character, keeping the long hair, but it goes red instead of gold, crackling
with fire and sparks". Win `ApeRage.SUPER_4_FITS` (5) ape fits (`TAMED` count) and `SUPER_4_OPEN` sets; Saiyan
(warrior) lineage only. Hidden from the training screen until then. C from your highest unlocked form goes straight to
Super 4 (90 ki); C in Super 4 is the Spirit Bomb. Stats are the last entry of each form array in `ZFighterPower`
(fist 0.75, ki 1.55, drain 0.22). Hair stays long (Super 3 shape) and turns red via `ZFighterLineage.COLORS`; red fur
on back, shoulders and arms from `ZFighterPrimalFur` (texture from `tools/gen_primal_fur.py`); flames, small flames
and lava embers crackle off you. No mastery for Super 4. Test: `/powerdebug zfighter ape super4`.

## Primal Super, settled aura, six hair sets (pack 0.16.5)
Andy (2026-10-25): Super 4 renamed **Primal Super** (`/powerdebug zfighter ape primalsuper`). The full storm of aura,
sparks and fire now only runs for `POWER_UP_TICKS` after going into a form (the power-up blast); after that a form
settles to a thin aura shell out past the body (`settledAura`) and the rising ground debris, both stronger each form,
with Primal Super's flames licking at the aura's edge. The fur layer sits at 0.36 so MCA clothes don't hide it. Six
Super hair sets (swept, wild, flame, gale, crown, fringe; `tools/zfighter_hair/hair.js`), one per fighter by UUID.

## Ape punch, ape camera, Fusion on the Mode slot (pack 0.16.6)
Andy (2026-10-25): the Great Ape no longer swings or digs; left click is `ApeSmash` (hold to draw a fist back, full at
1 s; let go and it slams where you look, a blast scaled by the charge and the ape's attack damage). The ape vanished in
third person because Shoulder Surfing skips drawing you when its own (unscaled) camera is within your width:
`ShoulderSurfingGiantMixin` keeps you drawn while `GiantCamera` is in use. The powers screen's Mode slot is named for
each power's Mode line ("No Mode" when there is none); a power can give it its own job (`PowerClient.screenMode`, lang
`power.<id>.toggle.mode`): Z Fighter's is Fusion (taps Z, then C). The Primal Super fur is now worn like a costume
(`CostumeModels.worn`), so it follows MCA's body and every animation.
