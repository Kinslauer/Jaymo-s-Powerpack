# The wind no longer stops the server

- Fixes: the server crashed while land was being generated (an empty slot in the world's entity list reached the wind's push). Such a slot is now skipped (`WindPush`).
- Built by CI; not played.
