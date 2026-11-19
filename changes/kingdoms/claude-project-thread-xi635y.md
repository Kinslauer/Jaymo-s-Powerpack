# NPC sea ships pay for their powder and shot
- Andy said "yes" to making NPC sea ships pay for their ammo the way airships do. **Every sea ship sailed by her own crew now carries a magazine**: gunpowder and a count of each of the five shot types. Each round a gunner loads spends 1 powder and 1 shot. He loads the type he wants if she has it, otherwise round, heavy, chain or grape, whichever is left. He never falls back to a shell he didn't ask for. With no powder, or no shot of any kind, her guns stay empty.
- **Stock at launch** (ships saved before this are stocked the first time they load): a warship carries 36 powder, 18 round, 7 heavy, 5 chain, 5 grape and 2 shells. A pirate carries 28 powder, 14 round, 5 heavy, 4 chain, 4 grape and 1 shell. A merchantman, whaler or fishing smack carries 8 powder, 6 round and 2 grape.
- **Topping up**: within 80 blocks of home and with no ship to fight, she is topped up once a minute.
  - A realm's ship is paid for from its treasury: powder 2, round 2, heavy 5, chain 4, grape 3. A shell costs 40, and is bought only while the realm holds at least 1,040, the same rule as its wall guns.
  - A pirate takes what she needs from her cove's storage. Her cove makes a shell from 3 TNT, or from 15 gunpowder if it holds at least 23, plus a round shot.
  - Pirate coves now cast 2 round shot a day so their ships don't run dry.
- Airships are unchanged and still load from their hold's chests.
- Classes: new `ShipMagazine`; `Crew.magazine` (saved); `Corsairs.gunnery`; `PirateCoves.DAY_SHOT`.
- Built; not played.
