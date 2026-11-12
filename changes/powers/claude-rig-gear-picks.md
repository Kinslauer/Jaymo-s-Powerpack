# Chrono, Blindness and Rig additions, arrowheads, a belt of throwables, shield moves, three draughts, two pacts and trained hands

**Chrono**
- **Calculation**: parries, perfect parries and blows that pass through you in Time Flow feed a bar. Full, every blow you land finds a weak point (more damage, may open the most hurt limb). Sneak + G spends it: for a few seconds blows and shots aimed at you miss and you slip aside (falls, fire, magic and blasts still land).
- **Afterimage decoys** (sneak + V while overclocked): copies of you stand about, more in a higher gear. Hostile creatures go for them, are slowed when they pop one, and now and then you change places with a copy.

**Blindness**
- **Radar sense**: someone who can't see is no longer blinded by flashes, smoke or darkness thrown at them. Hostile creatures near you are felt and shown, and one behind you or out of sight is called out with a red mark on that side of the screen and a low knock.
- **Pursuit**: a melee blow marks the creature and opens a cut; while the mark lasts you sprint faster toward it. It shows in gold on the radar.

**The Rig** (all from the Rig menu, Sneak + J, and the new Wrist Table screen)
- **Drones**: one for each level of the Mind, up to five. Formations (close in, surround, strafe, scout), a shell that takes blows for you, one sent as a missile, and duties when there is nothing to fight (fetch items, mend you, patrol a route of up to four marks).
- **Wrist Table**: a map of the land the suit has seen with drones, enemies and patrol marks, a list of creatures studied (read with the Scrying Lens, or five of a kind felled), and every drone order with what it does.
- **Decoy**: a lure that every enemy turns on while you go unseen, or three images that copy your moves.
- **Storing field**: keeps up to three parried blows (a perfect parry at double strength) and lets them all go at once.
- **Bank shot**: look at a wall with an enemy near it and a faint line shows the bounce; arrows, thrown things and the palm blast turn off that wall toward the enemy.

**Arrowheads** (they ride in the quivers the pack already has)
- Fourteen heads fitted to plain arrows at the Fletching Station: bola, net, caltrop, harpoon, grapple, smoke, flare, whistle, barbed, broadhead, fire, flash, drill and boomerang. Iron heads are forged from blanks; every route shows in JEI.
- **Quiver: Next Arrowhead** (mouse button 5 unless changed) brings the next kind to the top of a worn quiver (needs Jaymo's Kitchen for its quiver).
- **Trained archers** (by arrows loosed): drawing in the air slows your fall, sneak + full draw looses a second arrow behind the first, and a full draw loosed in the air becomes a volley on the spot you aimed at.
- Arsenal's rift arrows can carry a fitted head.

**Belt of throwables** (leather, iron, steel; worn in the belt slot)
- Eight throwables: smoke pot, flash powder, sneezing powder, bleeding dart, acid vial, choking vial, caltrops and bear trap. Click them onto the belt to load it. **Belt: Throw** is mouse button 4 unless changed; sneak + throw picks the next kind.

**Shield moves** (shield up)
- Attack throws the shield (it ricochets and comes back; a round shield throws best), sneak + attack slams it into the ground, sprinting charges behind it, and standing still or sneaking braces: nothing from the front moves you and arrows are turned back.

**Alchemy**
- **Skin Draught**: drink, then touch stone, iron, glass, ice, wood, slime, honey, sponge, obsidian, sand, wool or a bell to take its skin for a while, each with a gain and a cost.
- **Dampening Draught** (also as splash, lingering and tipped arrows): shuts a power away for a few minutes. It always wears off. Great foes shrug most of it off.
- **Draught of Release** (late research): drunk twice, slowly, it gives a power up for good and leaves it in a sealed phial a powerless person can drink. A Xeno-Band comes off instead. It will not take on a Z Fighter, a bonded symbiote or anyone bound by a pact.
- **Two pacts**, for a holder whose power is already corrupted, one for a person, never undone: one is offered by a demon master; the other comes to those who carry a Maw void shard long enough.

**Everyone**
- **Matching colours**: wearing the same costume design as a companion within 12 blocks steadies you both (a little toughness and slow mending).
- **Boxing**: with fist gear, trained boxers get a loose-joint chance on any punch (style level 2), a pressure point on sneak + punch that deadens an arm or leg for 10 seconds (level 4; on a player's arm it can shut two ability keys for a moment), a knee kick on sprint + punch (level 5) and a sweep on sneak + strafe + punch that puts a person on their back (level 7).
- **Planted stance**: sneak with any guard up for a second to plant. Blows are cut by 60%, nothing stuns or moves you, and you barely walk. A parried melee blow hands part of its weight back to the attacker. Jump while guarding (not sneaking) to step up and back out of reach.

**Changed on the way**
- Sneaking now counts as standing still for the shield brace.

Classes: `power/chrono/ChronoCalculation, ChronoDecoys`; `power/blindness/BlindnessRadar, BlindnessPursuit`; `rig/RigPicks, RigDrones, RigTable, RigStudy, RigDecoy, RigField, RigBank`, `client/rig/RigTableScreen`; `gear/` (arrowheads, `QuiverCycle`, `ArcheryMoves`, belts, throwables, `shield/ShieldMoves`, `GearJei`); `alchemy/picks/SkinDraughts, Dampening, GivingUp, Pacts`; `costume/Rally`, `boxing/PressurePoints`, `combat/Stances`. Art from `tools/gen_gear_picks.py` and `tools/gen_alchemy_picks.py`.

Compiled only; nothing was played in a client. Least sure: the sweep's back-fall pose, the shield throw and charge feel, drone flight, the bank-shot bounce, the skin overlay on modded bodies, and mouse side buttons on mice that lack them (rebind in Controls).
