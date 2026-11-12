---
status: done
task: omniroute-ai-gateway-experiment
branch: 
pr: 
mods: 
versions: 
started: 2026-11-12
owns:
  -
---
# OmniRoute AI gateway experiment

**Task:** Andy asked whether https://github.com/cheaper-inference/OmniRoute is useful for the Powerpack setup. Log it as an experiment thread for later evaluation; don't build or integrate anything yet.

## Plan
- Record the OmniRoute assessment in the vault (this note + the Inbox task's Notes).
- No branch, no PR, no build: logging only.

## Notes
- 2026-11-12 6:28 PM EST hermes-b: OmniRoute is an AI gateway proxy: one OpenAI-compatible endpoint routing across 375+ providers (150+ free), quota-aware auto-fallback, RTK+Caveman token compression (15-95% savings); works with Claude Code, Codex, Cursor, OpenCode; MIT licensed, 86.9k GitHub stars.
- Initial assessment: probably not directly useful for the current Powerpack setup — it's not a build tool, dispatcher or PR manager (dispatch_bots.py, the vault Inbox, worktrees and cloud jobs already handle that); it doesn't integrate with Hermes Agent's bot fleet or Kanban board; the current direct-API setup (ANTHROPIC_API_KEY/GITHUB_TOKEN via run.py) already works.
- Where it might help: auto-fallback across providers when Anthropic rate limits hit cloud jobs; token compression to cut API costs on long cloud runs; free-tier providers as backup when paid quotas are exhausted.
- Downside: another dependency to run and configure; current setup is more streamlined. Not urgent. If Andy wants to experiment, run OmniRoute locally and point run.py at its OpenAI-compatible endpoint instead of calling Anthropic directly.

## Done
Logged the experiment thread (this note + Inbox task notes). Not built or integrated, as asked. Not tested — no code change.
