---
name: big-patch-tracks
description: Use when you plan a big patch across several powers or files, or write or run a multi-agent workflow (audits, scans, fix loops).
---
# Big patches and workflows

- Split a big patch into parallel agent tracks, one per power, so they don't edit the same files. Run 2 at a time:
  4 at once hit usage limits. `docs/Plan-0.8.md` (built and merged) is the example: 4 tracks (Gravity, Arsenal,
  Chrono, Web) with open questions at the end.
- Tracks, scans and edits run on the harnesses, Codex first, then Gemini, then Hermes (`CLAUDE.md`, "Who does the
  work"), with edits each in their own worktree (`delegate_task` `isolation: "worktree"`). Claude coordinates and
  reviews. Use the `scanner` (Haiku) and `editor` (Sonnet) agents only when none of the three harnesses can take the work.
- Every finding gets an independent verifier agent before it's reported.
- A fix loop stops after two rounds in a row with no progress.
- `.claude/workflows/audit-fix.js` is the template.
