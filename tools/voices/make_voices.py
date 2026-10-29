"""Voices for Jaymo's Kingdoms and Powers, made with VoxCPM2 (Apache-2.0, openbmb/VoxCPM2).

Each voice is designed once from a description (no real person is cloned), saved as refs/<voice>.wav, then every line is
cloned from that one clip so the voice stays the same. The lines come from Kingdoms' en_us.json by the same rule as
`Voices.voiced()` in Kingdoms: the narrator reads each life-story question (title and the line under it), each realm
page, every answer's tale and the closing line, plus (Kingdoms 0.7.342) the who-are-you page, the skip-the-story ask and
the hold screen; the spirits read their summon lines. Clips go straight into
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/voice/<voice>/<name>.ogg (mono Vorbis); a clip already there is
skipped, so a stopped run picks up where it left off. (Powers 0.17.183) The symbiote's combat calls (every say.* line;
its mood variants, mood.say.*, can follow) goes into Powers' assets/jaymospowers/sounds/voice/symbiote, roughened afterwards
(`grit`), and Powers plays whichever lines its sounds.json lists (`WebSymbioteVoice`). Delete a clip to remake it; delete a ref wav (or change its seed) to
redesign a voice (then delete its clips too).

    pip install voxcpm soundfile numpy audiotsm
    python make_voices.py                 # every missing clip
    python make_voices.py narrator        # one voice
    python make_voices.py symbiote
    python make_voices.py symbiote --limit=3   # only the first few, to hear them
    python make_voices.py --sounds        # only rewrite sounds.json (no model needed)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
POWERS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
POWERS_LANG = os.path.join(POWERS, "lang", "parts")
# each mod's assets, by namespace
ASSETS_OF = {"jaymoskingdoms": ASSETS, "jaymospowers": POWERS}
REFS = os.path.join(HERE, "refs")

# The sentence each voice is designed on; later lines are cloned from it.
REF_TEXT = "Hm. So you are the one I am to look after. Well, sit down, and listen closely, because I will not say it twice."

VOICES = {
    "narrator": dict(seed=11, desc="A warm, deep-voiced older male storyteller, calm and unhurried, speaking softly like reading an old legend by a fire",
                     ref="Long ago, before the realms had names, the land was quiet. Listen, and I will tell you how your story began."),
    "grimsby": dict(seed=21, desc="A very old, grumpy male wizard, gravelly and dry voice, tired and impatient, sighing between words"),
    "varro": dict(seed=31, desc="A loud, booming middle-aged male drill sergeant, gruff and barking, eager for battle"),
    "ambrose": dict(seed=41, desc="A kind, gentle elderly grandfather wizard, soft warm voice, patient and fond"),
    "thessaly": dict(seed=51, desc="A prim, fussy elderly female scholar witch, crisp precise voice, scolding but fond"),
    "quiet": dict(seed=61, desc="An ancient, hollow, breathy whisper, very slow, neither male nor female, distant and echoing"),
    # (Powers 0.17.183) Andy: "a snarling visceral voice". Lowered again in game for the black strain (WebSymbioteVoice).
    # Andy (2026-10-29): "The symbiote should hang on every word longer": written with a pause after every word (`hang`),
    # designed on a slow line, then each word drawn out and the gaps between words held longer (`drawl`).
    "symbiote": dict(seed=71, grit=True, drawl=True,
                     desc="A deep, guttural, snarling monster voice, wet and raspy, growling through bared teeth, menacing and inhuman, "
                          "speaking very slowly, drawing out every single word, with a long heavy pause after each word",
                     ref="We... are... hungry. Tear... it... open. Take... what... is... ours."),
}
# where each voice's clips go
NAMESPACE = {"symbiote": "jaymospowers"}
SYMBIOTE = "message.jaymospowers.web_symbiote."

QUESTIONS = ["born", "raised", "childhood", "youth", "turn", "road"]
SPIRIT_LINES = [f"spirit.jaymoskingdoms.{s}.summon.{n}" for s in ("grimsby", "varro", "ambrose", "thessaly") for n in (1, 2, 3)] \
    + ["spirit.jaymoskingdoms.quiet.word.1"]
STORY_END = "story.jaymoskingdoms.summary.end"
# (Kingdoms 0.7.342) Voices.INTRO_LINES
INTRO_LINES = ["story.jaymoskingdoms.gender.title", "story.jaymoskingdoms.gender.desc",
               "gui.jaymoskingdoms.story.skip.ask", "gui.jaymoskingdoms.story.skip.desc"] \
    + ["gui.jaymoskingdoms.story.hold." + k for k in ("title", "realm", "harbour", "future", "arriving")]


def realm_free(text):
    """A tale's realm name is filled in on screen, so the voice says "a realm" / "the realm" instead."""
    return text.replace("in %s", "in a realm").replace("%s", "the realm")


def lines(lang, powers):
    """(voice, lang key, text) for every voiced line, questions first so a short run still voices every page."""
    s = "story.jaymoskingdoms."
    out = [("narrator", s + q + ".title", lang[s + q + ".title"] + " " + lang[s + q + ".sub"]) for q in QUESTIONS]
    for q in QUESTIONS:
        for key in lang:
            parts = key.split(".")
            if key.startswith(s + q + ".") and len(parts) == 5 and parts[4] == "tale":
                out.append(("narrator", key, realm_free(lang[key])))
            if len(parts) == 4 and key.startswith(s + q + ".") and parts[3] not in ("title", "sub"):
                realm = s + "realm." + parts[3]
                if realm in lang:
                    out.append(("narrator", realm, lang[s + "realm.title"] + " " + lang[realm]))
    out.append(("narrator", STORY_END, lang["gui.jaymoskingdoms.story.summary_sub"]))
    for key in INTRO_LINES:
        out.append(("narrator", key, lang[key]))
    for key in SPIRIT_LINES:
        out.append((key.split(".")[2], key, lang[key]))
    # the symbiote: its combat calls (say.*; Andy picked these first, 2026-10-29). Add "mood.say." here for its moods'
    # variants, which are text only until then. A line with a %s in it stays text only.
    for start in ("say.",):
        for key in sorted(k for k in powers if k.startswith(SYMBIOTE + start)):
            if "%" not in powers[key]:
                out.append(("symbiote", key, powers[key]))
    return out


def sound_name(key):
    """The same rule as Voices.soundPath (and WebSymbioteVoice): (sound id, file under sounds/voice/<voice>/)."""
    if key.startswith("story.jaymoskingdoms."):
        voice, rest = "narrator", key[len("story.jaymoskingdoms."):]
    elif key.startswith("gui.jaymoskingdoms.story."):
        voice, rest = "narrator", key[len("gui.jaymoskingdoms.story."):]
    elif key.startswith(SYMBIOTE):
        voice, rest = "symbiote", key[len(SYMBIOTE):]
    else:
        voice, rest = key[len("spirit.jaymoskingdoms."):].split(".", 1)
    return "voice." + voice + "." + rest, voice + "/" + rest.replace(".", "_")


def clip_path(voice, key):
    return os.path.join(ASSETS_OF[NAMESPACE.get(voice, "jaymoskingdoms")], "sounds", "voice", sound_name(key)[1] + ".ogg")


def write_sounds(all_lines):
    """Each mod's sounds.json. Kingdoms registers its list in code, so it lists every line; Powers lists only the clips
    made so far, since what it lists is what the symbiote says aloud."""
    for ns, assets in ASSETS_OF.items():
        sounds = {}
        for voice, key, _ in all_lines:
            if NAMESPACE.get(voice, "jaymoskingdoms") != ns or (ns == "jaymospowers" and not os.path.exists(clip_path(voice, key))):
                continue
            sid, file = sound_name(key)
            entry = {"name": ns + ":voice/" + file}
            if voice == "narrator":
                entry["stream"] = True
            sounds[sid] = {"sounds": [entry]}
        path = os.path.join(assets, "sounds.json")
        if not sounds and not os.path.exists(path):
            continue
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(sounds, indent="\t") + "\n")
        print("wrote", path, len(sounds), "sounds")


def hang(text):
    """(the symbiote) A pause after every word: 'Tear it. Throw it.' -> 'Tear... it. Throw... it.'"""
    words = text.split()
    return " ".join(w if w[-1] in ".!?,;:" or n == len(words) - 1 else w + "..." for n, w in enumerate(words))


# drawl: words drawn out this much (speed kept by WSOLA, so the pitch stays), and a gap between words (silence over
# 90 ms) held at least this long, or this many times as long
DRAWL_STRETCH, DRAWL_GAP_MIN, DRAWL_GAP_TO, DRAWL_GAP_X = 1.35, 0.09, 0.38, 2.0


def drawl(wav, rate):
    """(the symbiote) Hangs on every word: each sounded stretch slowed without lowering it, each gap between words held."""
    import numpy as np
    from audiotsm import wsola
    from audiotsm.io.array import ArrayReader, ArrayWriter

    def slowed(seg):
        if seg.size < rate * 0.05:
            return seg
        out = ArrayWriter(1)
        wsola(1, speed=1.0 / DRAWL_STRETCH).run(ArrayReader(seg[np.newaxis, :]), out)
        return out.data[0].astype(np.float32)

    hop = int(rate * 0.01)
    n = wav.size // hop
    env = np.sqrt(np.mean(wav[:n * hop].reshape(n, hop) ** 2, axis=1))
    quiet = env < max(0.012, float(env.max()) * 0.06)
    out, i = [], 0
    while i < n:
        j = i
        while j < n and quiet[j] == quiet[i]:
            j += 1
        seg, dur = wav[i * hop:j * hop], (j - i) * 0.01
        if not quiet[i]:
            out.append(slowed(seg))
        elif dur >= DRAWL_GAP_MIN and 0 < i and j < n:
            out.append(np.zeros(int(rate * max(DRAWL_GAP_TO, dur * DRAWL_GAP_X)), np.float32))
        else:
            out.append(seg)
        i = j
    out.append(wav[n * hop:])
    return np.concatenate(out)


def grit(wav, rate):
    """(the symbiote) A growl through it: a fast flutter in its loudness (a throat rattling at ~31 Hz, a slower wobble on
    top) and soft clipping for the snarl."""
    import numpy as np
    t = np.arange(wav.size, dtype=np.float32) / rate
    flutter = 1.0 + 0.32 * np.sin(2 * np.pi * 31.0 * t) * (0.75 + 0.25 * np.sin(2 * np.pi * 4.3 * t))
    wav = wav * flutter / (float(np.max(np.abs(wav))) or 1.0)
    return np.tanh(2.6 * wav) / np.tanh(2.6)


def tidy(wav, rate, rough=False, slow=False):
    """Mono, edge silence trimmed (with 80 ms kept), peak at -1 dBFS; `slow` runs it through drawl, `rough` through grit."""
    import numpy as np
    wav = np.asarray(wav, dtype=np.float32)
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    loud = np.flatnonzero(np.abs(wav) > 0.01)
    if loud.size:
        pad = int(rate * 0.08)
        wav = wav[max(0, loud[0] - pad):loud[-1] + pad]
    if slow:
        wav = drawl(wav, rate)
    if rough:
        wav = grit(wav, rate)
    peak = float(np.max(np.abs(wav))) or 1.0
    return wav * (0.89 / peak)


def seeded(seed):
    """voxcpm's generate() takes no seed, so fix the global RNGs before each call instead."""
    import numpy as np
    import torch
    torch.manual_seed(seed)
    np.random.seed(seed)


def main():
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)
    powers = {}
    for name in sorted(os.listdir(POWERS_LANG)):
        if name.endswith(".json"):
            with open(os.path.join(POWERS_LANG, name), encoding="utf-8") as f:
                powers.update(json.load(f))
    all_lines = lines(lang, powers)
    write_sounds(all_lines)
    if "--sounds" in sys.argv:
        return
    only = {a for a in sys.argv[1:] if not a.startswith("--")}
    todo = [(v, k, t) for v, k, t in all_lines if (not only or v in only)
            and not os.path.exists(clip_path(v, k))]
    for a in sys.argv[1:]:
        if a.startswith("--limit="):
            todo = todo[:int(a[len("--limit="):])]
    print(len(todo), "clips to make")
    if not todo:
        return
    import soundfile as sf
    from voxcpm import VoxCPM
    os.makedirs(REFS, exist_ok=True)
    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False)
    rate = model.tts_model.sample_rate
    try:
        for n, (voice, key, text) in enumerate(todo, 1):
            make(model, rate, sf, voice, key, text)
            print(f"[{n}/{len(todo)}] wrote", clip_path(voice, key))
    finally:
        # (Powers lists only the clips that exist, so list the new ones even if the run is stopped)
        write_sounds(all_lines)


def make(model, rate, sf, voice, key, text):
    v = VOICES[voice]
    ref = os.path.join(REFS, voice + ".wav")
    ref_text = v.get("ref", REF_TEXT)
    if not os.path.exists(ref):
        seeded(v["seed"])
        wav = model.generate(text="(" + v["desc"] + ")" + ref_text, cfg_value=2.0, inference_timesteps=10)
        sf.write(ref, tidy(wav, rate), rate)
        print("designed", ref)
    seeded(v["seed"])
    if v.get("drawl"):
        text = hang(text)
    wav = model.generate(text=text, prompt_wav_path=ref, prompt_text=ref_text, reference_wav_path=ref,
                         cfg_value=2.0, inference_timesteps=10)
    path = clip_path(voice, key)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, tidy(wav, rate, v.get("grit", False), v.get("drawl", False)), rate, format="OGG", subtype="VORBIS")


if __name__ == "__main__":
    main()
