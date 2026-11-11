# Prometheus (hermes-modeller): 8:55 PM run error, 2026-11-10

Collected 2026-11-10 from `E:/hermes/profiles/hermes-modeller`. Secrets (API keys, tokens) removed and marked [REDACTED].

## Summary

- **Bot / profile:** Prometheus (`hermes-modeller`)
- **Job:** `inbox-modeller` (ID `d77df4111e52`), started 2026-11-10 20:54:58, failed 20:55:05. Output file: `cron/output/d77df4111e52/2026-11-10_20-55-06.md`
- **Reported by:** `E:/hermes/scripts/bot_health.py`. It reads each profile's `cron/jobs.json` (last_run_at, last_status, last_error, failure_streak), the profile's `state.db`/`state.db-wal` mtimes, the vault Inbox/Agents notes, `E:/hermes/logs/gateway-watchdog.log` and its own `E:/hermes/scripts/bot_health.state.json`, and writes `Bots.md`.
- **Cause:** HTTP 429 from Ollama Cloud. The account hit its **monthly usage limit**. All three models in the fallback chain were refused, and the error is non-retryable.
- **Model / provider:** primary `kimi-k3.1-pro:cloud`, then fallbacks `glm-5.3:cloud` and `kimi-k2.7-code:cloud`. Provider `custom` (local Ollama at `http://127.0.0.1:11434/v1`, which forwards `:cloud` models to ollama.com). The job has no model/provider override (`model: null`, `provider: null` in jobs.json), so it used the profile default.

## Full error (from the run output file)

```
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2612, in run_job
    final_response = _final_response_from_result(result, job_id, job_name, AIAgent)
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2068, in _final_response_from_result
    raise RuntimeError(result.get("error") or final_response_text or "agent reported failure")
RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)

```

## jobs.json state now (after later runs)

The 8:55 run is no longer the last run. jobs.json shows the newest one:

- last_run_at: `2026-11-10T21:22:34.991301-05:00`
- last_status: `error`
- last_error: `RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 3e888d41-ca54-415c-ab81-067b678584e7)`
- failure_streak: 75

The 20:55 error had ref `04a3637a-cb7b-4a1a-818c-2a64ab728974`. An earlier run at 20:48 failed the same way.

## bot_health state

`bot_health.state.json`: `{"problems": {}, "checked": 1794363661.449445}` (checked = 2026-11-10 21:21:01). `Bots.md` row now reads: `| Prometheus (hermes-modeller) | 💤 idle | idle, last run 9:16 PM (error) |`

## errors.log.1 (errors.log rotated at 9:10 PM; the current errors.log starts after 9:05): 8:45 to 9:05 PM (743 lines)

```text
2026-11-10 20:48:25,346 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:25,391 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)
2026-11-10 20:48:26,019 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:26,257 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:26,267 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)
2026-11-10 20:48:26,596 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:26,806 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:26,823 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)
2026-11-10 20:48:27,020 ERROR [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:50,540 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_sessions: tools.browser_tool no longer defines _active_sessions, _LOCAL_SUFFIX, _REAL_PROFILE_SESSION (needed for finding a chat's browser for screen share); that feature is off on this Hermes version.
2026-11-10 20:48:50,540 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_keepalive: tools.browser_tool_lifecycle not found (needed for keeping a browser open while you use it); that feature is off on this Hermes version.
2026-11-10 20:48:50,545 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_commands: tools.browser_tool_session not found (needed for phone-sized pages while you view them); that feature is off on this Hermes version.
2026-11-10 20:48:50,548 WARNING hermes_plugins.hermes_mobile: hermes-mobile: live_sessions: tui_gateway.server no longer defines _session_lookup_key (needed for matching the app's live chat to its browser); that feature is off on this Hermes version.
2026-11-10 20:48:52,694 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:52,803 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)
2026-11-10 20:48:52,847 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:52,952 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:52,988 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)
2026-11-10 20:48:53,030 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:53,166 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:53,283 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:48:53,457 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:53,566 ERROR cron.scheduler: Job 'inbox-modeller' failed: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2612, in run_job
    final_response = _final_response_from_result(result, job_id, job_name, AIAgent)
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2068, in _final_response_from_result
    raise RuntimeError(result.get("error") or final_response_text or "agent reported failure")
RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:48:53,705 ERROR gateway.session_authority: Admitted turn f6e40bfd224d49d2a427c3902d0570fb failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:54:51,021 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:51,077 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)
2026-11-10 20:54:51,418 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:54:51,591 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:51,600 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)
2026-11-10 20:54:51,851 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:54:52,001 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:52,019 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)
2026-11-10 20:54:52,319 ERROR [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:58,480 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_sessions: tools.browser_tool no longer defines _active_sessions, _LOCAL_SUFFIX, _REAL_PROFILE_SESSION (needed for finding a chat's browser for screen share); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_keepalive: tools.browser_tool_lifecycle not found (needed for keeping a browser open while you use it); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_commands: tools.browser_tool_session not found (needed for phone-sized pages while you view them); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: live_sessions: tui_gateway.server no longer defines _session_lookup_key (needed for matching the app's live chat to its browser); that feature is off on this Hermes version.
2026-11-10 20:55:00,217 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:00,356 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)
2026-11-10 20:55:00,392 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:55:00,502 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:00,525 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)
2026-11-10 20:55:04,342 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:55:04,456 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:04,481 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 20:55:04,631 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:05,172 ERROR cron.scheduler: Job 'inbox-modeller' failed: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2612, in run_job
    final_response = _final_response_from_result(result, job_id, job_name, AIAgent)
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2068, in _final_response_from_result
    raise RuntimeError(result.get("error") or final_response_text or "agent reported failure")
RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 20:55:05,228 ERROR gateway.session_authority: Admitted turn 3679c0893eb744b68fcf89ef6a763e01 failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 21:04:49,882 WARNING gateway.session_managed_worker: Managed worker lost: managed_worker_hello_timeout
2026-11-10 21:04:55,581 ERROR gateway.session_authority: Admitted turn 2810f8b64be44d1c9d595cbe75ff955a failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 292, in execute_admission
    return await admit_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 628, in admit_managed
    return await execute_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 660, in execute_managed
    hello = await worker.next_frame(HELLO_SECONDS, ack=0)
            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 268, in next_frame
    raise RuntimeStoreError('managed_worker_hello_timeout')
hermes_state_runtime.RuntimeStoreError: managed_worker_hello_timeout
```

## errors.log: 8:45 to 9:05 PM (0 lines)

_No lines in this window._

## agent.log: 8:45 to 9:05 PM (958 lines)

```text
2026-11-10 20:47:53,568 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65135
2026-11-10 20:47:54,593 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,596 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,673 INFO [20261108_165115_5e166e2c] tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,757 INFO [20261108_165115_5e166e2c] tools.tool_search: tool_search activated (tier 1): 32 core/visible tools kept, 7 deferred (~5725 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:47:54,759 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _a2a_tools_available returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,765 INFO [20261108_165115_5e166e2c] tools.registry: check_fn check_discord_tool_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,767 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _check_feishu returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,768 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _check_yuanbao returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,833 INFO [20261108_165115_5e166e2c] agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 20:47:54,834 INFO [20261108_165115_5e166e2c] tools.registry: check_fn check_video_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,836 INFO [20261108_165115_5e166e2c] tools.registry: check_fn _check_xai_video_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:54,841 INFO [20261108_165115_5e166e2c] agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 20:47:54,842 INFO [20261108_165115_5e166e2c] tools.registry: check_fn check_x_search_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:47:58,571 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65138
2026-11-10 20:48:13,209 INFO run_agent: OpenAI client created (agent_init, shared=True) thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:13,258 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:13,259 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:13,523 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:14,474 INFO tools.tool_search: tool_search activated (tier 1): 30 core/visible tools kept, 7 deferred (~5725 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:48:19,272 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.turn_context: conversation turn: session=local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202 model=kimi-k3.1-pro:cloud provider=custom platform=kanban history=0 msg='# Kanban task t_b4c7a3b0: Audit GeckoLib model & animation coverage (omen, redst...'
2026-11-10 20:48:23,070 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-11 (<lambda>):15848 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:25,346 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:25,347 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-11 (<lambda>):15848 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:25,391 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 28975ffa-d1ff-41c0-81c3-bd0606d5aa15)
2026-11-10 20:48:25,399 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] root: Rate-limit backoff level 0: cooldown 60 s (1.0 min, backoff#1, exponential fallback)
2026-11-10 20:48:26,017 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Fallback glm-5.3:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:48:26,017 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Fallback glm-5.3:cloud: extra_body resolved: None
2026-11-10 20:48:26,019 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:26,153 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-12 (<lambda>):23476 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:48:26,257 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:26,258 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-12 (<lambda>):23476 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:48:26,267 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 93adc54b-2595-4c64-a47d-27afd84b4b0d)
2026-11-10 20:48:26,267 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] root: Rate-limit backoff level 1: cooldown 120 s (2.0 min, backoff#2, exponential fallback)
2026-11-10 20:48:26,594 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:48:26,595 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: extra_body resolved: None
2026-11-10 20:48:26,596 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:26,703 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-13 (<lambda>):7188 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:26,806 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:26,807 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-13 (<lambda>):7188 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:26,823 WARNING [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)
2026-11-10 20:48:26,824 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] root: Rate-limit backoff level 2: cooldown 240 s (4.0 min, backoff#3, exponential fallback)
2026-11-10 20:48:27,020 ERROR [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f985b55a-6491-4a7a-9d12-98dbc31026c8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:27,487 INFO [local-db95c163038b048a435a0b414718856b9c021091debbdf839ebd60fe725c2202] run_agent: Shared OpenAI client retired (cache_evict, tcp_shutdown=0, fd_release=deferred_to_gc) thread=MainThread:4308 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:27,815 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65138 reason=client_disconnect(code=1000,reason=) messages=48 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 20:48:28,903 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65135 reason=client_disconnect(code=1000,reason=) messages=155 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 20:48:47,694 INFO agent.shell_hooks: shell hook registered: pre_tool_call -> E:/hermes/agent-hooks/powerpack_guard.py (matcher=terminal|write_file|patch, timeout=10s, fail_closed=False)
2026-11-10 20:48:47,699 INFO agent.shell_hooks: shell hook registered: pre_llm_call -> E:/hermes/agent-hooks/powerpack_context.py (matcher=None, timeout=10s, fail_closed=False)
2026-11-10 20:48:48,132 INFO hermes_cli.plugins: Plugin 'browser-browser-use' registered browser provider: browser-use
2026-11-10 20:48:48,170 INFO hermes_cli.plugins: Plugin 'browser-browserbase' registered browser provider: browserbase
2026-11-10 20:48:48,199 INFO hermes_cli.plugins: Plugin 'browser-firecrawl' registered browser provider: firecrawl
2026-11-10 20:48:49,243 INFO hermes_plugins.dashboard_auth__basic: dashboard-auth-basic: hashed env-supplied password in-memory (overrides any config password_hash).
2026-11-10 20:48:49,245 INFO hermes_cli.dashboard_auth.registry: dashboard-auth: registered global provider 'basic' (Username & Password)
2026-11-10 20:48:49,255 INFO hermes_cli.plugins: Plugin 'basic' registered dashboard-auth provider: basic (Username & Password)
2026-11-10 20:48:49,256 INFO hermes_plugins.dashboard_auth__basic: dashboard-auth-basic: registered password provider (username=hermes)
2026-11-10 20:48:49,658 INFO hermes_cli.plugins: Plugin 'default' registered delegation provider: default
2026-11-10 20:48:49,680 INFO hermes_cli.plugins: Plugin 'deepinfra' registered image_gen provider: deepinfra
2026-11-10 20:48:49,686 INFO hermes_cli.plugins: Plugin 'fal' registered image_gen provider: fal
2026-11-10 20:48:49,740 INFO hermes_cli.plugins: Plugin 'krea' registered image_gen provider: krea
2026-11-10 20:48:49,747 INFO hermes_cli.plugins: Plugin 'meta-ai-image-gen' registered image_gen provider: meta-ai
2026-11-10 20:48:49,759 INFO hermes_cli.plugins: Plugin 'openai' registered image_gen provider: openai
2026-11-10 20:48:49,766 INFO hermes_cli.plugins: Plugin 'openai-codex' registered image_gen provider: openai-codex
2026-11-10 20:48:49,776 INFO hermes_cli.plugins: Plugin 'openrouter' registered image_gen provider: openrouter
2026-11-10 20:48:49,776 INFO hermes_cli.plugins: Plugin 'openrouter' registered image_gen provider: nous
2026-11-10 20:48:49,787 INFO hermes_cli.plugins: Plugin 'xai' registered image_gen provider: xai
2026-11-10 20:48:50,030 INFO hermes_cli.plugins: Plugin 'deepinfra' registered video_gen provider: deepinfra
2026-11-10 20:48:50,037 INFO hermes_cli.plugins: Plugin 'fal' registered video_gen provider: fal
2026-11-10 20:48:50,045 INFO hermes_cli.plugins: Plugin 'openrouter' registered video_gen provider: openrouter
2026-11-10 20:48:50,053 INFO hermes_cli.plugins: Plugin 'xai' registered video_gen provider: xai
2026-11-10 20:48:50,072 INFO hermes_cli.plugins: Plugin 'web-brave-free' registered web provider: brave-free
2026-11-10 20:48:50,084 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65188
2026-11-10 20:48:50,090 INFO hermes_cli.plugins: Plugin 'web-ddgs' registered web provider: ddgs
2026-11-10 20:48:50,097 INFO hermes_cli.plugins: Plugin 'web-exa' registered web provider: exa
2026-11-10 20:48:50,117 INFO hermes_cli.plugins: Plugin 'web-firecrawl' registered web provider: firecrawl
2026-11-10 20:48:50,137 INFO hermes_cli.plugins: Plugin 'web-keenable' registered web provider: keenable
2026-11-10 20:48:50,151 INFO hermes_cli.plugins: Plugin 'web-openai-native' registered web provider: openai-native
2026-11-10 20:48:50,178 INFO hermes_cli.plugins: Plugin 'web-parallel' registered web provider: parallel
2026-11-10 20:48:50,255 INFO hermes_cli.plugins: Plugin 'web-perplexity' registered web provider: perplexity
2026-11-10 20:48:50,298 INFO hermes_cli.plugins: Plugin 'web-searxng' registered web provider: searxng
2026-11-10 20:48:50,308 INFO hermes_cli.plugins: Plugin 'web-tavily' registered web provider: tavily
2026-11-10 20:48:50,325 INFO hermes_cli.plugins: Plugin 'web-xai' registered web provider: xai
2026-11-10 20:48:50,369 INFO hermes_cli.plugin_capabilities: capability_check plugin=hermes-mobile capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:48:50,540 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_sessions: tools.browser_tool no longer defines _active_sessions, _LOCAL_SUFFIX, _REAL_PROFILE_SESSION (needed for finding a chat's browser for screen share); that feature is off on this Hermes version.
2026-11-10 20:48:50,540 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_keepalive: tools.browser_tool_lifecycle not found (needed for keeping a browser open while you use it); that feature is off on this Hermes version.
2026-11-10 20:48:50,545 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_commands: tools.browser_tool_session not found (needed for phone-sized pages while you view them); that feature is off on this Hermes version.
2026-11-10 20:48:50,548 WARNING hermes_plugins.hermes_mobile: hermes-mobile: live_sessions: tui_gateway.server no longer defines _session_lookup_key (needed for matching the app's live chat to its browser); that feature is off on this Hermes version.
2026-11-10 20:48:50,559 INFO hermes_cli.plugin_capabilities: capability_check plugin=kanban-gantt capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:48:50,591 INFO hermes_cli.plugin_capabilities: capability_check plugin=mcp-serve capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:48:50,655 INFO hermes_cli.plugins: Plugin discovery complete: 67 found, 62 enabled
2026-11-10 20:48:50,900 INFO cron.scheduler: Running job 'inbox-modeller' (ID: d77df4111e52)
2026-11-10 20:48:50,902 INFO cron.scheduler: Prompt: [IMPORTANT: The user has invoked the "minecraft-modpack-workflow" skill, indicating they want you to
2026-11-10 20:48:51,023 INFO run_agent: OpenAI client created (agent_init, shared=True) thread=asyncio_7:3936 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:51,028 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:51,031 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:51,070 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:48:51,148 INFO tools.tool_search: tool_search activated (tier 1): 17 core/visible tools kept, 5 deferred (~3459 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:48:51,238 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.turn_context: conversation turn: session=local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff model=kimi-k3.1-pro:cloud provider=custom platform=cron history=0 msg='[IMPORTANT: The user has invoked the "minecraft-modpack-workflow" skill, indicat...'
2026-11-10 20:48:52,569 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:52,694 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:52,728 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:48:52,803 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 5575207b-ba4c-41f3-91c2-53924e6726f3)
2026-11-10 20:48:52,806 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] root: Rate-limit backoff level 0: cooldown 60 s (1.0 min, backoff#1, exponential fallback)
2026-11-10 20:48:52,846 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Fallback glm-5.3:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:48:52,846 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Fallback glm-5.3:cloud: extra_body resolved: None
2026-11-10 20:48:52,847 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:52,866 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:48:52,952 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:52,976 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:48:52,988 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: e2e24b75-d3b8-4205-ba64-b2b603e3e4b0)
2026-11-10 20:48:52,991 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] root: Rate-limit backoff level 1: cooldown 120 s (2.0 min, backoff#2, exponential fallback)
2026-11-10 20:48:53,027 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:48:53,027 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: extra_body resolved: None
2026-11-10 20:48:53,030 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:48:53,049 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:53,166 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:53,185 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:53,283 WARNING [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4366_0:20284 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:48:53,285 INFO [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] root: Rate-limit backoff level 2: cooldown 240 s (4.0 min, backoff#3, exponential fallback)
2026-11-10 20:48:53,457 ERROR [local-1d98480e4248283faf9754361db6a41b4368d66da2dcd804f32fb41e673bacff] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:48:53,566 ERROR cron.scheduler: Job 'inbox-modeller' failed: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2612, in run_job
    final_response = _final_response_from_result(result, job_id, job_name, AIAgent)
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2068, in _final_response_from_result
    raise RuntimeError(result.get("error") or final_response_text or "agent reported failure")
RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:48:53,672 INFO run_agent: OpenAI client closed (agent_close, shared=True, tcp_force_closed=0) thread=cron-cleanup-d77df4111e52:14712 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:48:53,705 ERROR gateway.session_authority: Admitted turn f6e40bfd224d49d2a427c3902d0570fb failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:48:55,700 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65188 reason=client_disconnect(code=1000,reason=) messages=8 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 20:54:15,541 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65392
2026-11-10 20:54:16,132 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,135 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,202 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,279 INFO tools.tool_search: tool_search activated (tier 1): 32 core/visible tools kept, 7 deferred (~5725 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:54:16,281 INFO tools.registry: check_fn _a2a_tools_available returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,286 INFO tools.registry: check_fn check_discord_tool_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,292 INFO tools.registry: check_fn _check_feishu returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,294 INFO tools.registry: check_fn _check_yuanbao returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,326 INFO agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 20:54:16,327 INFO tools.registry: check_fn check_video_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,331 INFO tools.registry: check_fn _check_xai_video_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:16,337 INFO agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 20:54:16,338 INFO tools.registry: check_fn check_x_search_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:19,693 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65394
2026-11-10 20:54:34,489 INFO run_agent: OpenAI client created (agent_init, shared=True) thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:54:34,518 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:34,519 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:35,875 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:37,117 INFO tools.tool_search: tool_search activated (tier 1): 30 core/visible tools kept, 7 deferred (~5725 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:54:41,513 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.turn_context: conversation turn: session=local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54 model=kimi-k3.1-pro:cloud provider=custom platform=kanban history=0 msg='# Kanban task t_b4c7a3b0: Audit GeckoLib model & animation coverage (omen, redst...'
2026-11-10 20:54:46,482 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-11 (<lambda>):9080 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:54:51,021 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:51,021 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-11 (<lambda>):9080 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:54:51,077 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 428ef0c4-5038-499f-b6d6-9361e1acd725)
2026-11-10 20:54:51,116 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] root: Rate-limit backoff level 0: cooldown 60 s (1.0 min, backoff#1, exponential fallback)
2026-11-10 20:54:51,416 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Fallback glm-5.3:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:54:51,416 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Fallback glm-5.3:cloud: extra_body resolved: None
2026-11-10 20:54:51,418 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:54:51,500 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-12 (<lambda>):20840 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:54:51,591 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:51,592 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-12 (<lambda>):20840 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:54:51,600 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 11427358-ca25-4e1e-b709-82a45b7ee1ee)
2026-11-10 20:54:51,600 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] root: Rate-limit backoff level 1: cooldown 120 s (2.0 min, backoff#2, exponential fallback)
2026-11-10 20:54:51,850 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:54:51,850 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: extra_body resolved: None
2026-11-10 20:54:51,851 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:54:51,915 INFO run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=Thread-13 (<lambda>):11520 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:54:52,001 ERROR agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:52,002 INFO run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=Thread-13 (<lambda>):11520 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:54:52,019 WARNING [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)
2026-11-10 20:54:52,021 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] root: Rate-limit backoff level 2: cooldown 240 s (4.0 min, backoff#3, exponential fallback)
2026-11-10 20:54:52,319 ERROR [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 13cab187-b1ab-4ecb-9dee-e636ee787e76)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:54:52,972 INFO [local-8523f652fdcb7c53e42da194db585e5c5ef7c8fdbf08d5467f95c2ce542a0b54] run_agent: Shared OpenAI client retired (cache_evict, tcp_shutdown=0, fd_release=deferred_to_gc) thread=MainThread:20972 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:54:53,076 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65394 reason=client_disconnect(code=1000,reason=) messages=48 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 20:54:54,432 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65392 reason=client_disconnect(code=1000,reason=) messages=174 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 20:54:55,976 INFO agent.shell_hooks: shell hook registered: pre_tool_call -> E:/hermes/agent-hooks/powerpack_guard.py (matcher=terminal|write_file|patch, timeout=10s, fail_closed=False)
2026-11-10 20:54:55,976 INFO agent.shell_hooks: shell hook registered: pre_llm_call -> E:/hermes/agent-hooks/powerpack_context.py (matcher=None, timeout=10s, fail_closed=False)
2026-11-10 20:54:56,248 INFO hermes_cli.plugins: Plugin 'browser-browser-use' registered browser provider: browser-use
2026-11-10 20:54:56,339 INFO hermes_cli.plugins: Plugin 'browser-browserbase' registered browser provider: browserbase
2026-11-10 20:54:56,354 INFO hermes_cli.plugins: Plugin 'browser-firecrawl' registered browser provider: firecrawl
2026-11-10 20:54:57,389 INFO hermes_plugins.dashboard_auth__basic: dashboard-auth-basic: hashed env-supplied password in-memory (overrides any config password_hash).
2026-11-10 20:54:57,390 INFO hermes_cli.dashboard_auth.registry: dashboard-auth: registered global provider 'basic' (Username & Password)
2026-11-10 20:54:57,391 INFO hermes_cli.plugins: Plugin 'basic' registered dashboard-auth provider: basic (Username & Password)
2026-11-10 20:54:57,394 INFO hermes_plugins.dashboard_auth__basic: dashboard-auth-basic: registered password provider (username=hermes)
2026-11-10 20:54:57,549 INFO hermes_cli.plugins: Plugin 'default' registered delegation provider: default
2026-11-10 20:54:57,696 INFO hermes_cli.plugins: Plugin 'deepinfra' registered image_gen provider: deepinfra
2026-11-10 20:54:57,908 INFO hermes_cli.plugins: Plugin 'fal' registered image_gen provider: fal
2026-11-10 20:54:57,919 INFO hermes_cli.plugins: Plugin 'krea' registered image_gen provider: krea
2026-11-10 20:54:57,940 INFO hermes_cli.plugins: Plugin 'meta-ai-image-gen' registered image_gen provider: meta-ai
2026-11-10 20:54:57,954 INFO hermes_cli.plugins: Plugin 'openai' registered image_gen provider: openai
2026-11-10 20:54:57,965 INFO hermes_cli.plugins: Plugin 'openai-codex' registered image_gen provider: openai-codex
2026-11-10 20:54:57,972 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:65449
2026-11-10 20:54:57,982 INFO hermes_cli.plugins: Plugin 'openrouter' registered image_gen provider: openrouter
2026-11-10 20:54:57,983 INFO hermes_cli.plugins: Plugin 'openrouter' registered image_gen provider: nous
2026-11-10 20:54:57,998 INFO hermes_cli.plugins: Plugin 'xai' registered image_gen provider: xai
2026-11-10 20:54:58,187 INFO hermes_cli.plugins: Plugin 'deepinfra' registered video_gen provider: deepinfra
2026-11-10 20:54:58,193 INFO hermes_cli.plugins: Plugin 'fal' registered video_gen provider: fal
2026-11-10 20:54:58,197 INFO hermes_cli.plugins: Plugin 'openrouter' registered video_gen provider: openrouter
2026-11-10 20:54:58,204 INFO hermes_cli.plugins: Plugin 'xai' registered video_gen provider: xai
2026-11-10 20:54:58,217 INFO hermes_cli.plugins: Plugin 'web-brave-free' registered web provider: brave-free
2026-11-10 20:54:58,223 INFO hermes_cli.plugins: Plugin 'web-ddgs' registered web provider: ddgs
2026-11-10 20:54:58,229 INFO hermes_cli.plugins: Plugin 'web-exa' registered web provider: exa
2026-11-10 20:54:58,277 INFO hermes_cli.plugins: Plugin 'web-firecrawl' registered web provider: firecrawl
2026-11-10 20:54:58,290 INFO hermes_cli.plugins: Plugin 'web-keenable' registered web provider: keenable
2026-11-10 20:54:58,303 INFO hermes_cli.plugins: Plugin 'web-openai-native' registered web provider: openai-native
2026-11-10 20:54:58,326 INFO hermes_cli.plugins: Plugin 'web-parallel' registered web provider: parallel
2026-11-10 20:54:58,358 INFO hermes_cli.plugins: Plugin 'web-perplexity' registered web provider: perplexity
2026-11-10 20:54:58,370 INFO hermes_cli.plugins: Plugin 'web-searxng' registered web provider: searxng
2026-11-10 20:54:58,379 INFO hermes_cli.plugins: Plugin 'web-tavily' registered web provider: tavily
2026-11-10 20:54:58,387 INFO hermes_cli.plugins: Plugin 'web-xai' registered web provider: xai
2026-11-10 20:54:58,413 INFO hermes_cli.plugin_capabilities: capability_check plugin=hermes-mobile capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:54:58,480 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_sessions: tools.browser_tool no longer defines _active_sessions, _LOCAL_SUFFIX, _REAL_PROFILE_SESSION (needed for finding a chat's browser for screen share); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_keepalive: tools.browser_tool_lifecycle not found (needed for keeping a browser open while you use it); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: browser_commands: tools.browser_tool_session not found (needed for phone-sized pages while you view them); that feature is off on this Hermes version.
2026-11-10 20:54:58,481 WARNING hermes_plugins.hermes_mobile: hermes-mobile: live_sessions: tui_gateway.server no longer defines _session_lookup_key (needed for matching the app's live chat to its browser); that feature is off on this Hermes version.
2026-11-10 20:54:58,485 INFO hermes_cli.plugin_capabilities: capability_check plugin=kanban-gantt capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:54:58,509 INFO hermes_cli.plugin_capabilities: capability_check plugin=mcp-serve capability=tools.override decision=deny checked_by=plugin_capability_granted evidence=not granted
2026-11-10 20:54:58,513 INFO hermes_cli.plugins: Plugin discovery complete: 67 found, 62 enabled
2026-11-10 20:54:58,619 INFO cron.scheduler: Running job 'inbox-modeller' (ID: d77df4111e52)
2026-11-10 20:54:58,619 INFO cron.scheduler: Prompt: [IMPORTANT: The user has invoked the "minecraft-modpack-workflow" skill, indicating they want you to
2026-11-10 20:54:58,734 INFO run_agent: OpenAI client created (agent_init, shared=True) thread=asyncio_7:3936 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:54:58,739 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:58,743 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:58,786 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 20:54:58,858 INFO tools.tool_search: tool_search activated (tier 1): 17 core/visible tools kept, 5 deferred (~3459 tokens), listing full (budget ~4000 tokens)
2026-11-10 20:54:58,948 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.turn_context: conversation turn: session=local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7 model=kimi-k3.1-pro:cloud provider=custom platform=cron history=0 msg='[IMPORTANT: The user has invoked the "minecraft-modpack-workflow" skill, indicat...'
2026-11-10 20:55:00,115 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:55:00,217 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:00,234 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud
2026-11-10 20:55:00,356 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1 model=kimi-k3.1-pro:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 9cb6b33b-224c-4a7e-9bf9-09dee44469a5)
2026-11-10 20:55:00,360 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] root: Rate-limit backoff level 0: cooldown 60 s (1.0 min, backoff#1, exponential fallback)
2026-11-10 20:55:00,390 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Fallback glm-5.3:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:55:00,391 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Fallback glm-5.3:cloud: extra_body resolved: None
2026-11-10 20:55:00,392 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Profile hermes-modeller: kimi-k3.1-pro:cloud via custom refused for billing/credits — using fallback glm-5.3:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:55:00,409 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:55:00,502 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:00,518 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud
2026-11-10 20:55:00,525 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=glm-5.3:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f797111c-13fb-475d-b7c7-95fb361817b8)
2026-11-10 20:55:00,528 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] root: Rate-limit backoff level 1: cooldown 120 s (2.0 min, backoff#2, exponential fallback)
2026-11-10 20:55:04,340 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: reasoning_config resolved: {'enabled': True, 'effort': 'medium'}
2026-11-10 20:55:04,341 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Fallback kimi-k2.7-code:cloud: extra_body resolved: None
2026-11-10 20:55:04,342 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Profile hermes-modeller: glm-5.3:cloud via custom refused for billing/credits — using fallback kimi-k2.7-code:cloud via custom. Top up credits, or run `hermes -p hermes-modeller model` to pick a model this account can use.
2026-11-10 20:55:04,370 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client created (chat_completion_stream_request, shared=False) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:55:04,456 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.chat_completion_helpers: Streaming failed before delivery: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3736, in _call
    self.result["response"] = _with_stream_emitters(
                              ~~~~~~~~~~~~~~~~~~~~~^
        self.agent, lambda: self._call_wire(stream_attempt_id))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2497, in _with_stream_emitters
    response = run()
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3737, in <lambda>
    self.agent, lambda: self._call_wire(stream_attempt_id))
                        ~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3714, in _call_wire
    return self._call_chat_completions(stream_attempt_id)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3071, in _call_chat_completions
    stream = self._set_managed_stream(relay_llm.stream(self.api_kwargs, _open_stream,
                                      ~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        **_relay_stream_identity(self.agent, "provider"), finalizer=relay_response.finalize,
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    ...<2 lines>...
        completed_response_predicate=lambda value: hasattr(value, "choices"),
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
        metadata=_relay_stream_metadata(self.agent, "chat_completions"), defer_logical_completion=True))
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 331, in __init__
    self._start_unmanaged(request)
    ~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\relay_llm.py", line 337, in _start_unmanaged
    raw_stream = self._stream_factory(request)
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 3062, in _open_stream
    return self._open_chat_stream({**next_api_kwargs, "stream": True, "timeout": timeout})
           ~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\agent\chat_completion_helpers.py", line 2955, in _open_chat_stream
    return request_client.chat.completions.create(**stream_kwargs)
           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_utils\_utils.py", line 298, in wrapper
    return func(*args, **kwargs)
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\resources\chat\completions\completions.py", line 1296, in create
    return self._post(
           ~~~~~~~~~~^
        "/chat/completions",
        ^^^^^^^^^^^^^^^^^^^^
    ...<53 lines>...
        stream_cls=Stream[ChatCompletionChunk],
        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    )
    ^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1375, in post
    return cast(ResponseT, self.request(cast_to, opts, stream=stream, stream_cls=stream_cls))
                           ~~~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\installs\9249f73cb9b5d199\environments\119c16e0d0e645c380dd1c18b63e20fa\venv\Lib\site-packages\openai\_base_client.py", line 1148, in request
    raise self._make_status_error_from_response(err.response) from None
openai.RateLimitError: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:04,472 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] run_agent: OpenAI client closed (stream_error_cleanup, shared=False, tcp_force_closed=0) thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:55:04,481 WARNING [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: API call failed (attempt 1/8, not retryable) error_type=RateLimitError thread=ThreadPoolExecutor-4393_0:17096 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud summary=HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 20:55:04,483 INFO [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] root: Rate-limit backoff level 2: cooldown 240 s (4.0 min, backoff#3, exponential fallback)
2026-11-10 20:55:04,631 ERROR [local-c9702df85bd8fe41d88cc26e7e1b2bdaa5c149244655b78780edd09b97c092b7] agent.conversation_loop: Non-retryable client error: Error code: 429 - {'error': {'message': 'you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)', 'type': 'api_error', 'param': None, 'code': None}}
2026-11-10 20:55:05,172 ERROR cron.scheduler: Job 'inbox-modeller' failed: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2612, in run_job
    final_response = _final_response_from_result(result, job_id, job_name, AIAgent)
  File "E:\hermes\hermes-agent\cron\scheduler.py", line 2068, in _final_response_from_result
    raise RuntimeError(result.get("error") or final_response_text or "agent reported failure")
RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 20:55:05,219 INFO run_agent: OpenAI client closed (agent_close, shared=True, tcp_force_closed=0) thread=cron-cleanup-d77df4111e52:14752 provider=custom base_url=http://127.0.0.1:11434/v1/ model=kimi-k2.7-code:cloud
2026-11-10 20:55:05,228 ERROR gateway.session_authority: Admitted turn 3679c0893eb744b68fcf89ef6a763e01 failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 20:55:06,376 INFO tui_gateway.ws: ws closed peer=127.0.0.1:65449 reason=client_disconnect(code=1000,reason=) messages=8 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 21:01:06,843 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:55416
2026-11-10 21:01:12,249 INFO tools.registry: check_fn _browser_cdp_check returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,255 INFO tools.registry: check_fn _browser_dialog_check returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,671 INFO tools.registry: check_fn check_image_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,866 INFO tools.tool_search: tool_search activated (tier 1): 32 core/visible tools kept, 7 deferred (~5725 tokens), listing full (budget ~4000 tokens)
2026-11-10 21:01:12,874 INFO tools.registry: check_fn _a2a_tools_available returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,878 INFO tools.registry: check_fn check_discord_tool_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,881 INFO tools.registry: check_fn _check_feishu returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:12,906 INFO tools.registry: check_fn _check_yuanbao returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:13,026 INFO agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 21:01:13,028 INFO tools.registry: check_fn check_video_generation_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:13,030 INFO tools.registry: check_fn _check_xai_video_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 21:01:13,094 INFO agent.credential_pool: credential pool: no available entries (all exhausted or empty)
2026-11-10 21:01:13,095 INFO tools.registry: check_fn check_x_search_requirements returned False; dependent tools will be unavailable this turn
2026-11-10 21:04:49,882 WARNING gateway.session_managed_worker: Managed worker lost: managed_worker_hello_timeout
2026-11-10 21:04:55,581 ERROR gateway.session_authority: Admitted turn 2810f8b64be44d1c9d595cbe75ff955a failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 292, in execute_admission
    return await admit_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 628, in admit_managed
    return await execute_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 660, in execute_managed
    hello = await worker.next_frame(HELLO_SECONDS, ack=0)
            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 268, in next_frame
    raise RuntimeStoreError('managed_worker_hello_timeout')
hermes_state_runtime.RuntimeStoreError: managed_worker_hello_timeout
2026-11-10 21:04:56,016 INFO tui_gateway.ws: ws closed peer=127.0.0.1:55416 reason=client_disconnect(code=1005,reason=) messages=53 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
2026-11-10 21:04:58,695 INFO tui_gateway.ws: ws accepted peer=127.0.0.1:55059
2026-11-10 21:04:58,877 INFO tui_gateway.ws: ws closed peer=127.0.0.1:55059 reason=client_disconnect(code=1000,reason=) messages=1 parse_errors=0 dispatch_crashes=0 send_failures=0 reaped_sessions=0 detached_sessions=0
```

## gateway.log: 8:45 to 9:05 PM (49 lines)

```text
2026-11-10 20:48:53,705 ERROR gateway.session_authority: Admitted turn f6e40bfd224d49d2a427c3902d0570fb failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: f00ee4d0-b6f2-4940-b5b6-44086896fb9e)
2026-11-10 20:55:05,228 ERROR gateway.session_authority: Admitted turn 3679c0893eb744b68fcf89ef6a763e01 failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 287, in execute_admission
    return await execute(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_cron.py", line 315, in execute
    raise RuntimeError(result[3] or 'cron execution failed')
RuntimeError: RuntimeError: HTTP 429: you (toopoint09) have reached your monthly usage limit, upgrade for higher limits: https://ollama.com/upgrade or add usage credits: https://ollama.com/settings (ref: 04a3637a-cb7b-4a1a-818c-2a64ab728974)
2026-11-10 21:04:49,882 WARNING gateway.session_managed_worker: Managed worker lost: managed_worker_hello_timeout
2026-11-10 21:04:55,581 ERROR gateway.session_authority: Admitted turn 2810f8b64be44d1c9d595cbe75ff955a failed
Traceback (most recent call last):
  File "E:\hermes\hermes-agent\gateway\session_authority.py", line 1286, in _execute_claimed
    return await execute_finite_admission(self, ref, row), 'completed'
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_finite.py", line 81, in execute_finite_admission
    return await execute_admission(authority, ref, row)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_ingress.py", line 292, in execute_admission
    return await admit_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 628, in admit_managed
    return await execute_managed(authority, ref, row, policy)
           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 660, in execute_managed
    hello = await worker.next_frame(HELLO_SECONDS, ack=0)
            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "E:\hermes\hermes-agent\gateway\session_managed_worker.py", line 268, in next_frame
    raise RuntimeStoreError('managed_worker_hello_timeout')
hermes_state_runtime.RuntimeStoreError: managed_worker_hello_timeout
```
