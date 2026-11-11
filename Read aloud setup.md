# Read aloud setup (for Claude Code on Andy's PC)

Andy asked YOUTH to join a voice channel and read Lore.md from "The ages of the world", reading like to a person and
skipping dates and footnotes. YOUTH tried it with his text_to_speech tool, timed out after 9 minutes, and was stopped.
The tool for it is `tools/discord_read_aloud.py` (PR from branch `claude/project-thread-iwe61z`). Once Andy says merge
and it's on master in E:/Origins Mod, do these steps:

1. Install: `py -3 -m pip install "discord.py[voice]>=2.7" edge-tts` with the Python YOUTH's terminal uses
   (discord.py 2.7's voice extra brings PyNaCl and davey, which Discord's voice now needs). Check `ffmpeg -version`
   works in that shell; if not, `winget install ffmpeg` (or set FFMPEG to ffmpeg.exe's full path).
2. Env vars for YOUTH's gateway process (user env vars, then restart with `schtasks /run /tn Hermes_Gateway`):
   KANBAN_BOT_TOKEN is already YOUTH's token. READ_ALOUD_FOLLOW = Andy's Discord user id (or leave it and it uses
   KANBAN_MOVERS). Optional READ_ALOUD_CHANNEL (a default voice channel), READ_ALOUD_VOICE (default en-US-GuyNeural).
   Never print the token.
3. Test without Discord: `py -3 tools/discord_read_aloud.py Lore --section="The ages of the world" --only --dry-run`.
4. Teach YOUTH (his SOUL rules / a skill in his Hermes profile under E:/hermes): when asked to read a document aloud
   in voice, don't use text_to_speech; run in the background from E:/Origins Mod:
       py -3 tools/discord_read_aloud.py "<doc name>" [--section="<heading>"] [--only] [--channel=<voice channel>]
   It joins the voice channel the asker is in (or --channel), skips dates, footnotes and (new) marks, and prints
   "paragraph N of M". Reply once with what he's reading and where. To stop: `py -3 tools/discord_read_aloud.py --stop`.
   To carry on later: same command plus `--from=N`. Not sure which doc: `--list <part of name>`.
5. The bot needs Connect and Speak in the voice channel. Then have Andy join voice and ask YOUTH to read Lore from
   "The ages of the world". Write what happened at the end of this note and run tools/vault_sync.py.

## What happened (Claude Code, 2026-11-10, 11:35 PM)

- Master pulled in E:/Origins Mod (d469200be, has PR #1273 and tools/discord_read_aloud.py).
- Step 1 done: for `py -3` (Python 3.12) discord.py 2.7.1 with the voice extra (PyNaCl 1.5.0, davey 0.2.0) and
  edge-tts 7.3.1. ffmpeg 9.0.2 from `winget install Gyan.FFmpeg`. A short edge-tts sample generated fine.
- Step 2 done: user env vars READ_ALOUD_FOLLOW = 127224948107378688 (Andy, same id as KANBAN_MOVERS) and FFMPEG =
  the WinGet ffmpeg.exe. KANBAN_BOT_TOKEN was already set and was not read or printed. READ_ALOUD_CHANNEL and
  READ_ALOUD_VOICE are left unset (the tool follows Andy and uses en-US-GuyNeural).
- Step 3 done: the dry run found Lore.md and listed 9 paragraphs for "The ages of the world", with no dates,
  footnotes or (new) marks left.
- Step 4 done: a "Reading aloud in voice" section in E:/hermes/SOUL.md, and a new skill
  E:/hermes/skills/media/discord-read-aloud/SKILL.md (`hermes skills list` shows it enabled).
- Gateway restarted at 11:31 PM, after Andy paused Hephaestus. Now PID 16940, Discord connected as YOUTH.
  - `hermes gateway restart` first finished an earlier, unfinished Hermes update. It is still v0.23.0, and that
    update also synced the built-in skills and re-registered the Hermes_Gateway task.
  - The restart's direct spawn didn't get the new env vars, so the gateway was stopped once more and started with
    `schtasks /run /tn Hermes_Gateway` to pick them up.
  - Two restarts cut off a claude-writer kanban worker (t_3e3fded7, waiting on a retry) and two idle sessions.
- Step 5 still to do: Andy joins voice and asks YOUTH to read Lore from "The ages of the world". Check that the bot
  has Connect and Speak in that channel. If it says "ffmpeg isn't on PATH", the gateway didn't get FFMPEG.

## 2026-11-10 11:45 PM: YOUTH says the script doesn't exist
YOUTH looked for `E:/Origins Mod/tools/discord_read_aloud.py` and didn't find it. It is on master (merged in #1273).
For Claude Code on the PC:
1. In E:/Origins Mod: `git status` and `git branch --show-current`. If it's on master, `git pull origin master`. If it's
   on another branch with work in it, don't switch: `git fetch origin master` then
   `git checkout origin/master -- tools/discord_read_aloud.py` (a tools-only file, safe to bring in alone).
2. Check `Test-Path "E:/Origins Mod/tools/discord_read_aloud.py"` is True, then run the dry run from step 3 above.
3. Tell YOUTH (and put it in his read-aloud skill): if the script is missing, run `git -C "E:/Origins Mod" pull origin
   master` once and try again. Never write the script himself and never fall back to text_to_speech.

### Done (Claude Code, 2026-11-10, 11:50 PM)
- Why it was missing: at 11:00 PM something switched E:/Origins Mod from master to `claude/project-thread-1ly3a2`
  (PR #1274, already merged; its one commit was pushed). That branch is older than #1273, so the script wasn't
  there.
- That branch had no unpushed or unmerged work, so the checkout went back to master and pulled. It is now at
  43938b86c, and `Test-Path` is True. The two old local deletions ("Build Modpack.bat", the kitchen jar) and the
  untracked deck files are untouched.
- Dry run OK: 9 paragraphs for "The ages of the world".
- The missing-script rule is in YOUTH's skill (discord-read-aloud, "If it fails") and in his SOUL "Reading aloud"
  section.
- Still to do: Andy asks YOUTH again in voice. Whatever switched the main checkout to a PR branch may do it again.
  If it does, YOUTH's pull goes to that branch and won't bring the script.

### What switched E:/Origins Mod off master (found 2026-11-11, 12:10 AM)
- At 11:04 PM Andy opened PR #1274's cloud session (`claude/project-thread-1ly3a2`) from the Claude desktop app. To
  open it on the PC, the app ran `git fetch origin claude/project-thread-1ly3a2` (11:04:36 PM) and then
  `git checkout claude/project-thread-1ly3a2` (11:04:37 PM) in the main checkout. Andy confirmed it.
- It wasn't a bot. No Hermes session ran a checkout then, and powerpack_guard blocks them from doing it in
  E:/Origins Mod. (The "11:00 PM" above was the commit's date, not the time of the switch.)
- To avoid it: open cloud sessions in a worktree (E:\Origins Mod\.claude\worktrees\...), not in the main checkout.
  When done with one that did switch it: `git -C "E:/Origins Mod" checkout master`, then pull.

### A script is missing from E:/Origins Mod: check the branch first
For YOUTH, Claude Code or anyone who finds a tool from master missing (like tools/discord_read_aloud.py):
1. `git -C "E:/Origins Mod" branch --show-current`. If it isn't `master`, someone opened a session on a PR branch.
   Pulling won't help: it updates that branch, not master.
2. YOUTH: tell Andy which branch E:/Origins Mod is on and that the script is missing, and stop there. Don't switch
   branches, don't write the script, don't use text_to_speech.
3. Claude Code on the PC: check the branch has nothing unpushed (`git log origin/<branch>..HEAD` is empty) and
   no uncommitted changes besides the old "Build Modpack.bat" / kitchen-jar deletions. Then
   `git checkout master` and `git pull --ff-only origin master`.
- Watch out: powerpack_guard blocks every mutating git command in E:/Origins Mod for the Hermes bots and YOUTH,
  `pull` included. So the "pull master once" rule in YOUTH's skill and SOUL couldn't work as written.
- Changed (Andy, 2026-11-11): YOUTH no longer pulls. If the script is missing he checks the branch, tells Andy, and
  stops (SOUL "Reading aloud" section and skill discord-read-aloud, "If it fails"). The guard is unchanged.

## 2026-11-10 11:55 PM: YOUTH's Python is missing the packages, and no ffmpeg
YOUTH found the script but said discord.py and edge-tts aren't installed "in the Python available here" and ffmpeg
isn't on PATH. Step 1 installed them into a different Python than the one YOUTH's terminal tool runs. The bot sitting
deafened in General was not the reader (it never started); the reader no longer self-deafens anyway.
For Claude Code on the PC (the follow-up PR #1278, which adds --check, is merged; pull master first):
1. Ask YOUTH to run `py -3 tools/discord_read_aloud.py --check` from E:/Origins Mod, the same way he runs the reader,
   and paste the output. Its first line names the interpreter he really uses, and it prints the exact pip command for
   that interpreter. Wheels exist for Python 3.14 on Windows (PyNaCl 1.5 abi3, davey 0.2 cp314).
2. Run that pip command yourself (YOUTH's skill says not to install things; keep it that way).
3. ffmpeg: `winget install ffmpeg` if missing, then set a user env var FFMPEG to ffmpeg.exe's full path and restart
   the gateway with `schtasks /run /tn Hermes_Gateway` (a running gateway doesn't see new PATH entries).
4. Have YOUTH run --check again until it says Ready, then the reading. Append the result here and run vault_sync.

### Correction (12:05 AM, cloud thread)
The 11:55 PM section above was wrong about the cause: the reading had already worked with Python 3.12 (py -3); only
YOUTH's own check used a different Python. Andy chose to point YOUTH at Python 3.12 explicitly; don't install anything
into Hermes' Python 3.14.

### Done (Claude Code, 2026-11-11, 12:15 AM)
- E:/Origins Mod is on master at f9bcac27f, which has #1278 (--check, no self-deafen).
- What YOUTH's own history shows: at 11:53 PM he ran the reader with Python 3.12 and it read all 9 paragraphs of "The
  ages of the world" in Hotspot > General, finishing at 11:57 PM. His 11:55 PM check used `python3`, which is Hermes'
  Python 3.14 and doesn't have the packages. That check also found ffmpeg, so "no ffmpeg" was wrong too.
- `--check` with Python 3.12 (FFMPEG set): packages found, ffmpeg found, token set, follows 1 id, "Ready."
  With Hermes' 3.14 it reports the packages missing. Nothing was installed there.
- YOUTH's SOUL "Reading aloud" section and skill discord-read-aloud now use the full path
  C:/Users/toopo/AppData/Local/Programs/Python/Python312/python.exe for every use of the script (reading, --check,
  --stop, --list, --dry-run). They never use python3/python/py, and say not to test with `python3 -c "import discord"`.
  The skill also lists `--check` and says to report its result, not install things.
- Still to do: Andy asks YOUTH to read again. The bot should now join undeafened.

## 2026-11-11 12:15 AM: it plays, the ring is green, Andy hears nothing
Not muted, user volume fine, all permissions. Most likely Discord's E2EE voice (DAVE) dropping frames. For Claude Code on
the PC, after Andy merges PR #1279 (adds --debug) and you pull master into E:/Origins Mod:
1. `--stop` any reading still running. Check nothing else holds YOUTH's voice: the Hermes gateway may have its own
   Discord voice feature (YOUTH sat in General before the script ever ran, at 11:47 PM). If it does, turn that off,
   or two connections fight over one bot.
2. With Andy in General, run it yourself with the Python 3.12 that worked:
   `py -3 tools/discord_read_aloud.py Lore --section="The ages of the world" --only --debug 2> read_debug.log`
   Ask Andy if he hears it.
3. `py -3 -m pip show discord.py davey PyNaCl` and note the versions. Upgrade discord.py[voice] and davey to the
   newest if they aren't already, and try again.
4. Copy the stdout lines (dave_version / ready / can_encrypt) and the last ~80 lines of read_debug.log (no token is
   logged, but check) into a new section here and run vault_sync.

## 2026-11-11 12:31 AM: cause found and fixed (cloud thread)
The silence was two readings at once (YOUTH's background full-Lore read, process 21480, plus the next run) fighting
over the bot's one voice connection (4006 drops, DAVE never ready). PR #1280 (merged): a new reading now stops the one
still running before it joins. Pull master into E:/Origins Mod. Still open: the gateway watchdog logging "restarted
the gateway" every minute since 11:29 PM while the gateway was up (may start extra YOUTH logins).

## 2026-11-11 5:25 AM: why nobody hears it (cloud thread)
Andy still hears nothing. The cause: davey 0.2.0 (what discord.py[voice] installs now) needs prepare_transition /
execute_transition calls that discord.py 2.7.1 never makes, so Discord's voice encryption never gets ready
(ready=False in the debug log) and every packet is dropped. Fix, for Claude Code on the PC:
1. With the Python YOUTH uses (3.12): `py -3 -m pip install "davey==0.1.7"`. That's the whole fix; PR #1283 only
   makes --check catch it.
2. `py -3 tools/discord_read_aloud.py --status` and --stop anything running, then with Andy in General:
   `py -3 tools/discord_read_aloud.py Lore --section="The ages of the world" --only --debug 2> read_debug.log`
   Andy listens. Write here whether he heard it and whether `ready` turned True.

**Done (Claude Code on the PC, 5:21-5:25 AM): fixed. Andy heard it clearly in General.**
- **Cause confirmed:** discord.py 2.7.1 was installed with davey 0.2.0. davey 0.2.0's `DaveSession` has
  `prepare_transition` / `execute_transition`, and discord.py's gateway.py never calls them on the session.
- **Fix:** `py -3 -m pip install "davey==0.1.7"` (Python 3.12.10). It uninstalled 0.2.0, and `pip check` shows no
  conflicts.
- **Before the read:** `--status` (from #1281, now on master) said "Nothing is being read", so there was nothing to stop.
- **The read:** "The ages of the world" (11 paragraphs) played in Hotspot > General from 5:21 to 5:25 AM, exit 0.
  `ready=True can_encrypt=True` from paragraph 1 on, and there were 0 voice drops (4006) in `read_debug.log`.
- **Still needed:** the pin isn't in any requirements file. A new `pip install "discord.py[voice]"` brings davey 0.2.0
  back. PR #1283 (`--check` catches it) is the guard.
