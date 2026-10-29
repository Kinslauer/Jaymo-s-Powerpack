"""Voices for Jaymo's Kingdoms, made with VoxCPM2 (Apache-2.0, openbmb/VoxCPM2).

Each voice is designed once from a description (no real person is cloned), saved as refs/<voice>.wav, then every line is
cloned from that one clip so the voice stays the same. The lines come from Kingdoms' en_us.json by the same rule as
`Voices.voiced()` in Kingdoms: the narrator reads each life-story question (title and the line under it), each realm
page, every answer's tale and the closing line; the spirits read their summon lines; (Kingdoms 0.7.346) each tongue's
greeting, farewell, cheer and scream (`tongue.jaymoskingdoms.<tongue>.<line>`), by a man and a woman of that land, made
up words heard by those who don't speak it (`Voices.TONGUE_LINES`). Clips go straight into
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/voice/<voice>/<name>.ogg (mono Vorbis); a clip already there is
skipped, so a stopped run picks up where it left off. Delete a clip to remake it; delete a ref wav (or change its seed) to
redesign a voice (then delete its clips too).

    pip install voxcpm soundfile numpy
    python make_voices.py                 # every missing clip
    python make_voices.py narrator        # one voice
    python make_voices.py --sounds        # only rewrite sounds.json (no model needed)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
OUT = os.path.join(ASSETS, "sounds", "voice")
SOUNDS = os.path.join(ASSETS, "sounds.json")
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
}
# (Kingdoms 0.7.346) The tongues' speakers, a man and a woman each (`<tongue>_male`, `<tongue>_female`), designed on that
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

QUESTIONS = ["born", "raised", "childhood", "youth", "turn", "road"]
SPIRIT_LINES = [f"spirit.jaymoskingdoms.{s}.summon.{n}" for s in ("grimsby", "varro", "ambrose", "thessaly") for n in (1, 2, 3)] \
    + ["spirit.jaymoskingdoms.quiet.word.1"]
STORY_END = "story.jaymoskingdoms.summary.end"


def realm_free(text):
    """A tale's realm name is filled in on screen, so the voice says "a realm" / "the realm" instead."""
    return text.replace("in %s", "in a realm").replace("%s", "the realm")


def lines(lang):
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
    for key in SPIRIT_LINES:
        out.append((key.split(".")[2], key, lang[key]))
    for tongue in TONGUE_FOLK:
        for g in ("male", "female"):
            for line in TONGUE_LINES:
                out.append((tongue + "_" + g, f"tongue.jaymoskingdoms.{tongue}.{g}.{line}", lang[f"tongue.jaymoskingdoms.{tongue}.{line}"]))
    return out


def sound_name(key):
    """The same rule as Voices.soundPath: (sound id, file under sounds/voice/<voice>/)."""
    if key.startswith("story.jaymoskingdoms."):
        voice, rest = "narrator", key[len("story.jaymoskingdoms."):]
    elif key.startswith("tongue.jaymoskingdoms."):
        # tongue.jaymoskingdoms.northern.female.greet -> voice.tongue.northern.female.greet, tongue/northern_female_greet
        voice, rest = "tongue", key[len("tongue.jaymoskingdoms."):]
    else:
        voice, rest = key[len("spirit.jaymoskingdoms."):].split(".", 1)
    return "voice." + voice + "." + rest, voice + "/" + rest.replace(".", "_")


def write_sounds(all_lines):
    sounds = {}
    for voice, key, _ in all_lines:
        sid, file = sound_name(key)
        if "tongue" in VOICES.get(voice, {}) and not os.path.exists(os.path.join(OUT, file + ".ogg")):
            # (Kingdoms registers a tongue's line only once its clip exists)
            continue
        entry = {"name": "jaymoskingdoms:voice/" + file}
        if voice == "narrator":
            entry["stream"] = True
        sounds[sid] = {"sounds": [entry]}
    with open(SOUNDS, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(sounds, indent="\t") + "\n")
    print("wrote", SOUNDS, len(sounds), "sounds")


def tidy(wav, rate):
    """Mono, edge silence trimmed (with 80 ms kept), peak at -1 dBFS."""
    import numpy as np
    wav = np.asarray(wav, dtype=np.float32)
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    loud = np.flatnonzero(np.abs(wav) > 0.01)
    if loud.size:
        pad = int(rate * 0.08)
        wav = wav[max(0, loud[0] - pad):loud[-1] + pad]
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
    all_lines = lines(lang)
    write_sounds(all_lines)
    if "--sounds" in sys.argv:
        return
    only = {a for a in sys.argv[1:] if not a.startswith("--")}
    todo = [(v, k, t) for v, k, t in all_lines if (not only or v in only)
            and not os.path.exists(os.path.join(OUT, sound_name(k)[1] + ".ogg"))]
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
            print(f"[{n}/{len(todo)}] wrote", os.path.join(OUT, sound_name(key)[1] + ".ogg"))
    finally:
        # (tongue lines are listed only once made, so list the new ones even if the run is stopped)
        write_sounds(all_lines)


def make(model, rate, sf, lang, voice, key, text):
    v = VOICES[voice]
    ref = os.path.join(REFS, voice + ".wav")
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
    path = os.path.join(OUT, sound_name(key)[1] + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sf.write(path, tidy(wav, rate), rate, format="OGG", subtype="VORBIS")


if __name__ == "__main__":
    main()
