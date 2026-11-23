# Realms never wear the same armour: each keeps its own livery
- **Why five realms looked alike** (Andy, on the life story's realm page): a realm's guards are kitted with what the realm
  can pay for (`GuardKit`), and that replaced the trims and land dress `Outfits` gave them, so every new, poor realm's
  militia and soldiers stood in the same plain mail or leather. The realm page also never showed the harness, so the
  tabard in the realm's colours was missing there.
- **A livery for every realm** (`Liveries`): the trim its smiths work into every plain piece they issue (mail, leather,
  bronze, iron; in the metal nearest the realm's colour), and which of the levy's cloth its guards get for nothing: a
  hood in the realm's colour (a fur hood in the cold), leather tassets, padded cuisses. No two realms of one colour share
  a livery: the realms are taken oldest first, and one whose pick an older realm of its colour already has takes the
  next free one (18 trims times 8 cloth mixes, 144 to a colour). A fiefdom keeps its overlord's colour and arms, with a
  livery of its own.
- A kingdom's soldiers wear its own one of the kettle hat and the lobster-tailed pot (before, every kingdom's wore the
  lobster tail). Soldiers already wearing a lobster tail keep it unless the kettle hat guards better, so an old
  kingdom may show both for a while.
- Guards already in the world are re-marked and given their levy cloth at their next daily look-over.
- **The realm page** now shows the realm's guard with the harness over his armour (tabard, levy cloth), as he stands
  in the world, and dressing that figure no longer spends the realm's stores, gold or treasury: it's kitted with what
  the realm could pay for, counted piece by piece, but nothing is taken.
- **The check**: whenever the realms change, `Liveries` makes sure no two realms of one colour have one livery; in the
  dev game a clash stops the game, in a real one it's logged as an error. It checks the liveries, not every worn
  piece: a guard in a full set that takes no trim (a Templar's) shows only his levy cloth, and where militia and archers
  buy a hood anyway (the plains and the cold) the livery's hood doesn't show on them.
- Built by CI; not played. Not checked in game: how the trims and levy cloth read on the realm page's small figure, and
  that the harness draws on that figure.
