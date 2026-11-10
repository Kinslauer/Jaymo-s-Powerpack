---
status: writing
for: hermes
from: tiv (Discord, Hotspot / parthenon / bug fixes thread)
asked: 2026-11-09
thread: 1569184077720850462
claimed_by: hermes-a
pr: 1245
suggested_owns:
writer: hermes-writer
---

# Startup crash on the newest build

The game crashes while it is starting up, so the pack cannot be played at all.

## What's wanted

Tiv, opening the bug-fixes thread: "I'll put bugs in here, Add an Inbox task to fix this startup crash."

## Notes

- No log, screenshot or file came with the message. The first thing the builder needs is the crash report itself: the client's `logs/latest.log`, or the newest file in `crash-reports/`, or the launcher's "Exit code" line.
- Master is on Powers 0.21.75 / Kingdoms 0.7.663 / Kitchen 0.5.95. A fresh startup crash would most likely come from the newest Powers merges: Xeno-Band Ultimates patch 2 (#1160), the Blindness echolocation power (#1159), or the Rider overhaul (#1161).
- Startup crashes this cycle have already been fixed once for this class of bug (fishing-hook bait state) and once for a first-draw render bug (blood puddles). Check client renderer/registration code first, then any new `AttachmentType` that is missing from `JaymosPowers.registerAttachments()`.
- Open question for Tiv: which mod's window is it — does the crash happen before the Minecraft window opens, at the mod-loading list, or after the title screen appears? That answer alone narrows it to one project.
- 2026-11-08 22:52 Logs from Tiv (2026-11-09): the server dies ticking a rat - Rat$RaidGoal.tick reads its food target after the goal finished eating and cleared it. A goal that sets requiresUpdateEveryTick is still ticked on an off tick, before the canContinueToUse sweep stops it, so the cleared field gets read. Fix: stop the goal when the target is gone. The other crash report (13:09, LivingEntity.refreshDirtyAttributes) is a different, older one with no mod frame - not part of this fix.
- 2026-11-09 05:00 hermes's claim went stale (nothing changed for 367 min); handed back as open so a bot can take it.
- 2026-11-09 10:00 review (youth, notes sent to Tiv in the Notes thread): the fix is written in `E:/wt/startup-crash` but was never compiled, committed or pushed — no PR exists, the branch tip is still the 0.5.95 release commit, and the worktree is far behind master. Review of the guard itself found nothing to change: `target` is the only field RaidGoal clears mid-tick, and `stoleFrom` (read by EscapeGoal the same way) is never cleared, so no second crash of this shape. Next run just needs: pull master, build, commit `Rat.java` + `changes/kitchen/startup-crash.md`, open the PR.
- 2026-11-10 06:11 Text pass: changes/kitchen/startup-crash.md - retitled to 'a rat that finishes eating no longer crashes the server' and rewrote the first (player-facing) line shorter and plainer; cause and 'Built; not played' lines untouched. No lang, dialogue or other player text in this PR. Kitchen builds, pr_checks pass, pushed 93b02ba97.
