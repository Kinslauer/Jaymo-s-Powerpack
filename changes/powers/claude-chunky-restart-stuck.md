# Chunky picks up again after a restart

- Fixes: after every restart the world pre-generation came back stuck (0 chunks a second) until it was paused and continued by hand. The start-up continue now waits a minute, and Chunky is paused and continued once more three minutes in, unless someone is on (`ChunkyCompat`).
- Built by CI; checked on the next restart.
