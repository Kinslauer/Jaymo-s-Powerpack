"""Pilot voices for Jaymo's Kingdoms, made with VoxCPM2 (Apache-2.0, openbmb/VoxCPM2).

Each voice is designed once from a description (no real person is cloned), saved as voices/refs/<voice>.wav, then every
line is cloned from that one clip so the voice stays the same. Output: out/<voice>/<name>.ogg (mono Vorbis), laid out as
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/voice/. Delete a ref wav (or change its seed) to redesign it.

    pip install voxcpm soundfile numpy
    python make_voices.py            # all
    python make_voices.py grimsby    # one voice
"""
import os
import sys

import numpy as np
import soundfile as sf
from voxcpm import VoxCPM

HERE = os.path.dirname(os.path.abspath(__file__))
REFS = os.path.join(HERE, "refs")
OUT = os.path.join(HERE, "out")

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

# (voice, file name, text). The names match the sound ids in sounds.json.
LINES = [
    ("narrator", "born_title", "Where were you born? Your blood, and the land that bore you."),
    ("grimsby", "summon_1", "Yes, yes, I am coming. Must you always shout?"),
    ("grimsby", "summon_2", "Called again. I had just got comfortable. Well, what is it this time?"),
    ("grimsby", "summon_3", "Out I come. Stand behind me, and for once do as you are told."),
    ("varro", "summon_1", "Varro here! Point me at them!"),
    ("varro", "summon_2", "Called and ready. Name your target, recruit."),
    ("varro", "summon_3", "Reporting! Do not make me wait on you."),
    ("ambrose", "summon_1", "Here I am, little one. No need to shout. I heard you the first time."),
    ("ambrose", "summon_2", "Of course, of course. Let me have a look at you. Good. Still in one piece."),
    ("ambrose", "summon_3", "Up and about, then. Lead on. I shall be a step behind you, as always."),
    ("thessaly", "summon_1", "Yes, yes, dear, I am coming. Do mind the margins of your aura, they are most untidy."),
    ("thessaly", "summon_2", "Called again! I was in the middle of a footnote. Well, what is it this time?"),
    ("thessaly", "summon_3", "Out I come. Hands where I can see them, dear, and no mumbling the incantations."),
    ("quiet", "word_1", "Stay."),
]


def tidy(wav, rate):
    """Mono, edge silence trimmed (with 80 ms kept), peak at -1 dBFS."""
    wav = np.asarray(wav, dtype=np.float32)
    if wav.ndim > 1:
        wav = wav.mean(axis=1)
    loud = np.flatnonzero(np.abs(wav) > 0.01)
    if loud.size:
        pad = int(rate * 0.08)
        wav = wav[max(0, loud[0] - pad):loud[-1] + pad]
    peak = float(np.max(np.abs(wav))) or 1.0
    return wav * (0.89 / peak)


def main():
    only = set(sys.argv[1:])
    os.makedirs(REFS, exist_ok=True)
    model = VoxCPM.from_pretrained("openbmb/VoxCPM2", load_denoiser=False)
    rate = model.tts_model.sample_rate
    for name, v in VOICES.items():
        if only and name not in only:
            continue
        ref = os.path.join(REFS, name + ".wav")
        text = v.get("ref", REF_TEXT)
        if not os.path.exists(ref):
            wav = model.generate(text="(" + v["desc"] + ")" + text, cfg_value=2.0, inference_timesteps=10, seed=v["seed"])
            sf.write(ref, tidy(wav, rate), rate)
            print("designed", ref)
        for voice, file, line in LINES:
            if voice != name:
                continue
            wav = model.generate(text=line, prompt_wav_path=ref, prompt_text=text, reference_wav_path=ref,
                                 cfg_value=2.0, inference_timesteps=10, seed=v["seed"])
            os.makedirs(os.path.join(OUT, voice), exist_ok=True)
            path = os.path.join(OUT, voice, file + ".ogg")
            sf.write(path, tidy(wav, rate), rate, format="OGG", subtype="VORBIS")
            print("wrote", path)


if __name__ == "__main__":
    main()
