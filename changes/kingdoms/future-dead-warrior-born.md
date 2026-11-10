# The old dead: villager zombies and husks in the Overworld; the draugr are Warrior-born

Andy: "Zombies and husks should have villager like skins on the overworld too, as they are meant to be dead villagers
from time past ... much older and much more deteriorated" than MCA's zombies, "like the draugr, who are all undead
warriorborn ancestors".
- **The Overworld's dead**: grown zombies wear one of 32 old-villager skins and husks one of 16: villager robes of
  their land (plains, taiga, snow, swamp, jungle, savanna; the husks the desert's and the savanna's) faded almost to
  dun and torn, now and then a trade's apron, hat or collar, the heavy brow, empty sockets, the long nose rotted to a
  hole, bare teeth, the skull through the scalp, finger bones and shins down to bone, earth and moss on them (the
  husks leathery, in grave-wraps and sand). No green in them: MCA's zombie villagers, the recently dead, keep their own
  looks, so the two read apart. Picked by the mob's id. Babies, drowned, other mods' zombies and zombies outside the
  Overworld are left as they were.
- **Draugr**: each wears a Warrior-born's tail from under its mail, rimed with frost toward the tip, swaying as it stands
  and walks, and mangled its own way (whole, bent, snapped and hanging, cut through, a stump, hanging limp, broken
  twice, the tip gone). They're bound where they first stood: a draugr keeps within 24 blocks of that spot
  (`Draugr.HOME_RADIUS`), gives up a foe who leaves that ground and walks back to it, so the tomb's dead never leave the
  tomb. Draugr already in a world are bound where they stand on their first tick after this.
- `client/OldDeadSkins`, `mixin/OldDeadSkinMixin`, `mixin/OldDeadHuskSkinMixin`; `inhabitants/Draugr`,
  `client/inhabitants/InhabitantsClient` (`DraugrRenderer`). Art: `kingdoms/tools/gen_old_dead.py` writes
  `textures/entity/old_dead/zombie_<n>.png` and `husk_<n>.png`; `kingdoms/tools/gen_tomb_undead.py draugr` adds the
  tail to `kingdoms/art/draugr.bbmodel` (then `tools/bbmodel_geckolib.py` for the game's files).
- Built; not played. The skins were checked as images and the draugr's tail in renders of its model, not in game:
  check the draugr tail's place and states, and that draugr still fight well in their halls.
