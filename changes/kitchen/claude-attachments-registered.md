# Placed slabs remembered across restarts

- Fixes: the slabs a player placed in a chunk were forgotten when the chunk loaded before the class did (dropped as unknown data). The attachment is now registered at start-up.
- Built by CI; not played.
