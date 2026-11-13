# the Darkened Chapel, the Echo Bow, a target dummy, and two more mods (The Graveyard, Re:Animal)
- **The Darkened Chapel** (`jaymospowers:darkened_chapel`, rare, in birch forests and the dark forest): a big abandoned
  chapel of the Lantern Warden with the parish dead in its nave and cellars. The building is Tada_no_ahiru's *Dark
  Fantasy: Church of Sin* (MIT, Forge 1.20.1 only; credit and licence in `licenses/LICENSE_church_of_sin`), ported by
  `tools/church_of_sin_port.py`: block for block, with Forge's and Supplementaries' leftovers taken out, its two books
  rewritten for our world and its icons replaced by ten votive panels of our own (`tools/gen_votive_panels.py`; they
  are placeable paintings too, 4 wide and 6 tall).
- **Where the corrupted find their master** (Andy: "the church can be where the corrupted go to find their master, the
  demon master can come up from the nether and speak to them there"): when a holder whose power is corrupted stands
  before the altar, the floor opens as a pool of ember light and, three seconds later, the Master of the Gloam is
  standing in it, with his usual talk (the lesson in blood, the Cinder Pact) and a greeting of his own. He goes down
  again 30 seconds after the last corrupted holder has left. He never comes for anyone else. Nothing stays open: it is
  not a portal. Out under a dark sky, a corrupted holder now and then (every 5 minutes, half the time) gets a line on
  the action bar saying which way the nearest chapel lies, within 64 chunks. `ZFighterChapel`.
- **The Echo Bow** (`jaymospowers:echo_bow`; Andy: "the echo bow should be a weapon that spawns for arsenal"): a rare
  Reach Through pull (weight 6, beside a plain bow's 45), dressed like any pulled bow (Infinity and one arrow). Two bows
  of pale light hang at its holder's shoulders, and every full-enough shot is answered twice, 5 and 10 ticks later, each
  echo loosed from a shoulder at whatever the holder is looking at then, at the shot's speed and half an arrow's damage.
  The idea is Simply Bows' Echo bow; its licence forbids ports and bundling, so the code and art are our own
  (`EchoBow`, `tools/gen_echo_bow.py`). It is in the creative Combat tab; survival players only get it from the rift.
- **The Practice Dummy is a target dummy** (the idea of MehVahdJukaar's MmmMmmMmmMmm, CC0, which stops at 26.1.2 and
  needs Moonlight Lib; our own code): every blow floats its damage up off the dummy (white, gold from 10, red from 20)
  and tells the striker the blow, the bout's total and its damage a second (a bout ends after 3 seconds' rest). Use
  armour or anything held on it to dress it, and an empty hand while sneaking to undress it; the numbers are after what
  it wears. It flinches when hit, and gives its gear back when it is taken up. `PracticeDummy`.
- **Pack**: The Graveyard 4.0.0 (GPL-3.0, finallion; graveyards, crypts, a haunted house, overrun villages, its own
  undead and boss) with its library VillagersTradingPlus 1.1.0 (GPL-3.0), and Re:Animal 0.6.12 (all rights reserved,
  "feel free to include it" in modpacks; more wild animals with their own behaviour, taming and breeding) with its
  library ShatterLib / OctoLib 0.7.0-beta.5. All four are the authors' own 26.3 Fabric files, linked, never edited.
  GeckoLib was already in.
- Lore: three `(new)` paragraphs in Lore.md for Andy (the chapel where the lantern went out, the untended graves, the
  Echo Bow).
- Compiles (`compileJava`, `compileClientJava`) and `tools/check_data_refs.py` passes. **Not run in game**: the chapel
  has not been seen generated (its template is still in 1.20.1's data version, which the game upgrades on load, as it
  does the Stormborn Tomb's), the master's coming up, the Echo Bow's ghost bows and the dummy's numbers are untested,
  and the two mods were not booted beside the rest of the pack.
