# Z Fighter, round 3 (Andy's picks, 2026-11-22)

Andy picked 20 of the 22 rows on the round 3 pick list (https://claude.ai/artifact/5oVXAobms62dwpZhbNQeyn); "lairs for
the great foes" and "the stone of names" are left out. Every row was checked against master (Kingdoms 0.11.76) as not
built. Our own names, code and art throughout, no Dragon Ball names or assets. Lore: "How power is seen" (after
Attunement) and "The Chamber's door, and what fighters pass on" (in The Z Fighters), both `(new)` in Lore.md, waiting on
Andy.

Four tracks, each its own PR, two at a time (A with B, then C with D), split so they don't edit the same files.

## Track A: how power is seen (Powers + Kingdoms)
Andy's rulings (Decisions.md, 2026-11-22) turn the round's two aura rows into one change to how every aura is seen.
- **A power level for everyone** (Andy: "the aura is visible at a glance, but the size is influenced by all factors, so a
  non attuned player or MCA still has an aura"). One number per living body from everything about it: a Z Fighter's
  `ZFighterScouter.powerLevel` (stats, form, Limit Break, mastery), Kingdoms' `Mana.power` (arcana), the body's level,
  class, health, armour and attack. The aura's size and brightness follow it. Androids have none (Lore: the Tinkerer's
  androids burn with no aura).
- **Who sees what** (`zf3-npc-aura`, Andy's note). Ki, and the plain part of anyone's aura, is seen by everyone, faint.
  This reverses `KiSight` (0.19.70, "only visible to other sensitives"). Arcana is seen only by the attuned, and only
  up to their own depth: a viewer sees an arcana aura or a mana spirit when its `Mana.power` is within a margin of their
  own (`SIGHT_MARGIN`). So a beginner's teacher and their own spirit stay hidden until they grow.
- **"Could you see that the whole time?"** (Andy's note). When a villager who learns arcana first sees an aura or a
  spirit they couldn't before, they say so to the nearest attuned player. A line pool in Kingdoms lang.
- **Ki and arcana look different** (`zf3-limit-aura`, Andy's note): ki is a raw flame round the body (the aura rework's
  shell), arcana a quieter light. **Limit Break** adds a red edge over either, thicker each tier, and counts in the
  power level.
- **NPC auras** (`zf3-npc-aura`): tyrants, rogue fighters, masters and gifted villagers draw their aura when they charge
  or power up, the same way the player's is drawn.
- **A charging hum** (`zf3-aura-hum`): a low rumble while charging that rises with the ki bar, a crackle in high forms;
  heard nearby.
- Files: client `ZFighterKiAura*`, `KiSight`, `ZFighterKiGlow`; Powers `ZFighterScouter`; Kingdoms `Mana` (the
  sight check), its villager talk, a new shared `core/PowerLevel` (Powers) read by Kingdoms through `PowersCompat`.

## Track B: ki craft (Powers)
- **Ki Guard** (`zf3-ki-guard`): a toggle, ki drains and cuts damage taken, faster drain the harder the hit; from a master.
- **Ki-wrapped fists** (`zf3-ki-fists`): a toggle, fists hit harder and push further, steady drain; counts as fists.
- **Surge** (`zf3-surge`): three seconds of charging past full gives twelve seconds of faster, harder fighting, then
  weariness.
- **Overcharge** (`zf3-overcharge`): charged techniques held past full to 150%; past 120% a chance to burst in your hands,
  lower with mastery.
- **Technique ranks** (`zf3-tech-ranks`): five ranks per technique earned by landing it in real fights: cheaper, faster
  charge, a little stronger, a look change at rank 5.
- **Healing and shielding ki** (`zf3-support`): a mending beam (your ki for a friend's health) and a ki dome over allies;
  the Divine-born learn them faster.
- **Technique scrolls** (`zf3-scrolls`): write a creator technique onto a scroll item; a reader learns it for TP.
- Files: `ZFighterKi`, `ZFighterArts`, `ZFighterSlots`, `ZFighterCreator`, new classes per technique, `ZFighterTraining`.

## Track C: fighting together (Powers + Kingdoms)
- **Team beams** (`zf3-team-beam`): beams from two or more fighters on the same target, close together, merge into one
  bigger beam that wins clashes more easily.
- **Sparring** (`zf3-spar`): a party member or sparring partner is K.O.'d, not killed; both gain a little training,
  more from a close fight.
- **A student** (`zf3-students`): a strong enough fighter is asked by a gifted villager to teach him; spar and teach
  techniques, he grows, fights beside you and defends his town; looked after he stays loyal, left alone he trains on.
- **A rival** (`zf3-rival`): a fighter of your age who grows with you and turns up at tournaments, raids and your door;
  friend in the end or a mocker. Built on Kingdoms' rivals so they don't clash.
- Files: `ZFighterPoise` (K.O.), the party, `ZFighterMasters`, Kingdoms gifted villagers and rivals.

## Track D: the world and growth (Powers + Kingdoms)
- **The Time Chamber** (`zf3-chamber`): a door on the Lookout; a day inside is a long stretch of training in harsh air,
  heat and cold; once a season per fighter, the Keeper's leave.
- **The sky answers** (`zf3-ki-weather`): charging in the highest forms darkens the sky round you, a wind, lightning
  nearby; stops when you stop.
- **Appetite** (`zf3-appetite`): training burns food fast; a big meal after (a Kitchen feast through `KitchenCompat`)
  gives a short training boost.
- **Classes shape growth** (`zf3-class-growth`): a Kingdoms class nudges Z Fighter growth (warrior strength, mage ki,
  rogue speed).
- **Late legendary blood** (`zf3-legend-wake`): a Warrior-born not born legendary has a small chance, on a full moon
  after a near-death, of waking it.
- **A gi that tears** (`zf3-battle-wear`): heavy blows scuff then tear the gi in stages, mended by a night's rest or a
  tailor. Looks only.
- Files: `ZFighterLookout`, `ZFighterLegendary`, `ZFighterTraining`, the gi's art and layer, `KitchenCompat`.

## Open questions for Andy
1. The power level's mix (Track A): how much each factor weighs, e.g. should a strong warrior with no gift ever look as
   big as a Z Fighter in base form? The plan's default: a Z Fighter's or mage's power outweighs plain strength several
   times over.
2. The sight margin (Track A): how far above your own arcana level you can still see. Default: a third of the scale.
