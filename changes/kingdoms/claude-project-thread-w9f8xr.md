# fallen trees settle into real blocks, and wake when moved
- A fallen tree lying still for 30 seconds (nobody pushing it, holding it or chopping at it) becomes real blocks where it lies: its logs turned the way they lie, its leaves dropping off (giving what they would only when a player brought it down and is near, as before). Drawn with the land, a felled forest no longer costs a quarter of every frame (Andy's spark profile, 2026-11-28, 96 fallen trees about).
- It wakes back into a lying tree, every log where it was, when someone strong enough to shove it (brawn 2+) walks into one of its logs, when a giant's hand closes on it, when a tornado's winds reach it, or when a player breaks one of its logs or the block under one (broken through, it comes apart in two; with what held it gone, it falls). It settles again 30 seconds after it's left alone.
- A log with no room where it lay (sunk in the ground) is kept with it out of sight and comes back when it wakes.
- Settled logs are never taken for a standing tree (felling, giants, tornado uprooting).
- Not kept: on settled logs, chopping is ordinary log breaking (no three-stroke chop), and someone too weak to shove it no longer leans both hands on it (it only wakes for the strong).
- `SettledTrees` (kept per chunk, indexed while loaded), `FallingTree.settle`/`wake`/`SETTLE_AFTER`, `Trees.natural`, `Tornadoes`.
- Built; not played.
