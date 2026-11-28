# Rig menu (Sneak+J) in groups
- **Rig menu, two rings** (Andy: "the shift J menu is way too cluttered"): the menu had grown to 43 tiles on one ring
  (52 with an Octo Core), packed so tight they overlapped. The inner ring now holds groups: **Flight** (Hover, Take Off
  Into Flight, Thruster Dash, Pull Up, Hyper Thrust, Banking, Meteor Dive, Smooth Others), **Blasts** (Palm Blast,
  Launcher, Barrage, Unibeam, Unibeam Slow Beat), **Guns** (Palm Charge, Gatling Arm, Railgun, Sonic Cannon, Twin Beam,
  Siege Mode), **Arm Forms** (Arm Blade, Mace Arm, Pile Bunker, Drill Breaker, Thruster Lance, Energy Lash, Weapon
  Mimic), **Back Rig** (Wing Slash, Spear Legs, Pincers, Grapple Claw, Horn Lightning), **Helm** (Faceplate, Night
  Vision, Scan, Mark Route, Wrist Table, Bank Shot), **Suit and Drones** (Suit park/call, Sentry, Failsafe, Drones,
  Decoy, Storing Field) and **Octo Arms** (its nine, only while an Octo Core is worn, as before).
- Point at a group and its entries fan out on the outer ring around it; carry on out to one and let go of J (or
  click). Nothing was cut: every entry is in exactly one group (the screen checks this when it loads). The tiles,
  cooldown shading, "Lv" locks, lit "on" strips and the panel under the rings work as before. A group's tile has a lit
  rim when it holds what J uses now, a lit strip when one of its switches is on, and goes dark when nothing in it can
  be used yet. A click on a bare group keeps the menu open.
- `RigMenuScreen` (groups, two-ring layout and picking); lang `gui.jaymospowers.rig_menu.group.*` and `group_hint`.
- Built; not played.
