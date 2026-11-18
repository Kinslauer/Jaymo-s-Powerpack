# Teleports no longer lost on a ride that already ended

- Fixes: a player whose game still thought they were riding something the server had already let go of ignored every teleport and rubber-banded back until they died (Jungxxv, 12:36 PM on the 18th). When the same far teleport is sent again while they still ride, they now get off the stale ride first (a single teleport of a ride with its rider, through an End gateway say, is left alone), and the ride is logged so the cause can be found (`StuckRideTeleportMixin`).
- Built by CI; not played.
