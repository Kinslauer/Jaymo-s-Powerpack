---
status: done
task: pirateface-worth-rescuing-experiment
branch: (none - research task)
with: 
pr: 
mods: 
versions: 
started: 2026-11-13
owns:
  - 
---
# Pirate Face "Worth Rescuing" AI model archive experiment

**Task:** Evaluate https://pirateface.co/worth-rescuing for usefulness to the Powerpack setup (from tiv, Discord, 2026-11-12).

## Plan
- Review the site's offerings: AI model archival via torrent magnet links
- Assess relevance to current Hermes/Powerpack infrastructure
- Document findings

## Notes
- Site preserves historic/open-weight models via torrent magnet links (DeepSeek-V4.1-Flash, Qwen3.5-35B-A3B, Qwen3.8-27B, GLM-5.3-Flash, Kimi-K3, Nemotron 3.5 Super VL, etc.)
- Models range from 249 GB to 1.6 TB
- No direct integration with Hermes Agent's bot fleet, dispatch system, or cloud jobs
- Current cloud jobs use ANTHROPIC_API_KEY/GITHUB_TOKEN directly via `run.py task`
- Cannot download torrents or run local models in current setup
- Only relevant if self-hosting models locally on own hardware in future

## Done
Research complete. Not useful for current Powerpack setup — no integration point, requires local hardware for torrent downloads and inference. Logged for future evaluation if self-hosting becomes relevant. No code changes needed.