# the wicked harvest - field crows, witch's cauldron, Harvest Lich, ruined barn
- **Field crows**: by day, flocks land on open farmland (10 or more open crops) and peck crops back a stage. More open field draws more crows; a field within a Kitchen scarecrow's 16 blocks draws none. They leave at dusk, in rain, or after a few pecks. `FieldCrows`, `Crow`.
- **Witch's Cauldron**: over soul fire at night, 2 rotten pumpkins (Kitchen) and a full soul jar of an undead kind brew 2 Wicked Resin in three stages. No new ghost appears. `WitchCauldronBlock`.
- **Harvest Altar**: takes 2 Wicked Resin and raises the Harvest Lich only in late autumn (Kitchen Seasons) at night; otherwise cold smoke and the resin stays. `HarvestAltarBlock`.
- **Harvest Lich**: boss on a skeleton-horse steed; charges, lobs rotten pumpkins, calls up to 3 flying wicked pumpkins that shoot orbs. Drops Spark of the Harvest (wakes scarecrows), resin and gold. `HarvestLich`, `LichSteed`, `WickedPumpkin`, `WickedOrb`, `ThrownRottenPumpkin`, client `LichModel`, `LichRenderer`, `PumpkinRenderers`.
- **Ruined barn**: `wicked_barn` (standing and ruined), drawn in `tools/small_designs/wicked_barn.py`, rare on plains, sunflower plains and meadow, with the altar, a chest and a scarecrow.
- Art and ideas from Seasonal: The Wicked Harvest (MIT, Lazurium), licence in `licenses/seasonal/`. Not ported: custom steed model, Lich armour, other Seasonal mobs.
- Compiled; not played.
