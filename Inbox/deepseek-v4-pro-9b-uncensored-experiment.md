---
status: open
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-12)
asked: 2026-11-12
thread: 1570846670168657920
claimed_by:
pr:
suggested_owns: []
---

# DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED experiment

Andy asked about https://t.co/kpl4Dgr4ME — what can be done with this Hugging Face model.

## What it is

Decensored 9B reasoning model (`xtools-at/DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED-heretic`), distilled from DeepSeek-V4-Pro on Qwen3.5-9B. Math & STEM focused (~250K training samples). Only 3/100 refusals vs 100/100 on original. Evaluated with vLLM NVFP4 and llama.cpp MTP-Q8_0.

## Initial assessment

Not directly useful for current Powerpack setup:
- Can't download or run local models from here
- No integration with Hermes Agent's bot fleet, dispatch system, or cloud jobs
- Current cloud jobs use ANTHROPIC_API_KEY via `run.py task` — direct API, not local model runner

## Where it *might* matter

If you ever self-host models locally instead of cloud API calls — this is a deployable 9B reasoning model via vLLM or llama.cpp. Would need your own hardware to run.

## Notes

Not urgent. Logged alongside OmniRoute, Pirate Face, OpenCode, and Minecraft server bot experiments.
