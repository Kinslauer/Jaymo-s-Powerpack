"""Gives the art agent one brief and prints each step as it happens, with no time limit.

`agents-cli run` gives up when the agent is silent for 2 minutes, which a slow Gemini answer (free tier, busy
hours, a big texture) can be. This runs the agent in this process instead.

Run from tools/art-agent:
  uv run python -m app.ask "Paint a 16x16 item texture of a copper key" [--file picture.png ...]

The key comes from GEMINI_API_KEY in the environment or in tools/art-agent/.env.
"""

import asyncio
import logging
import mimetypes
import os
import sys
import time

from dotenv import load_dotenv

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def _short(v, n=160):
    s = str(v).replace("\n", " ")
    return s if len(s) <= n else s[:n] + "..."


async def probe(names, timeout_s=20):
    """Splits the models into those that answer a tiny request within timeout_s and those that don't."""
    from google import genai
    from google.genai import types

    client = genai.Client(
        http_options=types.HttpOptions(
            timeout=timeout_s * 1000,
            retry_options=types.HttpRetryOptions(attempts=1),
        )
    )

    async def one(m):
        try:
            await client.aio.models.generate_content(model=m, contents="say hi")
            return True
        except Exception:
            return False

    ok = await asyncio.gather(*(one(m) for m in names))
    return [m for m, k in zip(names, ok, strict=True) if k], [
        m for m, k in zip(names, ok, strict=True) if not k
    ]


async def run(brief, files):
    from google.adk.runners import InMemoryRunner
    from google.genai import types

    from app.agent import MODELS, make_model, root_agent

    # Busy free-tier models can take a minute just to say no, on every call. Ask each one "say hi" first and put
    # the ones that answer at the front, so the agent's many calls don't each wait out the busy ones.
    print("[0s] checking which Gemini models answer...", flush=True)
    ready, busy = await probe(MODELS)
    if not ready:
        print(
            "[0s] every model is busy right now; trying anyway in the usual order",
            flush=True,
        )
    else:
        print(
            f"[0s] using {ready[0]}" + (f" (busy: {', '.join(busy)})" if busy else ""),
            flush=True,
        )
    root_agent.model = make_model(ready + busy)
    runner = InMemoryRunner(agent=root_agent, app_name="app")
    session = await runner.session_service.create_session(app_name="app", user_id="ask")
    parts = [types.Part.from_text(text=brief)]
    for f in files:
        with open(f, "rb") as fh:
            parts.append(
                types.Part.from_bytes(
                    data=fh.read(),
                    mime_type=mimetypes.guess_type(f)[0] or "image/png",
                )
            )
    start = time.monotonic()
    print("[0s] asking Gemini...", flush=True)
    async for ev in runner.run_async(
        user_id="ask",
        session_id=session.id,
        new_message=types.Content(role="user", parts=parts),
    ):
        t = f"[{time.monotonic() - start:.0f}s]"
        for p in (ev.content.parts if ev.content else None) or []:
            if p.function_call:
                args = {
                    k: _short(v, 60) for k, v in (p.function_call.args or {}).items()
                }
                print(f"{t} tool {p.function_call.name} {args}", flush=True)
            elif p.function_response:
                print(f"{t} -> {_short(p.function_response.response)}", flush=True)
            elif p.text and not p.thought:
                print(f"{t} {p.text}", flush=True)
        if ev.error_message:
            print(f"{t} error: {ev.error_message}", flush=True)


def main():
    args = sys.argv[1:]
    files = []
    while "--file" in args:
        i = args.index("--file")
        files.append(args[i + 1])
        del args[i : i + 2]
    if not args:
        raise SystemExit(__doc__)
    load_dotenv(os.path.join(HERE, ".env"))
    if not os.getenv("GEMINI_API_KEY") and not os.getenv("GOOGLE_CLOUD_PROJECT"):
        raise SystemExit(
            "No Gemini key: put GEMINI_API_KEY=... in tools/art-agent/.env"
        )
    # show Gemini retries (busy free tier) instead of sitting silent
    logging.basicConfig(level=logging.WARNING, format="%(message)s")
    logging.getLogger("google_genai").setLevel(logging.INFO)
    logging.getLogger("google_adk.google.adk.models._fallback_model").setLevel(
        logging.INFO
    )
    try:
        asyncio.run(run(" ".join(args), files))
    except Exception as e:  # one readable line instead of a traceback
        raise SystemExit(f"failed: {type(e).__name__}: {_short(e, 400)}") from None


if __name__ == "__main__":
    main()
