# the armed body and special cuts - stance, draw, flip, parry lines, swatted shots
Andy's picks from Star Wars: Galaxy in a Cube (All Rights Reserved, 1.20.1 only: ideas only, every pose, name and line
of code is ours). Patches 1 to 4 of 6 (the Twinblade still to come) of the SWGC swordplay picks.
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
- The stance and the guard are poses of ours, so Entity Model Features (Fresh Animations, Touch'n Motion) pauses on a
  body while they're on, as it does for the Fang Gauntlets' guard.
- Compiled; not played. Every angle is a first pass set without seeing it in game: the knobs are at the top of
  `ArmedPose` and `ParryPose`.
