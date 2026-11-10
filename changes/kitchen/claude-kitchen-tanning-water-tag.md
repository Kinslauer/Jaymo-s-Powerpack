# Kitchen: tanning recipes work again with tiered waterskins

The `tanning_water` item tag referenced `jaymoskitchen:waterskin`, but that item no longer exists — the waterskin-tiers feature replaced it with `waterskin_leather`, `waterskin_iron` and `waterskin_dragon`. The missing tag made the `salted_hide` and `wet_tanned_hide` recipes fail to load, which crashed the server on startup.

The tag now lists all three waterskin tiers plus `minecraft:water_bucket`, and `gen_hides.py` was updated to match.

Not tested in game.
