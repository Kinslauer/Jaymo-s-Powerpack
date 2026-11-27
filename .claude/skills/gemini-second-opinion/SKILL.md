---
name: gemini-second-opinion
description: Use when you want Gemini as an extra verifier on code or a finding, or need Gemini to take a task.
---
# Gemini second opinion

Gemini runs on Andy's Gemini subscription through the Antigravity harness in T3 Code (`agy.exe`, provider
`antigravity`). Send it a review or task with `delegate_task`, target
`{providerInstanceId: "antigravity", model: "gemini-3.8-flash-low"}`, or a `gemini-4-*` model for harder work.
`orchestrator_capabilities` with `search: "antigravity"` lists the models.

- Treat its answers as advice and check them against the code.
- "Provider is not authenticated": ask Andy to click **Sign in with Google** in T3 Settings → Providers → Antigravity.
  Running `agy` or `agy acp --login` in a terminal doesn't sign T3 in, because T3 gives agy its own profile.

Without T3 (cloud sessions), the old omni-ai-mcp server in `.mcp.json` (`tools/gemini-mcp.sh`) is the fallback. It
needs `GEMINI_API_KEY` as a plain environment variable: a network secret breaks it, because Gemini rejects the Bearer
header the proxy adds. Use only its text and code tools (`ask_gemini`, `gemini_code_review`, `gemini_challenge`,
`gemini_analyze_codebase`). Media, agent and OpenRouter tools are denied in `.claude/settings.json`.
