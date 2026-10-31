"""Voices for Jaymo's Kingdoms and Powers, made with VoxCPM2 (Apache-2.0, openbmb/VoxCPM2).

Each voice is designed once from a description (no real person is cloned), saved as refs/<voice>.wav, then every line is
cloned from that one clip so the voice stays the same. The lines come from Kingdoms' en_us.json by the same rule as
`Voices.voiced()` in Kingdoms: the narrator reads each life-story question (title and the line under it), each realm
page, every answer's tale and the closing line, plus (Kingdoms 0.7.352) the who-are-you page, the skip-the-story ask and
the hold screen; the spirits read their summon lines; (Kingdoms 0.7.348) each tongue's
greeting, farewell, cheer and scream (`tongue.jaymoskingdoms.<tongue>.<line>`), by a man and a woman of that land, made
up words heard by those who don't speak it (`Voices.TONGUE_LINES`). Clips go straight into
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/voice/<voice>/<name>.ogg (mono Vorbis); a clip already there is
skipped, so a stopped run picks up where it left off. (Powers 0.17.202) The symbiote's combat calls (every say.* line;
its mood variants, mood.say.*, can follow) goes into Powers' assets/jaymospowers/sounds/voice/symbiote, roughened afterwards
(`grit`, then a growl into every word: `snarl`), and Powers plays whichever lines its sounds.json lists (`WebSymbioteVoice`). Delete a clip to remake it; delete a ref wav (or change its seed) to
redesign a voice (then delete its clips too).

    pip install voxcpm soundfile numpy audiotsm
    python make_voices.py                 # every missing clip
    python make_voices.py narrator        # one voice
    python make_voices.py symbiote
    python make_voices.py symbiote --limit=3   # only the first few, to hear them
    python make_voices.py --resnarl=<folder>   # remake the symbiote's clips from gritted takes, no model
    python make_voices.py --audition           # candidate symbiote voices, 5 lines each, into audition/
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
    # (Powers 0.17.202) Andy: "a snarling visceral voice". Lowered again in game for the black strain (WebSymbioteVoice).
    # Andy (2026-10-29): "The symbiote should hang on every word longer", then "it should be a long snarl and growl into
    # each word instead of the current meek whimper into silence". So no written pauses: each word is drawn out and a
    # growl swells into it (`snarl`).
    "symbiote": dict(seed=71, grit=True, snarl=True,
                     desc="A deep, guttural, snarling monster voice, wet and raspy, growling and hissing through bared teeth, hungry and menacing, inhuman",
                     ref="We are hungry. Tear it open. Take what is ours, and feed us."),
}
# (Kingdoms 0.7.348) The tongues' speakers, a man and a woman each (`<tongue>_male`, `<tongue>_female`), designed on that
# tongue's own greeting and farewell so the made-up words keep its sound. Each line is said in its mood (`TONGUE_MOODS`).
TONGUE_FOLK = {
    "common": "a plain, friendly country villager from green farmland, clear and homely",
    "northern": "a hardy villager from the frozen north, deep and strong, with hard rolled r's and clipped words",
    "southern": "a desert trader from hot dunes, warm and lilting, with soft breathy h's and rolling vowels",
    "wildwood": "a villager from a steaming jungle, bright and rhythmic, quick clicking consonants and open vowels",
    "blossom": "a soft-spoken villager from cherry-blossom hills, light, even and polite, crisp short syllables",
}
TONGUE_LINES = ["greet", "farewell", "celebrate", "scream"]
TONGUE_MOODS = {"greet": "warm and welcoming", "farewell": "kind, saying goodbye", "celebrate": "cheering loudly with joy",
                "scream": "screaming in terror, loud, shrill and panicked"}
for _n, (_tongue, _desc) in enumerate(TONGUE_FOLK.items()):
    for _g, _who in (("male", "A man in his thirties"), ("female", "A woman in her thirties")):
        VOICES[_tongue + "_" + _g] = dict(seed=101 + _n * 10 + (_g == "female"), desc=_who + ", " + _desc, tongue=_tongue)
# (2026-10-29) Andy: "the voice itself sounds too much like the wizards, it needs more snarl and twist like an evil lich
# or swamp thing". Candidate symbiote voices for `--audition`: each designed on SYMBIOTE_REF, then made to say
# AUDITION_LINES, roughened and snarled, into tools/voices/audition/<name>_<n>.ogg. Andy: "These are INCREDIBLE"; each
# strain took one (SYMBIOTE_STRAINS), cloned from its audition ref.
SYMBIOTE_REF = "The wall is ours. Tear it open, and feed us. Nothing escapes us."
AUDITION = {
    "lich": dict(seed=81, desc="An ancient undead lich, a dry, rotten, rasping voice, cruel and sneering, hissing every s and "
                               "drawing out every l, twisted and evil, inhuman"),
    "swamp": dict(seed=82, desc="A huge swamp monster, a wet, bubbling, gurgling throat, slurred and twisted, deep and guttural, "
                                "sneering, dragging out the ends of its words"),
    "venom": dict(seed=83, desc="A twisted demonic creature, a raw, raspy, venomous snarl, spitting and hissing its s sounds, "
                                "rolling its l sounds, mocking and cruel, inhuman"),
    "rot": dict(seed=84, desc="A rotting, choking, croaking ghoul voice, deep and wet, slurring and hissing, sinister and hungry, "
                              "inhuman and evil"),
}
AUDITION_LINES = ["The wall is ours.", "Tear it. Throw it.", "Hold on.", "Up. We pull.", "Out of the way."]
# where each voice's clips go
# (2026-10-29) the strain each symbiote line belongs to (by its say.<strain>. key; black has none) and the voice it takes
SYMBIOTE_STRAINS = {"": "venom", "red.": "rot", "white.": "lich"}
for _name in SYMBIOTE_STRAINS.values():
    VOICES["symbiote_" + _name] = dict(seed=AUDITION[_name]["seed"], grit=True, snarl=True, desc=AUDITION[_name]["desc"],
                                       ref=SYMBIOTE_REF, ref_file="audition_" + _name)
NAMESPACE = {"symbiote": "jaymospowers", **{"symbiote_" + n: "jaymospowers" for n in SYMBIOTE_STRAINS.values()}}
SYMBIOTE = "message.jaymospowers.web_symbiote."

QUESTIONS = ["born", "raised", "childhood", "youth", "turn", "road"]
SPIRIT_LINES = [f"spirit.jaymoskingdoms.{s}.summon.{n}" for s in ("grimsby", "varro", "ambrose", "thessaly") for n in (1, 2, 3)] \
    + ["spirit.jaymoskingdoms.quiet.word.1"]
STORY_END = "story.jaymoskingdoms.summary.end"
# (Kingdoms 0.7.352) Voices.INTRO_LINES
INTRO_LINES = ["story.jaymoskingdoms.gender.title",
               "gui.jaymoskingdoms.story.skip.ask", "gui.jaymoskingdoms.story.skip.desc"] \
    + ["gui.jaymoskingdoms.story.hold.title"]


def realm_free(text):
    """A tale's realm name is filled in on screen, so the voice says "a realm" / "the realm" instead."""
    return text.replace("in %s", "in a realm").replace("%s", "the realm")


def lines(lang, powers):
    """(voice, lang key, text) for every voiced line, questions first so a short run still voices every page."""
    s = "story.jaymoskingdoms."
    # (Kingdoms 0.7.360) each question's title alone, and the line under it apart (read only if the page waits; none
    # for the road)
    out = [("narrator", s + q + ".title", lang[s + q + ".title"]) for q in QUESTIONS]
    out += [("narrator", s + q + ".sub", lang[s + q + ".sub"]) for q in QUESTIONS if q != "road"]
    for q in QUESTIONS:
        for key in lang:
            parts = key.split(".")
            if key.startswith(s + q + ".") and len(parts) == 5 and parts[4] == "tale":
                out.append(("narrator", key, realm_free(lang[key])))
            if len(parts) == 4 and key.startswith(s + q + ".") and parts[3] not in ("title", "sub"):
                realm = s + "realm." + parts[3]
                if realm in lang:
                    out.append(("narrator", realm, lang[s + "realm.title"] + " " + lang[realm]))
    # (Kingdoms 0.7.448) a blood's own telling of the story (story.jaymoskingdoms.as.<heritage>.<question>...): its pages'
    # titles and lines and its answers' tales. Kingdoms only voices these once their clip is made (`Voices.heritageVoiced`).
    for key in lang:
        parts = key.split(".")
        if key.startswith(s + "as.") and (len(parts) == 6 and parts[5] in ("title", "sub") or len(parts) == 7 and parts[6] == "tale"):
            out.append(("narrator", key, realm_free(lang[key])))
    out.append(("narrator", STORY_END, lang["gui.jaymoskingdoms.story.summary_sub"]))
    for key in INTRO_LINES:
        out.append(("narrator", key, lang[key]))
    for key in SPIRIT_LINES:
        out.append((key.split(".")[2], key, lang[key]))
    for tongue in TONGUE_FOLK:
        for g in ("male", "female"):
            for line in TONGUE_LINES:
                out.append((tongue + "_" + g, f"tongue.jaymoskingdoms.{tongue}.{g}.{line}", lang[f"tongue.jaymoskingdoms.{tongue}.{line}"]))
    # the symbiote: its combat calls (say.*; Andy picked these first, 2026-10-29). Add "mood.say." here for its moods'
    # variants, which are text only until then. A line with a %s in it stays text only.
    for start in ("say.",):
        for key in sorted(k for k in powers if k.startswith(SYMBIOTE + start)):
            if "%" not in powers[key]:
                rest = key[len(SYMBIOTE + start):]
                strain = next((k for k in ("red.", "white.") if rest.startswith(k)), "")
                out.append(("symbiote_" + SYMBIOTE_STRAINS[strain], key, powers[key]))
    return out


def sound_name(key):
    """The same rule as Voices.soundPath (and WebSymbioteVoice): (sound id, file under sounds/voice/<voice>/)."""
    if key.startswith("story.jaymoskingdoms."):
        voice, rest = "narrator", key[len("story.jaymoskingdoms."):]
    elif key.startswith("gui.jaymoskingdoms.story."):
        voice, rest = "narrator", key[len("gui.jaymoskingdoms.story."):]
    elif key.startswith(SYMBIOTE):
        voice, rest = "symbiote", key[len(SYMBIOTE):]
    elif key.startswith("tongue.jaymoskingdoms."):
        # tongue.jaymoskingdoms.northern.female.greet -> voice.tongue.northern.female.greet, tongue/northern_female_greet
        voice, rest = "tongue", key[len("tongue.jaymoskingdoms."):]
    else:
        voice, rest = key[len("spirit.jaymoskingdoms."):].split(".", 1)
    return "voice." + voice + "." + rest, voice + "/" + rest.replace(".", "_")


def clip_path(voice, key):
    return os.path.join(ASSETS_OF[NAMESPACE.get(voice, "jaymoskingdoms")], "sounds", "voice", sound_name(key)[1] + ".ogg")


def write_sounds(all_lines):
    """Each mod's sounds.json. Kingdoms registers its list in code, so it lists every line (a tongue's only once made); Powers lists only the clips
    made so far, since what it lists is what the symbiote says aloud."""
    for ns, assets in ASSETS_OF.items():
        sounds = {}
        for voice, key, _ in all_lines:
            if NAMESPACE.get(voice, "jaymoskingdoms") != ns:
                continue
            if (ns == "jaymospowers" or "tongue" in VOICES.get(voice, {}) or key.startswith("story.jaymoskingdoms.as.")) \
                    and not os.path.exists(clip_path(voice, key)):
                # (Kingdoms registers a tongue's line, and a blood's own telling, only once its clip exists)
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


# snarl: each word drawn out this much (WSOLA, so its pitch stays), the growl into it this long (s) and this loud; under
# the words, the grunge: the octave-lower double, the rattling low rumble, the raspy hiss, and how loud all of it is
# (Andy: "it doesnt sound legible at all, i do like the added noise and grunge to the growl, increase that, make it a
# little more understandable")
SNARL_STRETCH, SNARL_LEAD, SNARL_LEAD_GAIN = 1.3, 0.32, 0.6
SNARL_DOUBLE, SNARL_RUMBLE, SNARL_HISS, SNARL_GRUNGE = 0.32, 0.9, 0.35, 0.6
# and each word's end dragged out further, this much of it this many times (Andy: "The wallll is ourssss.")
SNARL_TAIL, SNARL_TAIL_STRETCH = 0.35, 2.0


def tsm(seg, k):
    """`seg` made `k` times as long at the same pitch (WSOLA)."""
    import numpy as np
    from audiotsm import wsola
    from audiotsm.io.array import ArrayReader, ArrayWriter
    if seg.size < 2048:
        return np.repeat(seg, int(round(k))) if k >= 2 else seg
    out = ArrayWriter(1)
    wsola(1, speed=1.0 / k).run(ArrayReader(seg[np.newaxis, :].astype(np.float32)), out)
    return out.data[0].astype(np.float32)


def octave_down(seg):
    """The same length, an octave lower: played at half speed, then sped back up by WSOLA."""
    import numpy as np
    slow = np.interp(np.arange(seg.size * 2) / 2.0, np.arange(seg.size), seg).astype(np.float32)
    out = tsm(slow, 0.5)
    if out.size < seg.size:
        out = np.pad(out, (0, seg.size - out.size))
    return out[:seg.size]


def word_dips(wav, rate):
    """Where one word gives way to the next in a run of speech: dips in its loudness, under half the sound either side."""
    import numpy as np
    hop = rate // 100
    n = wav.size // hop
    env = np.sqrt(np.mean(wav[:n * hop].reshape(n, hop) ** 2, axis=1))
    env = np.convolve(env, np.ones(3) / 3, mode="same")
    floor = max(0.012, float(env.max()) * 0.06)
    out, last = [], -999
    for k in range(4, n - 4):
        if env[k] > env[k - 1] or env[k] > env[k + 1]:
            continue
        left, right = env[max(0, k - 15):k].max(), env[k + 1:k + 16].max()
        if left >= floor * 2 and right >= floor * 2 and env[k] < 0.45 * min(left, right) and k - last >= 16:
            out.append(k * hop)
            last = k
    return out


def snarl(wav, rate):
    """(the symbiote) A long snarl into every word, no dead air: the line is cut into words (at its silences, which are
    dropped, and at the dips between run-together words), each word drawn out and led into by a growl made from its own
    first moment (an octave down, dragged out, rattling, swelling). The words stay clear on top; the grunge goes under
    them, driven hard: noise riding the voice's loudness (a rattling low rumble and a raspy hiss) and an octave-lower
    double."""
    import numpy as np
    hop = rate // 100
    n = wav.size // hop
    env = np.sqrt(np.mean(wav[:n * hop].reshape(n, hop) ** 2, axis=1))
    floor = max(0.012, float(env.max()) * 0.06)
    loud = np.flatnonzero(env > floor)
    if not loud.size:
        return wav
    a, b = loud[0] * hop, (loud[-1] + 1) * hop
    body = wav[a:b]
    quiet = env[loud[0]:loud[-1] + 1] < floor
    # the sounded runs: a silence under 80 ms joins its neighbours, a longer one is dropped
    runs, k, joined = [], 0, False
    while k < quiet.size:
        j = k
        while j < quiet.size and quiet[j] == quiet[k]:
            j += 1
        if quiet[k]:
            joined = j - k < 8
        elif joined and runs:
            runs[-1][1] = j * hop
        else:
            runs.append([k * hop, j * hop])
        k = j
    words = []
    for s0, e0 in runs:
        run = body[s0:e0]
        cuts = [0] + word_dips(run, rate) + [run.size]
        words += [run[s:e] for s, e in zip(cuts, cuts[1:])]
    t_lead = int(rate * 0.09)
    fade, overlap = int(rate * 0.04), int(rate * 0.06)
    out = [wav[:a]]
    for word in words:
        if word.size < rate * 0.06:
            out.append(word)
            continue
        tail = int(word.size * SNARL_TAIL)
        word = np.concatenate([tsm(word[:-tail], SNARL_STRETCH), tsm(word[-tail:], SNARL_STRETCH * SNARL_TAIL_STRETCH)]) \
            if tail > 0 else tsm(word, SNARL_STRETCH).copy()
        growl = octave_down(tsm(word[:t_lead], SNARL_LEAD * rate / max(1, min(t_lead, word.size))))
        t = np.arange(growl.size) / rate
        growl = growl * (1.0 + 0.8 * np.sin(2 * np.pi * 29.0 * t) * (0.7 + 0.3 * np.sin(2 * np.pi * 5.1 * t)))
        growl = growl * np.linspace(0.0, 1.0, growl.size) ** 1.6 * SNARL_LEAD_GAIN
        m = min(fade, growl.size, word.size)
        word[:m] += growl[-m:] * np.linspace(1.0, 0.0, m)
        growl = growl[:-m] if m else growl
        o = min(overlap, out[-1].size, growl.size)
        if o > 0 and len(out) > 1:
            prev = out[-1].copy()
            prev[-o:] += growl[:o]
            out[-1] = prev
            growl = growl[o:]
        out += [growl, word]
    out.append(wav[b:])
    dry = np.concatenate(out)
    dry = dry / (float(np.max(np.abs(dry))) or 1.0)
    k = max(1, int(rate * 0.025))
    env = np.convolve(np.abs(dry), np.ones(k) / k, mode="same").astype(np.float32)
    noise = np.random.default_rng(7).standard_normal(dry.size).astype(np.float32)
    t = np.arange(dry.size) / rate
    rumble = band(noise, rate, 70, 700) * (1.0 + 0.9 * np.sin(2 * np.pi * 27.0 * t) * (0.7 + 0.3 * np.sin(2 * np.pi * 5.1 * t)))
    hiss = band(noise, rate, 2000, 7000)
    rumble /= float(np.max(np.abs(rumble))) or 1.0
    hiss /= float(np.max(np.abs(hiss))) or 1.0
    grunge = (SNARL_RUMBLE * rumble + SNARL_HISS * hiss) * env * 2.5 + SNARL_DOUBLE * octave_down(dry)
    grunge = np.tanh(4.0 * grunge) / np.tanh(4.0)
    return np.tanh(1.4 * dry) / np.tanh(1.4) + SNARL_GRUNGE * grunge


def band(x, rate, lo, hi):
    """`x` with only lo..hi Hz left."""
    import numpy as np
    f = np.fft.rfft(x)
    hz = np.fft.rfftfreq(x.size, 1.0 / rate)
    f[(hz < lo) | (hz > hi)] = 0
    return np.fft.irfft(f, x.size).astype(np.float32)


def grit(wav, rate):
    """(the symbiote) A growl through it: a fast flutter in its loudness (a throat rattling at ~31 Hz, a slower wobble on
    top) and soft clipping for the snarl."""
    import numpy as np
    t = np.arange(wav.size, dtype=np.float32) / rate
    flutter = 1.0 + 0.32 * np.sin(2 * np.pi * 31.0 * t) * (0.75 + 0.25 * np.sin(2 * np.pi * 4.3 * t))
    wav = wav * flutter / (float(np.max(np.abs(wav))) or 1.0)
    return np.tanh(2.6 * wav) / np.tanh(2.6)


def tidy(wav, rate, rough=False, snarling=False):
    """Mono, edge silence trimmed (with 80 ms kept), peak at -1 dBFS; `rough` runs it through grit, `snarling` then through snarl."""
    import numpy as np
    wav = np.asarray(wav, dtype=np.float32)
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    loud = np.flatnonzero(np.abs(wav) > 0.01)
    if loud.size:
        pad = int(rate * 0.08)
        wav = wav[max(0, loud[0] - pad):loud[-1] + pad]
    if rough:
        wav = grit(wav, rate)
    if snarling:
        wav = snarl(wav, rate)
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
    if "--audition" in sys.argv:
        audition()
        return
    for a in sys.argv[1:]:
        if a.startswith("--resnarl="):
            resnarl(a[len("--resnarl="):], all_lines)
            write_sounds(all_lines)
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
            make(model, rate, sf, lang, voice, key, text)
            print(f"[{n}/{len(todo)}] wrote", clip_path(voice, key))
    finally:
        # (Powers and the tongues list only the clips that exist, so list the new ones even if the run is stopped)
        write_sounds(all_lines)


def audition():
    """Each candidate symbiote voice says AUDITION_LINES, the way the symbiote's clips are made."""
    import soundfile as sf
    from voxcpm import VoxCPM
    out = os.path.join(HERE, "audition")
    os.makedirs(out, exist_ok=True)
    os.makedirs(REFS, exist_ok=True)
    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False)
    rate = model.tts_model.sample_rate
    for name, v in AUDITION.items():
        ref = os.path.join(REFS, "audition_" + name + ".wav")
        if not os.path.exists(ref):
            seeded(v["seed"])
            wav = model.generate(text="(" + v["desc"] + ")" + SYMBIOTE_REF, cfg_value=2.0, inference_timesteps=10)
            sf.write(ref, tidy(wav, rate), rate)
        for n, text in enumerate(AUDITION_LINES, 1):
            path = os.path.join(out, f"{name}_{n}.ogg")
            if os.path.exists(path):
                continue
            seeded(v["seed"])
            wav = model.generate(text=text, prompt_wav_path=ref, prompt_text=SYMBIOTE_REF, reference_wav_path=ref,
                                 cfg_value=2.0, inference_timesteps=10)
            sf.write(path, tidy(wav, rate, True, True), rate, format="OGG", subtype="VORBIS")
            print("wrote", path)


def resnarl(folder, all_lines):
    """(the symbiote) Remake its clips without the model, from takes already roughened by grit (`folder` holds
    say_<act>_<n>.ogg): each is run through snarl again. Any clip already made is redone."""
    import soundfile as sf
    n = 0
    for voice, key, _ in all_lines:
        if not voice.startswith("symbiote"):
            continue
        take = os.path.join(folder, sound_name(key)[1].split("/", 1)[1] + ".ogg")
        if not os.path.exists(take):
            print("no take for", key)
            continue
        wav, rate = sf.read(take, dtype="float32")
        path = clip_path(voice, key)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        wav = snarl(wav, rate)
        sf.write(path, wav * (0.89 / (float(abs(wav).max()) or 1.0)), rate, format="OGG", subtype="VORBIS")
        n += 1
    print("resnarled", n, "clips")


def make(model, rate, sf, lang, voice, key, text):
    v = VOICES[voice]
    ref = os.path.join(REFS, v.get("ref_file", voice) + ".wav")
    ref_text = v.get("ref", REF_TEXT)
    if "tongue" in v:
        ref_text = lang[f"tongue.jaymoskingdoms.{v['tongue']}.greet"] + " " + lang[f"tongue.jaymoskingdoms.{v['tongue']}.farewell"]
        # a tongue's line is said in its mood: (screaming in terror...)Aiyee!
        text = "(" + TONGUE_MOODS[key.rsplit(".", 1)[1]] + ")" + text
    if not os.path.exists(ref):
        seeded(v["seed"])
        wav = model.generate(text="(" + v["desc"] + ")" + ref_text, cfg_value=2.0, inference_timesteps=10)
        sf.write(ref, tidy(wav, rate), rate)
        print("designed", ref)
    seeded(v["seed"])
    wav = model.generate(text=text, prompt_wav_path=ref, prompt_text=ref_text, reference_wav_path=ref,
                         cfg_value=2.0, inference_timesteps=10)
    path = clip_path(voice, key)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, tidy(wav, rate, v.get("grit", False), v.get("snarl", False)), rate, format="OGG", subtype="VORBIS")


if __name__ == "__main__":
    main()
