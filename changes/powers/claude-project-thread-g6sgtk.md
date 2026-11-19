# creative keeps your prosthetics
- **Creative mode no longer pops prosthetics off**: creative's healing (`Wounds.creativeHeal`) used to grow every severed limb back, and a limb that grows back drops its wooden, iron or Rig limb as an item. A limb that wears a prosthetic now stays as it is; bare severed limbs and the head still grow back. Chrono's rewind out of death (`Wounds.undoAll`) goes through the same heal, so it keeps them too.
- `Dismember.regrow(target, all, keep)`: regrow that leaves the parts in `keep` missing.
- Compile-checked; not played.
