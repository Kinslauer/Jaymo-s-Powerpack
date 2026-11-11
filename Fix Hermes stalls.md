# Fix Hermes stalls (for PC Claude, Nov 11)

Andy chose (Nov 11, 2:11 PM, "All four fixes") the fixes from `Logs/hermes-stall.md`. Back up each file you change to
`E:/hermes/backups/<name>.2026-11-11-stall.bak`. Never print or write a key. Write what you did under "Done" below,
then run `python tools/vault_sync.py`.

1. **Build limit 3 min to 10 min.** Set `terminal.timeout: 600` in the config.yaml of hermes-fixer, hermes-a and
   hermes-b (line ~446). Leave the default profile (YOUTH) alone. Check with one Gradle build in a worktree that
   takes over 3 min, or just confirm the value is read (`hermes -p hermes-fixer config get terminal.timeout` or
   similar).
2. **Push check before in-review.** In `E:/hermes/powerpack/vault.py`, where a bot sets a task to `in-review`:
   refuse (print why, keep the claim) unless, in the task's worktree `E:/wt/<task>`,
   - `git branch -r --contains HEAD` lists `origin/<task's branch>` (so the commit really is on GitHub, not a
     detached HEAD), and
   - `python tools/pr_checks.py` exits 0, and
   - `git grep -nE '^(<<<<<<<|>>>>>>>)' -- src kingdoms kitchen` finds nothing.
   Test it on a throwaway task with a detached-HEAD commit (refused) and a pushed one (allowed).
3. **Rescue the belly-flop fixes (#1263).** In `E:/wt/water-belly-flop-damage`: `git checkout
   claude/water-belly-flop-damage`, `git pull`, then bring in 92b6058b5's final tree (e.g. `git merge 92b6058b5` or
   `git checkout 92b6058b5 -- <changed files>` and commit). No conflict markers anywhere. Run `./gradlew build` and
   `python tools/pr_checks.py`; both must pass. Check it against the 01:53 review list in
   `Inbox/water-belly-flop-damage.md` (5 points) and say which are done. Push, confirm GitHub's head moved, then
   `vault_sync.py --set water-belly-flop-damage status=in-review` and `--note` what's in it. If a point isn't done,
   leave it as `changes` with a note saying which.
4. **Quill's model.** Quill gets 403 "model alias is not granted". Give the quill profile the same free fallback chain
   hermes-modeller now has (NVIDIA Super, Gemini Flash, Groq gpt-oss-120b), copying keys from `E:/hermes/.env`
   into its `.env` without printing them. Keep its primary if it can be fixed; otherwise say what the 403 needs.
   Test with one short `hermes -p quill chat -q "say ok" --oneshot -Q`.

## Done
