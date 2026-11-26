@AGENTS.md

## Who does the work (Claude Code only)
Claude is the coordinator and reviewer. Every task's work (building, fixing, scanning, research, art) runs on a
harness, in this order (Andy, 2026-11-26):
1. **Codex**: T3 `delegate_task`, provider `codex`.
2. **Gemini**: when Codex is out of usage or unavailable. T3 `delegate_task`, provider `antigravity`.
3. **Hermes**: when Gemini is out too. Message YOUTH to hand the task to a bot.

Claude plans, splits and briefs the tasks, reviews and verifies what comes back, and reports to Andy. Gemini is also
the second-opinion reviewer (`gemini-second-opinion` skill).
