---
status: changes
for: claude-cloud
from: claude-cloud-menu-redesign-thread
asked: 2026-11-18
claimed_by: hermes-fixer
pr: 1853
text_pass: 
writer: hermes-writer
---

# Heritage paths (picked, for later)

**Andy (2026-11-18):** asked to "brainstorm heritage progressions" for the Paths section of the new picker (menu redesign, docs/MenuRedesign-Plan.md), ticked 62 of 71 rows on https://claude.ai/artifact/W4TR9W13GcvfSums7na6eY (db `picks/andy`) and said: "put this on the vault as well, for pickup later."

**Not built yet.** Build in patches (a few heritages per track). Each kept path needs a `(new)` Lore.md paragraph quoted to Andy for approval. The picker's Paths section (Kingdoms `client/ui/PickerLayout`, HeritageScreen) shows a path's five steps once these exist.

## How paths work (picked)
- [x] **Your deeds open paths**: You don't pick a path from a menu. Each path opens as you live it (a Stoneborn who mines deep opens the Deepdelver), and the picker's Paths section shows how close each one is. _Sympathy: the game teaches the real thing. Built on counters we already keep (blocks mined, kills, travel, prayers)._
- [ ] **Or: choose at your people's elder**: When a path opens, you take it at an elder or shrine of your blood. You keep one path for life, as in your lineage screenshot (“choose one at your first evolution”). _A talk-menu choice at a heritage elder villager. The Rite of Rebirth resets it._
- [x] **Paths deepen with the ladder**: Each path has five steps, one for each king or terror on the ladder of Knowledge. A step wakes once its king falls and you have kept living the path. _Lore already says the blood wakes with the ladder. Five icons per path in the picker, filled as they wake._
- [x] **One path per life**: You walk one path to its end. Children are born with their parent's path already opened (Lineage), but not chosen. _Lineage and Elders carry it. Hybrids can open paths from both bloods._
- [x] **Nothing is lost for leaving it**: A path never fades if you stop. It just stops deepening (stewardship: nobody is punished for leaving it alone). _A rule for every row below._
- Not picked: "Or: choose at your people's elder". Paths open by deeds alone.

## Paths (picked rows; names are placeholders Andy may rename)
### Heartlander
- **The Hearthkeeper** (`hl_hearth`): Tend one home: sleep there, farm there, feed its folk. Crops near your bed grow faster, and the village trusts you sooner. At the end, the village answers your bell in a fight. For us: Feedback: a cared-for village grows, and its growth comes back to you as help.
- **The Wayfarer** (`hl_way`): Walk the roads between realms. Hunger and stamina last longer on roads, and horses tire slower. At the end, you always know the way home. For us: The milestones and journeys we already have.
- **The Borrowed Blood** (`hl_borrowed`): Live among another people. You slowly take one small gift of a blood you've lived beside, and at the end, two. For us: Heartlanders keep the tally of every other blood. This turns that into growth. Uses Prejudice and settlement heritage.

### Stoneborn
- **The Deepdelver** (`sb_deep`): Mine deep and long. You mine faster the deeper you go, and your ore sense grows. At the end, loose stone parts under your hands. For us: Feedback: the mine you dig is the road you run on. Lanterns matter more down there.
- **The Mountain's Shield** (`sb_shield`): Take blows and stand. You gain knockback resistance and hold like stone while braced. At the end, nothing moves you while you crouch behind your shield. For us: Guard and parry for everyone, the stance wheel.
- **The Lanternkeeper** (`sb_lantern`): Light the dark places and keep them lit. Lights you place burn longer, and undead keep back from your lantern. At the end, the lantern's light hurts the dead. For us: Stoneborn picks and lanterns. Fits Dark Nights.

### Tideborn
- **The Reefwalker** (`tb_reef`): Swim and dive. You hold your breath longer and swim faster. At the end, you breathe water. For us: The dry-land flaw stays, and the sea is where they grow.
- **The Saltblood** (`tb_salt`): Sail and fish. Fish bite faster, and a ship you crew sails faster. At the end, a squall calms round your ship. For us: Ships, crews and whaling. Feedback: a good crew comes back to sail with you.
- **The Deep Mother's Voice** (`tb_mother`): Keep the Deep Mother's shrines and offerings. The sea's creatures calm, and the drowned are slower to rise against you. At the end, you call a surge of tide. For us: Their bound patron. A Tideborn priest's road.

### Sylvan
- **The Canopy Runner** (`sy_canopy`): Move through the trees. Drops from branches are longer and silent. At the end, leaves never hurt you as you fall through them. For us: Climbing for everyone. Tree physics.
- **The Elder's Ear** (`sy_ear`): Plant and keep the wood. The wood-wisps answer you more, and the elders remember you. At the end, a wisp scouts ahead for you. For us: Wood-wisps (Youth). Feedback: the wood you plant keeps you.
- **The Thornward** (`sy_thorn`): Defend the woods. Bows draw faster under trees, and thorns snare what chases you. At the end, the wood hides you while you stand still. For us: Stoneborn and Sylvan: the mountain against the wood.

### Verdant
- **The Sunfed** (`vd_sun`): Live in the sun. You eat less in full sun and mend faster in it. At the end, full sun is all the food you need. For us: Sympathy: plants and light.
- **The Moss-heart** (`vd_moss`): Grow things. Crops you tend grow faster, and moss under you mends you. At the end, you can root in place to heal deeply. For us: Farming and seasons.
- **The Ashwalker** (`vd_ash`): Live through burning. Fire burns you a little shorter each time you survive it. At the end, the old weakness is gone. For us: The flaw is the road. Emberborn against Verdant.

### Emberborn
- **The Forgeheart** (`eb_forge`): Smith. Your forge heats faster and your quenches are cleaner. At the end, you can work hot metal in your bare hands. For us: Overgeared and Tinkers smithing, the iron-is-forged rule.
- **The Wildfire** (`eb_wild`): Fight with fire. Your blows sometimes set foes alight. At the end, heat rolls off you in a fight. For us: Emberborn hugs and heat.
- **The Coalbank** (`eb_coal`): Endure the cold. Frost saps you less, and the folk near you stay warm. At the end, you are a walking hearth in the snow. For us: Frostborn and Emberborn. Seasons and cold.

### Frostborn
- **The Glasswright** (`fb_glass`): Make and mend Frostborn tech. Scouters read further, and prosthetics hold stronger. At the end, rigs and mech parts cost you less to build. For us: Rigs, mechs, scouters (which read ki, as you said), prosthetics.
- **The Peakwarden** (`fb_peak`): Live on the peaks and snowfields. You walk on snow and grow stronger in the cold. At the end, you see through blizzards. For us: The heat flaw stays.
- **The Voidfarer** (`fb_void`): Cross between worlds. Pods burn less fuel and you last longer in the dark between worlds. At the end, you need no pod to survive a short crossing. For us: Space travel and pods. Crossing the void was theirs first.

### Nightborn
- **The Warden's Shadow** (`nb_lantern`): Keep the Lantern Warden's faith. You see further into the night. At the end, you go unseen in deep dark while sneaking. For us: Their faith. The lore's own example of a blood waking.
- **The Night Hunter** (`nb_hunt`): Hunt after dark. You hit harder at night and track by heartbeat. At the end, a hunted thing can't hide from you at night. For us: Blood trails and wolf stalkers.
- **The Day-Bearer** (`nb_day`): Walk in daylight anyway. Daylight dulls you less each time. At the end, it no longer dulls you at all. For us: Nightborn and Dawnborn. Stays clear of the vampires' road.

### Warrior-born
- **The Raider** (`wb_raid`): Take what you want. You carry off more loot, and grow stronger when losing. At the end, you rise in fury at low health. For us: The tail and False Super, the blood's old way.
- **The Settled** (`wb_settled`): Live peacefully under one realm. Its folk watch you less and less. At the end, one realm counts you its own. For us: The one hatred everyone shares, softened by staying. Prejudice.
- **The Star-Walker** (`wb_star`): Go back out between worlds. You last longer out there and fight harder off-world. At the end, you call a raiding pod. For us: Far worlds, pods, the dark between worlds.
  - **Andy's note:** "this is what galactic patrolmen are, they are also paladins with an honorable oath."

### Earthling
- **The Disciple** (`ea_disc`): Train under masters. You learn faster from a teacher. At the end, you can learn one ability from a second class. For us: Z Fighter masters, the Keeper and Hermit.
  - **Andy's note:** "you should be able to learn ki control with no aura. so z fighting with flight and increased fist damage but no projectiles"
- **The Self-made** (`ea_self`): Train alone. Shadowboxing and meditation give more. At the end, you break one of your limits once. For us: Training and mastery.
- **The Jack-of-all** (`ea_jack`): Fight with every kind of weapon. You gain a small edge with each kind you've used. At the end, no weapon is strange to you. For us: Weapon forms and finishers.

### Stormborn
- **The Thunderhead** (`st_thunder`): Fight in storms. You strike harder under storm clouds. At the end, you can call lightning in a storm. For us: Wind and Thunderhead.
- **The Gale-rider** (`st_gale`): Ride the wind. Glides and jumps carry further in wind. At the end, you can ride a storm's wind. For us: Gliding and wind.
- **The Rodkeeper** (`st_rod`): Keep the copper rods. Lightning no longer hurts you and charges you instead. At the end, you carry the charge for a strike. For us: Their icon is the lightning rod.

### Dawnborn
- **The Sunsworn** (`db_sworn`): Keep the Crowned Sun's dawn rites. At dawn you bless the folk near you. At the end, your blessing turns the dead at noon. For us: Their bound patron.
  - **Andy's note:** "your blessing becomes an aura that lasts all night"
- **The Savanna Runner** (`db_run`): Run long in open land. Stamina lasts in the open. At the end, you outrun horses on the savanna. For us: Sympathy: savanna hunters.
- **The Noonblade** (`db_noon`): Fight in high sun. Your strength peaks at noon. At the end, your blade burns in full sun. For us: The sun is their strength.

### Endborn
- **The Voidstep** (`en_step`): Walk the End's ways. You blink a short way without a pearl. At the end, you blink twice. For us: An Enderscape feel.
- **The Pearl-keeper** (`en_pearl`): Gather the End's pearls. Your pearls are sometimes not used up. At the end, a pearl takes you home. For us: End cities.
- **The Unmoored** (`en_float`): Fall far and live. Falls slow under you. At the end, you hang in the air for a breath. For us: Fall damage always applies, so this only slows it.

### Dragonblood
- **The Hoarder** (`dr_hoard`): Grow your hoard. Your hoard counts for more. At the end, a great hoard makes you terrible. For us: The hoard already exists, and this deepens it.
- **The Wyrmtongue** (`dr_tongue`): Seek out dragons and spare them. Dragons are slower to turn on you. At the end, a dragon may answer your call. For us: Saint's Dragons and the dragon taming we have.
- **The Scaled** (`dr_scale`): Take fire and blows. Scales thicken on you as natural armour. At the end, dragonfire turns off your scales. For us: Their own look's scales.

### Deepborn
- **The Surfacer** (`dp_surface`): Bear the surface. Sun and noise grate on you less. At the end, they no longer do. For us: Deepborn and everyone above.
- Not picked: The Listener, The Warden's Kin.

### Divine-born
- **The Mender** (`dv_mend`): Heal others. Your healing magic and bandages work better. At the end, your touch mends the downed. For us: Restoration magic, downed help-ups.
- **The Judge** (`dv_judge`): Stand against the wicked. You strike the wicked and the dead harder. At the end, the wicked cannot hide from you. For us: “The wicked know them”: crime, corruption.
- Not picked: The Shrine-keeper.

### Hollow
- **The Gravewalker** (`ho_grave`): Walk among the dead. The risen are slower to notice you. At the end, they don't, unless you strike. For us: The dead, risen corpses.
- **The Revenant** (`ho_rev`): Refuse to stay down. Your downed time lasts longer. At the end, you rise once a day from what would kill you. For us: Downed and rising.
- **The Warmed** (`ho_warm`): Live among the living. The grave's cold fades, and villages fear you less. At the end, they no longer know you on sight. For us: Hollow and the living. Prejudice.

### Rubber-folk
- **The Bouncer** (`rb_bounce`): Fall and bounce. You bounce higher and turn falls into bounces. At the end, a fall becomes a leap. For us: Their knock-and-bounce.
- **The Swallower** (`rb_swallow`): Swallow foes. You swallow bigger ones and keep more of what they had. At the end, you can hold two traits at once. For us: The stomach system.
- Not picked: The Fair's Child.

### Goblin
- **The Cutpurse** (`gb_purse`): Steal and get away. Your picking and lockpicking get quicker. At the end, nobody feels your hand. For us: Pickpocketing and lockpicking.
- Not picked: The Barrelwright, The King's Own.

### Orc
- **The Bloodied** (`or_blood`): Fight hurt. You fight even harder the more you bleed. At the end, wounds don't slow you. For us: Their existing flaw and gift.
- **The Ranger** (`or_ranger`): Range ahead of the war camp. You track by sight and smell. At the end, you're never surprised. For us: Orc rangers.
- Not picked: The Shaman.

### Beastfolk
- **The Fang** (`bf_fang`): Hunt. Bare-hand blows hit harder, and you track game. At the end, you have the beast's own weapons. For us: Hunting and carcasses.
- **The Den-keeper** (`bf_den`): Raise animals. Beasts tame faster for you and stay loyal. At the end, a pack follows you. For us: Stewardship: looked-after animals stay.
- **The Tamed** (`bf_tamed`): Live in the towns. The beast shows less, and folk jeer less. At the end, the towns forget it. For us: Prejudice.

### Bio-built
- **The Tinkerer's Heir** (`bb_heir`): Rebuild yourself with parts. Metal repairs mend more. At the end, you fit your own upgrades. For us: The Mad Tinkerer, android repairs.
- **The Unmasked** (`bb_mask`): Live among those who hate you. Their disgust fades slowly. At the end, one village calls you theirs. For us: Every kingdom finds them repulsive. This is the slow road through that.
- Not picked: The Perfected.

## Notes
- Andy's notes carry new lore: the Star-Walker road is what the Galactic Patrol's patrolmen are, and they are also Paladins with an honourable oath (needs a Lore.md paragraph, approved by Andy). The Disciple can learn ki control with no aura: Z Fighter flight and stronger fists, no ki projectiles.
- 2026-11-19 14:49 heritage-paths's claim had no sign of work for 30 min: handed back as open.
- 2026-11-19 20:22 PR #1853 is open but the Kingdoms build fails: fix it on claude/heritage-paths, get checks green, set in-review.
- 2026-11-19 17:09 Text pass: kingdoms/lang/parts/heritage-paths.json — made steps 2 and 3 distinct (Hearthkeeper folk now "welcome you sooner"; Mountain's Shield "less still with your shield raised"; Reefwalker clauses reordered). kingdoms/lang/parts/heraldry-armour-items.json — restored 19 item-name keys for items still registered in code (tabard, quilted_coat, hide_armor, bronze_armor, laced_lamellar, quilted_cotton_armor, veiled_helm, faith_cuirass, oath_sleeves, spiked_spaulders, etched_cuirass, gilt_banded_*, fur_lined_*) so they show names instead of raw translation keys. changes/powers/heritage-paths.md — rewrote to player-facing wording without code class names. Kingdoms build was already red on this branch before my changes (HeritagePaths.java:614, EntityType tag API missing); I changed no code.
- 2026-11-19 22:37 Review (head 433e735, vault coordinator): not reviewed - no PR checks ran at all on this head (0 check runs; GitHub shows mergeable 'unknown', likely a merge conflict with master). Bring master in, push, and get the checks green. Note the PR also waits on Andy's yes to its four (new) Lore.md paragraphs: raise them in the task Notes so the coordinator can ask him.
