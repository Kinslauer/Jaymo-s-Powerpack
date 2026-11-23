# Wounds shown like potions; bandages wrap several limbs at once; splints by two, from plant string too
- **Wound icons**: bleeding, a broken bone and a lost limb now show like potion effects (top-right of the screen and in the
  inventory): Bleeding, Broken Bone, Lost Limb, the level counting the parts (two broken limbs: Broken Bone II). They only
  show the state and go when it heals; milk can't hide them. `core/wounds/WoundSigns`, icons from
  `tools/vanilla_style/wound_signs.py`.
- **Bandages**: one linen bandage wraps up to 4 hurt parts at once (the one you pick, then the worst others), a grass
  bandage up to 2; each is used up in the one go (a linen bandage used to be three separate dressings). Rags still do one.
  `Treatment.BANDAGE_LIMBS`, `GRASS_BANDAGE_LIMBS`.
- **Splints**: two sticks and string *or plant string* make 2 splints (and with leather, 2).
- **New bandage recipe**: any colour of wool and string or plant string make 4 linen bandages.
- Built on CI; not played.
