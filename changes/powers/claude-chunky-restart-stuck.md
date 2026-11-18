# Chunky picks up again after a restart

- Fixes: after every restart the world pre-generation came back stuck (0 chunks a second) until it was paused and continued by hand. `ChunkyCompat` now uses Chunky's API instead of its commands: it is still paused while a player is on and continued once nobody is, but a continue now waits until the paused task has finished stopping (it used to be lost when the pause was slow to drain). With nobody on, a watchdog pauses and continues a task that has run a minute without a progress event, up to three times in a row.
- Built by CI; checked on the next restart.
