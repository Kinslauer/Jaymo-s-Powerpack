# Sitting cats no longer stand on end under Fresh Animations
- **Pets' poses on Fresh Animations' models**: a tamed cat left sitting was drawn standing on its end, legs laid flat
  and tail sticking straight up (Andy's clip at a camp). Our pet poses (the cat's loaf, knead, groom and stretch; the
  wolf's greeting, play bow, lying down and dozing) are worked out on vanilla's cat and wolf parts, and Fresh Animations
  builds those animals its own way (head and tail inside the body, the body's boxes turned flat inside it), so resetting
  the body for the loaf turned the whole cat upright. Where a resource pack has replaced the animal's model through
  Entity Model Features, PetPoses now steps aside and the pack's own sitting and sleeping animations show
  (`EmfCompat.customModel`, `PetPoses.extract`). Without Fresh Animations the poses are unchanged.
- Built; not played.
