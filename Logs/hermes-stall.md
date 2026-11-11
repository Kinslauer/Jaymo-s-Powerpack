# Why Hermes stalled (PC Claude, Nov 11, 12:30-12:50 PM)

Answers "Check why Hermes stalls.md". Each step was checked by a second, independent agent; corrections are folded in.
No keys printed or written.

## Short answer

The PC and Hermes never went down. Hermes looked stalled for two reasons:
- **Most bots couldn't get a model.** From 5:48 AM nothing on the PC got far enough to claim a task or sync the vault:
  - **hermes-modeller** failed every run (Ollama 429 "monthly usage limit" on all 3 of its models);
  - **Quill** got 403 "model alias is not granted";
  - **Apollo (hermes-a)** got empty replies at 7:19-7:23 AM.
- **Hephaestus works but can't finish.** It runs fine on NVIDIA, but the terminal's 180 s limit on each command kills
  its Gradle builds. It also never pushed its belly-flop commits (they're on a detached HEAD) and still set the task
  to in-review, saying "All changes are pushed".

## 1. Is the PC and Hermes up?

- **Up the whole time.** No sleep, hibernate or reboot since 4:30 AM. System log power events: none. `powercfg /lastwake`: 0.
  Last boot Nov 9, 3:24 PM.
- **Idle, not asleep, 6:20-8:49 AM.** The PC was idle (no System events), and Hermes kept running through it.
- **Gateway:** PID 16940, running since 11:31 PM Nov 10. The Hermes_Gateway task is Ready (last run 12:39 AM, result 0).
- **Watchdog:** runs every minute. Its log has had no new lines since my 12:39 AM fix; healthy checks don't log.
- **dispatch-bots:** it fired every 5 minutes all morning (last run 12:28 PM, ok). Cron lines per hour in agent.log stay
  steady from 5 AM to 11 AM.
- **What the dispatches did after 5:48 AM:**
  - **modeller:** fired every 6 min and failed all 75 runs.
  - **Quill:** failed at 5:46 AM (403).
  - **Hephaestus:** its last run was 5:22-5:42 AM; it next fired at 12:28 PM.
  - **hermes-a / hermes-b / claude-writer:** no dispatch runs after 8:55 PM Nov 10.
  - **Gateway's own kanban dispatcher:** it reaped 3 zombie powerpack workers at 7:20-7:24 AM. These were Apollo's
    empty-reply runs.

## 2. Why vault sync stopped

- **No error:** `python tools/vault_sync.py` and `--status` both run without errors, and Hermes's path
  (`E:/hermes/powerpack/vault.py`) works too.
- **Nothing scheduled runs it.** On the PC, vault_sync only runs from Claude Code hooks (session start, after a
  Write/Edit) or when a bot run gets far enough to claim or update a task.
- **The gaps:** after 5:48 AM every bot run died on its model first, and no Claude session was editing. So there was
  no PC fetch or push from 5:48:22 AM to 12:27:49 PM, per the reflog in E:/Jaymo Vault Live. The same happened from
  12:50 AM to 5:20 AM.
- **Back now:** syncing resumed at 12:27 PM. Nothing to fix in the sync itself.
- **Consequence:** while the PC's copy goes stale, its claims look dead and cloud VMs hand them back. PR from
  `claude/project-thread-qp0izy` covers that.

## 3. Hephaestus (hermes-fixer) on #1263 belly flops

**Its runs.** 6 runs from 9:00 PM to 6:00 AM, all on its primary model, NVIDIA
`nvidia/nemotron-3.5-lightning-30b-a3b`. No fallback and no 429: the Ollama limit never touched this profile. Every run
ended "completed successfully". The errors were all inside runs:
- 180 s command timeouts (6 Gradle builds killed, 7 terminal timeouts in all);
- bad gradle flags;
- a blocked execute_code.

**The 5:22-5:42 AM run** (session local-a528c216 in hermes-fixer state.db):
- **Commits went nowhere.** It made 3 commits on a **detached HEAD** in `E:/wt/water-belly-flop-damage` (b63ffdfc9,
  34fa1fae1, 92b6058b5), and no branch contains them.
- **Conflict markers committed.** 34fa1fae1 has 5 conflict markers (`>>>>>>>`) committed in Dives.java.
- **Builds:** `./gradlew` was killed at 180 s once (5:31 AM), failed twice on bad flags, and failed once on those
  conflict markers. The last build, at 5:41:20 AM, passed.
- **The push didn't carry the fixes.** `git push origin claude/water-belly-flop-damage` at 5:27:55 AM pushed the old
  branch ref, not its commits. It moved GitHub from ff167835b to f566a84ff, which is only the 9:34 PM master merge.
  The later pushes said "Everything up-to-date".
- **pr_checks ignored.** `tools/pr_checks.py` failed twice, and the model called it "pre-existing".
- **False report.** At 5:41:46 AM it set the task to in-review and said "All changes are pushed". That's false: GitHub
  is still at f566a84ff with no review fixes. This is the 3rd time, after 10:14 PM and 11:20 PM.
- **Why:** NVIDIA's lightning model loses track of git state, and the 180 s cap keeps killing builds. It doesn't look
  like a crash.

## 4. [SILENT] runs and the cap

5 runs ended [SILENT] since 9 PM, all before 9:30 PM. **The cap caused none of them:**
- **Quill (9:11, 9:16, 9:22, 9:28 PM): wasted fires.** The dispatcher counts any open `for: quill` task as Quill's
  work. Quill's job only claims text passes (`--writes` → `claim_text_pass()`, which takes in-review tasks or its own
  unfinished "writing" one). So it printed "No task waiting".
- **Hephaestus at 9:29:50 PM did real work.** It fixed #1262 and set it in-review at 9:29:15 PM, then answered
  [SILENT], which hid it.
- **No case of a dead run holding the cap.** No "held back" lines in the 50 dispatcher outputs that survive (7:34 AM
  on). Earlier output is gone, so I can't rule it out before that.
- **Each fire started one run.** The "Job is already being fired" lines in dispatch-hermes-fixer.log are a duplicated
  print inside one fire block, not a double run. cron/executions.db shows one run per fire, each after the last ended.
- **What the cap counts:** at most 2 counted bots, and never two local builders at once (dispatch_bots.py lines 47-62
  and 127-142). The gateway's "reaped zombie worker" lines come from its own kanban dispatcher, not these runs.

## 5. Run time limits

- **No run-level limit near 30 min.** Nothing kills a whole run: one fixer run went 44 min and finished.
  dispatch_bots.py only has a 60 s timeout on its own `hermes cron` call. max_turns is 500, and gateway_timeout is
  commented out.
- **The real limit is `terminal.timeout: 180`** in hermes-fixer, hermes-a and hermes-b config.yaml (line 446) and in
  the default config (line 313). Every Gradle build longer than 3 min is killed mid-compile: 6 times since 9 PM, and
  5 more terminal timeouts earlier on Nov 10. The bot then carries on without a passing build.
- **Apollo (hermes-a):** its runs at 7:19, 7:21 and 7:23 AM ended with `empty_response_exhausted` (the model kept
  returning empty replies), not a timeout.
- **Bacchus (hermes-b):** no runs after 11:31 PM Nov 10.

## 6. Fallbacks (default profile), tested about 12:39-12:45 PM

| # | Model | Now |
|---|---|---|
| primary | NVIDIA | 200 OK |
| 1 | NVIDIA `nemotron-3.5-lightning-30b-a3b` | 200 OK |
| 2 | Gemini `gemini-flash-latest` | **503 "currently experiencing high demand"** (Google overload, may pass) |
| 3 | Groq `openai/gpt-oss-120b` | 200 OK |
| 4-6 | Ollama `deepseek-v4.1-flash`, `glm-5.3`, `kimi-k2.7-code` (cloud) | **429 monthly usage limit** |

The default profile (YOUTH) ran on NVIDIA all day: 257 calls, no 429s and no fallbacks.

## Changed (Andy asked, 12:31 PM: "make those changes to the models")

- **hermes-modeller fallbacks:** now NVIDIA `nemotron-3.5-super-vl-120b-a12b` (reads images), then Gemini
  `gemini-flash-latest`, then Groq `openai/gpt-oss-120b`, then the two Ollama models. Before, it only had Ollama
  models, so it failed all 120 runs on Nov 11. `hermes -p hermes-modeller fallback list` shows the 5-entry chain.
- **Keys:** I copied GEMINI_API_KEY and GROQ_API_KEY into `profiles/hermes-modeller/.env` from `E:/hermes/.env`
  without printing them.
- **Backups:** `E:/hermes/backups/hermes-modeller.{config.yaml,env}.2026-11-11.bak`.
- **Nothing else changed.**

## For Andy to decide

1. **Quill's 403** "model alias is not granted": it needs a model it's allowed to use, or the same free fallbacks
   modeller now has.
2. **Hephaestus's build timeout:** raise `terminal.timeout` for hermes-fixer (and hermes-a/b) from 180 s to about
   600 s so Gradle builds can finish.
3. **Hephaestus's checks:** before it sets in-review, check that `git log origin/<branch>` contains its commit, and
   refuse in-review while `pr_checks.py` fails. #1263 needs its fixes redone or rescued from 92b6058b5. That commit
   has conflict markers in its history; its final tree builds.
4. **Apollo's empty replies:** look at hermes-a's model (empty_response_exhausted).
5. **Quill's wasted fires:** stop dispatching Quill for `for: quill` tasks it can't claim with `--writes`.
