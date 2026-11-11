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
