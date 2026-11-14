# Fortress knight's plate: model spec (REA method)

Andy (2026-11-14): "Remodel our five bosses". The knight was a wither skeleton in plain netherite. He fights on
`MobCombat`'s humanoid weapon strings, so he keeps his body and gets armour of his own on it. Bosses'Rise is All Rights
Reserved: nothing of its models or textures was opened or copied. Everything is made by
`kingdoms/tools/gen_fortress_knight.py`.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | `nether/FortressKnight` (our own code) | "a wither skeleton grown to a champion's size in blackened plate"; souls rise off him |
| R2 | Lore.md, "Four more great ones": one of the old war's dead, a champion of the demons' country | horns, the Nether's colours |
| R3 | Late medieval plate: great helm, gorget, lamed cuirass, tassets, pauldrons, vambraces, poleyns, sabatons | which pieces, where they sit |
| R4 | `client/ReverentLayer` and `WornModel` | bone names, how pieces hang on a humanoid's limbs |

## 2. Facts

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | Blackened plate | R1 | near-black plate, worn bright on upper edges |
| F2 | A champion, taller than his kind | R1 | horns and pauldron spikes raise the outline; his 1.35 scale is unchanged |
| F3 | Of the demons' country | R2 | two swept horns on the helm, a cape the red of Nether brick |
| F4 | Souls rise off him | R1 | the visor slit is lit soul-blue |
| F5 | Dead a long time | R1, R2 | the cape's lower third torn into strips |
| F6 | It must take every pose his limbs do | R4 | one piece group per limb, no bones of its own that move |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: heraldry. Chosen: none; a line of old gilt on the belt only.
- U2: the cape moves. Chosen: no, it hangs stiff, tipped a little off his back.
- U3: the visor glows in the dark. Chosen: no, it is painted bright but lit like the rest.

## 4. Differences left (validation)

Checked in the `--preview` render (project files `art-overhaul/boss-remodels/fortress-knight.png`):
- A wither skeleton's arms are two wide; the arm plates are built for four, so they sit a little outside the bone.
- The cape does not swing with his blows and can pass through his legs in a deep lunge.
- Not yet seen in game.
