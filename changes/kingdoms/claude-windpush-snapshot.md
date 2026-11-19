# The wind no longer stops the server (for real)

- Fixes: the server crashed while land was being generated: the wind's push walked the world's list of creatures and items while its own shelter check could load a chunk and add to that list. It now walks a copy (`WindPush`).
- Built by CI; checked on the Deck under pre-generation.
