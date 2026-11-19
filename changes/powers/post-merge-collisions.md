# Rig: flight picks, arm-form poses and gun fire poses fixed
- **Flight actives** (pull-up, hyper thrust, banking, meteor dive, smooth others): their ids in `RigNet` (19-23) clashed with the drone, mark, table and decoy picks. The five old ids are gone; J, the HUD highlight and the level gate use `RigPicks.PULLUP..SMOOTH`.
- **Arm forms**: `RigForms` started the move with the action id instead of the move kind, so no arm-form pose played. It now starts `ARM_BLADE + (form - AF_BLADE)`.
- **Palm charge and railgun**: firing restarted the move with a tick constant (which started the launcher and palm poses). It now restarts the palm-charge and railgun moves.
- Not built here (CI compiles); not played.
