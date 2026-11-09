# Animal meat and hides only from butchering

- Andy: "Deer also drop their venison and hide immediately, it should only be acquired from butchery." Meat or a hide that a dying animal drops by any path other than its normal death loot, before its body appears, now goes into its body, so it only comes from butchering (meat hook, butcher block, knife). Applies to every animal that leaves a body, not only deer.
- `mixin/EntityMixin` (`strayCarcassLoot`). Built; not played. The path that dropped the deer's loot wasn't found in the code (its normal death loot already goes into the body), so this may not be the cause: check in game that a killed deer leaves a body and drops nothing.
