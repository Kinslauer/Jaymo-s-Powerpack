# Small boats at sea are a realm's fishing smacks with a helm; the talk screen's empty purple box is gone

- Andy met one of Moog's Ocean Structures' small boats at sea with no helm and two jobless villagers aboard: "they should
  be fishers belonging to some land and out here to get it and go back. thats it." Now the nearest standing realm with a
  harbour (within 1100 blocks) owns each small boat: a helm goes on her deck and she is cast off as that realm's fishing
  smack, crewed by its fishermen. She is found fishing where she floats, fishes her spell out, and sails home to land the
  catch in the realm's stores, as the realm's own smacks do (`SeaSites.smack`, `Fishers.harbourRealm`, `Fishers.atSea`).
  Small boats met before this change get the same once nobody is near: their two old villagers go and she puts to sea
  (`IllagerSites.refit`). While no realm's harbour is in reach, a small boat is left as she is and looked at again later.
  If she won't cast off, she stays empty with her helm for a player to try.
- Why she had no helm: the helm was only looked for up to 8 blocks above the sea, and the small boat's deck stands 9 above
  it, so none was placed (the deck search already went to 12; the helm's now does too, for every crewed Moog boat).
- Why nitwits: one was a fisherman with no workstation, who loses the trade, and the other a stranger given no trade at all.
- The talk screen: the empty purple box at the top right, behind the talk wheel, is gone. MCA's Talk button takes the
  screen's buttons away without laying out a new page, so the button column's panel kept framing buttons that weren't
  there (`McaButtonColumnMixin` now counts only the buttons still on the screen).
- Not tested in game: a small boat casting off and sailing home, the refit of one already met, and the talk screen with
  the panel gone.
