# Boxing: fist gear worn on the hands
- **Hand Wraps, the cestus and the bracers are worn, not held**, like the Fang Gauntlets: holding them hides the item
  and draws the gear on the hands, for everyone and in your own first person. Wraps wind round the hand, a cestus adds
  a studded band over the knuckles, bracers run up the forearm. With the off hand empty, both hands wear it.
- Holding fist gear also raises the arms into the Fang Gauntlets' fists-up guard when nothing is swinging (the
  styles' own idles come later).
- Classes: client `armory/FangGauntletsClient` (boxing's shapes and textures beside the gauntlets', through the same
  mixins), `boxing/BoxingGear.wornIndex`. Textures `textures/entity/boxing/` from `tools/boxing_assets.py`
  (placeholder).
- **Not built locally and not seen in game.** The shapes' sizes and the textures' layout were set without a render.
