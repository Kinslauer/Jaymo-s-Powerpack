# Stormborn Tomb: real fix for stone fill and trial-chamber look

The Stormborn Tomb structure was generating with solid stone gaps and looking like vanilla trial chambers because the template pool IDs used `stormborn_tomb/part` (with a slash) but the actual template files are named `stormborn_tomb_part` (with underscore). In 26.3, the pool `name` field is ignored and the file path ID is used, so the pieces referenced non-existent pools and turned into air or wrong blocks.

The fix changes the pool element ID construction from `stormborn_tomb/` to `stormborn_tomb_` to match the template file names, so the correct templates are loaded and placed.

Not tested in game.