# Z Fighter, round 2: Andy's picks from Dragon Block NOEA (2026-10-27)

Andy ticked 63 of 78 items on the pick page (https://claude.ai/artifact/2aabiRQSzcUmacpkBXxdAN), plus a note: "our
lookout structure is nice, but needs more". Dragon Block NOEA is All Rights Reserved; DragonMineZ, which it runs on, is
GPL-3.0, and our mods are MIT. So both are ideas only: our own code, our own names (no Dragon Ball names), our own art,
like the first build (`docs/ZFighter-Plan.md`).

Not picked: Ki dash, ki cloak, technique creator, finger laser, self-destruct, turn-to-sweets beam, form creator, bulked
grades, Sphinx-like race, healing tank, more bean kinds, flying cloud, punch meter, hair editor, and the power level
number as its own feature (the scouter still reads one). Andy can still add any of them later.

The picks are split into eight patches, each its own PR (Andy merges to test, see the merge-to-test note). Inside a
patch the work runs as two agent tracks at a time that don't touch the same files.

## Patch 1: combat feel and senses (Powers)
- **Lock-on** (new key, default unbound until Andy picks one): locks camera and attacks onto one target in sight; the
  hold combo, Flash Step and ki attacks aim at it; breaks past 32 blocks or out of sight.
- **Poise and K.O.**: every living thing gets a hidden poise bar. Blows wear it (heavier blows more), a parry wears
  the attacker's. Broken: staggered for a second (open to a counter); broken again while staggered: K.O. for 2 s. Ties
  into the existing downed state, never kills on its own.
- **Rush attacks** (bought with TP, bound to a technique slot): a dragon-fist dash through the target, a wolf-like
  flurry, a grab-and-pummel, a kick-up-and-chase. Each locks the foe for its length with a short camera cut.
- **Grab and throw**: sneak + left click at close range grabs, release throws; slams use `ZFighterImpact`.
- **Beam clash timing**: in a clash, a marker sweeps a bar; right click in the window (Perfect/Good/Miss) pushes harder
  than holding V. Shown inside the clash film.
- **Impact frames**: one or two frames of a black-and-white high-contrast flash on finishers, Spirit Bomb landings,
  ape smashes; client setting to turn off.
- **Combat flight**: banking into turns, barrel roll on double-tap strafe, a short air burst.
- **Ki weapons**: shape ki into a blade, scythe or clawed lance (technique slot); ki-power scaled, ki drain while held;
  counts as fists for "fists first".
- **Meditation dodge**: a trained passive chance to slip melee blows (higher the faster/stronger you are than the
  attacker), grown by shadowboxing.
- **Ki sense** (key): nearby living things' ki shown through walls as faint outlines, brighter for stronger ones,
  range grows with training; a fighter hiding ki shows dim.
- **Scouter**: a craftable eyepiece (helmet slot or Curios-free head slot) that reads a target's power level on the
  HUD (a number from stats, form, Limit Break and mastery); overloads and cracks on a number far above its tier.
- **Instant teleport**: a late technique: teleport to a spot in sight up to 64 blocks; hold to pick a friend or master
  whose ki you've sensed and jump to them (cooldown, big ki cost).
- **Lend ki**: sneak + right click a friend while charging pours your ki into theirs.

## Patch 2: techniques (Powers)
- **Eight technique slots** on two bars of four (a key swaps bars); the current G/V loadout becomes bar 1.
- New techniques (our names): **Drill beam** (spiral piercing beam), **Ring of orbs** (orbs hang round the target and
  close in), **Triangle cannon** (crushing downward blast, costs health), **Rising dome blast** (launch then upward
  pillar), **Ki rain** (sprays up, rains over an area), **Super nova** (giant overhead ball, Frost-born signature),
  **Rage scream** (shockwave shout, Rage-mode only), **Afterimage decoy** (a still copy foes keep attacking).
- **Aura style choice**: smooth, sparking or godly, on the training screen; our flame aura becomes "sparking".

## Patch 3: forms (Powers)
- **False Super**: before the first Super, a near-death flare (short, unstable) that fills more hardship.
- **Golden Great Ape**: a Super who turns ape becomes golden; a step towards Primal Super.
- **God form ritual**: past Super 3, five good-hearted fighters (players or gifted villagers) lend ki in a ring at the
  Lookout to lift one into a red-haired god form with a calm aura.
- **Silver instinct state**: auto-dodge and counter while it lasts; very costly, needs god form mastered.
- **Ego state**: a dark mirror: power grows with damage dealt and taken; opens through Corruption.
- **Legendary line**: 1 in 40 Warrior-born are born with the mutation; a second line of berserk legendary forms.
- **Android upgrade**: a mad-scientist questline rebuilds you: ki costs halve, regen soars, ki hits a little weaker;
  its own two android forms.
- **More Verdant forms**: dark giant and super Verdant. **More Frost-born forms**: a fifth form, and a metal rebuild
  after a near-death.

## Patch 4: fusion (Powers)
- **Fusion earrings** (rare: wishes, tyrant drops): fuse at once with any lineage, 30 minutes.
- **Fusion arm bands** (craftable, weaker): mixed lineages, 3 minutes, both must agree.
- **Five-way fusion**: a late ritual for five fighters in a ring; one huge body, one steers, the rest watch.
- **Fusion partner for solo play**: a master who has trained you enough fuses with you.
- **Forms burn fusion time**: each form step shortens the timer.

## Patch 5: races (Kingdoms + Powers)
- **Divine-born** heritage: celestial, ki hidden from ki sense/scouters, faster regen, punishes the opposite
  alignment; Restoration-magic friendly.
- **Bio-built** heritage: absorbs downed foes to evolve through three forms, drains life, later buds small copies
  (careful not to overlap the Symbiote: no tendrils, no goo).
- **Rubber-folk** heritage: pink and stretchy, regrows, absorbs weak foes, heals more from sweets (Kitchen via
  `KitchenCompat`).
- **Racial extras**: Earthling adrenaline + blood-paid ki; Verdant merging and water regen; Frost-born ki reserve and
  ki-hardened body; Warrior-born near-death comeback that raises the limit.

## Patch 6: items, tournaments and the Lookout (Powers + Kingdoms)
- **The Lookout, bigger** (Andy's note): see the open question below.
- **Gravity machine** (a room of 5-25 blocks set to 1-50x gravity, needs fuel), **pocket capsules** (throw: a small
  house or vehicle pops out), **gi and armour patterns** + **suit editor** (recolour, chest mark, gloves, boots, belt,
  helmet, cape, fins, live preview, on our costume system), **fighter weapons** (extending staff, legendary sword,
  hero's blade, allowed under fists first), **more wishes** (re-make character, reset racial skill, move stat points,
  change difficulty), **memory drill** (fifth Training Post game), **HUD editor + first-time tips**, **realistic mode**
  (server config).
- **Last-one-standing tournament**, a **bracket screen**, and **themed invasions** (waves, boss bar, a boundary to stay
  inside, reward list, standing lost if the kingdom falls) as an upgrade of tyrant raids.

## Patch 7: party and story (Powers + Kingdoms)
- **Party**: invite, shared quest progress, friendly fire off, members' stats at any distance.
- **Story sagas**: an original quest tree told through fighters, using our lore (the Aether remnants, the Giants and
  monoliths), with bosses and set difficulty. **Side quests** from fighters and masters, reusing town errands.

## Patch 8: other worlds (Powers)
- **Space pod**: a ship with a star map: our moon, two or three new planets, a sun-side forge world and a black hole
  stop, each its own dimension with its own terrain; the Verdant World and the future dimension join the map.
- **Planet missions**: free a planet or conquer it, nearby players share progress (Kingdoms' good/Rogue paths).
- **Afterlife**: a Z Fighter who dies wakes with a halo in an other world (a judge, a long road to a master, a
  tournament of the dead); a fortune teller or a wish sends him back. Optional by config.
- **Demon realm**: three dark layers with demon villages and an army; the dark masters' home.

## Open questions for Andy
1. Patch order: the list above, or something else first?
2. The Lookout "needs more": more of what? Proposed: a second ring of rooms round the courtyard (the Keeper's
   quarters, a library of techniques where masters' lessons can be bought back, a meditation garden that grows Hermit
   Beans), a sparring ring on the edge, a ki-sense pool that shows where the wish orbs are, and the god ritual circle.
3. Lock-on key: which key? (R/G/V/B/C/Z/H/' are taken.)
