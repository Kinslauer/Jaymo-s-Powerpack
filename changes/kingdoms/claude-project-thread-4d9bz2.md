# special cuts and the schools' styles - Wheel Cut, Figure Eight, Hurled Blade, five styles
Andy's picks from Star Wars: Galaxy in a Cube (All Rights Reserved: ideas only, the code, names and poses are ours).
Three new blade forms (`forms/SpecialCuts`), taught by the blade school after Sheath Draw and Crescent Sweep (standing
70, 95, 120) and castable from the skill bar like the others.
- **Wheel Cut**: a 1.25 second wind-up with the blade circling overhead, then one blow across 120 degrees for three
  times your attack. Costs 6 feathers. The red glint shows as it winds up: a guard can only block it, never parry it,
  and a raised shield is knocked out of use for 5 seconds. Being hurt during the wind-up breaks it off.
- **Figure Eight**: the blade spins in eights. Each 0.7 second loop makes three light cuts (right, behind on the right,
  ahead) and costs 2 feathers; it runs until they give out, the blade leaves the hand, or you press again. While it
  spins, blows and shots from the front are turned aside for a feather each (not unparryable ones).
- **Hurled Blade**: throws the blade in your hand, tumbling. It strikes once and sticks where it lands for you to
  fetch. It flies back to the hand only for Gravity, a Z Fighter, or anyone with a mana spirit. Needs Powers.
- The picklist said "hold attack" for the Wheel Cut; Better Combat owns that button, so it is a form on the belt.
- Compiled; not played. No Skill Web perks for the three yet (the school teaches them).

The schools' fighting styles (`forms/Styles`; the picklist row said seven, SWGC's count, but ours go one a school, so five):
- While a school you've joined has its weapon in your hand you fight in its style. A style trades four things: the
  feathers a landed swing costs, what a blocked blow costs, attack speed, and how fast feathers come back.
  - Still Water (the Quiet Tide, blades): swing 0.75, block x0.6, speed x0.95. The warding style.
  - Long Measure (the Skyward Reach, spears): swing 0.4, block x1.4, regrowth x1.1. The fencer's style.
  - Deep Root (the Stone Root, fists): swing 0.5, block x0.8, speed x0.9, regrowth x1.3.
  - Low Flame (the Low Lantern, knives): swing 0.5, block x1.5, speed x1.15.
  - Red Ember (the Ashen Fang, claws): swing 1, block x1.8, speed x1.25, regrowth x0.85. The wild style.
- The style shows over the belt in battle stance. Sneak and use a Training Post with a weapon in hand to put the styles
  aside and fight plain, or take them up again.
- A school's guards and duellists fight in its style: the wait between their blows follows it (Red Ember students strike
  fastest, then tire after 8 blows; Low Flame after 14), and in a duel a student turns some of your blows aside (Still
  Water nearly half, Red Ember almost none; a tired one turns nothing).
- Needs Powers for the trades (`combat/BladeStyle`). Compiled; not played. Every number is a first pass.

Mill Charge and the guards' challenge:
- **Mill Charge** (`forms/SpecialCuts`), a spear-school form for quarterstaffs, glaives and halberds (new item tag
  `form_staves`): a short dash with the shaft turning before you. Everyone you pass is struck once for 1.5 times your
  attack and thrown aside, and blows and shots from ahead are turned while it lasts. 5 second cooldown. Taught by the
  Skyward Reach after Couched Lance.
- Staffs, glaives and halberds now count as the spear family's weapons, so the battle stance and the spear forms take
  them too.
- A guard who walks up to challenge you ("Halt!") levels their polearm at you until they have spoken (`Levelled`; Powers
  draws it).
- Wheel Cut, Figure Eight, Hurled Blade and Mill Charge play Powers' new blade swings instead of the vanilla sweep.
