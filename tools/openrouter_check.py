"""Check an OpenRouter key and find free models the Hermes bots can use as a non-NVIDIA backup.

OpenRouter (https://openrouter.ai) serves many models behind one OpenAI-compatible API at
https://openrouter.ai/api/v1. Models ending in ":free" cost nothing: 20 requests a minute, and 50 requests a day
per account (1,000 a day once the account has bought $10 of credit, one time).

  py -3 -m pip install openai                      (once)
  py -3 tools/openrouter_check.py --list           free models that take tool calls, biggest context first (no key)
  py -3 tools/openrouter_check.py                  one tool-call request to the default model, plus the key's usage
  py -3 tools/openrouter_check.py --model thinkingmachines/inkling-2-small:free

Env:
  OPENROUTER_API_KEY   made by Andy at https://openrouter.ai/keys. Never print it, commit it, or put it in a command.

Exit code 0 means the key works and the model answered with a tool call (or --list printed models).
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

BASE_URL = "https://openrouter.ai/api/v1"
DEFAULT_MODEL = "google/gemma-4-31b-it:free"
MIN_CONTEXT = 128_000  # a bot call sends 64-135k tokens, so smaller windows are useless
TIMEOUT_S = 120

# One small tool, so the check proves tool calling works and not just chat.
TOOLS = [{
    "type": "function",
    "function": {
        "name": "get_server_status",
        "description": "Returns whether the named Minecraft server is online.",
        "parameters": {
            "type": "object",
            "properties": {"server": {"type": "string", "description": "Server name"}},
            "required": ["server"],
        },
    },
}]


def get_json(path, key=None):
    req = urllib.request.Request(BASE_URL + path)
    if key:
        req.add_header("Authorization", "Bearer " + key)
    with urllib.request.urlopen(req, timeout=TIMEOUT_S) as resp:
        return json.load(resp)


def list_free_tool_models(min_context):
    models = get_json("/models")["data"]
    rows = []
    for m in models:
        if not m["id"].endswith(":free"):
            continue
        params = m.get("supported_parameters") or []
        ctx = m.get("context_length") or 0
        if "tools" in params and ctx >= min_context:
            rows.append((ctx, m["id"]))
    rows.sort(reverse=True)
    if not rows:
        print(f"No free models with tool calling and >= {min_context:,} context right now.")
        return 1
    print(f"Free models with tool calling and >= {min_context:,} tokens of context:")
    for ctx, mid in rows:
        print(f"  {ctx:>9,}  {mid}")
    return 0


def check_model(key, model):
    try:
        from openai import OpenAI, APIStatusError, APIConnectionError
    except ImportError:
        print("The openai package is missing: run  py -3 -m pip install openai")
        return 2

    client = OpenAI(base_url=BASE_URL, api_key=key, timeout=TIMEOUT_S, max_retries=0)
    try:
        resp = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": "Is the server called 'jaymo' online? Use the tool."}],
            tools=TOOLS,
            extra_headers={"X-OpenRouter-Title": "Hermes key check"},
        )
    except APIStatusError as e:
        hints = {
            401: "the key is wrong or revoked: make a new one at https://openrouter.ai/keys",
            402: "the account needs credit for this model (pick a :free one)",
            404: "no such model, or it no longer supports tools: run --list",
            429: "rate limited: 20/min, and 50/day on free models until $10 of credit is bought once",
        }
        print(f"FAIL {e.status_code}: {hints.get(e.status_code, e.message)}")
        return 1
    except APIConnectionError as e:
        print(f"FAIL: could not reach OpenRouter ({e.__class__.__name__}). Check the network.")
        return 1

    if not resp.choices:
        print("FAIL: OpenRouter answered with no choices.")
        return 1
    msg = resp.choices[0].message
    served_by = getattr(resp, "provider", None) or "?"
    if msg.tool_calls:
        call = msg.tool_calls[0].function
        print(f"OK {model} (served by {served_by}) called {call.name}({call.arguments})")
        ok = 0
    else:
        print(f"WEAK {model} (served by {served_by}) answered without calling the tool: {(msg.content or '')[:200]}")
        ok = 1
    if resp.usage:
        print(f"   tokens: {resp.usage.prompt_tokens} in, {resp.usage.completion_tokens} out")
    return ok


def show_key_usage(key):
    try:
        data = get_json("/key", key).get("data", {})
    except (urllib.error.URLError, ValueError) as e:
        print(f"(couldn't read key usage: {e})")
        return
    print(f"   key: free tier={data.get('is_free_tier')}, limit={data.get('limit')}, usage=${data.get('usage')}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", action="store_true", help="list free tool-calling models (no key needed)")
    ap.add_argument("--model", default=DEFAULT_MODEL, help=f"model slug to test (default {DEFAULT_MODEL})")
    ap.add_argument("--min-context", type=int, default=MIN_CONTEXT, help="smallest context to list")
    args = ap.parse_args()

    if args.list:
        try:
            return list_free_tool_models(args.min_context)
        except (urllib.error.URLError, ValueError) as e:
            print(f"FAIL: couldn't read the model list: {e}")
            return 1

    key = os.environ.get("OPENROUTER_API_KEY", "").strip()
    if not key:
        print("OPENROUTER_API_KEY isn't set. Andy: make a key at https://openrouter.ai/keys and set it as a user")
        print("environment variable (and in E:/hermes/.env for Hermes). Never paste it into chat or a command.")
        return 2
    result = check_model(key, args.model)
    show_key_usage(key)
    return result


if __name__ == "__main__":
    sys.exit(main())
