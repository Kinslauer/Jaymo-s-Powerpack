# Fire no longer burns grass blocks away
- **Grass blocks and podzol aren't fuel**: Inferno always lists them as very flammable (60, 100) and, with its scorched
  earth off (Andy's pick), meant to zero that at the end of `FireBlock.getIgniteOdds`/`getBurnOdds`. Fabric API answers
  those methods early for any block it has flammability for, so the zeroing never ran: fire caught on grass blocks and
  burnt them away, leaving pits. When a server starts with Inferno loaded and its scorched earth off, the two are set to
  0, 0 in Fabric's flammable-block registry. Plants, leaves and logs burn as before; turn scorched earth on and grass
  scorches the Inferno way again. `core/InfernoPreset`.
