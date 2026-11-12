# the armed body and special cuts - stance, draw, flip, parry lines, swatted shots
Andy's picks from Star Wars: Galaxy in a Cube (All Rights Reserved, 1.20.1 only: ideas only, every pose, name and line
of code is ours). Patches 1 to 4 of 6 of the SWGC swordplay picks.
- **Armed stance** (`client/combat/ArmedPose`, `combat/Armed`): with a weapon in hand the whole body changes. Standing,
  the weapon shoulder turns forward, the blade is held out ahead, the other hand goes back, the feet set apart and the
  weight shifts slowly. Walking, the blade is carried low at the side; running, it trails behind and you lean into it.
  A two-handed weapon is held in both hands before the body; a polearm stands upright at your side and comes across
  the body on the move. Picks, shovels, hoes and fist gear aren't weapons here. Better Combat's swings are untouched:
  the stance lets go for any swing, guard, item use or other pose of ours.
- **Draw flourish**: taking a weapon into the hand plays a half-second draw, from the far hip for a one-handed blade
  and over the shoulder for the big ones, with a ring of steel (a stand-in sound until the sound patch). A swing or a
  guard cuts it short.
- **Armed crouch**: sneaking with a weapon sinks into a low guard, the blade levelled ahead.
- **Mid-air flip** (`stamina/Dodge`, `DodgeClient`, `DodgeRollPose`; Andy: "tied to weapon forms but activated by dodging
  in midair, after a jump", "the camera should always follow"): Dodge pressed in the air off a jump, a weapon in hand
  and Weapon Forms learnt (any form from a teacher, or the Strength perk), turns a somersault the way your movement keys
  point (forward with none held). Once a jump, 4 feathers, a small push, and it evades like a dodge. Only those who fly, are stronger or bend their own weight get a lift from it (Web Slinger, Gravity, Darktide, Wind, Z Fighter, Smoke, anyone who may fly, Strength, Might or great size). In first
  person the view turns over with you. A dodge pressed while falling without those still queues the landing roll.
- **Parry lines** (`client/combat/ParryPose`): a raised weapon guard is carried across the body, and when a blow meets
  it the guard goes to the line it came on: high over the head, low across the legs, or hanging point-down across the
  far side (blows at your own height take high and hanging turn about). A plain block rocks you back; a parry barely
  does. Everyone sees it (`Armed.MET`, synced), and fighters who parry you snap to the line too. Shields and the
  powers' own guards keep their poses.
- **Batting shots back** (`combat/Swat`): a perfect parry made with a blade (not a shield) knocks an arrow back at
  whoever loosed it, at half the speed it came with. Never a gun's ball or anything tagged unparryable.
- **Darktide swats every shot** (Andy: "darktide can block unparryable projectiles. it animates him swinging away at it
  whenever they impact the guard"): each shot Darktide's guard meets, balls and tagged ones too, is seen swung at, with
  sparks off a katana or a knife. What the guard stops and sends back is as it was.
- **Special cuts, the body's side** (for Kingdoms' new blade forms): `Armed.CUT` (synced) has `ArmedPose` draw the Wheel
  Cut (the blade circling overhead, then across), the Figure Eight and the throw. `combat/BladeThrow` hurls any sword as
  a tumbling `ThrownWeapon` that sticks where it lands, or flies back to the hand when the caller says its thrower can
  call it.
- **Fighting styles, the trades** (`combat/BladeStyle`, for Kingdoms' schools): a player set in a style pays feathers
  for the swings that land, pays more or less for a blow a weapon guard blocks (`Guard`), swings faster or slower (an
  attack speed modifier) and gets feathers back faster or slower (`Feathers`). Nobody has a style until Kingdoms sets one.
- **Two blades** (`combat/TwoBlades`, `Armed.dual`): with a one-handed blade in each hand the stance is wider and
  squarer, the off hand carrying its blade like the other; a raised guard crosses the two; and every third swing that
  lands, both blades cut together, the second for 0.6 of your attack on the same foe. Better Combat's off-hand swings
  are untouched.
- **Guards at attention** (`client/combat/AttentionPose`): a humanoid mob with a polearm that is standing with nobody
  to fight holds it upright at its side; one with someone to fight (or, with Kingdoms, a guard challenging you) levels
  it in both hands. Their swings are as they were.
- **The Twinblade** (`armory/Twinblade`; Andy: "an ancient warrior born design, not often wielded by many, and the
  schematic is found in the stormborn dungeon"): the Twinblade Schematic turns up in the Stormborn Tomb (35% in the
  boss's hoard, 10% in the last chest). Use it to learn the joining (the sheet is used up). Then sneak and use with two
  swords of the same make, one in each hand: they become one Twinblade that strikes for the sword's damage plus 1, a
  little slower, lasts as long as the two together, and is held, swung and guarded with like a quarterstaff (it takes
  Kingdoms' Mill Charge). Sneak and use it with the other hand empty to part the two again, the wear shared. No recipe.
  Their enchantments sleep while joined, and a Twinblade that breaks takes both swords with it. Our own sprite
  (`tools/gen_twinblade.py`).
- The stance and the guard are poses of ours, so Entity Model Features (Fresh Animations, Touch'n Motion) pauses on a
  body while they're on, as it does for the Fang Gauntlets' guard.
- Compiled; not played. Every angle is a first pass set without seeing it in game: the knobs are at the top of
  `ArmedPose` and `ParryPose`.
- **Cuts you can see** (Andy: "the cuts are real and can be healed with bandages"): a bleeding part now shows red
  gashes on the body, for everyone who looks (players and humanoid mobs; `DressingsLayer`, the bleeding parts are now
  synced to all). The mark is the wound itself: a bandage or rag covers it, and it is gone when the cut closes. Armour
  and costumes hide it as they hide dressings. Our own texture (`tools/gen_cut_marks.py`).
- **Our own blade sounds** (`combat/BladeSounds`, `tools/gen_blade_sounds.py`): five swings each for light, medium and
  heavy weapons, six clashes of steel and three draws, one picked at random each time with a slightly bent pitch. They
  play for the draw, the arrow swat, the both-blades cut, a thrown blade, and steel meeting steel on a guard. Better
  Combat's swing sounds are untouched. Synthesised, not yet heard in game.
- **Reel what Lift holds**: while Gravity's Lift holds a creature, a chunk or anything else, the scroll wheel pushes it
  out or reels it in, a block a notch, from 1 block to 16 (`GravityReel`). Heavier things follow more slowly. The
  hotbar stays put while you hold something (sneak and scroll is still the stance wheel).
- **Lift tears any block loose** (Andy: "it certainly should be able to grab ANYTHING"): the block you aim at comes
  whatever it is made of, obsidian included; only what can't be broken at all and blocks with something inside (a
  chest, a furnace) stay. The rest of the chunk still grows with the power. Harder chunks hit harder when thrown: up
  to three quarters more at obsidian's hardness.
- **Great-helm view** (`client/GreatHelmView`): in first person, a helmet tagged `jaymospowers:great_helms` (the
  Paladin's Great Helm to start) narrows the view to the visor's slit and you hear your own breath, quicker while you
  sprint or are hurt. `/helmview off` turns it off. Breath synthesised (`tools/gen_helm_breath.py`).
- **Our own title splashes**: the title screen's yellow line now comes from 45 of the pack's own lines
  (`assets/minecraft/texts/splashes.txt`). A first draft for Andy to cut and add to.
- **The Z Fighter's ki weapon is the energy blade** (Andy, asked who should carry one: "z fighters"). It has its own
  sounds now: it flares out and draws in, hums while it is out and sings on every blow that lands
  (`tools/gen_ki_blade_sounds.py`). With it out he stands armed (the blade one-handed, the scythe and lance as
  polearms). A parry made with it out strikes an arrow back at whoever loosed it. Cost, damage and reach are unchanged,
  and its blows still count as fists.
