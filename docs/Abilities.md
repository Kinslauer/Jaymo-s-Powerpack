# Jaymo's Powers: every ability (v0.6.6)

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
| 18 | Starter | passive | Pull of small things | Items within 4 blocks drift toward you; you jump a little higher. Kept for good. | attraction increases up to 12 as you level |
| 19 | Starter | V (hold) | Item pull | Until Weightless is unlocked, holding V drags items and XP in from 12 blocks, fast. | |
| 20 | Base | R | Lift | Hold for up to 6 s (8 s cooldown). Range 14 blocks, +4 per ability unlocked, max 30. Works on any entity. Let go and it falls (real fall damage). | |
| 21 | Base | R + right click | Choke, then Slam | 1st click: held by the throat, raised high, 0.5 heart a second. 2nd click: hauled up, then slammed down, buried 1-3 blocks, debris ring. One of 4 full-body slam animations on zombies, players and big mobs. | **Your note:** R + Left click: push people away really far (through walls if inside them, and it pushes blocks losing momentum after impact, and stopping if the mob dies).  |
| 22 | Base | R + right click (mid-air) | Aerial slam | You hang in the air, tilt 40°, and slam harder. Always 3 blocks deep, and it smashes the ground. Mastered: flying debris knocks you clear of the crater. | tilt 30° |
| 23 | Base | R + right click (items, corpses) | Draw in | Items fly into your inventory; Kitchen corpses are bagged or carried. | |
| 24 | Base | R at a block | **Chunk** (new) | With nothing to lift where you aim, Lift tears a chunk out of the wall or ground (3 blocks +1 per ability, harder stone as it grows) and holds it. **Left click** (or right) throws it: it batters what it hits (up to 6 hearts) and shatters on impact. | require a held R input to charge for 2s before lifting, screen shakes slightly to indicate struggle |
| 25 | Base | R + right click (objects) | Hurl | Carts, boats, armour stands, falling blocks: drift in to 2.5 blocks while held, then thrown hard (max 6 hearts to what they hit) and smash blocks. | **Your note:** R + Right click on *blocks*: rip them out of place, then throw with left click. |
| 26 | Base | R on players | Held players | Stunned 4 s (Wind users: 0.5 s and they tumble). After that they can struggle or fly 3 blocks free. | |
| 27 | Base | damage | Slam damage | Max 10 hearts: a mid-air slam on something big, fully mastered and ascended. Fresh power about 35% of that, ground slams 65%, small mobs 75%. | |
| 28 | Base | G | Gravity Well | A point where you aim (24 blocks) pulls everything within 7 blocks in for 4 s. 14 s cooldown. | |
| 29 | Base | V | Weightless | Toggle: gravity -70%, jump high, fall slow, **no fall damage**. Costs hunger, lasts up to 30 s; 10 s cooldown (2 s if switched off early). Creatures within 6 blocks of you get heavier. | when ascended or corrupted, holding V activates a low altitude rock flight. you stomp and the ground below you, any amount of solid blocks within a radius of 3x3 around the player, are randomly conglomerated underfoot, enabling flight controlled similarly to wind, only the user is stood atop the conglomerate, instead of posing, Air brake doesnt emit particles other than from the bottom of the conglomerate, already constantly falling after formation simulating falling debris. you are unable to gain much altitude, making the ability more for low ground traversal.  |
| 30 | Base | B | Crush | Hold up to 3 s, release: everything around where you aim (24 blocks, radius 2.5-5.5) is slammed down, hurt 2-8 hearts and pinned 3 s. The zone is weighed down while charging. Pops flowers, item frames and paintings. 6-20 s cooldown. | enemies caught in the abilitie's aoe are no longer damaged, but made extremely heavy to the point the crushing pressure stops them from moving, and deals damage if they are wearing armor |
| 31 | Base | G + B | Cave-in | Crush landing inside your own Well tears up 14-40 blocks and hurls them into the middle: 7-18 hearts. | ignores snow blocks and instead prioritizes heavy blocks like stone and dirt if available |
| 32 | Ascended (Event Horizon) | passive | Event Horizon | You can't be knocked back, and projectiles bend away within 3 blocks of you. | 6 blocks |
| 33 | Ascended | C | Singularity | A black hole where you aim (32 blocks): pulls everything within 10 blocks for 3 s, 1.5 hearts every 0.5 s, then bursts for 3 hearts. 25 s cooldown. | **Your note:** a large rock from space is pulled down onto the target: animated falling meteor, big explosion and particles. |
| 34 | Corrupted (Collapse) | passive | Collapse | You're 35% heavier (+25% fall damage); loose things within 6 blocks drift toward you; Crush and cave-ins hit 1.6× harder; letting go of Lift flings what you held. | letting go will now plummet held mobs to the ground under heavy gravity applying amplified fall damage. |
| 35 | Corrupted | C | (none yet) | Collapse has no C move of its own. | **Your note:** becomes the old singularity black hole from event horizon. |
| 36 | Both (Collapsing Horizon) | | | All of the ascended and corrupted effects. | you are such an immense concentration of energy, that you pull blocks near you have a chance to fly straight out of their places, struggling slightly against your force, then drifiting towards the user, increasing in speed as they get closer. all other corrupt/ acsended effects except corrupted flinging when letting go of R|
| 37 | Ascension | | How | 30 creatures killed by weight you gave them (falls after Lift, Crush, slams, the Singularity), with everything mastered. | surviving fall damage also adds to the progress, any number of either requirement will grant the acsension|
| 38 | Corruption | | How | Each kill by weight +2; every 20 s weightless +1 (100 corrupts). | the power is now corrupted by reaching the nether |
| 39 | Cure | | How | Be lifted by a shulker's levitation for 10 s. | |

### Your rework notes, and what I need to know

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
| 60 | Starter | passive | Wind cloak | A little quicker on foot; hold Jump in mid-air to drift down slowly. Kept for good. | animated cloud and wisps like breezes while drifting down. gradually dissipates shortly after landing|
| 61 | Base | R / Sneak + double Jump | Flight | Hover (WASD, Jump up, Sneak down). Sprint: cruise, up to sonic speed. Sneak at speed locks speed. S at speed is the air brake. Diving gains speed; pulling up is limited at speed; turning too hard throws you into a 2 s tumble. You lean into turns. Costs hunger. | |
| 62 | Base | flight | Crashes and rams | Hitting walls or the ground at speed hurts (wall damage grows with speed²) and smashes blocks. Flying through mobs at speed rams them (up to 18 hearts) and throws them. | |
| 63 | Base | G (hold ≤5 s) | Tailwind / Gust | In the air: a dash, Speed and Jump Boost. On the ground: a gust blast (a full charge is TNT-strength). Cooldown up to 3 min for a full blast. | separate cooldowns |
| 64 | Base | V (hold) | Gale Barrier | Resistance and knockback resistance, and a gust shoves mobs away. 30 s cooldown. | |
| 65 | Base | B (hold) | Zephyr's Breath | Heals you and nearby players (Regeneration, Slow Falling). 45 s cooldown. | |
| 66 | Weather | | Weather factor | Clear weather is slower, a storm stronger (flight and abilities). | |
| 67 | Corrupted (Stormbound) | passive | Stormbound | Every cast calls lightning at its target, but **only if it's raining** (60% strength; full in a thunderstorm). Gales hit twice as hard; slower on foot and in flight. | |
| 68 | Corrupted | B | Call Lightning | In the rain, replaces Zephyr: a bolt where you aim (3-4.5 hearts, sets fire, charges creepers), full strength in a thunderstorm. Costs hunger; static bites back. **Under a clear sky B is Zephyr's Breath again.** | |
| 69 | Corrupted | Z | Stormbound mode | Melee hits stun and stack static; 5 stacks = shocked (rooted 2.5 s). The storm sometimes arcs into you. | add electric particles and a temporary blue vignette on the user and nearby players pov |
| 70 | Ascended (Tempest) | Z | Tempest mode | Circling wind deflects projectiles; other abilities 1.25× stronger. | play gusting noises from the user, add a temporary white vignette for all  |
| 71 | Ascended | C | Cyclone Lance | A spear of wind 32 blocks ahead: 7 hearts and a big throw. Stormbound in the rain: lightning along it; otherwise the plain Tempest lance. **In a mode (Z), it also stuns** (Slowness V, Weakness, Nausea for 2.5 s). 6 s cooldown. | |
| 72 | Mastered | X | **Tempest Kick** (new) | A roundhouse kick that throws a white crescent of wind ahead: 5 blocks, 3 hearts (ascended: 20 blocks, 6 hearts). 3 s cooldown (2 s ascended). | |
| 73 | Any | passive | **No shields** (new) | A Wind user can't hold a shield: the wind tears it out of the hand into the inventory. | |
| 74 | Both (Thunderhead) | Z | Thunderhead mode | Both modes at once. Turning it on in rain or a storm brings bolts down around you and darkens everyone's screen within 48 blocks. | the screen flashes from black and white a few times during the existing duration, simulating lightning strikes and thunder|
| 75 | Any | Z | Mode hunger | Modes cost hunger and switch off at 3 hunger shanks. | thunder head costing the most hunger, and you are unable to eat during the mode duration|
| 76 | Item | | Sora's Dagger | Iron → Gilded (feather + gold block) → Diamond (breeze rod + diamond block, right click Flurry) → Tempest (ascended Wind, Nether Star + Dragon's Breath + Heavy Core). Its gifts only work for Wind users. | |
| 77 | Ascension | | How | Kill 5 breezes while flying, with everything mastered: the Gale Heart drops; use it. | you now just have to find and kill a breeze, there is a 66% chance the item drops|
| 78 | Corruption | | How | Using the wind during real thunderstorms fills the meter (about 3 min of use); it drains outside storms. | |
| 79 | Cure | | Eye of the Storm | Hover at Y 192+ in the Overworld during a thunderstorm for 60 s without casting. | it will also be cured automatically after 2 days or when in a snowstorm|

## Web Slinger

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 85 | Starter | B | Webline | Hold on one block, let go on another: a walkable web bridge (24 blocks, lasts 60 s). Kept for good. Symbiote bridges are black rope (suit) or tendrils (Living Armor). | |
| 86 | Base | R / G (hold) | Swing | Main- and off-hand swing webs; let go on the upswing for a boost. Up to 5 lines when mastered. No fall damage while swinging. | make the user act more like a pendulum, gaining momentum through the arc of the swing and stalling at the top |
| 87 | Base | both webs + Sneak | Slingshot | Hold Jump to draw back, release to fire. Drawing into a wall grinds into it: blocks behind and underneath crack, then break (3×3 with Living Armor). | blocks underneath you always start to crack, eventually breaking underneath the player, if the player breaks a block and is standing on air, break the attached lines and ragdoll the player for 2s. |
| 88 | Base | Jump (mid-air) | Web wings | Glide, with Feather Falling. | give controls more akin to the elytra|
| 89 | Base | Z | Web Zip | Zip to where you aim; let go short and you're flung up. | |
| 90 | Base | V | Web Shot | **Tap**: a fast webshooter pellet (0.75 heart, 1.25 with the symbiote; 0.2 s cooldown, no webs). **Hold 1 s+**: the charged blast (splash Slowness III, cobwebs, up to 5 hearts; the symbiote's black mass up to 9). | |
| 91 | Base | B | Yank | Hold: web a creature and pull it in, or yourself to a big one; hold to keep it on a lead; release aimed at a block to hitch it there. | change the pull function to a tap instead of hold. the lead is still functional and on a button hold like it was. |
| 92 | Any | punch | Cut lines | Anyone can punch a web line (swing, hitch or bridge) to break it. | they can take anywhere from 1 to 4 hits to break|
| 93 | Ascended (Spider-Sense) | passive | Spider-Sense | Threats about to hurt you glow red; the first hit every 20 s misses (sidestep). | you can dodge up to 5 hits, they individually cooldown, and you flip out of the way with an acrobatic animation everytime you dodge, either stylishly spinning to sidesteep, or backflipping into a crouch, or a clean duck and roll, and weave. |
| 94 | Ascended | C | Web Strike | Zip to whatever is under the crosshair (32 blocks) and kick it (5+ hearts). 8 s cooldown. | |
| 95 | Symbiote | X menu | Suit | Show or hide the black suit (the powers keep working). | |
| 96 | Symbiote | X | Tendril Grab | Yank a creature to you, or yourself to a block (20 blocks). | when grabbing to a block, the tendrils on both sides all animate reaching out and pulling you, then turning your back to the wall grabbed, attaching with multiple of the tendrils, fanned out impacting the wall and animating block breaking cracks wherever the tendrils make contact. |
| 97 | Symbiote | Sneak + R/G at a block | **Chunk** (new) | With Living Armor, a tendril tears a chunk (up to 6 blocks) out of the wall or ground and holds it like a mob. **Left click** swings the main tendril's chunk at what's in front (up to 5 hearts, a block chips off each hit), **right click** swings the other's. Let go of the key to throw it. | |
| 98 | Symbiote | X | Living Armor | Swollen bulk: 8 hearts of regrowing absorption, knockback resistance, bigger. Hunched idle and all-fours gallop, tendrils that strike on their own, grab and hold 2 mobs with R/G. Hungrier ×3. | visible vein-like tendrils overlaid throughought the bigger body's model, like veins coursing with symbiote power, ungulating and writhing as you idle. base activation only activates the tendrils, and they can operate over any of the suits. while the large body activates only when you are nearing death, or if it takes control randomly due to a weak bond. if near death it activates to defend itself, and takes over control, getting annoyed and stressed and losing bond with the user.  |
| 99 | Symbiote | X | Wall Crawl | Climb walls and ceilings. | |
| 100 | Symbiote | X | Consume | Eat something small (5 hearts or less) or a carried corpse: heals you and feeds you both. Won't eat skeletons, creepers or endermen. | |
| 101 | Symbiote | X | Spike Burst | Spikes hit everything within 5 blocks (4 hearts) and throw them. | |
| 102 | Symbiote | X | Shroud | Invisible until you attack (max 20 s). | |
| 103 | Symbiote | passive | Instinct | Out of the armour, tendrils burst out to shove you from fire and blasts, catch long falls, and grab or shove attackers. With the armour, it strikes back when hit. |the symbiote will ALWAYS avoid fire, pushing you away if there is room, or otherwise running you cannot use flint and steel without recoiling and screaming and causing unpredictable actions from the symbiote.  |
| 104 | Symbiote | passive | Hunger and moods | It must be fed (raw meat, Consume). Starving, it eats your hunger. Hungry with Living Armor (or briefly swelling into it) it eats corpses near you. If your own hunger runs out it sulks: no suit for about 30 s to 4 min, depending on the bond. | when it takes the liberty to eat corpses it picks the corpse up with a tendril and brings it to the mouth to devour with a single large mouth bite, and some chewing. for cow sized animals, the corpse is bitten once in half, with plenty of bloody particles, and then one more chomp to finish the corpse. chewing for a few seconds afterward, then the head returns to normal size. or disappears if you were not in a suit |
| 105 | Symbiote | passive | Frenzy (takeover) | Starving and stressed, or worn as armour too long, it takes control and hunts. It keeps control **until its prey is dead or gets 30 blocks away** (60 s at most). Jumps steps and steers around walls. | it displays more and more negative messages as it is stressed, when bond improves, it takes control but only to investigate different mobs and players, randomly grabbing them, getting the big head and staring for anywhere from 2 to 8s. the mob is then either placed back down with no issue, as the symbiote claims it was just looking, or the mob is attacked allowing the player to regain control and continue fighting or cease altogether |
| 106 | Symbiote | passive | Weaknesses | Fire and loud noises (bells, horns, explosions) hurt it; 6 exposures and it leaves you for good. | there is a disorienting vignette applied to the Pov, either orange-red and fiery when reacting to fire, or bluish white and bright when reacting to sound |
| 107 | Ascension | | How | Swing 2000 blocks through the air, with everything mastered. | |
| 108 | Corruption | | | None: the symbiote is Web's dark side. | |

## Pocket Arsenal

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 114 | Starter | R | Rummage | Reach in and see what turns up (mostly junk; sometimes a weapon). 30 s cooldown. | sometimes a flint and steel, fishing rod or other useful tools |
| 115 | Base | R | Reach Through | A rift opens, your arm reaches in, and a random weapon (with ammo) comes out. Up to 3 held, each lasting 75 s. 18 s cooldown. | |
| 116 | Base | G | Return | Send the held weapon back; refunds part of Reach's cooldown. | refunds a percentage, meaning the more time you have remaining the more is refunded |
| 117 | Base | V | Barrage | 3-5 rifts open around you and fire weapons at what you look at (3.5 hearts each). 30 s cooldown. | |
| 118 | Base | B | Pocket Stash | Your personal 27-slot pocket dimension. | |
| 119 | Base | passive | Relics | Weapons lying in dungeon, temple and city chests. Picking one up claims it into your Stash, and Reach brings it out first. | various randomly generated modified existing weapons, for example a "soul saber"grants life steal and has a ranged slash, is found in the nether, or "Grand Shield" that reflects projectiles
| 120 | World | right click | **Sword in the Stone** (new) | A rare stone pedestal on the overworld surface with Durandal driven into it. Only an Arsenal user can draw it (anyone else: it won't budge). Durandal is a relic, a netherite-strength sword (9 damage) that goes into your Stash. |make variations of durandal that have a different design, different rock embedding, and are found in different areas, the nether bastions, the end, the deep dark etc |
| 121 | Ascended (Armory) | Sneak + R | Pin | Holding a summoned weapon: every Reach brings that kind first. Empty hand: unpin. | |
| 122 | Ascended | C | Hundred Blades | Rifts in the sky over where you aim rain 20 weapons (1.5 hearts each, about 15 hearts total). 20 s cooldown. | |
| 123 | Corrupted (Void Arsenal) | passive | Void Arsenal | Summoned weapons hit 1.3× harder and add Darkness and Wither; water and rain burn you; a heavy hit can blink you away. | |
| 124 | Corrupted | V | **Void Gates** (new) | V: fix a gate where you aim (64 blocks; up to 5, a 6th replaces the oldest). Sneak + V: step through to the gate you're aiming toward, from any distance, through walls. Hold Sneak + V with no gate here: a gate drifts forward from you (up to 32 blocks, with a body-sized outline of where you'll land); let go to go there. 1.5 s cooldown. Only you see your gates. | |
| 125 | Both (Void Armory) | | | All of both. | you gain a set of flintlock pistols, that when pinned, becomes the only weapon type you can draw, but firing and then dropping the weapon gives you a new loaded flintlock ready to fire. |
| 126 | Ascension | | How | The Dimensional Key (bastion chests), used with everything mastered. | |
| 127 | Corruption | | How | Every brush with an Enderman fills the meter; setting foot in the End corrupts outright. | |
| 128 | Cure | | How | Be there, having fought it, when the Ender Dragon dies. | it also goes away after 2 days |

## Chronokinesis

| Line | Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|---|
| 134 | Starter | R | Nudge | Slow what you look at a little, or (sneaking or aiming at nothing) speed yourself a little. 15 s cooldown. | swap the binds, shift + R should slow the target, while r gives you a speed boost|
| 135 | Base | R | Rewind | Snap back to where you were 3 s ago (health restored if it was higher, fire put out). 15 s cooldown. | |
| 136 | Base | G | Stasis Field | Stop time for mobs, projectiles and players within 6 blocks for 3 s. 30 s cooldown. | increases to 6 seconds as you level|
| 137 | Base | V | Overclock | 6 s of Speed III, Haste II and faster attacks; everyone near you is slowed. 25 s cooldown. | |
| 138 | Base | B | Time Bloom | 5 s of fast-forward for crops, saplings, furnaces and smokers within 5 blocks, and it heals you 5 hearts over those 5 s. 40 s cooldown. | |
| 139 | Base | passive | Echoes | Every level-up leaves an echo of you somewhere nearby, 5-25 minutes later, walking your old paths. Only attacks shatter it, and a shattered one comes back. This is Chrono's curse in place of corruption. | |
| 140 | Ascended (Timeless) | passive | Timeless | Cooldowns recover 25% faster; clock hands circle you. | |
| 141 | Ascended | C | Chrono Punch | 10 punches in a second into everything in front of you, each harder than the last, stronger with mastery (about 11 hearts total at full mastery). Targets are pinned; the last punch throws them. 8 s cooldown. | punches are extremely fast |
| 142 | Both (Timeless + corrupted) | Z | Time Flow | Free-flow combat. Attack toward an enemy within 9 blocks and you're carried to it: jab/hook/uppercut up close, a lunging punch further out, a flying kick from far. Enemies' melee blows are held about 0.7 s with a white flash over them; right click to counter (all held blows at once). Combo counter (up to double damage). | |
| 143 | Ascension | | How | Spend 10 minutes in Stasis or rewound, sleep through a night: you wake holding the Stopped Clock. Use it with everything mastered. | |
| 144 | Corruption | | How | No natural path yet (only `/power corrupt`). Needed for Time Flow. | path to corruption is destroying an echo in the nether, killing it for good.|
