#!/bin/sh
# Starts omni-ai-mcp (Gemini second opinion, see CLAUDE.md) with the key from the GEMINI_API_KEY environment variable.
# The google-genai SDK sends it as x-goog-api-key. Gemini ignores that header whenever an Authorization: Bearer header
# is also present, so GEMINI_API_KEY must be a plain environment variable, NOT a network secret (the proxy sends network
# secrets as Bearer tokens, which Gemini rejects with 401 ACCESS_TOKEN_TYPE_UNSUPPORTED).
key=$(printf '%s' "${GEMINI_API_KEY:-}" | tr -d ' \t\r\n"'"'")
case "$key" in
  GEMINI_API_KEY=*) key=${key#GEMINI_API_KEY=} ;;
esac
if [ -z "$key" ]; then
  echo "gemini-mcp: GEMINI_API_KEY is not set in this session's environment; Gemini tools will report a missing key." >&2
fi
export GEMINI_API_KEY="$key"
export GEMINI_ACTIVITY_LOG=false
# Free-tier keys get no quota (limit 0) on the pro models (gemini-4-argon, gemini-3.1-pro), so by default every
# "pro" call goes to Flash. With billing on, set GEMINI_MODEL_PRO=gemini-4-argon in the environment instead.
export GEMINI_MODEL_PRO="${GEMINI_MODEL_PRO:-gemini-3.8-flash}"
exec uvx omni-ai-mcp==4.8.2
