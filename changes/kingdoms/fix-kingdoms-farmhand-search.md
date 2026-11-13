# fix the server freezing when a Family farmhand sets out
- Setting out on the Family farmhand road could freeze the server until its watchdog shut it down: the farmhouse search looked 100 chunks out (making every chunk it checked) and tried again past a taken house. It now looks 40 chunks out, and the whole hunt stops after 15 seconds; with no farmhouse found by then you get the village start, as when none is in reach.
- Built in CI; not played.
