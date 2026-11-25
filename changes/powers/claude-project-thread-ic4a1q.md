# worn totems and villagers who carry one
- **Totems as baubles**: a Totem of Undying (and Kingdoms' crude totem, or anything with vanilla's death protection) fits
  the charm slot, and worn there it saves you exactly as a held one does (1 health, Regeneration, Absorption, Fire
  Resistance, the totem burst and the "used" stat), after the hands have had their turn. Sneak-use with one in hand puts it on.
- **Some folk carry one**: 1 in 60 grown villagers (MCA's too, so guards and rulers), 1 in 40 illagers and 1 in 6 evokers
  carry an old totem, rolled once when they first load. It saves them once the same way (burst and sound for everyone
  near). Killed past it (the void, /kill, a beheading), they drop it.
- The downed state, Mortality and the Darktide shade leave a worn or carried totem to do its work, as they did for a
  held one (`WornTotems.ready`).
- Classes: `baubles/WornTotems`, `mixin/WornTotemMixin` (on `LivingEntity#checkTotemDeathProtection`), charm tag.
- Built; not played. Not done: Kingdoms' blight cure still spends only a held totem.
