# Aether towns and villages only on great landmasses
- **Sky towns, sky villages (and their ruins) and the sky castle** are now built only on one of the Aether's great
  landmasses, never over open sky and cloud. Before, each town lot and each village was checked at nine spots, so a
  town could spread across islets and holes with aerclouds showing underneath and still pass the "enough lots" check.
- Now, every lot of a town (and a village's or castle's whole square) must be island at the town's own floor height in
  at least 90% of its columns, sampled every 4 blocks (`SkyTowns.LOT_COVER`, `AetherStructure.SETTLEMENT_COVER`). The island under it
  must also be one big landmass joined at that height: at least 160 x 160 blocks of land for a town and 112 x 112 for a
  village or castle (`Ground.island`, `SkyTowns.ISLAND_LEAST`, `AetherStructure.SETTLEMENT_ISLAND`).
- Only rock and soil count as land (`Ground.land`): holystone and the other Aether stones, Aether dirt and grass. An
  aercloud never does.
- New worlds and newly generated Aether only; towns already built stay where they are. Built; not played.
