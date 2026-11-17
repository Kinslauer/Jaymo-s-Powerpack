# faster singleplayer world loading (template cache, Dynamic Waters samplers)
- **Old structure templates are upgraded once, ever**: 9,719 of the pack's 13,599 structure templates are saved in older
  Minecraft versions, and the game ran each one through the data fixer every session the first time it was placed
  (about 30% of all CPU while a new world picked its spawn and while the first player joined). The upgraded copy is now
  kept in `.cache/jaymospowers/templates-v<world version>/` and read straight back next time (`TemplateUpgrades`,
  `TemplateUpgradeMixin` on `TemplateSource.readStructure`). The folder is cleared when the game version changes; if it
  can't be written, templates are upgraded as before.
- **Dynamic Waters (port)**: its height field asked `RandomState.getSampler` for the surface and density functions on
  every column, and each ask hashed the whole density-function tree (20% of all CPU at first join). The five samplers it
  uses are now looked up once per world (`WiredRouter.Samplers`, kept on `RandomState`); `CoarseHeightField`,
  `VanillaRiverFlow` and `BasinTile` use them.
- Cloud timing, new world, seed 12345, 4 cores: spawn search 175 s -> 163 s with the sampler fix alone, 69 s once the
  template cache is warm (181 s the very first time, while it fills).
- Built and timed on a dev server; not played.
