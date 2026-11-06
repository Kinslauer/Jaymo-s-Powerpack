---
name: patrol-needs-cause
description: Galactic patrolmen never spawn at random; only pursuit of a raider band or recruiting a strong fighter (PR #808, 2026-10-31)
metadata:
  type: feedback
  modified: 2026-10-31T22:10:34.750Z
---
2026-10-31 Andy: "i found a glactic patrolman doing nothing, they shouldnt just appear. they always need a reason."
PR #808 MERGED 2026-10-31 22:4xZ as pack 0.19.5: the random town visit every ~3 days is gone. Patrolmen come only to chase a raider band (60%, 30 s behind it) or to recruit a fighter with power 1500+ and Borderlands open (asks again after 7 days). Visit.cause NONE = leave at once.
**Why:** Andy wants every NPC arrival to be tied to an event.
**How to apply:** any new patrolman (or similar visitor) spawn must carry a cause, act on it, then leave. Related: [[zfighter]].
