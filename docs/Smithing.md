# Smithing (after Overgeared)

Overgeared (phuccom000, MIT; notice in `tools/overgeared_license.txt`) rewritten into our Tinkering for 26.3 Fabric.
Code: `tinkering/smithing/` (server) and `client/tinkering/smithing/`. Tuning numbers are the constants at the top of
`Grade`, `HotMetal` and `Forging`.

## Heat and tongs
- Lay ingots (iron, gold, copper, silver, steel and the alloys) or a forging blank in a Forge Hearth's coals (right-click
  it). After `HotMetal.HEAT_TICKS` (100 ticks at heat 2; bellows halve it) they are hot. Take them with tongs or an
  empty hand; they stay hot while they lie in a lit hearth.
- Hot metal goes cold `HotMetal.COOL_TICKS` (60 s) after it leaves the fire: a Hot Ingot turns back into its ingot.
- Carrying anything hot with no tongs in either hand sets you alight. With tongs, they lose 1 durability every 2 s.
  Fire Resistance, creative and Emberborn (bare hands) are spared.
- Tongs: wooden (crafted: planks and sticks), iron and steel (forged: Tongs Jaws at the Tool Station).

## The Smithing Anvil
- Sneak-use a smithing hammer on a vanilla anvil to turn it into a Smithing Anvil.
- Lay hot ingots of one metal on it (up to 9), right-click with the hammer and pick what to make. Its ingots become a
  forging blank; the rest come back to you.
- Strike (right-click) in time with the bar under the crosshair: the green zone is perfect, the amber zone good. The
  zones shrink and the arrow speeds up each blow; harder metals (steel) shrink them faster. A dagger takes 3 blows, a
  claymore 8.
- If the metal goes cold before you finish, take the blank back to the hearth; the blows struck so far stay on it.
- Hammers: copper (its head casts, so there is always a first hammer) and steel, both forged at the Tool Station.

## Grades
| Grade | Durability | Attack | Attack speed | Mining speed | Armour |
| --- | --- | --- | --- | --- | --- |
| Poorly Forged | x0.75 | -1 | -0.1 | x0.85 | -1 |
| Well Forged | x1 | 0 | 0 | x1 | 0 |
| Expertly Forged | x1.1 | +0.5 | +0.05 | x1.1 | +0.5 |
| Perfectly Forged | x1.2 | +1 | +0.1 | x1.2 | +1 |
| Masterwork | x1.3 | +2 | +0.15 | x1.3 | +1.5 |

Average score per blow (perfect 1, good 0.6, miss 0): 0.9 Perfect, 0.72 Expert, 0.45 Well, below that Poor. An
all-perfect forging rolls Masterwork 15% of the time (gold name, glint). The head's grade carries onto the tool made
from it at the Tool Station.
