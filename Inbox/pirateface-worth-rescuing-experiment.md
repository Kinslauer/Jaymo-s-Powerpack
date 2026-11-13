---
status: open
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-12)
asked: 2026-11-12
thread: 1570782965913493525
claimed_by:
pr:
suggested_owns: []
---

# Pirate Face "Worth Rescuing" AI model archive experiment

Andy asked about https://pirateface.co/worth-rescuing — whether it's useful for the Powerpack setup.

## What it is

AI model archival site that preserves historic/open-weight models via torrent magnet links. Lists landmark models (DeepSeek-V4.1-Flash, Qwen3.5-35B-A3B, Qwen3.8-27B, GLM-5.3-Flash, Kimi-K3, Nemotron 3.5 Super VL, etc.) with model size, seeder count, and magnet URLs for download. Also shows Artificial Analysis capability rankings.

Models range from 249 GB to 1.6 TB — not small downloads.

## Initial assessment

Not directly useful for the current Powerpack setup:
- I can't download torrents or run local models
- No integration point with Hermes Agent's bot fleet, dispatch system, or cloud jobs
- Current cloud jobs use ANTHROPIC_API_KEY/GITHUB_TOKEN directly via `run.py task`

Where it *might* matter:
- If you ever want to self-host models locally instead of cloud API calls, these are the magnet links
- It's a preservation archive, not live inference — you'd need your own hardware

## Notes

Not urgent. Logged for later evaluation alongside the OmniRoute experiment.
