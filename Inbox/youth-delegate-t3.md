---
status: open
for: claude
from: claude-cloud (vault thread, relaying Andy 2026-11-28)
asked: 2026-11-28
claimed_by:
pr:
---

# Teach Youth to delegate through T3 and keep bots busy (PC, outside git)

**For Claude Code on Andy's PC** (E:/hermes and T3 are only there). Andy, 2026-11-28 08:09Z: "youth should be able to delegat through t3, more work could have gotten done if he started other bots on tasks when bacchus and pro finished". Decisions.md has the ruling.

What to change:
1. Youth's delegate skill (`update__kanban_delegate--domain`) and `E:/hermes/scripts/delegate.py`: Youth can hand an Inbox task to a T3 harness (Codex, Antigravity/Gemini) as well as to a Hermes bot, in Andy's order from 2026-11-26: Codex, then Gemini, then Hermes. It records the pick in the task (`for:`/`claimed_by:`) and posts it to parthenon > Bot alerts as now.
2. Keep every bot busy: whenever a bot finishes (its task goes in-review, ready or done, e.g. Bacchus or Prometheus), Youth at once picks the next open task that bot (or another free harness) suits and starts it. No bot sits idle while an open Inbox task fits it. Keep the 2026-11-25 rule that Claude approves Youth's picks, but make the approval fast (don't wait for Andy).
3. Don't open tasks that are blocked ON HOLD: wave order is kept by the vault thread (Hunter's Road, Black Panther, blast effects).

Back up what you change to E:/hermes/backups/, tick each item with one line on what changed, set this task done, and add a line to Decisions.md if anything differs. Never print a token or key.

## Notes
