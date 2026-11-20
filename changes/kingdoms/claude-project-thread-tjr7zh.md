# forest-fire pack - our wildfires, firefighters and heat run through it
- With Peterwolf's Forest Fire in the pack (`ForestFireCompat`; every part does nothing without it):
- **Wildfires** (Calamities) become its fire incidents: its fire runs them (embers, crown fires, faster uphill, trees
  burning through), our smoke follows where it burns, and a taken fire may burn three times as long. Its wind follows
  ours, so its fire, our smoke and the smog lean the same way.
- **Firefighters**: when our people put a fire out (beaten out, a golem's fist, a thrown water bottle), its fire there
  goes out too, so it doesn't light again from the hot ground (`Firefighting`, `SplashDousesForestFireMixin`).
- **Kept fires**: a fire on netherrack or anything else that burns forever (fire pits, village hearths, the Nether's
  fires) never becomes one of its incidents, so it isn't put out or spread (`ForestFireKeptMixin`).
- **Heat**: its burning blocks within 12 blocks warm you through Kitchen's body heat (more for a bigger, hotter front,
  up to 50 degrees); its own heat damage is off (Powers' preset). Its smoke still hurts.
- **HUD**: its firefighter panel (always at the top left, in fixed English, whenever any fire burned) is replaced by
  one plate on the top-left rail under the party, shown only while you carry its kit: the fire's name, status, blocks
  burning and containment, the wind, and your nozzle, pressure and hose (`ForestFireHud`, lang part `forest_fire`).
- Lore: "The fire crews" in Lore.md (new): Frostborn pumps and air tanks, Verdant crews at the command posts.
- Built and compiled; not played. The port itself was tested on a plain server (it removes fires on netherrack, which
  is why the kept-fire mixin exists); the hand-off, dousing, kept fires, body heat and the HUD were not run in game.
