"""Voice lines made with Google Cloud Text-to-Speech, the "Google guy" Andy asked for (2026-11-19).

The same lines, file names and sounds.json as `make_voices.py` (whose `lines`, `clip_path`, `write_sounds` and `tidy`
this reuses), but each clip is spoken by a Google voice instead of a VoxCPM2 one. Google's terms let us ship the audio
in our own product ("You can use the audio data files you create using Cloud Text-to-Speech in your applications or
media", https://cloud.google.com/text-to-speech/quotas); this is not a recording of Chrome's built-in reading voice.

The key is read from the GOOGLE_TTS_API_KEY environment variable and never written anywhere. Never commit it.

By default only clips that don't exist yet are made, so the lines the VoxCPM2 narrator already reads keep his voice.
Every clip this script makes is listed in google_clips.txt, so the Google ones can be told apart (and deleted, if the
test is a no). `--redo` remakes that voice's existing clips too (one narrator voice throughout).

    pip install soundfile numpy
    python google_voices.py --dry-run          # what would be made and how many characters (no key needed)
    python google_voices.py --audition         # each CANDIDATES voice reads AUDITION_LINES into audition/google/
    python google_voices.py                    # every missing narrator clip
    python google_voices.py --limit=3          # only the first few, to hear them
    python google_voices.py --voice=en-US-Chirp3-HD-Orus   # another Google voice for this run
    python google_voices.py --redo             # remake every narrator clip, VoxCPM2 ones too
    python google_voices.py --keys=story.jaymoskingdoms.faith.   # only lines whose key starts with this

Free each month: 1 million characters on Chirp 3 HD voices, 4 million on WaveNet (all the narrator's lines are ~30,000).
"""
import base64
import io
import json
import os
import sys
import urllib.error
import urllib.request

import make_voices as mv

API = "https://texttospeech.googleapis.com/v1/text:synthesize"
KEY_ENV = "GOOGLE_TTS_API_KEY"
# The Google voice each of our voices takes. Only the narrator so far; the Rig's Mind can follow.
VOICES = {"narrator": "en-US-Chirp3-HD-Charon"}
# US English men's voices to choose from (`--audition`): the newest (Chirp 3 HD) and the older WaveNet/Neural2/Studio ones
CANDIDATES = ["en-US-Chirp3-HD-Charon", "en-US-Chirp3-HD-Orus", "en-US-Chirp3-HD-Iapetus", "en-US-Chirp3-HD-Fenrir",
              "en-US-Chirp3-HD-Algieba", "en-US-Wavenet-D", "en-US-Neural2-D", "en-US-Neural2-J", "en-US-Studio-Q"]
AUDITION_LINES = ["Long ago, before the realms had names, the land was quiet. Listen, and I will tell you how your story began.",
                  "Where were you born?",
                  "You were born among the wheat of the open plains, where the days are long and the bread is honest."]
MADE = os.path.join(mv.HERE, "google_clips.txt")


def synthesize(key, voice, text):
    """`text` spoken by the Google voice `voice`: (mono float samples, rate)."""
    import numpy as np
    import soundfile as sf
    body = {"input": {"text": text},
            "voice": {"languageCode": "-".join(voice.split("-")[:2]), "name": voice},
            "audioConfig": {"audioEncoding": "LINEAR16"}}
    req = urllib.request.Request(API, data=json.dumps(body).encode("utf-8"), method="POST",
                                 headers={"Content-Type": "application/json", "X-Goog-Api-Key": key})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            audio = base64.b64decode(json.load(r)["audioContent"])
    except urllib.error.HTTPError as e:
        sys.exit(f"Google said {e.code}: {e.read().decode('utf-8', 'replace')[:500]}")
    wav, rate = sf.read(io.BytesIO(audio), dtype="float32")
    return np.asarray(wav), rate


def write_ogg(path, wav, rate):
    import soundfile as sf
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, mv.tidy(wav, rate), rate, format="OGG", subtype="VORBIS")


def made():
    if not os.path.exists(MADE):
        return {}
    with open(MADE, encoding="utf-8") as f:
        return dict(line.rstrip("\n").split(" ", 1) for line in f if " " in line)


def remember(done):
    with open(MADE, "w", encoding="utf-8", newline="\n") as f:
        for k in sorted(done):
            f.write(k + " " + done[k] + "\n")


def audition(key):
    out = os.path.join(mv.HERE, "audition", "google")
    for voice in CANDIDATES:
        for n, text in enumerate(AUDITION_LINES, 1):
            path = os.path.join(out, f"{voice}_{n}.ogg")
            if not os.path.exists(path):
                write_ogg(path, *synthesize(key, voice, text))
                print("wrote", path)


def main():
    args = sys.argv[1:]
    opt = {a.split("=", 1)[0]: a.split("=", 1)[1] for a in args if a.startswith("--") and "=" in a}
    key = os.environ.get(KEY_ENV, "").strip()
    if "--audition" in args:
        if not key:
            sys.exit(f"Set {KEY_ENV} first.")
        audition(key)
        return
    lang, powers = mv.load_lang()
    all_lines = mv.lines(lang, powers)
    only = {a for a in args if not a.startswith("--")} or set(VOICES)
    redo = "--redo" in args
    todo = [(v, k, t) for v, k, t in all_lines if v in only and v in VOICES
            and k.startswith(opt.get("--keys", "")) and (redo or not os.path.exists(mv.clip_path(v, k)))]
    if "--limit" in opt:
        todo = todo[:int(opt["--limit"])]
    print(len(todo), "clips to make,", sum(len(t) for _, _, t in todo), "characters")
    if "--dry-run" in args:
        for v, k, t in todo:
            print(" ", k, "|", t[:90])
        return
    if not todo:
        return
    if not key:
        sys.exit(f"Set {KEY_ENV} first (an environment variable; never commit the key).")
    done = made()
    try:
        for n, (voice, k, text) in enumerate(todo, 1):
            name = opt.get("--voice", VOICES[voice])
            write_ogg(mv.clip_path(voice, k), *synthesize(key, name, text))
            done[mv.sound_name(k)[1]] = name
            print(f"[{n}/{len(todo)}] {name}:", mv.clip_path(voice, k))
    finally:
        remember(done)
        mv.write_sounds(all_lines)


if __name__ == "__main__":
    main()
