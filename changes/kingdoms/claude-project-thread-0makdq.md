# Stormborn Tomb pool loads again
- **Worlds load again:** the Stormborn Tomb's start pool (`overworld_stormborn_tomb`) wrote its ignored structure block the old way (`"Name"`); 26.3's block state codec wants `"id"`, so the datapack failed to load and the server stopped with "Failed to load datapacks, can't proceed with server load". It now says `"id"`.
- Checked by starting a Kingdoms dev server: it failed on this file before and starts with the fix.
