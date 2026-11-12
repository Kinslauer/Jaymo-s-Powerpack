# Boxing: stances, poses and breath
- **Each style has its own moving stance** (third person, with fist gear and a style): the Arrow Slit sways behind a
  high, tight guard; the Long Guard paws the air with its lead hand; the Bastion stands southpaw with its lead arm low
  and shrugs its shoulder now and then. You can read a boxer's style from how they stand.
- **Blocks, punches and evades have poses**: three different blocks; the arm goes out by the punch (jab, cross, hook,
  uppercut); a lean, a duck, a pull or a shoulder turn for the evades.
- **Tiring and gassed show**: a faint puff of breath now and then with four feather icons or fewer, clear puffs and
  heaving shoulders when out. No sound.
- Classes: client `boxing/BoxingPoses` (new), `armory/FangGauntletsClient` (hands a boxer over to it), `boxing/Boxing`
  (`LOOK`, the breath), `boxing/BoxingEvades` (`MOVED`).
- **Not built locally and not seen in game.** Every angle was set without a render; expect to tune the knobs at the top
  of `BoxingPoses`. First person shows none of it.
