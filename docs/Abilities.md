# Jaymo's Powers: every ability (v0.7.0)

Edit the **Change to** column (or anything else) and send it back. Blank means keep it as it is.

**Keys:** R G V B are the four abilities; **Z** is the power's mode; **C** is the ascended ability; **X** is the
power skill key (Web: the symbiote menu; Wind: Tempest Kick). Every power starts with only its **starter**, and unlocks its four abilities one by one by using them.
**Ascended** and **corrupted** are separate fates: a power can be one, the other, or both.

---

## Gravity: rework plan

### What it does now, in every form

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 17 | Starter | R | Lift (weak) | Hold: the creature, item or object under the crosshair floats where you point. You can't choke or slam with it yet. | |
| 18 | Starter | passive | Pull of small things | Items drift toward you from 4 blocks, growing 2 a level to 12 fully mastered; you jump a little higher. Kept for good. | |
| 19 | Starter | V (hold) | Item pull | Until Weightless is unlocked, holding V drags items and XP in from 12 blocks, fast. | |
| 20 | Base | R | Lift | Hold for up to 6 s (8 s cooldown). Range 44 blocks, +4 per ability unlocked, max 60. Works on any entity; players are easier to catch (a wider aim round them, wider the further off). Purple corners round the crosshair show when something Lift can take is in reach. Let go and it falls (real fall damage). | |
| 21 | Base | R + right click | Choke, then Slam | 1st right click: held by the throat, 0.5 heart a second. 2nd: slammed into the ground, buried 1-3 blocks. **Left click: the Push**: launched straight away from you 20 blocks (50 mastered); started inside a wall it goes through walls, otherwise it shoves loose blocks out of its way (each slows it) and stops dead (and hurt) on anything hard; whatever it passes through is battered aside. | |
| 22 | Base | R + right click (mid-air) | Aerial slam | You hang in the air, tilt 30°, and slam harder. Always 3 blocks deep, and it smashes the ground. Mastered: flying debris knocks you clear of the crater. | |
| 23 | Base | R + right click (items, corpses) | Draw in | Items fly into your inventory; Kitchen corpses are bagged or carried. | |
| 24 | Base | R at a block | **Chunk** (new) | Hold R at a block: a struggle (your view shakes, cracks spread). After 2 s it can come loose: let go of R (or hold until it's as big as it gets) and a ragged chunk tears out, 1 block at 2 s and a block more every 0.6 s, up to 1 + 2 per ability (9). Fully mastered it's quicker: the whole struggle, to its biggest, is over within 1 s. It hangs in your hold without R: **left click** throws it (up to 6 hearts, shatters, 30% of its blocks drop), **right click** pockets it, R drops it. | |
| 25 | Base | R + right click (objects) | Hurl | Carts, boats, armour stands, falling blocks: drift in to 2.5 blocks while held, then left click throws them hard (max 6 hearts to what they hit) and they smash blocks. | |
| 26 | Base | R on players | Held players | Stunned 4 s (Wind users: 0.5 s and they tumble). After that they can struggle or fly 3 blocks free. | |
| 27 | Base | damage | Slam damage | Max 10 hearts: a mid-air slam on something big, fully mastered and ascended. Fresh power about 35% of that, ground slams 65%, small mobs 75%. | |
| 28 | Base | G | Gravity Well | Not aimed: it bursts out around you and stays on you for 4 s (radius 8). It doesn't pull: everything that touches it is weighed down and kneels. 14 s cooldown. It goes off as a swelling purple bubble (16 blocks across) that blasts up the ground's debris and shoves 24 lumps of the ground up out of the earth, leaning out from the middle; each lies 3-11 s (Lift can take them) and crumbles away. Players and humanoid mobs it holds are forced to their knees. | |
| 29 | Base | V | Weightless | Tap V: Weightless (gravity -70%, jump high, fall slow, no fall damage; up to 30 s). **Hold V: the earthboard**: you stomp and a ragged crater of ground under you (about a creeper's blast) tears up into a slab you ride, steered like a wind's flight (W drives, S brakes, A/D slide, Jump lifts): up to 19 blocks over the ground, struggling and shuddering in the top 3, and always sinking a little. Dust pours only from its underside. You ride in a low surfer's crouch that leans into your turns. Until the power is fully mastered (or ascended, or corrupted) you ride only while V is held; after that, once it's up you can let go of V and keep riding, and press V again to step off. Stepping off, the board drops to the ground whole and lies 8 s (you can stand on it, and holding V on it rides it again), then crumbles. | |
| 30 | Base | B | Crush | Hold up to 3 s, release: everything around where you aim (24 blocks, radius 2.5-5.5) is pinned: too heavy to move or jump for 3 s, and players and humanoid mobs fight it, then drop to their knees (4 poses), getting up when it ends. It doesn't hurt them; only armour suffers (0.6 per point of armour, with the charge). Pops flowers, item frames and paintings. 6-20 s cooldown. | |
| 31 | Base | B tap (G + B tap) | Force kneel / Launch | Tapping B pins and kneels everything within 5 blocks of you; tapped while your Well is up it also launches you straight up about 14 blocks (once per Well). Holding B is the Crush above. The old Cave-in is gone. | |
| 32 | Ascended (Event Horizon) | passive | Event Horizon | You can't be knocked back, and projectiles bend away within 6 blocks of you. | |
| 33 | Ascended | C | Singularity | **Meteor**: hold C to gather it (3 s to full), let go to call it. Where you aim (48 blocks for a tap, out to 128 at full), a rock of stone and magma is torn out of the sky and falls for 2 s, burning and smoking, its shadow spreading below. A tap brings the small, narrow one (about 2 blocks across); a full charge one over 30 blocks across. Impact: a TNT-strength blast, up to 20 hearts at the centre (30 at full) falling off to nothing at 25 blocks (60 at full), then a shockwave racing out along the ground that throws everything it passes, harder the bigger the meteor. A charged meteor tears a wide, shallow crater: rings of lighter blasts go off as the shockwave passes (up to 3 rings, about 25 blocks out), instead of one deeper hole. Cooldown grows with the charge: 45 s after a tap, up to 3 min after a full charge. On the way down it tears through every block it passes (a roof, a tree), then explodes. The rock bursts apart on impact into 4 (a tap) to 30 (full) lumps of obsidian, blackstone, basalt and the like (now and then some iron ore), thrown over the crater; each lies 5-11 s and crumbles. Less fire than before (the blasts don't light the ground). Rarely one carries a symbiote. | |
| 34 | Corrupted (Collapse) | passive | Collapse | You're 35% heavier (+25% fall damage); loose things within 6 blocks drift toward you; Crush and cave-ins hit 1.6× harder. Letting go of Lift drives the creature into the ground in a crushing plummet: 1 more heart per block it falls. | |
| 35 | Corrupted | C | (none yet) | The black hole (the old Singularity): where you aim (32 blocks), pulls everything within 10 blocks for 3 s, 1.5 hearts every 0.5 s, then bursts for 3 hearts. 25 s cooldown. | |
| 36 | Both (Collapsing Horizon) | | | All of both (but no fling). **Passive**: blocks within 5 tear loose now and then (any block, bases too; stone and harder a tenth as often), struggle and crack, then drift in and orbit you (8 at most). R takes one; right click pockets it. Holding B, then letting go, rains the ring down on the target (a tap is still the force kneel). **C, Skyfall**: gravity turns over in 20 blocks: every creature and up to 40 loose blocks fall up into the sky for 3 s, then come crashing down at once (the fall, plus more per block fallen; heavier things worse). 90 s cooldown. | |
| 37 | Ascension | | How | 30, counting both creatures killed by weight you gave them and hard falls you survived (5+ hearts), with everything mastered. | |
| 38 | Corruption | | How | Reaching the Nether corrupts at once. | |
| 39 | Cure | | How | Be lifted by a shulker's levitation for 10 s. | |

### Your rework notes, and what I needed to know (all built in 0.7.0)

1. **R + left click: the push.** Push the held target really far (through walls if they're inside one, and it shoves
   blocks). Does this *replace* choke/slam on right click, or sit beside it? How far is "really far" (20 blocks? 50?),
   and should walls they hit break?/ right click function stays, left click is added beside. they should go up to 50 blocks, increasing as the user levels
2. **R + right click on blocks: rip out and throw.** Aim at a block, it tears loose and floats in front of you; left
   click throws it. How big: one block, or a chunk (3×3)? Does it drop as the block afterwards, or shatter? / it should be one block to start, increasing to 3x3 with increased struggle and time holding the button. the blocks should not be pulled in a uniform 3x3, however more sporadic and cratered, like a creeper explosion but smaller. afterwards the block shatters with a 30% chance of dropping the blocks it was made of
3. **Singularity C (ascended): meteor.** A rock pulled down out of the sky onto where you aim, seen falling, then a
   big explosion. Should it break blocks (like TNT), or only hurt creatures? Damage target? / it should do large damage, break blocks, and effect a large (25 block) area
4. **Collapse C (corrupted): black hole.** This is roughly what the Singularity does today. Move the current
   Singularity to Collapse's C as-is, or make it bigger and darker? / yes move the singularity over. 
5. **Both (Collapsing Horizon)**: which C wins, or does it get a third move? / it gets a new move, you can suprise me here. think immense gravity ultimate attack, that doesnt have to do with a meteor or a black hole. 

---

## Wind Control

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 60 | Starter | passive | Wind cloak | A little quicker on foot; hold Jump in mid-air to drift down slowly, with a cloud under you and breeze wisps curling round you (everyone sees them), fading shortly after you land. Kept for good. | |
| 61 | Base | R | Flight | Hover (WASD, Jump up, Sneak down). Sprint: cruise, up to sonic speed. Sneak at speed locks speed. S at speed is the air brake. Diving gains speed; pulling up is limited at speed; turning too hard throws you into a 2 s tumble. You lean into turns. Costs hunger. | |
| 62 | Base | flight | Crashes and rams | Hitting walls or the ground at speed hurts (wall damage grows with speed²) and smashes blocks. Flying through mobs at speed rams them (up to 18 hearts) and throws them. | |
| 63 | Base | G (hold ≤5 s) | Tailwind / Gust | In the air: a dash, Speed and Jump Boost. On the ground: a gust blast (a full charge is TNT-strength). The two have **separate cooldowns** (the gust's up to 3 min). | |
| 64 | Base | V (hold) | Gale Barrier | Resistance and knockback resistance, and a gust shoves mobs away. 30 s cooldown. | |
| 65 | Base | B (hold) | Zephyr's Breath | Heals you and nearby players (Regeneration, Slow Falling). 45 s cooldown. | |
| 66 | Weather | | Weather factor | Clear weather is slower, a storm stronger (flight and abilities). | |
| 67 | Corrupted (Stormbound) | passive | Stormbound | Every cast calls lightning at its target, but **only if it's raining** (60% strength; full in a thunderstorm). Gales hit twice as hard; slower on foot and in flight. | |
| 68 | Corrupted | B | Call Lightning | In the rain, replaces Zephyr: a bolt where you aim (3-4.5 hearts, sets fire, charges creepers), full strength in a thunderstorm. Costs hunger; static bites back. **Under a clear sky B is Zephyr's Breath again.** | |
| 69 | Corrupted | Z | Stormbound mode | Melee hits stun and stack static; 5 stacks = shocked (rooted 2.5 s). Turning it on crackles with electric sparks and washes your and nearby players' screens blue for a moment. | |
| 70 | Ascended (Tempest) | Z | Tempest mode | Circling wind deflects projectiles; other abilities 1.25× stronger. Gusts howl off you; turning it on washes everyone nearby's screens white for a moment. | |
| 71 | Ascended | C | Cyclone Lance | A spear of wind 32 blocks ahead: 3 hearts (6 in a thunderstorm) and a big throw. Stormbound in the rain: lightning along it; otherwise the plain Tempest lance. **In a mode (Z), it also stuns** (Slowness V, Weakness, Nausea for 2.5 s). 6 s cooldown. | |
| 72 | Mastered | X | **Tempest Kick** (new) | A roundhouse kick (now and then a spinning back kick) that throws a white crescent of wind ahead: 5 blocks, 3 hearts (ascended: 20 blocks, 6 hearts). 3 s cooldown (2 s ascended). | |
| 73 | Any | passive | **No shields** (new) | A Wind user can't hold a shield: the wind tears it out of the hand into the inventory. | |
| 74 | Both (Thunderhead) | Z | Thunderhead mode | Both modes at once. Turning it on in rain or a storm brings bolts down around you and darkens everyone's screen within 48 blocks; while it lasts, lightning flashes everyone's screens black and white every few seconds, with thunder. | |
| 75 | Any | Z | Mode hunger | Modes cost hunger (the Thunderhead the most) and switch off at 3 shanks. You can't eat while a mode is on. | |
| 76 | Item | | Sora's Dagger | Iron → Gilded (feather + gold block) → Diamond (breeze rod + diamond block, right click Flurry) → Tempest (ascended Wind, Nether Star + Dragon's Breath + Heavy Core). Its gifts only work for Wind users. | |
| 77 | Ascension | | How | Any breeze a wind kills (not yet ascended) drops the Gale Heart 66% of the time; use it with everything mastered. | |
| 78 | Corruption | | How | Using the wind during real thunderstorms fills the meter (about 3 min of use); it drains outside storms. | |
| 79 | Cure | | Eye of the Storm | Hover at Y 192+ in a thunderstorm for 60 s without casting; or stand in a snowstorm for 10 s; or wait: it blows itself out 3 Minecraft days after it took you. | |

## Web Slinger

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 85 | Starter | B | Webline | Hold on one block, let go on another: a walkable web bridge (24 blocks, lasts 60 s). Kept for good. Symbiote bridges are black rope (suit) or tendrils (Living Armor). | |
| 86 | Base | R / G (hold) | Swing | Main- and off-hand swing webs; a real pendulum: speed builds through the bottom of the arc and stalls at the top. Let go on the upswing for a boost. Up to 5 lines when mastered. No fall damage while swinging. | |
| 87 | Base | both webs + Sneak | Slingshot | Hold Jump to draw back, release to fire. The draw always grinds into the floor under you (and the wall behind): it cracks, then breaks (3×3 with Living Armor). If the floor goes and you're over air, the lines snap and you ragdoll for 2 s. | |
| 88 | Base | Jump (mid-air) | Web wings | Glide like an elytra: pitch down to dive and build speed, pull up to trade it for height. Hold Sneak to airbrake (arms flare forward, legs tuck). Landing from a glide does no fall damage; a fast landing still rolls. | |
| 89 | Base | Z | Web Zip | Zip to where you aim; let go short and you're flung up. | |
| 90 | Base | V | Web Shot | **Tap**: a fast webshooter pellet (0.75 heart; 0.2 s cooldown, no webs; none with the symbiote, whose V only charges). **Hold 1 s+**: the charged blast (splash Slowness III, cobwebs, up to 5 hearts; the symbiote's black mass up to 9). | |
| 91 | Base | B | Yank | **Tap**: web a creature and pull it in. **Hold**: keep it on a lead; release aimed at a block to hitch it there. A big one pulls you to it. | |
| 92 | Any | punch | Cut lines | Anyone can punch a web line (swing, hitch or bridge) to break it; each takes 1-4 punches. | |
| 93 | Ascended (Spider-Sense) | passive | Spider-Sense | Threats about to hurt you glow red. Up to 5 hits are dodged, each charge on its own 20 s cooldown, with an acrobatic move every time: a spinning sidestep, a backflip into a crouch, a duck and roll, or a weave. | |
| 94 | Ascended | C | Web Strike | Zip to whatever is under the crosshair (32 blocks) and kick it (5+ hearts). 8 s cooldown. | |
| 95 | Symbiote | X menu | Suit | Show or hide the black suit (the powers keep working). | |
| 96 | Symbiote | X | Tendril Grab | Yank a creature to you, or yourself to a block (20 blocks): every tendril reaches for the wall and hauls you in, turns your back to it, and lifts you a block off it, each lower tendril tethered to its own cracked spot. Your view is held facing away from the wall; you sway on the tethers toward where you look, and lean further with the movement keys, bouncing back like elastic; jump, the grab again, or any other radial ability lets go. R and Shift+G still grab and tear chunks there. | |
| 97 | Symbiote | Sneak + R/G at a block | **Chunk** (new) | With Living Armor, a tendril tears a chunk (up to 6 blocks) out of the wall or ground and holds it like a mob. **Left click** swings the main tendril's chunk at what's in front (up to 5 hearts, a block chips off each hit), **right click** swings the other's. Let go of the key to throw it. | |
| 98 | Symbiote | X | Living Armor | X brings out the **tendrils** only, over any suit (or none): they strike on their own, grab and hold 2 mobs with R/G, and tear chunks. The **big body** (swollen bulk, 8 hearts of regrowing absorption, knockback resistance, galloping, hungrier ×3, veins coursing over it) comes only near death (it takes over to save itself, stressed, the bond suffering) or when it takes over. | |
| 99 | Symbiote | X | Wall Crawl | Climb walls and ceilings. Four tendrils come out and walk you along it like Doctor Octopus, planting ahead in diagonal pairs. | |
| 100 | Symbiote | X | Consume | Eat something small (5 hearts or less) or a carried corpse: heals you and feeds you both. Won't eat skeletons, creepers or endermen. | |
| 101 | Symbiote | X | Spike Burst | Spikes hit everything within 5 blocks (4 hearts) and throw them. In the big body it's a **Roar** instead (6 blocks, 4 hearts, hurled away), the same roar it gives near death as the armour builds over you. | |
| 102 | Symbiote | X | Shroud | Invisible until you attack (max 20 s). | |
| 103 | Symbiote | passive | Instinct | It always flees fire: it shoves you toward open ground away from it, or up if there's nowhere to go. A flint and steel or fire charge in your hand makes it scream and refuse, then drop it, lash out, or bolt. Out of the armour, tendrils shove you from blasts, catch long falls, and grab or shove attackers. With the armour, it strikes back when hit. | |
| 104 | Symbiote | passive | Hunger and moods | It must be fed (raw meat, Consume). Starving, it eats your hunger. Hungry, it eats corpses near you: a tendril brings the corpse up to its maw; one bite for something small, while a cow-sized one is bitten in half (blood) and finished with a second chomp; then it chews a while and the head sinks back. If your own hunger runs out it sulks (no suit for 30 s to 4 min, depending on the bond). | |
| 105 | Symbiote | passive | Frenzy (takeover) | Its stress speaks up as it climbs (darker each time). Starving and stressed, or worn too long, it takes control and hunts until its prey is dead or 30 blocks away (60 s at most). Fed on its own kill, it often coils a full charged jump during or after the meal and leaps you clear (always in a village or a crowd), the way with the fewest about. With a good bond it takes over out of curiosity: it grabs a mob or player, holds them up to its swollen head and stares for 2-8 s, then sets them down ("just looking") or attacks, and hands control back. | |
| 106 | Symbiote | passive | Weaknesses | Fire and loud noises (bells, horns, explosions) hurt it, and wash your screen (fiery orange-red for fire, bright bluish white for noise); 6 exposures and it leaves you for good. | |
| 107 | Ascension | | How | Swing 2000 blocks through the air, with everything mastered. | |
| 108 | Corruption | | | None: the symbiote is Web's dark side. | |

## Pocket Arsenal

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 114 | Starter | R | Rummage | Reach in and see what turns up: mostly junk; sometimes a stone weapon; sometimes a useful tool (flint and steel, fishing rod, shears, compass, spyglass...). 30 s cooldown. | |
| 115 | Base | R | Reach Through | A rift opens, your arm reaches in, and a random weapon (with ammo) comes out. Up to 3 held, each lasting 75 s. 18 s cooldown. | |
| 116 | Base | G | Return | Send the held weapon back; refunds Reach's cooldown in proportion to the time it had left (up to 90%). | |
| 117 | Base | V | Barrage | 3-5 rifts open around you and fire weapons at what you look at (3.5 hearts each). 30 s cooldown. | |
| 118 | Base | Z (and B) | Pocket Stash | Your personal 27-slot pocket dimension. Moved to Z in 0.8.38; B still opens it uncorrupted. | |
| 119 | Base | passive | Relics | Relic weapons, each with a gift, in chests where they belong: **Soul Saber** (Nether: life steal, a soul-fire slash), **Grand Shield** (mansions, outposts, strongholds: reflects projectiles), **Voidreaver** (End cities: blinds, steps behind your target), **Echo Cleaver** (ancient cities: a sonic boom), **Tidebreaker** (shipwrecks, buried treasure, ocean ruins: harder in water or rain, a riptide), **Sunfang** (desert and jungle temples: sets alight, a sand blast). Picking one up claims it into your Stash; Reach brings it out first. | |
| 120 | World | right click | **Sword in the Stone** (new) | Stone pedestals with legendary blades in them, each its own design, stone and home: **Durandal** (Overworld surface), **Laevateinn** (the Nether, blackstone), **Caladbolg** (End islands, end stone), **Gram** (the Deep Dark, deepslate and sculk), **Hrunting** (frozen peaks, ice). Only an Arsenal user can draw one; each is a netherite-strength relic. | |
| 121 | Ascended (Armory) | Sneak + R | Pin | Holding a summoned weapon: every Reach brings that kind first. Empty hand: unpin. | |
| 122 | Ascended | C | Hundred Blades | Rifts in the sky over where you aim rain 20 weapons (1.5 hearts each, about 15 hearts total). 20 s cooldown. | |
| 123 | Corrupted (Void Arsenal) | passive | Void Arsenal | Summoned weapons hit 1.3× harder and add Darkness and Wither; water and rain burn you; a heavy hit can blink you away. | |
| 124 | Corrupted | V | **Void Gates** (new) | V: fix a gate where you aim (64 blocks; up to 5, a 6th replaces the oldest). Sneak + V: step through to the gate you're aiming toward, from any distance, through walls. Hold Sneak + V with no gate here: a gate drifts forward from you (up to 32 blocks, with a body-sized outline of where you'll land); let go to go there. 1.5 s cooldown. Only you see your gates. | |
| 124b | Corrupted | B | **Sword of Damocles** (new) | A great void rift opens 30 blocks above you; 3 s later a 16-block sword falls through it, smashing everything in its path, and drives its point into the ground (30 damage to what it lands on, up to 8 hearts around the point with knockback). Stands 12 s. 45 s cooldown. | |
| 125 | Both (Void Armory) | | | All of both, and the **flintlocks**: Reach sometimes brings a pair of single-shot pistols (always the first time). Pin them and they're all the rift gives you, a pair every Reach. Fire one, drop it spent, and a loaded one is already in your hand. | |
| 126 | Ascension | | How | The Dimensional Key (bastion chests), used with everything mastered. | |
| 127 | Corruption | | How | Every brush with an Enderman fills the meter; setting foot in the End corrupts outright. | |
| 128 | Cure | | How | Be there, having fought it, when the Ender Dragon dies; or wait: the void lets go 3 Minecraft days after it took you. | |

## Chronokinesis

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 134 | Starter | R | Nudge | R speeds you up a little; Sneak + R slows what you look at a little. 15 s cooldown. | |
| 135 | Base | R | Rewind | Snap back to where you were 3 s ago (health restored if it was higher, fire put out). 15 s cooldown. | |
| 136 | Base | G | Stasis Field | Stop time for mobs, projectiles and players within 6 blocks: 3 s fresh, up to 6 s fully mastered. 30 s cooldown. | |
| 137 | Base | V | Overclock | 6 s of Speed III, Haste II and faster attacks; everyone near you is slowed. 25 s cooldown. | |
| 138 | Base | B | Time Bloom | 5 s of fast-forward for crops, saplings, furnaces and smokers within 5 blocks, and it heals you 5 hearts over those 5 s. 40 s cooldown. | |
| 139 | Base | passive | Echoes | Every level-up leaves an echo of you somewhere nearby, 5-25 minutes later, walking your old paths. Only attacks shatter it, and a shattered one comes back. This is Chrono's curse in place of corruption. | |
| 140 | Ascended (Timeless) | passive | Timeless | Cooldowns recover 25% faster; clock hands circle you. | |
| 141 | Ascended | C | Chrono Punch | 10 punches in half a second into everything in front of you, each harder than the last, stronger with mastery (about 11 hearts total at full mastery). Targets are pinned; the last punch throws them. 8 s cooldown. | |
| 142 | Both (Timeless + corrupted) | Z | Time Flow | Free-flow combat. Attack toward an enemy within 9 blocks and you're carried to it: jab/hook/uppercut up close, a lunging punch further out, a flying kick from far. Enemies' melee blows are held about 0.7 s with a white flash over them; right click to counter (all held blows at once). Light blows (a fresh combo needs eight or nine to drop a zombie) that grow with the combo, up to triple damage. Single attackers get one of six counters: circle, slip, deflect, vault, leg sweep or shoulder throw. | |
| 143 | Ascension | | How | Spend 10 minutes in Stasis or rewound, sleep through a night: you wake holding the Stopped Clock. Use it with everything mastered. | |
| 144 | Corruption | | How | Shatter one of your echoes in the Nether: it's destroyed for good, and the hole it leaves corrupts you. Needed for Time Flow. | |
