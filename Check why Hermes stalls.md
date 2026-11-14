# Check why Hermes stalls (for PC Claude, Nov 11)

Andy asked (Nov 11, 11:45 AM): "Whats going on with hermes? Why isnt it making progress?" A cloud thread checked
everything it can reach (vault-live history, PR branches). It needs the PC side. Do these in order, write what you
find to `Logs/hermes-stall.md`, then run `python tools/vault_sync.py`. Never print or write a key.

## What the cloud found

1. **The PC went quiet at 5:48 AM Nov 11.** The last vault-live commit from DESKTOP-49GFK3E is 5:48:21 AM (a
   Kanban board push at 5:48:22). Nothing since: no claims, no syncs, no Kanban. Every Hermes handback after that
   was done by cloud VMs sweeping stale claims.
2. **Hephaestus (hermes-fixer) never fixed #1263 (belly flops).** He claimed it at 9:34 PM, 10:52 PM and 5:23 AM.
   The only push to `claude/water-belly-flop-damage` after the 8:53 PM review is a master merge at 9:34 PM. At
   5:41 AM he set the task to in-review with no new commit (same thing happened before 12:14 AM). So he either can't
   do the edits or his runs die, and he marks it done anyway.
3. **#1262 (undead shelter)** got the reviewed fixes (947704b, 12:19 AM), then Andy's 7:39 AM ask ("keep searching
   till it finds shelter") arrived after the PC went quiet, so no bot could take it.
4. **Cloud VMs were handing back Hermes claims they can't judge.** The stale check counts the worktree in `E:/wt`
   as a sign of work, but a cloud VM has no `E:/wt`, so a bot building there for 30 min looked dead from the cloud.
   Fixed in the PR from branch `claude/project-thread-qp0izy` (cloud leaves Hermes claims to the PC).

## Steps

1. **Is the PC and Hermes up?** `schtasks /query /tn Hermes_Gateway /v /fo list` (status, last run, last result),
   the last 30 lines of the gateway watchdog log, and whether `dispatch-bots` is still firing
   (`hermes cron list` or the default profile's cron state). If the PC slept or rebooted at about 5:48 AM, say so.
2. **Why vault sync stopped.** Run `python tools/vault_sync.py --status` in `E:/Origins Mod`. If it errors (merge
   conflict, auth, lock file), paste the error (no tokens) and fix it.
3. **Hephaestus's runs on belly flops.** For the hermes-fixer job, list its runs from 9:00 PM Nov 10 to 6:00 AM
   Nov 11: start/end time, which model served each (primary or which fallback), last status, error text in a few
   words (e.g. "Ollama 429", "credit", "timeout"). Look at the 5:23 to 5:41 AM run: why did it set in-review with
   nothing pushed? Did it run `./gradlew` in `E:/wt/water-belly-flop-damage`, did the build fail, did it push?
4. **[SILENT] runs.** In `E:/hermes/scripts/dispatch_bots.py`, check the cap logic: how many runs since 9 PM ended
   [SILENT], and was it because the cap (2 counted bots, never two local builders) said full while a dead or stuck
   run still counted? Say which bots it counted.
5. **Run time limit.** What is the per-run timeout for hermes-fixer and hermes-a/b? If runs are killed at 30 min
   before a build and push finish, say so.
6. **Fallbacks.** `hermes fallback list` for the default profile: is every fallback reachable now (NVIDIA, Gemini,
   Groq; Ollama has its 429 monthly limit)? Name any that fail.

Don't change bot configs beyond fixing the sync (step 2); write findings and let Andy decide.
