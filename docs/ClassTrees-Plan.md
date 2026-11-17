# Plan: class levels and class trees (2026-11-17)

Source: the vault's `Inbox/class-trees-plan.md` (the full design, kept as written). Pick page:
https://claude.ai/artifact/2SkYD55StgJfJik4QdXBSL (db doc `picks/andy`). Andy ticked 92 of 93 rows; the one row not
picked is **Clockwork Scope** (`ct-bench-scope`), so the Workshop bench has no scope.

## Andy's notes
- Page note: "There should be an option to spawn without a class and learn one later." A **No class yet** choice on the
  class step of the life story; a classless player has no class key, no class tree tab (it shows how to find a teacher
  instead), and learns a class later from that class's teacher (`ClassTeachers`) at level 0. Once learnt it is the same
  as having picked it at the start.
- Speed Load (`ct-mm-load`): "They should animate a spin or flourish." The instant reload plays a gun spin.
- Last Rites (`ct-cle-rites`): "Villagers can do this too." Village priests (and clerics among MCA folk) read rites over
  the village's dead before burial, so those dead never rise as Unburied.
- Fox and Stag (`ct-dru-foxstag`): "Use the vanilla fox and existing deer." The shapes reuse the vanilla fox model and
  the deer already in the pack, no new beast models.
- Pack Leader (`ct-dru-pack`): "You should grow larger than their model as well." In a beast shape with Pack Leader you
  are drawn and sized a little larger than the beasts that follow you.

## Patches (one branch, a commit per patch)
1. **Class levels** (framework): `ct-level`, `ct-points`, `ct-forks`, `ct-practice`, `ct-second`, `ct-respec`, `ct-tab`,
   `ct-move`, `ct-xp`, and the No-class-yet start (page note). Level and XP per class beside the pillars, class points,
   crossroads, the K screen's class tab, the old class pages (Rogue's Arts, Spellcraft, Sorcery and Forbidden Arts,
   Archery, Warfare) moved into the trees with points refunded and existing characters migrated.
2. **Sorcerer** (`ct-sor-*`) and the **Thief, Mage and Ranger forks** (`ct-thief-fork`, `ct-mage-fork`, `ct-ranger-fork`).
3. **The Workshop**: Marksman (`ct-mm-*`, Speed Load's flourish), the bench (`ct-bench-smith`, `-upgrades`, `-trick`,
   `-kit`; no scope), Tinkerer (`ct-tink-*`, including the tier eye).
4. **Warlord and Brute** (`ct-wb-*`): Composure and Rage gauges and the mirrored trees.
5. **Rider** (`ct-rid-*`).
6. **Monk and Paladin** (`ct-monk-*`, `ct-pal-*`): Catch an Arrow and Lay on Hands as the starting moves.
7. **Cleric, Druid and Warlock** (`ct-cle-*` with villagers' last rites, `ct-dru-*` with vanilla fox, pack deer and the
   larger Pack Leader, `ct-war-*`).

## As built
### Patch 1: class levels (framework)
- `ClassTrees` holds level and XP per class in the pillar progress map (`class_<key>`), level cap 25, a level costs
  30 + 15 x level XP, one class point per level, a milestone fanfare every 5 levels. Second class: XP at
  `Classes.SECOND_SHARE`, ranks work at that share (`ClassTrees.strength`).
- Trees are laid out in `trees/` (one file per class, `TreeLayout` calls them); this patch fills the moved pages:
  Thief (Rogue's Arts), Mage (Spellcraft, Arcane/Fire/Frost columns), Sorcerer (Sorcery, Forbidden Arts), Ranger
  (Archery), Warlord and Brute (Warfare). My calls: Mortal Strike is in both the Warlord and Brute trees; Throw Net
  (Warlord) and Bear Trap (Thief) are shared with the Ranger. `RpgSeriesGate` follows the same owners.
- Migration on join: a moved rank owned by a held class is paid from that class and the class's XP is raised to cover
  it; ranks no held class owns are removed and their pillar points come back; taught ranks stay free.
- Respec gives class points back and keeps the level (ranks are counted, not stored as spent); the Writ wipes both.
- XP sources wired now, every class: see `changes/kingdoms/claude-project-thread-kb4272.md`. Practice (`ct-practice`)
  has its hook (`ClassTrees.practice`); each class's starting move takes it in that class's patch. Cleric rites XP
  comes with Last Rites (patch 7). Mage "spells that land" is counted as magic damage dealt.
- No class yet: youth answer "Drifted" and a "No class yet" button on the class screen. Classless players learn a first
  class from **any villager of that class's trade** (the teachers' trades list, `SecondClass.offers`) for 10 silver,
  rather than only from a named master, so it can be found in any village.

### Patch 2: Sorcerer, and the Thief, Mage and Ranger forks
- **Sorcerer** (`Sigils`, `Spellcraft`, `trees/SorcererTree`). A tap of J throws the **sigil** (`ct-sor-sigil`): a glyph flies
  off the blade (or the open hand) until it meets something and bursts with the element through `Spellcraft.strike` (fire
  alights, ice freezes, lightning chains, earth staggers, air launches, water douses, and puts out fire blocks in the burst).
  Damage 3 x practice (`ClassTrees.practice`, so it grows with the level), the old bolt's mana price, a 1.5 s wait. It
  replaces sneak+J's bolt, which is gone. **Sigilcraft** (3 ranks): +0.75 burst radius, +6 blocks of flight, -15% wait a
  rank. **Imbue** (level 5) is held now: holding J with a blade in hand and nothing of the element to draw soaks the blade
  for 2 minutes. **Ward Sigil / Twin Imbue** (10): sneak+J lays the sigil on the ground (two at once, a minute each; bursts
  at 1.5x under the first foe) / the off hand's blade takes the same imbue. **Elemental Burst / Lingering Imbue** (15): J
  held on a blade still burning ends the imbue in a blast round you (8 damage scaled by what was left) / imbues last twice
  as long. **Absorbing** is the old Sorcery perk renamed (absorbing, the barrage, and the element's own workings: Storm
  Ring, Bolt Step, Bulwark, Tide Pearl, Harrying Gale, death's and blood's keys); **Forbidden Arts** stays after it. **Etched
  Blade** (top, 20): while imbued, every 4th combo link throws a free sigil (hooked into `CombatArts`' chain).
- My calls: the old Sorcery perk stays the save key (renamed "Absorbing") so nobody loses ranks; a Sorcerer who had it before
  trees keeps the Imbue free (`ClassTrees.keptIf`, a kept rank survives a respec). Restoration's keys (Mend etc.) are its
  starting move instead of a damaging sigil, and stay free; death's tap still raises a body (Andy's earlier call), so a
  Death Sorcerer's sigil is on sneak+J. Wild bloodline sigils take a random element. Sorcerers who don't absorb (the wild
  bloodline, or Absorbing not learned) now send a tap/hold to the server too, so the hold can imbue.
- **Thief** (`ClassForks`): **Assassin** (sneak attack on the unaware: blow x2.5 + 4 instead of x1.5 + 2) or **Acrobat**
  (climbing 1.6x as fast by a mixin on the climb, +4 safe fall, fall damage halved, and +0.5 step height at a run, so a
  block is vaulted in stride).
- **Mage**: **Evoker** (magic, blasts and fiery non-arrow shots from the Mage hit 1.3x, and never hurt players, townsfolk,
  golems or their own beasts unless those are after them) or **Warder** (Warding, Sanctuary and Bulwark cast from the staff
  last 1.5x; Warding's absorption a step stronger).
- **Ranger**: **Hunter** (+25% on the favoured foe, its trail read 1.5x as far) or **Beast Master** (sneak+J looking at a beast
  you tamed bonds it; it gains health, bite and size with your Ranger level, at the second-class share for a second class).
- Gaps: the Acrobat's "vaulting" is a step up at a run, not a faster version of Jaymo's Powers' own vault (that lives in
  Powers' parkour; changing it would mean a Powers hook). The Warder's longer wards are our own wards; the RPG Series' Frost
  Shield is the Wizards mod's spell and isn't lengthened.
- Lore: the sigil is the canon's typed "sigil" (a working laid as a circle of marks); nothing new to place.

### Patch 3: the Workshop (Marksman, the bench, Tinkerer)
- **Trees** (`trees/MarksmanTree`, `trees/TinkererTree`): the Marksman's column on the left (cols 0-1), the bench in the
  middle (col 2, nodes open to both classes), the Tinkerer's on the right (cols 3-4). Each class's tab shows its own column
  and the bench.
- **Marksman** (`Workshop`). Practice: a Marksman reloads faster with each level (`Workshop.loadRate`, the early guns and
  WeaponMod's). **Steady Aim** (3): crouched and still for half a second, +10% damage, +1.5 degrees of aim assist and 25%
  less stray from an early gun a rank. **Speed Load**: every 3rd gun kill primes the next reload, which is done at once with a
  spin of the gun (a new `SPIN` gesture: the arm flicks in third person, the gun turns twice over in first person).
  **Called Shot / Quick Shot** (fork, 10): sneak and fire, and the height the shot strikes picks head (x1.75), arm (a mob
  drops its weapon) or leg (crippled and slowed for 6 s); or, with a loaded gun, a mob that swings at you or a friend within
  10 blocks takes a ball from you, spending the gun's load (once per short rest, `Rests` pool `quick_shot`). **Precision**
  (top, 20): a shot on an unaware mob (`Senses.unaware`) or a crippled foe hits x1.5 with the critical's sparks and sound.
- **The bench.** **Gunsmith**: guns, barrels and bullets come out of a crafting grid only for a Gunsmith (`TreeLocks.mayCraft`),
  and only a Gunsmith forges a gun barrel at the anvil. **Gun Upgrades** (3): sneak-use a station with the gun in hand and
  the stuff in the off hand: Muffled (wool: no Hearing noise, a quiet report, no Deafened), Long Barrel (an iron ingot: the
  ball flies 1.35x as fast, so further), Choke (a copper ingot, blunderbuss only: each pellet is pulled 60% back to the line of the barrel), Bayonet (a sword:
  +5 to a melee blow with the gun); one part a rank on a gun. **Trick Shot**: arrows at a station become 4 grapple (tripwire
  hook), oil (coal), water (a water bottle) or shrapnel (gunpowder) bolts; oil makes what it soaks burn longer and a player's fire
  hurt it 1.5x, water puts fire out and makes lightning (and a lightning Sorcerer's magic) hurt 1.75x for 30 s. **Field Kit**:
  the Marksman's class key (a Tinkerer's sneak and key) mends 15% of the gear in hand from its own repair stuff (or an iron
  nugget), every 30 s.
- **Tinkerer** (`TinkerPerks`). **The craftsman's eye**: `TierGate.gearTier` is the higher of the world tier and the
  Tinkerer's eye (Borderlands at 5, Wildlands 10, Dread 15, Legend 20); it sets what merchants offer them (`TierGate.allowed`),
  the grade their smiths forge to (`maxGrade`), what JEI and the recipe book hide from them (`TierHiding`) and the early gun
  barrels they may forge. Monsters still read the real tier. Practice: a Tinkerer studies an ability faster with their level
  (`MimicStudy`). **Spare Hands** (2): +1 infusion kept a rank, and Muffled boots (wool: your noise and your heard footsteps
  carry half as far, `Awareness.noise`, `Senses`), a Warded chest (magma cream: freezing builds and fire burns out half as
  fast) and a Keen blade (flint: mends a point every 5 s, in hand or pack), made at a station with 2 of the stuff in the off
  hand. **Contraption** (5, 2 ranks): sneak-use 4 copper nuggets with gunpowder in the pack and a scrap bundle is made (a
  3 s wait); thrown, it bursts at random with a flash (blinds foes), a net (slows them hard), a healing mist (regeneration for
  friends), water, oil, a small bang, or a dud; rank 2 (Educated Guess) gives a dud back. **Dismantle** (10): sneak and touch a
  tripwire, its hook, a pressure plate or a covered pit with an empty hand and it breaks off (dropping its parts); a locked
  chest's lock comes open the same way; golems and clockwork mobs take 1.75x from a Tinkerer's blows. **Iron Pup** (12): built at
  a station (sneak, empty hand, 6 iron, 2 redstone, a clock in the pack); it follows, bites and carries loose items to you; it
  takes no food. **Iron Defender / Iron Hound** (fork, 18): +6 armour and patches you 2 health every 6 s when near / faster, a
  harder bite, and every 10 s the foes within 24 blocks glow. **Imitation** (top, 22): one sight of an ability studies it whole.
- My calls: Called Shot and Quick Shot are a fork at 10 (the crossroads row), not Quick Shot at 15 (its own line). Gunsmith
  gates the guns for everyone, so a weaponsmith sells guns and shot to anyone who isn't a Gunsmith, not only to Marksmen
  (otherwise no other class could get one). Gun parts, trick bolts and contraptions are vanilla items with our data, names and
  an item model (no new art). Spare Hands' kinds take their stuff from the off hand, since boots and swords already take
  Springing and Returning. Dismantle on a lock is the same "spirit" opening as a spell's (`Lockpicking.pickBySpirit`).
- Gaps: the **Iron Pup** is a tamed vanilla wolf in copper-dyed wolf armour, named Iron Pup, not a new clockwork model (no
  model or art in this patch). **Quick Shot** fires as the swing lands (Fabric's after-damage hook), not before it. Muffled on
  WeaponMod guns stops our noise and deafening, but WeaponMod's own report still plays. The Tinkerer's **own forging** has no
  tier ceiling in Kingdoms today, so there was none to raise. Dismantle opens our locked chests; door locks aren't ours.
- Lore: the Workshop is the gunsmith's and tinker's craft already in the canon (the stations, the early guns); nothing new to place.

### Patch 4: Warlord and Brute
- **The gauges.** **Composure** (`Discipline`, new): +0.1 a full-strength blow, +0.12 a block, +0.2 a parry (Powers'
  `Guard.PARRIED`), -0.2 a clean hit taken; it ebbs after 10 s out of a fight. At full the Warlord hits +15% (practice: x
  `ClassTrees.practice`), with gold motes about them. Drawn as a thin bar left of the hotbar (`ComposureHud`), the mirror of
  the rage bar on the right. **Rage** is the Brute's existing `BruteRage`; its damage now grows by practice, and while raging
  the Brute can't block, drink or eat (any item that blocks or is consumed is refused, and one in use is dropped from use).
- The class key (J) is now the Warlord's and the Brute's: it shows the gauge, or spends it with the top perk.
- **Discipline** (cols 0-2): **Measured Strikes** (3): +25% Composure a full-strength blow and +5% at full, a rank. **Riposte**
  (3): a blow within 1 s of a block or parry x1.5 and a stagger (`spirits.Staggered`, made public). Shout (5) and Throw Net (8)
  stay where patch 1 put them. **Sword and Board / Two-Handed Master** (fork, 10): a blow landed with the shield up (Powers'
  shield bash) staggers 2 s, and blocks and parries build double / the Warlord's cleave reaches 1.5x as far and takes one more
  foe, and two-handed blows pierce armour (+2% a point of the foe's armour, at most +40%). **Read the Foe** (15): at full
  Composure a block up to 24 ticks into the guard is a parry (Powers' window is 12; done through the guard's spark hook,
  ahead of the sparks). **Commander / Duelist** (fork, 20): orders reach 2x (`Presence.reach`, the quick orders), and your
  hirelings and companions take them whatever their tongue / +30% with no more than one foe within 10 blocks. **Perfect Form**
  (top, 24): J at full Composure spends it; the next blow within 5 s can't be parried by a mob (`MobParry`), hits x2, staggers
  3 s, and breaks a boss's posture outright (`BossPosture`: it kneels).
- **Fury** (cols 2-4): **Blood Up** (3): Rage fills 25% faster a rank, and blows hit +5% a rank times how full it is (all of it
  while raging). **Pain Feeds** (3): 20% of a blow taken while raging is healed. Throw (5) and Warrior's Charge (8) as before.
  **Reckless / Juggernaut** (fork, 10): no shield, +25% while raging / heavy armour's slow is lifted while raging
  (`ClassPerks.attributes`, refreshed as the rage starts and ends). **Shrug It Off** (15): raging, full knockback resistance
  and slowness (a stagger) shaken off. Warrior's Last Stand moved up to row 5 to make room. **Blackout / Bloodlust** (fork,
  20): raging, a killing blow leaves you at 1 health until the rage runs out / a kill while raging fills the rage's time again
  and heals 15%. **Rampage** (top, 24): J while raging: for 8 s every blow carries 75% to every foe within 3 blocks of the one
  struck; when it ends, the rage's usual spent plus Slowness II and Weakness for 15 s.
- My calls: Composure's numbers and its +15% at full (the plan names no base effect, and a gauge that does nothing until the
  top perk would be dead weight). Commander's "hirelings and companions follow them" is read as: the ones who take your
  orders take them across a tongue they don't share (they already obey you otherwise). Two-Handed Master's "pierce armour"
  is a bonus by the foe's armour, not a change to vanilla's armour sums.
- Gaps: Shrug It Off sheds slowness and knockback; Powers' own stuns on a player are Powers' and aren't cleared. Perfect Form
  "can't miss" is "can't be parried by a mob, and lands whole"; a vanilla shield held up by a mob still blocks it.
- Lore: Discipline and Fury are the two warrior traditions already in the canon (the levy's drill and the berserker's rage);
  nothing new to place.
### Patch 5: Rider
- All in `equestrian/Horsemanship` (new); the equestrian systems ask it what the ranks change (`Equestrian` drain, ridden
  speed, barding, shying, trampling; `Bonding` bravery, throwing off, war training, the whistle; `Herding` orders and
  defence; `Mounted` charge and run-down; `Riders.lance`). Tree in `trees/RiderTree`, lang in `class-trees-rider.json`.
- **Spur** (start, the class key): at the gallop, +40% speed for 2.5 s at no stamina (an attribute modifier, so the drain is
  untouched); wait 30 s divided by `ClassTrees.practice`. Off the gallop the key says so (or, with Trick Riding, hangs you).
- **Long Wind** (3 ranks): -15% stamina drain a rank. **Horse Whisperer** (3): no shying, no throw-offs of any kind (the buck,
  the racing saddle's throw, a shy), +0.3 bravery under you. **Lancer / Horse Archer** (fork, 5): the lance's gallop bonus
  x1.5 and the mounted charge fills x2 / a shot from the saddle within ~8 degrees of your look is set straight along it at the
  same speed (multishot's side bolts keep their spread). **Stay in the Saddle** (8): a shy from a blow or a blast never
  throws you, and anything else that knocks you off in the tick you were hit puts you back on the same horse (within 4).
  **Trick Riding** (10): J off the gallop toggles hanging (synced; `HangPose` rolls the body down the left flank about the
  hips). Hanging, a projectile hits the horse for half and not you; at the gallop items within 2.5 are picked up and a downed
  player or follower is pulled up behind (`rider.rescued`).
- **Cavalier / Outrider** (fork, 15): barding no longer slows a Courser, charge, run-down and elephant trample x1.5, war
  discipline XP x2 / trot and canter x1.15 (both sides, through `riddenSpeed`), no fall damage to the horse, and the whistle
  in the saddle calls your freshest horse (most stamina, within 96) at a run; within 3 blocks you're swapped onto it and the
  old one is told to follow. **Herd Master** (18): a wheel order (follow, stay, home...) goes to every horse of yours within
  24, and your horses come to your defence whatever their trust. **Banner Bearer** (20): with a banner in your horse's tack,
  everyone within 16 who is a player, takes your orders or is tamed by you is calmed of fear each second, hits +10%
  (`TreePerks.dealt`), and the horse they ride drains at 75%. **Bonded / Beast of Legend** (fork, 24): your horse, once
  downed, can't be killed by anything short of what bypasses invulnerability (health held at 1) / a nightmare, bat horse or
  war elephant you ride is tamed to you with trust 100, and a bat horse aloft drains at half.
- My calls: Spur's numbers; Whisperer also stops bucking (a horse that "never throws you"); Trick Riding uses J off the
  gallop because sneak dismounts; the banner counts every player near as a friend (there's no party to ask). The Rider's
  class description now names Spur.
- Gaps: Horse Archer's "no draw slowdown": the pack never slows a draw in the saddle (vanilla doesn't slow a passenger's
  input), so only the steady aim is built. Cavalier's "barding weighs nothing": only Coursers are slowed by barding, so it
  is theirs. Bat horse "flies twice as long": it has no flight limit, only stamina, so it's half the drain aloft. Bonded:
  Powers already downs a tamed horse instead of killing it; Bonded is that a downed horse can no longer be finished off. The
  hanging pose has no easing and always takes the left flank.
- Lore: the Chivalry realms' tilt-yards and the stables already hold every one of these; nothing new to place.
### Patch 6: Monk and Paladin
- **Monk** (`KiArts`, new; `Monks` asks it). Catch an Arrow stays the start; practice widens its window (12 ticks x
  `ClassTrees.practice`). **Deep Breath** (3 ranks): +1 ki a rank. **Stunning Blow** (3) and **Step of the Wind** (8) hold
  today's stun and wall slide, both kept free by Monks who had the class (`ClassTrees.kept`). **Deflect** (3): fists free, an
  arrow not caught does half; sneak held as you catch one and it's thrown straight back at its shooter. **Flurry** (5):
  sneak+J with no caught arrow to throw readies it (5 s); the next fist blow spends a ki and two blows follow 4 and 8 ticks
  later at half its damage. **Step of the Wind**: J in the air spends a ki: a dash along the look (1.4) with a lift; the wall
  slide now takes no fall harm. **Open Hand / Iron Body** (fork, 10): flurry blows shove, the last stuns 1.5 s (`HeldFast`,
  so bosses resist) / a blow that would take you under half health spends a ki: half harm for 5 s. **Still Mind** (15): fear
  (`Fear.calm`) and charms (`Charm.free`) are shed each second; while meditating (Powers' Meditate) a ki comes back every 10 s
  (`Rests.regain`, new). **Quivering Palm / Empty Body** (fork, 20): a landed stunning blow sets the palm for a ki more; within
  2 minutes and 48 blocks, J ends it: health + 100 as magic from you (a boss or a player takes 30% of its max health) / sneak+J
  in the air spends 2 ki: 5 s invisible, every mob hunting you loses you, and every blow from anyone misses.
- **Paladin** (`OathGifts`, new; `Paladins` asks it). Lay on Hands stays the start; practice grows the pool (20 x practice).
  **Deep Wells** (3 ranks): +6 to the pool and +1 smite a rank. **Smite** (3) and the **Aura** (8) hold today's (the aura's saves
  and the Paladin's own fearlessness), kept free by Paladins who had the class. **Shield of Faith** (5): sneak+J on a friend
  within 4: 4 from the hands' pool, and the next 3 blows on them each lose up to 4 harm, for a minute. **Oath's Gift** (10):
  Mercy, a foe your blow brings down (downed, not dead) heals you 6, once a foe; Justice, a smite on one Crooks knows for a
  crime, or on a raider, x1.5; Honour, J on a hostile within 16 (or one after you) calls it out for 30 s: it's kept targeting
  you and your blows on it are +25%. **Divine Sense** (12): J with no harm to mend: undead and fiends within 32 glow 5 s (every
  20 s). **Aura of Courage / Aura of Warding** (fork, 15): friends in the aura can't be frightened / take 30% less from spell
  damage (`WITCH_RESISTANT_TO`). **Vigil** (top, 20): a shrine prayer by night (12500-23500) refills smites and hands; until the
  next sunset the smite, the pool and the aura (reach and save) are x1.5.
- The class descriptions and class-screen lines now say which of today's gifts come at 3 and 8.
- My calls: the keys. Monk: J catches (arrow coming), leaps (in the air), ends a palm (one set), or readies the stun; sneak+J
  throws a caught arrow back, or goes Empty Body (in the air), or readies a Flurry. Paladin: J calls out a foe (Honour),
  heals, or (nothing to heal) Divine Sense; sneak+J shields a friend, or readies a smite. Iron Body is spent on its own (no key
  was left), on the blow that would put you under half. "Fiends" are fire-proof monsters (blazes, ghasts, magma cubes, wither
  skeletons, ...). A lawbreaker is one Crooks marks as a known criminal, or a raider. Levels the rows didn't give: Deflect 3,
  Shield of Faith 5, Divine Sense 12, both tops 20.
- Gaps: Divine Sense's glow is the vanilla effect, so others near see it too. Empty Body's "slip through blows" is every blow
  from a living source missing. A Vigil is one prayer at night, not a night kept awake at the shrine.
- Lore: the oaths (mercy, justice, honour), the shrines and the ki of the boxing schools are already canon; nothing new to place.
