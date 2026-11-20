# A new life no longer keeps the old body's lost-leg slowness
- **The bug**: losing both legs puts a saved -85% walking speed (and no jump) on you. Ending a life wiped the lost legs with every other mark, but that saved slowness stayed on, so a fresh character walked at about a sixth of normal speed and couldn't jump, with both legs.
- **Fix**: a new life (`Mortality.endLife`) now takes the slowness and the old stump tags off (`Dismember.bodyRenewed`). Anyone already stuck with it is put right within a second of this version loading (`Prosthetics`' check also reworks a lost-leg slowness that has no lost leg under it, `Dismember.legsSlowed`). Someone who really has lost legs keeps their slowness.
- Built; not played.
