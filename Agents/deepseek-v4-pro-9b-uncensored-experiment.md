---
status: running
task: deepseek-v4-pro-9b-uncensored-experiment
branch: claude/deepseek-v4-pro-9b-uncensored-experiment
with:
pr:
mods:
versions:
started: 2026-11-13
owns:
  -
---
# DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED experiment

**Task:** Research what can be done with the Hugging Face model xtools-at/DeepSeek-V4-Pro-Qwen3.5-9B-UNCENSORED-heretic for the Powerpack project.

## Plan
- Investigate the model capabilities and deployment options
- Assess relevance to current Hermes Agent / Powerpack workflow
- Document findings for the coordinator

## Notes
- Model is a decensored 9B reasoning model distilled from DeepSeek-V4-Pro on Qwen3.5-9B
- Math & STEM focused (~250K training samples)
- Only 3/100 refusals vs 100/100 on original
- Evaluated with vLLM NVFP4 and llama.cpp MTP-Q8_0

## Done
Research completed. Model is not directly useful for current Powerpack setup as it requires self-hosted hardware. Would only matter if self-hosting models locally instead of cloud API calls.