---
name: claude-gemini-tandem
description: How Claude and Gemini could work together on the powerpack (argon-bridge vs omni-ai-mcp evaluation, 2026-11-03)
metadata:
  type: project
  modified: 2026-11-03T16:02:42.165Z
---
2026-11-03 Andy asked to use Claude and Gemini more in tandem. evaluated two MIT repos (code read, nothing installed):
- argon-bridge: Claude Code plugin; hooks on every prompt/Bash/commit/plan call the `agy` (Antigravity) CLI so Gemini 4 Argon reviews plans and commits, plus opt-in delegated coding in a worktree. Needs an interactive Google login for agy, so it can't run in ephemeral cloud threads; only the Steam Deck Claude Code session could host it.
- omni-ai-mcp: stdio MCP server (28 Gemini/OpenRouter tools: ask_gemini, code review, 1M-token codebase analysis, deep research, image/video/music). Needs GEMINI_API_KEY. Cloud containers reach generativelanguage.googleapis.com and PyPI, so it works in cloud threads with the key as an environment secret and a pinned `uvx omni-ai-mcp==4.8.2` in the repo's .mcp.json.
- Recommendation given: omni-ai-mcp in cloud as a second-opinion/verifier tool only (pinned, media tools unused to avoid cost); skip argon-bridge unless Andy wants it on the Deck.
Andy chose "Neither for now" (2026-11-03 16:47Z): nothing installed; Gemini work keeps arriving as pasted handoffs. Later (2026-11-04 00:02Z) Andy added GEMINI_API_KEY as a network secret and asked for it: PR #886 (MERGED 2026-11-04 00:09Z) adds .mcp.json (uvx omni-ai-mcp==4.8.2) + deny rules in .claude/settings.json for media/agent/research/OpenRouter tools (its GEMINI_DISABLED_TOOLS env is dead code in 4.8.2). Live key not yet tested. Never accept keys pasted in chat; Andy once pasted one and was told to revoke it. Related: [[gemini-handoff-ki-forge]], [[reference-notes]].
2026-11-04 first test (bug sweep thread, PR #887): Gemini did NOT answer. omni-ai-mcp never loaded (no mcp__omni-ai-mcp tools) and GEMINI_API_KEY isn't in the env; the key is a *network* secret, which the proxy sends as an Authorization bearer token, and Gemini rejects that (401 ACCESS_TOKEN_TYPE_UNSUPPORTED; it wants x-goog-api-key). Fix: Andy adds GEMINI_API_KEY as an environment variable in the cloud environment; new sessions pick it up.
Env-variable box format: one line `GEMINI_API_KEY=<key>` (Andy's first try with name and key on separate lines wouldn't save, 2026-11-04).
2026-11-04 00:50Z root cause confirmed by test: when an Authorization: Bearer header is present Gemini ignores x-goog-api-key (401 API_KEY_SERVICE_BLOCKED), so the NETWORK SECRET must be deleted; keep GEMINI_API_KEY only as an environment variable. Also `${GEMINI_API_KEY}` in .mcp.json made Claude Code skip the server when unset. PR #890 launches via tools/gemini-mcp.sh (trims quotes/whitespace/prefix, runs uvx omni-ai-mcp==4.8.2).
Free tier: pro models (gemini-4-argon, gemini-3.1-pro) have quota limit 0; gemini-2.5-flash 404 (retired); gemini-3.8-flash works but can 503 under load. #890's wrapper defaults GEMINI_MODEL_PRO to gemini-3.8-flash (override via env once billing is on). `?key=` query param also authenticates even with the Bearer present, but the SDK uses the header.
2026-11-04 01:00Z Andy confirmed the network secret is deleted (env var kept). Next new thread: confirm ask_gemini answers via omni-ai-mcp.

2026-11-04 01:56Z: GEMINI_API_KEY env var present (53 chars, not AIza-prefixed but valid). Proxy still injects Bearer for generativelanguage.googleapis.com, so x-goog-api-key calls 401. Passing ?key= in URL bypasses it (got 503 high demand on gemini-3.8-flash = auth OK). omni-ai-mcp tools did not load at session start in that thread though the wrapper starts manually (first uvx download may exceed MCP startup).
2026-11-04 02:04Z: Andy deleted the proxy credential; same container's header call then passed auth (503 high demand only), so the change applies live.
