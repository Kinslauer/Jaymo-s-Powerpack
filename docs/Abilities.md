# Jaymo's Powers: every ability (v0.6.6)

Edit the **Change to** column (or anything else) and send it back. Blank means keep it as it is.

**Keys:** R G V B are the four abilities; **Z** is the power's mode; **C** is the ascended ability; **X** is the
power skill key (Web: the symbiote menu; Wind: Tempest Kick). Every power starts with only its **starter**, and unlocks its four abilities one by one by using them.
**Ascended** and **corrupted** are separate fates: a power can be one, the other, or both.

---

## Gravity: rework plan

### What it does now, in every form

| Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|
| Starter | R | Lift (weak) | Hold: the creature, item or object under the crosshair floats where you point. You can't choke or slam with it yet. | |
| Starter | passive | Pull of small things | Items within 4 blocks drift toward you; you jump a little higher. Kept for good. | |
| Starter | V (hold) | Item pull | Until Weightless is unlocked, holding V drags items and XP in from 12 blocks, fast. | |
| Base | R | Lift | Hold for up to 6 s (8 s cooldown). Range 14 blocks, +4 per ability unlocked, max 30. Works on any entity. Let go and it falls (real fall damage). | |
| Base | R + right click | Choke, then Slam | 1st click: held by the throat, raised high, 0.5 heart a second. 2nd click: hauled up, then slammed down, buried 1-3 blocks, debris ring. One of 4 full-body slam animations on zombies, players and big mobs. | **Your note:** R + Left click: push people away really far (through walls if inside them, and it pushes blocks). |
| Base | R + right click (mid-air) | Aerial slam | You hang in the air, tilt 40°, and slam harder. Always 3 blocks deep, and it smashes the ground. Mastered: flying debris knocks you clear of the crater. | |
| Base | R + right click (items, corpses) | Draw in | Items fly into your inventory; Kitchen corpses are bagged or carried. | |
| Base | R at a block | **Chunk** (new) | With nothing to lift where you aim, Lift tears a chunk out of the wall or ground (3 blocks +1 per ability, harder stone as it grows) and holds it. **Left click** (or right) throws it: it batters what it hits (up to 6 hearts) and shatters on impact. | |
| Base | R + right click (objects) | Hurl | Carts, boats, armour stands, falling blocks: drift in to 2.5 blocks while held, then thrown hard (max 6 hearts to what they hit) and smash blocks. | **Your note:** R + Right click on *blocks*: rip them out of place, then throw with left click. |
| Base | R on players | Held players | Stunned 4 s (Wind users: 0.5 s and they tumble). After that they can struggle or fly 3 blocks free. | |
| Base | damage | Slam damage | Max 10 hearts: a mid-air slam on something big, fully mastered and ascended. Fresh power about 35% of that, ground slams 65%, small mobs 75%. | |
| Base | G | Gravity Well | A point where you aim (24 blocks) pulls everything within 7 blocks in for 4 s. 14 s cooldown. | |
| Base | V | Weightless | Toggle: gravity -70%, jump high, fall slow, **no fall damage**. Costs hunger, lasts up to 30 s; 10 s cooldown (2 s if switched off early). Creatures within 6 blocks of you get heavier. | |
| Base | B | Crush | Hold up to 3 s, release: everything around where you aim (24 blocks, radius 2.5-5.5) is slammed down, hurt 2-8 hearts and pinned 3 s. The zone is weighed down while charging. Pops flowers, item frames and paintings. 6-20 s cooldown. | |
| Base | G + B | Cave-in | Crush landing inside your own Well tears up 14-40 blocks and hurls them into the middle: 7-18 hearts. | |
| Ascended (Event Horizon) | passive | Event Horizon | You can't be knocked back, and projectiles bend away within 3 blocks of you. | |
| Ascended | C | Singularity | A black hole where you aim (32 blocks): pulls everything within 10 blocks for 3 s, 1.5 hearts every 0.5 s, then bursts for 3 hearts. 25 s cooldown. | **Your note:** a large rock from space is pulled down onto the target: animated falling meteor, big explosion and particles. |
| Corrupted (Collapse) | passive | Collapse | You're 35% heavier (+25% fall damage); loose things within 6 blocks drift toward you; Crush and cave-ins hit 1.6× harder; letting go of Lift flings what you held. | |
| Corrupted | C | (none yet) | Collapse has no C move of its own. | **Your note:** a black hole. |
| Both (Collapsing Horizon) | | | All of the ascended and corrupted effects. | |
| Ascension | | How | 30 creatures killed by weight you gave them (falls after Lift, Crush, slams, the Singularity), with everything mastered. | |
| Corruption | | How | Each kill by weight +2; every 20 s weightless +1 (100 corrupts). | |
| Cure | | How | Be lifted by a shulker's levitation for 10 s. | |

### Your rework notes, and what I need to know

1. **R + left click: the push.** Push the held target really far (through walls if they're inside one, and it shoves
   blocks). Does this *replace* choke/slam on right click, or sit beside it? How far is "really far" (20 blocks? 50?),
   and should walls they hit break?
2. **R + right click on blocks: rip out and throw.** Aim at a block, it tears loose and floats in front of you; left
   click throws it. How big: one block, or a chunk (3×3)? Does it drop as the block afterwards, or shatter?
3. **Singularity C (ascended): meteor.** A rock pulled down out of the sky onto where you aim, seen falling, then a
   big explosion. Should it break blocks (like TNT), or only hurt creatures? Damage target?
4. **Collapse C (corrupted): black hole.** This is roughly what the Singularity does today. Move the current
   Singularity to Collapse's C as-is, or make it bigger and darker?
5. **Both (Collapsing Horizon)**: which C wins, or does it get a third move?

---

## Wind Control

| Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|
| Starter | passive | Wind cloak | A little quicker on foot; hold Jump in mid-air to drift down slowly. Kept for good. | |
| Base | R / Sneak + double Jump | Flight | Hover (WASD, Jump up, Sneak down). Sprint: cruise, up to sonic speed. Sneak at speed locks speed. S at speed is the air brake. Diving gains speed; pulling up is limited at speed; turning too hard throws you into a 2 s tumble. You lean into turns. Costs hunger. | |
| Base | flight | Crashes and rams | Hitting walls or the ground at speed hurts (wall damage grows with speed²) and smashes blocks. Flying through mobs at speed rams them (up to 18 hearts) and throws them. | |
| Base | G (hold ≤5 s) | Tailwind / Gust | In the air: a dash, Speed and Jump Boost. On the ground: a gust blast (a full charge is TNT-strength). Cooldown up to 3 min for a full blast. | |
| Base | V (hold) | Gale Barrier | Resistance and knockback resistance, and a gust shoves mobs away. 30 s cooldown. | |
| Base | B (hold) | Zephyr's Breath | Heals you and nearby players (Regeneration, Slow Falling). 45 s cooldown. | |
| Weather | | Weather factor | Clear weather is slower, a storm stronger (flight and abilities). | |
| Corrupted (Stormbound) | passive | Stormbound | Every cast calls lightning at its target, but **only if it's raining** (60% strength; full in a thunderstorm). Gales hit twice as hard; slower on foot and in flight. | |
| Corrupted | B | Call Lightning | In the rain, replaces Zephyr: a bolt where you aim (3-4.5 hearts, sets fire, charges creepers), full strength in a thunderstorm. Costs hunger; static bites back. **Under a clear sky B is Zephyr's Breath again.** | |
| Corrupted | Z | Stormbound mode | Melee hits stun and stack static; 5 stacks = shocked (rooted 2.5 s). The storm sometimes arcs into you. | |
| Ascended (Tempest) | Z | Tempest mode | Circling wind deflects projectiles; other abilities 1.25× stronger. | |
| Ascended | C | Cyclone Lance | A spear of wind 32 blocks ahead: 7 hearts and a big throw. Stormbound in the rain: lightning along it; otherwise the plain Tempest lance. **In a mode (Z), it also stuns** (Slowness V, Weakness, Nausea for 2.5 s). 6 s cooldown. | |
| Mastered | X | **Tempest Kick** (new) | A roundhouse kick that throws a white crescent of wind ahead: 5 blocks, 3 hearts (ascended: 20 blocks, 6 hearts). 3 s cooldown (2 s ascended). | |
| Any | passive | **No shields** (new) | A Wind user can't hold a shield: the wind tears it out of the hand into the inventory. | |
| Both (Thunderhead) | Z | Thunderhead mode | Both modes at once. Turning it on in rain or a storm brings bolts down around you and darkens everyone's screen within 48 blocks. | |
| Any | Z | Mode hunger | Modes cost hunger and switch off at 3 hunger shanks. | |
| Item | | Sora's Dagger | Iron → Gilded (feather + gold block) → Diamond (breeze rod + diamond block, right click Flurry) → Tempest (ascended Wind, Nether Star + Dragon's Breath + Heavy Core). Its gifts only work for Wind users. | |
| Ascension | | How | Kill 5 breezes while flying, with everything mastered: the Gale Heart drops; use it. | |
| Corruption | | How | Using the wind during real thunderstorms fills the meter (about 3 min of use); it drains outside storms. | |
| Cure | | Eye of the Storm | Hover at Y 192+ in the Overworld during a thunderstorm for 60 s without casting. | |

## Web Slinger

| Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|
| Starter | B | Webline | Hold on one block, let go on another: a walkable web bridge (24 blocks, lasts 60 s). Kept for good. Symbiote bridges are black rope (suit) or tendrils (Living Armor). | |
| Base | R / G (hold) | Swing | Main- and off-hand swing webs; let go on the upswing for a boost. Up to 5 lines when mastered. No fall damage while swinging. | |
| Base | both webs + Sneak | Slingshot | Hold Jump to draw back, release to fire. Drawing into a wall grinds into it: blocks behind and underneath crack, then break (3×3 with Living Armor). | |
| Base | Jump (mid-air) | Web wings | Glide, with Feather Falling. | |
| Base | Z | Web Zip | Zip to where you aim; let go short and you're flung up. | |
| Base | V | Web Shot | **Tap**: a fast webshooter pellet (0.75 heart, 1.25 with the symbiote; 0.2 s cooldown, no webs). **Hold 1 s+**: the charged blast (splash Slowness III, cobwebs, up to 5 hearts; the symbiote's black mass up to 9). | |
| Base | B | Yank | Hold: web a creature and pull it in, or yourself to a big one; hold to keep it on a lead; release aimed at a block to hitch it there. | |
| Any | punch | Cut lines | Anyone can punch a web line (swing, hitch or bridge) to break it. | |
| Ascended (Spider-Sense) | passive | Spider-Sense | Threats about to hurt you glow red; the first hit every 20 s misses (sidestep). | |
| Ascended | C | Web Strike | Zip to whatever is under the crosshair (32 blocks) and kick it (5+ hearts). 8 s cooldown. | |
| Symbiote | X menu | Suit | Show or hide the black suit (the powers keep working). | |
| Symbiote | X | Tendril Grab | Yank a creature to you, or yourself to a block (20 blocks). | |
| Symbiote | Sneak + R/G at a block | **Chunk** (new) | With Living Armor, a tendril tears a chunk (up to 6 blocks) out of the wall or ground and holds it like a mob. **Left click** swings the main tendril's chunk at what's in front (up to 5 hearts, a block chips off each hit), **right click** swings the other's. Let go of the key to throw it. | |
| Symbiote | X | Living Armor | Swollen bulk: 8 hearts of regrowing absorption, knockback resistance, bigger. Hunched idle and all-fours gallop, tendrils that strike on their own, grab and hold 2 mobs with R/G. Hungrier ×3. | |
| Symbiote | X | Wall Crawl | Climb walls and ceilings. | |
| Symbiote | X | Consume | Eat something small (5 hearts or less) or a carried corpse: heals you and feeds you both. Won't eat skeletons, creepers or endermen. | |
| Symbiote | X | Spike Burst | Spikes hit everything within 5 blocks (4 hearts) and throw them. | |
| Symbiote | X | Shroud | Invisible until you attack (max 20 s). | |
| Symbiote | passive | Instinct | Out of the armour, tendrils burst out to shove you from fire and blasts, catch long falls, and grab or shove attackers. With the armour, it strikes back when hit. | |
| Symbiote | passive | Hunger and moods | It must be fed (raw meat, Consume). Starving, it eats your hunger. Hungry with Living Armor (or briefly swelling into it) it eats corpses near you. If your own hunger runs out it sulks: no suit for about 30 s to 4 min, depending on the bond. | |
| Symbiote | passive | Frenzy (takeover) | Starving and stressed, or worn as armour too long, it takes control and hunts. It keeps control **until its prey is dead or gets 30 blocks away** (60 s at most). Jumps steps and steers around walls. | |
| Symbiote | passive | Weaknesses | Fire and loud noises (bells, horns, explosions) hurt it; 6 exposures and it leaves you for good. | |
| Ascension | | How | Swing 2000 blocks through the air, with everything mastered. | |
| Corruption | | | None: the symbiote is Web's dark side. | |

## Pocket Arsenal

| Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|
| Starter | R | Rummage | Reach in and see what turns up (mostly junk; sometimes a weapon). 30 s cooldown. | |
| Base | R | Reach Through | A rift opens, your arm reaches in, and a random weapon (with ammo) comes out. Up to 3 held, each lasting 75 s. 8 s cooldown. | |
| Base | G | Return | Send the held weapon back; refunds part of Reach's cooldown. | |
| Base | V | Barrage | 3-5 rifts open around you and fire weapons at what you look at (3.5 hearts each). 30 s cooldown. | |
| Base | B | Pocket Stash | Your personal 27-slot pocket dimension. | |
| Base | passive | Relics | Weapons lying in dungeon, temple and city chests. Picking one up claims it into your Stash, and Reach brings it out first. | |
| World | right click | **Sword in the Stone** (new) | A rare stone pedestal on the overworld surface with Durandal driven into it. Only an Arsenal user can draw it (anyone else: it won't budge). Durandal is a relic, a netherite-strength sword (9 damage) that goes into your Stash. | |
| Ascended (Armory) | Sneak + R | Pin | Holding a summoned weapon: every Reach brings that kind first. Empty hand: unpin. | |
| Ascended | C | Hundred Blades | Rifts in the sky over where you aim rain 20 weapons (1.5 hearts each, about 15 hearts total). 20 s cooldown. | |
| Corrupted (Void Arsenal) | passive | Void Arsenal | Summoned weapons hit 1.3× harder and add Darkness and Wither; water and rain burn you; a heavy hit can blink you away. | |
| Corrupted | V | **Void Gates** (new) | V: fix a gate where you aim (64 blocks; up to 5, a 6th replaces the oldest). Sneak + V: step through to the gate you're aiming toward, from any distance, through walls. Hold Sneak + V with no gate here: a gate drifts forward from you (up to 32 blocks, with a body-sized outline of where you'll land); let go to go there. 1.5 s cooldown. Only you see your gates. | |
| Both (Void Armory) | | | All of both. | |
| Ascension | | How | The Dimensional Key (bastion chests), used with everything mastered. | |
| Corruption | | How | Every brush with an Enderman fills the meter; setting foot in the End corrupts outright. | |
| Cure | | How | Be there, having fought it, when the Ender Dragon dies. | |

## Chronokinesis

| Form | Key | Ability | What it does now | Change to |
|---|---|---|---|---|
| Starter | R | Nudge | Slow what you look at a little, or (sneaking or aiming at nothing) speed yourself a little. 15 s cooldown. | |
| Base | R | Rewind | Snap back to where you were 3 s ago (health restored if it was higher, fire put out). 15 s cooldown. | |
| Base | G | Stasis Field | Stop time for mobs, projectiles and players within 6 blocks for 3 s. 30 s cooldown. | |
| Base | V | Overclock | 6 s of Speed III, Haste II and faster attacks; everyone near you is slowed. 25 s cooldown. | |
| Base | B | Time Bloom | 5 s of fast-forward for crops, saplings, furnaces and smokers within 5 blocks, and it heals you 5 hearts over those 5 s. 40 s cooldown. | |
| Base | passive | Echoes | Every level-up leaves an echo of you somewhere nearby, 5-25 minutes later, walking your old paths. Only attacks shatter it, and a shattered one comes back. This is Chrono's curse in place of corruption. | |
| Ascended (Timeless) | passive | Timeless | Cooldowns recover 25% faster; clock hands circle you. | |
| Ascended | C | Chrono Punch | 10 punches in a second into everything in front of you, each harder than the last, stronger with mastery (about 11 hearts total at full mastery). Targets are pinned; the last punch throws them. 8 s cooldown. | |
| Both (Timeless + corrupted) | Z | Time Flow | Free-flow combat. Attack toward an enemy within 9 blocks and you're carried to it: jab/hook/uppercut up close, a lunging punch further out, a flying kick from far. Enemies' melee blows are held about 0.7 s with a white flash over them; right click to counter (all held blows at once). Combo counter (up to double damage). | |
| Ascension | | How | Spend 10 minutes in Stasis or rewound, sleep through a night: you wake holding the Stopped Clock. Use it with everything mastered. | |
| Corruption | | How | No natural path yet (only `/power corrupt`). Needed for Time Flow. | |
