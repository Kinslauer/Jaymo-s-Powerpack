# Town golems and the throne golem: behaviour spec

Clean-room spec in the REA-Prompts style (behaviour, not code). Source of ideas: **Special Golems** (CurseForge
`special-golems`, leo1011101; "Custom License", Forge/NeoForge 1.19.2 to 1.21.1). Its files are not used: what's below
is read off its public description and gallery images only, then our own design.

## What the original does (as described)
| Golem | Described behaviour | Ours |
|---|---|---|
| Clay / Terracotta | 40 HP, holds weapons, wears armour | not remade (copper golems exist) |
| Snow | 4 HP, rain-proof with pumpkin, 1-damage snowballs | not remade (vanilla snow golems) |
| Iron | 100 HP, follows its maker, runs at targets | not remade (vanilla iron golems) |
| Gold | 200 HP, slow, long laser, more harm to undead | **Gilt Lamp Warden**: night glow, beam, double on undead + ignites |
| Diamond | castle-tower golem, 500 HP; on its owner's word it sits, folding into a throne (body = back, arms = armrests, legs = seat), and takes damage for allies | **Throne Golem**: our walking keep in diamond-set stone; serves its realm's ruler only; kneels into a throne on their word; takes every blow aimed at the ruler within 48 blocks; past 20 taken, rises and attacks the attacker |
| Emerald | three parts: head watches an ally so they won't die, chest heals, legs re-form | **Verdant Mender**: heals and keeps one from dying, one body |
| Lapis | stationary laser, engraves enchantment upgrades | not remade (Andy) |
| Redstone | unfinished | not remade |
UNKNOWN: exact ranges, cooldowns, summoning shapes (sources disagree); all numbers here are ours.

## Ours
- How you get them: golemworks realms keep them; anyone with Golemancy makes them on the infusion altar.
- Golemworks: 15% of realms, folk attuned to mana, roster 2 lamp wardens, 2 menders, 1 throne golem knelt by the seat.

## The models (redesign, 2026-11-11)
Andy: "The village golems all need redesigns, the throne golem NEEDS to turn into a throne among the other previous
descriptors." The earlier descriptors kept: the throne golem folds into a giant throne like the mod's picture (body the
back, arms the armrests, legs the seat), it must not look like a diamond block (dark stone set with diamond), it answers
only to its ruler; the lamp warden and mender stand beside iron golems.

Every cube, pixel and keyframe is ours, written by `kingdoms/tools/gen_town_golems.py` (no file or pixel of Special
Golems; its one gallery picture of the throne was the only reference, for the idea of which part becomes what). Each kind
has its own GeckoLib model, 16 units to a block before the kind's scale:

| Kind | Shape | Sheet | Scale | Animations |
|---|---|---|---|---|
| Lamp warden | lean sandstone lamplighter in gold bands; the head is a lantern cage with a crystal flame turning in it | 128x64 | 1.15 | idle, walk, beam, hurt, death |
| Mender | squat mossy river-stone boulder, long arms and mitten hands, small head in front of the hump, emerald crystals on the back | 128x128 | 0.95 | idle, walk, mend, hurt, death |
| Throne golem | walking keep of dark masonry and diamond trim: turret shoulders, bracer forearms, crenellated head | 128x128 | 1.25 | idle, walk, smash, hurt, kneel, throne, rise, death |

**The fold into the throne** (`kneel`, 1.5 s, the same 30 ticks the game waits before the ruler can sit):
1. 0.2 to 0.85 s: it sits. Thighs swing level (the seat, 13 units up), shins stay upright in front (the step), hips sink
   just as far as keeps the feet on the ground.
2. 0.45 to 0.95 s: the keep (body) slides 8.5 units back until its face is the seat's back.
3. 0.7 to 1.1 s: a plinth telescoped inside the keep is let down 13 units to the ground under it.
4. 0.75 to 1.2 s: the forearms swing level (the armrests, fists at the front) and the arms drop 2.5 units.
5. 1.0 to 1.4 s: a crenellated crest telescoped inside the keep rises 12 units behind the head (the high back).
`rise` is the same played backwards; `throne` holds the end pose while the head slowly looks to each side.

**Animations match what the game does:** the warden's `beam` bows the lantern as the beam is fired (0.1 s in), the
mender's `mend` holds its hands out as Regeneration is given, the throne golem's `smash` lands as the blow is dealt, and
it doesn't walk or strike until `rise` is done. The warden's flame and chest light are drawn only at night.

UNKNOWN: how any of it reads in game (not played); the sitter's height on the seat (`TownGolem.SEAT_Y`, `SEAT_Z`) is
worked out from the model, not seen.

## Differences from the original (deliberate)
No summoning by block shape (altar only), no Laslem's Pumpkin, no armour/weapons on clay golems, no following iron golem,
no three-part emerald golem, the throne shields only its ruler (not allies round it), all numbers our own.
