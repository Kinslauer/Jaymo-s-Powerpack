# Butchers work the town's carcasses on the meat hook through the day
- Andy: "Butcher at the hook on their own": in working hours a butcher with a free meat hook within 14 blocks of their
  workstation fetches a carcass lying within 20 blocks of the hook (a passive animal's, fresh, loose, and never a
  player's kill), carries it over, hangs it, and works it with the hook's own steps: bled into the grate, head off,
  skinned, gutted, three cuts. About ten steps fill a working day, with the blade worked between them, a cleaver or a
  skinning knife in hand for each step.
- What comes off goes to the town's stores (Jaymo's Kingdoms, `Butchers.setStock`), else a chest within 4 blocks of the
  hook, else at the butcher's feet. A carcass they can't get to (fenced in) is left alone.
- `Hanging.dressStep` runs the hook's steps for a villager (no tool wear, no messages; a player's blessings and perks
  aren't added). `CorpseEntity.killedByPlayer()`, `Butchers.butchering` and `Butchers.wantsCarcass` are new for other mods.
- Built in CI; not played.
