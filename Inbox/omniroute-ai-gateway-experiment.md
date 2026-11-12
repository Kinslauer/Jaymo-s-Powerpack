---
status: claimed
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-12)
asked: 2026-11-12
thread: 1570579626151903354
claimed_by: hermes-b
pr:
suggested_owns: []
---

# OmniRoute AI gateway experiment

Andy asked about https://github.com/cheaper-inference/OmniRoute — whether it's useful for the Powerpack setup.

## What's wanted

Log this as an experiment thread for later evaluation. Don't build or integrate anything yet.

## What OmniRoute is

AI gateway proxy: one OpenAI-compatible endpoint routing across 375+ providers (150+ free), with quota-aware auto-fallback and RTK+Caveman token compression (15-95% savings). Works with Claude Code, Codex, Cursor, OpenCode, etc. MIT licensed, 86.9k stars on GitHub.

## Initial assessment

Probably not directly useful for the current Powerpack setup:
- It's not a build tool, dispatcher, or PR manager — dispatch_bots.py, vault Inbox, worktrees, and cloud jobs already handle that
- Doesn't integrate with Hermes Agent's bot fleet or Kanban board
- Current direct-API setup (ANTHROPIC_API_KEY/GITHUB_TOKEN via run.py) already works

Where it *might* help:
- Auto-fallback across providers when Anthropic rate limits hit cloud jobs
- Token compression could cut API costs on long cloud runs
- Free-tier providers as backup when paid quotas are exhausted

Downside: another dependency to run and configure; current setup is more streamlined.

## Notes

Not urgent. If Andy wants to experiment, run OmniRoute locally and point run.py at its OpenAI-compatible endpoint instead of calling Anthropic directly.
- 2026-11-12 18:30 hermes-b: logged the experiment thread; see Agents/omniroute-ai-gateway-experiment.md. OmniRoute not directly useful for the current setup (no build tool/dispatcher/PR-manager fit, no Hermes fleet integration, direct-API setup already works); keep for later evaluation — auto-fallback, token compression, free-tier backup. Not urgent. Not built or integrated, as asked.
