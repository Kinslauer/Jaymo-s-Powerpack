# Mutant zombies vary like the old dead: by where they rose, their land and their rot
- **Mutant zombie looks**: each mutant zombie now wears the old dead look of where it rose, as the zombies do:
  - plain, dried (desert, badlands, savanna), bloated (swamp, river, beach), pale (caves, the deep dark) or frostbitten
    (snow);
  - in its land's clothes;
  - at one of three stages of rot.
  That's 63 skins (`kingdoms/tools/gen_mutant_dead.py`). `RisenLooks` marks mutants where they first stand, and
  `client/MutantDead` picks the skin through a mixin on Mutant Monsters' renderer that is skipped when the mod isn't
  there. A mutant with no mark keeps the plain skin from the built-in pack.
- Built; not played.
