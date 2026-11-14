# changelings take faces; shapeshifters far rarer and always MCA villagers
- **Far fewer stolen lives** (Andy: "they should be far less common", three in one clip). A settlement now loses at most
  one of its own to a shapeshifter every ten days (`Doubles.TAKEN_GAP`, kept on the overworld as `stolen_taken`), whatever
  the source. Before, every chunk in the ring 50 to 140 blocks round a seat could get its own cellar, and every villager
  found underground in a village counted. A dungeon captive is a stolen life 15% of the time (was 50%), a cellar roll comes
  up 0.02 (was 0.05), and copies already waiting for the same settlement are cut to one.
- **Always an MCA villager** (Andy: "always copy an mca skin not the regular minecraft skin or anything else"). Only an MCA
  villager can be stolen (`Doubles.stealable`), a copy that would not be one is never made, and copies of plain villagers
  left in a world from before are removed when a player comes near. Without MCA there are no stolen lives.
- **Changeling impostors**: one copy in three (`CHANGELING_IN`, fixed by whose face it is) is a Sylvan changeling under the
  face, not the grey shapeshifter. Found out the same ways, it stays a villager: it gets a face, hair and name of its
  own, the Sylvan blood and the Changeling look, says its piece and runs. It never fights.
- **Changeling players**: Mirror Shift on an MCA villager within 16 blocks puts that villager's look on you (skin, face,
  hair, clothes, build), shown to everyone and kept through a relog (`BorrowedFaces`, `McaBridge.wearLook`). It comes off
  when you use Mirror Shift with nobody in sight, when you are hurt to half health, or when you die. On any other
  creature Mirror Shift still trades places.
- Not done: villagers do not treat a player wearing a face as that villager, and the borrowed name is not shown.
- Built (compileJava); not played. The fresh logs could not be read from the cloud session, so the cause of the
  vanilla-skinned copy was taken from the code (plain villagers under villages could be stolen), not seen in a log.
