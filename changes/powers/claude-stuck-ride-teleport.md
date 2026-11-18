# Teleports no longer lost on a ride that already ended

- Fixes: a player whose game still thought they were riding something the server had already let go of ignored every teleport and rubber-banded back until they died (Jungxxv, 12:36 PM on the 18th). A teleport further than 8 blocks now gets them off the stale ride first, and the ride is logged so the cause can be found (`StuckRideTeleportMixin`).
- Built by CI; not played.
