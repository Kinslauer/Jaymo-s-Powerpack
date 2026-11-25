# the wicked harvest - rotten pumpkins, Spark of the Harvest, woken scarecrows
- **Rotten pumpkins**: a pumpkin that grows beside a stem now and then turns rotten (6%, 20% in late autumn). `RottenPumpkins`, `RottenPumpkinMixin`.
- **Crow range**: `Scarecrows.guardedFromCrows` keeps crows off within 16 blocks (the old 8-block guard is unchanged); Kingdoms' field crows ask it by id.
- **Woken scarecrows**: a Spark of the Harvest used on a scarecrow with a pumpkin wakes it. It reaps ripe crops within 6 blocks, replants them, never tramples farmland, and carries the harvest to a chest you choose (empty hand, then click the chest). It works only while mended with straw: a hay block gives 64, wheat 6. A well-kept one sometimes brings in an extra crop. Nobody is punished for not having one. `Farmhand`, `FarmhandEntity`, `ScarecrowBlock`.
- Art from Seasonal: The Wicked Harvest (MIT, Lazurium), licence in `licenses/seasonal/`.
- Compiled; not played.
