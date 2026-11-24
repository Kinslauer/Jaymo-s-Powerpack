# Thick black smoke over the flames; whittling recipes in JEI
- **Smoke on the ground**: a big fire now banks thick, near-black smoke right over its flames, on the ground under the
  trees as well as in a burning canopy. It hardly rises and drifts with the wind, so a burning wood is hidden behind it
  even seen from outside. Andy's clip: under the trees the flames had no smoke, only a cloud high over the canopy ("the
  thick black smoke should generate at ground level, right overtop the flames, to show reduced visibility even from
  outside the fire"). The look now checks each column's ground under the leaves too. `FireSmoke#smother`, tuning
  (`GROUND_*`) at the top; the plumes and the pall above are unchanged.
- **Whittling recipes in JEI** on a server (sent with `RecipeSynchronization`; JEI only asks for Minecraft's own).
- Built on CI only; not played, so the smoke's look hasn't been seen.
