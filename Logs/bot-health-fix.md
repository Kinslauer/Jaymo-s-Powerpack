# bot_health.py: "failing" check fixed (2026-11-10)

**Problem:** Prometheus (hermes-modeller) had failure_streak 75+ and last_status error, but Bots.md showed him as plain "💤 idle" and bot_health.state.json had `problems: {}`.

**Why:** The failing check only ran when the bot was `not running`, and "running" means any of the profile's state.db or logs files changed in the last 10 minutes. Prometheus's runs die with an Ollama 429 within seconds, and the dispatcher relaunches him every few minutes, so those files were never older than 10 minutes. He always looked "running", and the failing check was skipped every time.

**What changed in `E:/hermes/scripts/bot_health.py`:**
- Removed the `not running` guard from the failing check. A job with `failure_streak >= 2` is now ⚠️ failing whether or not a run is going. failure_streak only counts finished runs and resets on a success, so this is safe.
- Paused or disabled jobs are still checked first and show "⏸️ paused", never failing.
- Added `short_error()`: Details now shows a few words such as "Ollama 429 monthly limit" instead of the first 120 characters of the raw error, which held an account name, URLs and a request ref.
- Nothing else changed: the stalled and stopped checks, `bot_activity()` (also used by dispatch_bots.py) and all bot jobs and configs are as they were.

Backup of the old script: `E:/hermes/backups/bot_health.py.2026-11-10-pre-failing-fix.bak`

**Test (9:35 PM):** Bots.md now shows `| Prometheus (hermes-modeller) | ⚠️ failing | its last 77 runs failed (Ollama 429 monthly limit) |`. The other four bots are unchanged. With the job's `enabled` set to false in memory only, he shows "paused".
