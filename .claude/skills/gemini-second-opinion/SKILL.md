---
name: gemini-second-opinion
description: Use when you want Gemini as an extra verifier on code or a finding, or the omni-ai-mcp server won't start.
---
# Gemini second opinion

`.mcp.json` runs omni-ai-mcp 4.8.2 through `tools/gemini-mcp.sh`. It needs `GEMINI_API_KEY` as a plain environment
variable: a network secret breaks it, because Gemini rejects the Bearer header the proxy adds.

Use only its text and code tools (`ask_gemini`, `gemini_code_review`, `gemini_challenge`, `gemini_analyze_codebase`),
as an extra verifier. Its answers are advice to check against the code. Media, agent and OpenRouter tools are denied
in `.claude/settings.json`.
