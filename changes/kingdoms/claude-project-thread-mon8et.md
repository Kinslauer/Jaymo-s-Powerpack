# Castle towns: a real Epic town round the castle, crowned in the castle
- **Seat compounds** (`SeatCompounds`) now lay the land's Epic Structures town round their castle instead of our five
  drawn homes (Andy: "a kingdom built around a castle like fenford's and the surrounding kingdom's buildings made like
  the ati thalstead"). The town is generated from its own mod by id with the castle where its middle would be; every
  piece that would stand on the castle, the square, the garden or the barracks, or reach past 128 blocks, is left out.
  With fewer than 12 pieces of town left (`TOWN_MIN_PIECES`), no compound is placed there.
- **Grim Kingdoms' castles** (Fenford's kind: Glacierfall Keep, Glimmervault Fortress, Silentgale and the rest, 18 by
  land, `GRIM_CASTLES`) can be a compound's castle now, beside ATi's and Repurposed Structures'. Alone they are still only
  fiefdoms' holds, never a kingdom's seat.
- **Crowned in the castle:** far from any realm the compound is a kingdom (an Epic town with a castle at its heart is a
  great town); `Seats.buildings` puts the castle's rooms first, so the throne, banner and panic room go in the castle's
  hall and not in a big house of the town. Near a kingdom it is still its fiefdom, else an allied village. Compounds
  already in a world (with our drawn homes) keep the old rule and are never crowned.
- New chunks only. Built and compile-checked; not played (no world generated with it).
