# TaCZ loot lookup no longer stalls the server
- **Pre-generating and exploring**: new land no longer freezes the server while TaCZ works out which loot table is being rolled. It used to search every loot table for each mob's equipment roll; it now looks the table up in a map built once per reload.
- Classes: new optional mixin `TaczLootLookupMixin` (own config `jaymospowers.tacz.mixins.json`, skipped when TaCZ is absent). Nothing of TaCZ is copied.
- Built by CI; not played.
