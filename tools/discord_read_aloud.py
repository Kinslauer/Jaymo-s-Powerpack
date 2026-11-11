"""Read a document out loud in a Discord voice channel: one command, so YOUTH doesn't have to work it out each time.

    python tools/discord_read_aloud.py "Lore"                  # find Lore.md in the vault, join, read it
    python tools/discord_read_aloud.py "Lore" --from=12        # start at paragraph 12 (after a stop)
    python tools/discord_read_aloud.py "Lore" --section="The ages of the world"   # start at that heading
    python tools/discord_read_aloud.py "Lore" --section="The ages of the world" --only   # just that section
    python tools/discord_read_aloud.py "Lore" --channel=General  # a voice channel by name or id
    python tools/discord_read_aloud.py --list lore             # which documents match, without joining
    python tools/discord_read_aloud.py "Lore" --dry-run        # print what would be read, no Discord
    python tools/discord_read_aloud.py --stop                  # stop every reading that's running and leave
    python tools/discord_read_aloud.py --status                # is anything being read? (finds every running reading)
    python tools/discord_read_aloud.py "Lore" --debug          # also log the voice connection (no sound? run this)
    python tools/discord_read_aloud.py --check                 # is everything installed for THIS Python? (no Discord)

Setup on the PC (once):  pip install "discord.py[voice]>=2.7" "davey==0.1.7" edge-tts   and have ffmpeg on PATH (or set FFMPEG).

Finding the document: a path, or a name looked up in the live vault (docs/vault/Live), then docs/vault, then docs/.
An exact file name wins, then a name that starts with it, then one that contains it. More than one equal match: it lists
them and stops, so the caller picks.

Finding the channel: --channel (id or name), else READ_ALOUD_CHANNEL, else the voice channel the first person in
READ_ALOUD_FOLLOW (or KANBAN_MOVERS) is sitting in right now. So when Andy is in voice, plain `"Lore"` is enough.

What gets read: the text without frontmatter, code blocks, tables, links' targets or markdown marks; headings are read
as their own line. Read the way you'd read to a person: dates and who-said-it notes in brackets ("(Andy, 2026-11-11)"),
bare dates, footnotes ([^1] and their lines) and the lore's "(new)" marks are left out (--keep-notes keeps them). Each paragraph is turned into speech while the one before it plays, so there's no long wait to start.
It prints "paragraph N of M" as it goes; if it's stopped, --from=N carries on from there. It leaves the channel when it
finishes, when it's stopped (--stop, Ctrl+C) or when everyone else has left.

Settings (environment variables):
  KANBAN_BOT_TOKEN / DISCORD_BOT_TOKEN   the bot's token (YOUTH's). Never print it.
  READ_ALOUD_CHANNEL   default voice channel (id or name)
  READ_ALOUD_FOLLOW    Discord user ids whose voice channel to join, comma-separated (falls back to KANBAN_MOVERS)
  READ_ALOUD_VOICE     edge-tts voice (default en-US-GuyNeural; `edge-tts --list-voices` lists them)
  READ_ALOUD_RATE      speed, e.g. +10% or -15% (default +0%)
  FFMPEG               path to ffmpeg.exe when it isn't on PATH

The speech comes from edge-tts (Microsoft's online voices, no key). If it can't be reached, the Windows voice
(pyttsx3, offline) is used instead when it's installed. The bot needs Connect and Speak in the voice channel.
"""
import argparse
import asyncio
import os
import re
import shutil
import signal
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PIDFILE = os.path.join(os.path.expanduser("~"), ".discord_read_aloud.pid")  # the newest reading
PIDDIR = os.path.join(os.path.expanduser("~"), ".discord_read_aloud")  # one file per running reading
DOC_EXTS = (".md", ".txt")
CHUNK_CHARS = 900  # longest piece sent to the speech engine at once; paragraphs longer than this are split at sentences
DEFAULT_VOICE = "en-US-GuyNeural"


def clock(t=None):
    """12-hour clock, as Andy wants every time written: 3:05 PM."""
    return time.strftime("%I:%M %p", time.localtime(t)).lstrip("0")


# ---------------------------------------------------------------- finding the document

def search_roots():
    roots = []
    try:
        sys.path.insert(0, HERE)
        import vault_sync  # the live vault can live outside this checkout (main_checkout())
        roots.append(vault_sync.live_dir())
    except Exception:
        pass
    roots += [os.path.join(ROOT, "docs", "vault", "Live"), os.path.join(ROOT, "docs", "vault"), os.path.join(ROOT, "docs")]
    seen, out = set(), []
    for r in roots:
        r = os.path.normcase(os.path.abspath(r))
        if r not in seen and os.path.isdir(r):
            seen.add(r)
            out.append(r)
    return out


def all_docs():
    seen, docs = set(), []
    for root in search_roots():
        for dirpath, dirnames, files in os.walk(root):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            for f in files:
                if f.lower().endswith(DOC_EXTS):
                    p = os.path.normcase(os.path.abspath(os.path.join(dirpath, f)))
                    if p not in seen:
                        seen.add(p)
                        docs.append(os.path.join(dirpath, f))
    return docs


def find_docs(query):
    """Best matches for a name: exact name, then starts-with, then contains (in the name, then in the path)."""
    if os.path.isfile(query):
        return [query]
    q = os.path.splitext(query.replace("\\", "/").strip().lower())[0]
    docs = all_docs()
    ranks = []
    for d in docs:
        name = os.path.splitext(os.path.basename(d))[0].lower()
        rel = os.path.splitext(d.replace("\\", "/").lower())[0]
        if name == q or rel.endswith("/" + q):
            ranks.append((0, d))
        elif name.startswith(q):
            ranks.append((1, d))
        elif q in name:
            ranks.append((2, d))
        elif q in rel:
            ranks.append((3, d))
    if not ranks:
        return []
    best = min(r for r, _ in ranks)
    hits = [d for r, d in ranks if r == best]
    # the same note in the live vault and in a copy: keep the first root's (search_roots order)
    by_name = {}
    for d in hits:
        by_name.setdefault(os.path.basename(d).lower(), d)
    return sorted(by_name.values(), key=hits.index)


# ---------------------------------------------------------------- turning markdown into speakable text

def pick_section(text, name, only=False):
    """The text from the heading that matches `name` on (to the next heading as big or bigger with only=True)."""
    lines = text.replace("\r\n", "\n").split("\n")
    want = name.strip().lstrip("#").strip().lower()
    heads = [(i, len(m.group(1)), m.group(2).strip().lower()) for i, l in enumerate(lines)
             for m in [re.match(r"^(#{1,6})\s+(.*)$", l.strip())] if m]
    hit = [h for h in heads if h[2] == want] or [h for h in heads if want in h[2]]
    if not hit:
        raise SystemExit(f"No heading matches {name!r}. Headings: " + "; ".join(h[2] for h in heads))
    i, level, _ = hit[0]
    end = len(lines)
    if only:
        end = next((j for j, lv, _ in heads if j > i and lv <= level), len(lines))
    return "\n".join(lines[i:end])


DATE = r"\d{4}-\d{2}-\d{2}"


def drop_notes(s):
    """Dates, who-said-it brackets, footnotes and (new) marks: things you skip when reading to a person."""
    s = re.sub(r"\[\^[^\]]*\]", "", s)  # footnote refs
    s = re.sub(r"\s*\([^()]*" + DATE + r"[^()]*\)", "", s)  # (Andy, 2026-11-11), (since 2026-11-09)
    s = re.sub(r"\(\s*new\s*\)\s*", "", s, flags=re.I)  # the lore's (new) marks
    s = re.sub(r"\s*(?:on|since|from|in|at)?\s*\b" + DATE + r"\b", "", s)  # bare dates
    s = re.sub(r"\s+([,.;:!?])", r"\1", s)
    return s


def speakable(text, keep_notes=False):
    """Markdown -> paragraphs a voice can read."""
    text = text.replace("\r\n", "\n")
    if not keep_notes:
        text = re.sub(r"(?m)^\[\^[^\]]*\]:.*(?:\n[ \t]+.*)*", "", text)  # footnote bodies
    if text.startswith("---\n"):  # frontmatter
        end = text.find("\n---", 4)
        if end != -1:
            text = text[end + 4:]
    text = re.sub(r"%%.*?%%", "", text, flags=re.S)  # Obsidian comments
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    paras, cur = [], []

    def flush():
        if cur:
            paras.append(" ".join(cur))
            cur.clear()

    for line in text.split("\n"):
        s = line.strip()
        if not s or re.fullmatch(r"[-*_=]{3,}", s):
            flush()
            continue
        if s.startswith("|"):  # tables don't read well
            flush()
            continue
        head = re.match(r"^#{1,6}\s+(.*)$", s)
        item = re.match(r"^(?:[-*+]|\d+[.)])\s+(?:\[[ xX]\]\s+)?(.*)$", s)
        if head:
            flush()
            paras.append(clean_inline(head.group(1)).rstrip(".") + ".")
        elif item:
            flush()
            cur.append(clean_inline(item.group(1)))
            flush()
        else:
            cur.append(clean_inline(s.lstrip("> ")))
    flush()
    if not keep_notes:
        paras = [drop_notes(p) for p in paras]
    return [p for p in (re.sub(r"\s+", " ", p).strip() for p in paras) if re.search(r"\w", p)]


def clean_inline(s):
    s = re.sub(r"!\[\[[^\]]*\]\]", "", s)  # embeds
    s = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", s)  # images
    s = re.sub(r"\[\[([^\]|]+)\|([^\]]+)\]\]", r"\2", s)  # [[target|shown]]
    s = re.sub(r"\[\[([^\]]+)\]\]", lambda m: m.group(1).split("#")[0].split("/")[-1], s)
    s = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", s)  # [shown](url)
    s = re.sub(r"https?://\S+", "", s)
    s = re.sub(r"`([^`]*)`", r"\1", s)
    s = re.sub(r"(\*\*|__|\*|_|~~|==)(?=\S)(.+?)(?<=\S)\1", r"\2", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"(?<!\w)#(\w[\w/-]*)", r"\1", s)  # tags
    return s.strip()


def chunks(paras):
    """Paragraphs, with any too long for one speech call split at sentence ends."""
    out = []
    for p in paras:
        while len(p) > CHUNK_CHARS:
            cut = max(p.rfind(". ", 0, CHUNK_CHARS), p.rfind("! ", 0, CHUNK_CHARS), p.rfind("? ", 0, CHUNK_CHARS))
            if cut < CHUNK_CHARS // 3:
                cut = p.rfind(" ", 0, CHUNK_CHARS)
            if cut <= 0:
                cut = CHUNK_CHARS
            out.append(p[:cut + 1].strip())
            p = p[cut + 1:].strip()
        if p:
            out.append(p)
    return out


# ---------------------------------------------------------------- speech

class Speech:
    def __init__(self, workdir):
        self.dir = workdir
        self.voice = os.environ.get("READ_ALOUD_VOICE") or DEFAULT_VOICE
        self.rate = os.environ.get("READ_ALOUD_RATE") or "+0%"
        self.n = 0
        self.use_edge = True

    async def make(self, text):
        """Speech for one piece of text -> an audio file path."""
        self.n += 1
        if self.use_edge:
            try:
                import edge_tts
                path = os.path.join(self.dir, f"{self.n:04d}.mp3")
                await edge_tts.Communicate(text, self.voice, rate=self.rate).save(path)
                if os.path.getsize(path) > 0:
                    return path
            except Exception as e:
                print(f"edge-tts failed ({e.__class__.__name__}: {e}); trying the offline Windows voice", file=sys.stderr)
                self.use_edge = False
        path = os.path.join(self.dir, f"{self.n:04d}.wav")
        await asyncio.get_running_loop().run_in_executor(None, sapi_save, text, path)
        return path


def sapi_save(text, path):
    try:
        import pyttsx3
    except ImportError:
        raise SystemExit("No speech engine: pip install edge-tts (or pyttsx3 for the offline Windows voice).")
    engine = pyttsx3.init()
    engine.save_to_file(text, path)
    engine.runAndWait()
    engine.stop()


# ---------------------------------------------------------------- Discord

def ffmpeg_path():
    exe = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if not exe:
        raise SystemExit("ffmpeg isn't on PATH: install it (winget install ffmpeg) or set FFMPEG to ffmpeg.exe's path.")
    return exe


def follow_ids():
    raw = os.environ.get("READ_ALOUD_FOLLOW") or os.environ.get("KANBAN_MOVERS") or ""
    return [int(x) for x in re.findall(r"\d{15,}", raw)]


def pick_channel(client, wanted):
    import discord
    voices = [c for g in client.guilds for c in g.channels if isinstance(c, (discord.VoiceChannel, discord.StageChannel))]
    if wanted:
        w = wanted.strip().lstrip("#").lower()
        for c in voices:
            if str(c.id) == w:
                return c
        named = [c for c in voices if c.name.lower() == w] or [c for c in voices if w in c.name.lower()]
        if len(named) == 1:
            return named[0]
        if named:
            raise SystemExit("More than one voice channel matches: " + ", ".join(f"{c.name} ({c.id})" for c in named))
        raise SystemExit(f"No voice channel called {wanted!r}. Voice channels: " + ", ".join(c.name for c in voices))
    for uid in follow_ids():
        for c in voices:
            if uid in c.voice_states:
                return c
    raise SystemExit("Nobody to follow is in a voice channel: pass --channel=<name or id> or set READ_ALOUD_CHANNEL.")


def voice_status(vc):
    """The voice link's encryption state, for --debug: Discord's E2EE (DAVE) drops audio it can't decrypt silently."""
    c = getattr(vc, "_connection", None)
    if c is None:
        return "no connection state"
    sess = getattr(c, "dave_session", None)
    return (f"dave_version={getattr(c, 'dave_protocol_version', '?')} session={'yes' if sess else 'no'} "
            f"ready={getattr(sess, 'ready', '-') if sess else '-'} can_encrypt={getattr(c, 'can_encrypt', '?')} "
            f"mode={getattr(vc, 'mode', '?')} playing={vc.is_playing()} connected={vc.is_connected()}")


async def read_in_channel(token, title, pieces, start, wanted, debug=False):
    import discord
    if debug:
        import logging
        logging.basicConfig(level=logging.INFO, stream=sys.stderr, format="%(asctime)s %(name)s %(message)s")
        for name in ("discord.voice_state", "discord.voice_client", "discord.gateway", "discord.player"):
            logging.getLogger(name).setLevel(logging.DEBUG)

    intents = discord.Intents.none()
    intents.guilds = True
    intents.voice_states = True
    client = discord.Client(intents=intents)
    exe = ffmpeg_path()
    stop = asyncio.Event()
    result = {"done": 0}

    @client.event
    async def on_ready():
        if result.get("started"):  # on_ready fires again after a reconnect; read only once
            return
        result["started"] = True
        try:
            channel = pick_channel(client, wanted)
        except SystemExit as e:
            print(e, file=sys.stderr)
            result["error"] = 2
            await client.close()
            return
        print(f"{clock()} joining {channel.guild.name} > {channel.name} to read {title} "
              f"(paragraphs {start + 1}-{len(pieces)})", flush=True)
        vc = await channel.connect()  # not self-deafened: a deafened icon made people think it couldn't speak
        if isinstance(channel, discord.StageChannel):
            try:
                await channel.guild.me.edit(suppress=False)  # speak on a stage
            except discord.HTTPException:
                pass
        workdir = tempfile.mkdtemp(prefix="read_aloud_")
        speech = Speech(workdir)
        try:
            nxt = asyncio.create_task(speech.make(pieces[start]))
            for i in range(start, len(pieces)):
                path = await nxt
                if i + 1 < len(pieces):
                    nxt = asyncio.create_task(speech.make(pieces[i + 1]))
                if stop.is_set() or not others_here(vc):
                    break
                print(f"{clock()} paragraph {i + 1} of {len(pieces)}", flush=True)
                if debug:
                    print(f"  file {os.path.basename(path)} {os.path.getsize(path)} bytes; {voice_status(vc)}", flush=True)
                finished = asyncio.Event()
                loop = asyncio.get_running_loop()
                vc.play(discord.FFmpegPCMAudio(path, executable=exe),
                        after=lambda err: loop.call_soon_threadsafe(finished.set))
                if debug:
                    asyncio.get_running_loop().call_later(
                        3, lambda: print(f"  3 s in: {voice_status(vc)}", flush=True))
                while not finished.is_set():
                    if stop.is_set() or not others_here(vc):
                        vc.stop()
                        break
                    try:
                        await asyncio.wait_for(finished.wait(), timeout=1)
                    except asyncio.TimeoutError:
                        pass
                if stop.is_set() or not others_here(vc):
                    break
                result["done"] = i + 1
                try:
                    os.remove(path)
                except OSError:
                    pass
            if result["done"] >= len(pieces):
                print(f"{clock()} finished reading {title}", flush=True)
            else:
                why = "everyone left" if not others_here(vc) else "stopped"
                print(f"{clock()} {why} after paragraph {result['done']}; carry on with --from={result['done'] + 1}",
                      flush=True)
        except Exception as e:
            print(f"{clock()} reading failed at paragraph {result['done'] + 1}: {e.__class__.__name__}: {e}",
                  file=sys.stderr)
            result["error"] = 1
        finally:
            await vc.disconnect(force=True)
            shutil.rmtree(workdir, ignore_errors=True)
            await client.close()

    async def runner():
        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, stop.set)
            except (NotImplementedError, RuntimeError):  # Windows: plain handlers
                signal.signal(sig, lambda *_: loop.call_soon_threadsafe(stop.set))
        async with client:
            await client.start(token)

    await runner()
    return result.get("error", 0)


def others_here(vc):
    ch = vc.channel
    return ch is not None and any(uid != vc.user.id for uid in ch.voice_states)


NEEDED = [("discord", "discord.py[voice]>=2.7"), ("nacl", "discord.py[voice]>=2.7"), ("davey", "discord.py[voice]>=2.7"),
          ("edge_tts", "edge-tts")]


def missing_packages():
    import importlib.util
    return sorted({pip for mod, pip in NEEDED if importlib.util.find_spec(mod) is None})


def davey_mismatch():
    """davey 0.2 needs the caller to run prepare_transition/execute_transition after each MLS commit or welcome;
    discord.py 2.7.1 (written for davey 0.1) never does. The E2EE session then never gets ready, every packet goes out
    unencrypted, and Discord clients silently drop it: the bot's ring lights up and nobody hears a thing.
    Returns the pip fix when this Python has that pair, else None."""
    try:
        from importlib import metadata
        import importlib.util
        dv = metadata.version("davey")
        spec = importlib.util.find_spec("discord.voice_state")
        src = open(spec.origin, encoding="utf-8").read() if spec and spec.origin else ""
    except Exception:
        return None
    major_minor = tuple(int(x) for x in re.findall(r"\d+", dv)[:2])
    if major_minor >= (0, 2) and "prepare_transition" not in src:
        return f"davey {dv} doesn't work with this discord.py (E2EE never gets ready, so nobody hears it)", "davey==0.1.7"
    return None


def check():
    """What this Python is missing, and the exact command that installs it into this same Python."""
    ok = True
    print(f"Python: {sys.executable} ({sys.version.split()[0]})")
    miss = missing_packages()
    if miss:
        ok = False
        print("Missing packages. Install into THIS Python with:")
        print(f'  "{sys.executable}" -m pip install ' + " ".join(f'"{m}"' for m in miss))
    else:
        print("Packages: discord.py, PyNaCl, davey, edge-tts all found.")
    bad = davey_mismatch()
    if bad:
        ok = False
        print(f"Voice encryption: {bad[0]}. Fix with:")
        print(f'  "{sys.executable}" -m pip install "{bad[1]}"')
    exe = os.environ.get("FFMPEG") or shutil.which("ffmpeg")
    if exe and (os.path.isfile(exe) or shutil.which(exe)):
        print(f"ffmpeg: {exe}")
    else:
        ok = False
        print("ffmpeg: not found. Install it (winget install ffmpeg) and set FFMPEG to ffmpeg.exe's full path "
              "for the process that runs this (a new PATH entry doesn't reach an already-running gateway).")
    if os.environ.get("KANBAN_BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN"):
        print("Token: set (not shown).")
    else:
        ok = False
        print("Token: KANBAN_BOT_TOKEN / DISCORD_BOT_TOKEN not set in this process.")
    follow = follow_ids()
    print(f"Follows: {len(follow)} user id(s)" if follow else "Follows: nobody (READ_ALOUD_FOLLOW / KANBAN_MOVERS "
          "unset): pass --channel or set READ_ALOUD_CHANNEL.")
    print("Ready." if ok else "Not ready: fix the lines above, then run --check again.")
    return 0 if ok else 1


# ---------------------------------------------------------------- stop / pid

def pid_alive(pid):
    """Is `pid` a running Python process? (A PID can be reused, so a non-Python process counts as gone.)"""
    if pid <= 0 or pid == os.getpid():
        return False
    if os.name == "nt":
        import ctypes
        from ctypes import wintypes
        k32 = ctypes.windll.kernel32
        h = k32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
        if not h:
            return False
        try:
            code = wintypes.DWORD()
            if not k32.GetExitCodeProcess(h, ctypes.byref(code)) or code.value != 259:  # 259 = STILL_ACTIVE
                return False
            buf = ctypes.create_unicode_buffer(1024)
            size = wintypes.DWORD(1024)
            if k32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
                return "python" in os.path.basename(buf.value).lower()
            return True
        finally:
            k32.CloseHandle(h)
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    try:
        with open(f"/proc/{pid}/cmdline", "rb") as f:
            return b"python" in f.read().lower()
    except OSError:
        return True


def _reader_cmdline(cmd):
    """Is this command line a reading (not --stop, --status, --check, --list or --dry-run)?"""
    if not cmd or "discord_read_aloud" not in cmd:
        return False
    return not re.search(r"\s--(stop|status|check|list|dry-run)\b", cmd)


def scan_readers():
    """PIDs of running readings found by their command line, so a reading whose pid file was overwritten or removed
    is still found. On Windows a reading started from an elevated shell hides its command line from a normal one;
    the per-reading pid files (PIDDIR) cover that case."""
    found = set()
    if os.name == "nt":
        import json
        import subprocess
        ps = ("Get-CimInstance Win32_Process -Filter \"Name like 'python%'\" | "
              "Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress")
        try:
            out = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps],
                                 capture_output=True, text=True, timeout=30).stdout.strip()
            rows = json.loads(out) if out else []
        except (OSError, ValueError, subprocess.SubprocessError):
            return found
        for r in rows if isinstance(rows, list) else [rows]:
            if _reader_cmdline(r.get("CommandLine") or ""):
                found.add(int(r["ProcessId"]))
    else:
        for d in os.listdir("/proc") if os.path.isdir("/proc") else []:
            if not d.isdigit():
                continue
            try:
                with open(f"/proc/{d}/cmdline", "rb") as f:
                    cmd = f.read().replace(b"\0", b" ").decode(errors="replace")
            except OSError:
                continue
            if "python" in cmd.lower() and _reader_cmdline(" " + cmd):
                found.add(int(d))
    found.discard(os.getpid())
    return found


def running_readers():
    """{pid: what it is reading} for every reading still running: the pid files (the old single one and one per
    reading) plus a command-line scan. Pid files of readings that have ended are removed on the way."""
    readers = {}
    files = [PIDFILE]
    if os.path.isdir(PIDDIR):
        files += [os.path.join(PIDDIR, n) for n in os.listdir(PIDDIR)]
    for path in files:
        try:
            with open(path, encoding="utf-8") as f:
                first, _, rest = f.read().strip().partition("\n")
            pid = int(first)
        except (OSError, ValueError):
            continue
        if pid_alive(pid):
            readers[pid] = rest.strip()
        elif path != PIDFILE:
            try:
                os.remove(path)
            except OSError:
                pass
    for pid in scan_readers():
        readers.setdefault(pid, "")
    readers.pop(os.getpid(), None)
    return readers


def end_reading(pid):
    """Ends one reading and waits up to 10 s for it to go. False when it can't be ended (e.g. it runs elevated)."""
    try:
        # On Linux the reader catches this and leaves the channel cleanly. On Windows it ends the process at once and
        # Discord drops the bot from the channel a few seconds later.
        os.kill(pid, signal.SIGTERM)
    except OSError:
        return not pid_alive(pid)
    for _ in range(20):
        if not pid_alive(pid):
            return True
        time.sleep(0.5)
    return not pid_alive(pid)


def stop_older_reading():
    """One bot can hold only one voice connection per server: a second reading kicks the first out and back in
    (close code 4006) over and over, and nobody hears either. So a new reading ends the old ones first."""
    readers = running_readers()
    if not readers:
        return
    for pid in readers:
        print(f"{clock()} another reading is still running (process {pid}); stopping it first", flush=True)
        if not end_reading(pid):
            print(f"{clock()} could not stop process {pid} (it may run as administrator): end it in Task Manager, "
                  f"or this reading will fight it for the voice channel", file=sys.stderr, flush=True)
    time.sleep(3)  # let Discord notice the old voice connection is gone before ours joins


def write_pid(title=""):
    with open(PIDFILE, "w", encoding="utf-8") as f:
        f.write(str(os.getpid()))
    try:
        os.makedirs(PIDDIR, exist_ok=True)
        with open(os.path.join(PIDDIR, str(os.getpid())), "w", encoding="utf-8") as f:
            f.write(f"{os.getpid()}\n{title}, started {clock()}")
    except OSError:
        pass


def clear_pid():
    try:
        with open(PIDFILE) as f:
            if f.read().strip() == str(os.getpid()):
                os.remove(PIDFILE)
    except OSError:
        pass
    try:
        os.remove(os.path.join(PIDDIR, str(os.getpid())))
    except OSError:
        pass


def describe(pid, what):
    return f"process {pid}" + (f" ({what})" if what else "")


def show_status():
    """Is a reading running? Found by pid files and by command line, not only by the one pid file."""
    readers = running_readers()
    if not readers:
        print("Nothing is being read.")
        return 0
    for pid, what in readers.items():
        print(f"Reading: {describe(pid, what)}.")
    if len(readers) > 1:
        print("More than one reading is running: they fight over the voice channel. Run --stop.")
    return 0


def stop_running():
    readers = running_readers()
    stuck = []
    for pid, what in readers.items():
        if end_reading(pid):
            print(f"Stopped the reading: {describe(pid, what)}.")
        else:
            stuck.append(pid)
            print(f"Could not stop {describe(pid, what)}: it may run as administrator. End it in Task Manager "
                  f"(Details, PID {pid}).")
    if not readers:
        print("Nothing is being read.")
    try:
        os.remove(PIDFILE)
    except OSError:
        pass
    return 1 if stuck else 0


# ---------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser(description="Read a document out loud in a Discord voice channel.")
    ap.add_argument("document", nargs="?", help="a path, or a vault note's name (Lore, Fix YOUTH, ...)")
    ap.add_argument("--channel", help="voice channel name or id (default READ_ALOUD_CHANNEL, else where Andy is)")
    ap.add_argument("--section", help="start at the heading that matches this (e.g. \"The ages of the world\")")
    ap.add_argument("--only", action="store_true", help="with --section: read only that section")
    ap.add_argument("--keep-notes", action="store_true", help="also read dates, footnotes and (new) marks")
    ap.add_argument("--from", dest="start", type=int, default=1, help="paragraph to start at (default 1)")
    ap.add_argument("--list", action="store_true", help="list the documents that match and stop")
    ap.add_argument("--dry-run", action="store_true", help="print the paragraphs that would be read; no Discord")
    ap.add_argument("--stop", action="store_true", help="stop every reading that is running")
    ap.add_argument("--status", action="store_true", help="say whether a reading is running")
    ap.add_argument("--debug", action="store_true", help="log the voice connection and its encryption state")
    ap.add_argument("--check", action="store_true", help="check this Python has everything; no Discord")
    a = ap.parse_args(argv)

    if a.stop:
        return stop_running()
    if a.status:
        return show_status()
    if a.check:
        return check()
    if not a.document:
        ap.error("name a document (or use --stop)")
    hits = find_docs(a.document)
    if a.list:
        for h in hits:
            print(h)
        return 0 if hits else 1
    if not hits:
        print(f"No document matches {a.document!r}. Try --list with part of its name.", file=sys.stderr)
        return 1
    if len(hits) > 1:
        print(f"{len(hits)} documents match {a.document!r}; name one of these (or pass its path):", file=sys.stderr)
        for h in hits:
            print("  " + h, file=sys.stderr)
        return 1
    path = hits[0]
    with open(path, encoding="utf-8", errors="replace") as f:
        text = f.read()
    if a.section:
        text = pick_section(text, a.section, a.only)
    pieces = chunks(speakable(text, a.keep_notes))
    title = os.path.splitext(os.path.basename(path))[0]
    if not pieces:
        print(f"{path} has nothing to read.", file=sys.stderr)
        return 1
    start = max(1, min(a.start, len(pieces))) - 1
    if a.dry_run:
        print(f"{path}: {len(pieces)} paragraphs")
        for i in range(start, len(pieces)):
            print(f"[{i + 1}] {pieces[i]}")
        return 0

    if missing_packages() or davey_mismatch():
        return check()
    token = os.environ.get("KANBAN_BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN")
    if not token:
        print("Set KANBAN_BOT_TOKEN (or DISCORD_BOT_TOKEN) to the bot's token.", file=sys.stderr)
        return 2
    wanted = a.channel or os.environ.get("READ_ALOUD_CHANNEL")
    stop_older_reading()
    write_pid(title)
    try:
        return asyncio.run(read_in_channel(token, title, pieces, start, wanted, a.debug))
    except KeyboardInterrupt:
        return 0
    finally:
        clear_pid()


if __name__ == "__main__":
    sys.exit(main())
