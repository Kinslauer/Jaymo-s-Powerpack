#!/usr/bin/env python3
"""Turns pending change notes into version bumps and MODLOG entries. Run on master after a merge.

    py -3 tools/release.py              do it (writes files; commit them yourself)
    py -3 tools/release.py --dry-run    print what it would do, change nothing
    py -3 tools/release.py --check      exit 1 if notes are waiting (pbdr uses it to warn)

The release workflow (.github/workflows/release.yml) runs this on every push to master and commits the result, so
nobody normally runs it by hand. It exists because every PR used to bump the same `version=` line and append to the
end of the same MODLOG, and each squash merge made every later PR "conflict" on those lines.

A PR now adds a note instead (see changes/README.md): `changes/<powers|kingdoms|kitchen>/<slug>.md`, first line
`# <title>`, then the MODLOG text. For each note, oldest first, this script:
  1. raises that mod's patch version in its gradle.properties (0.21.64 -> 0.21.65);
  2. appends `## <version> (<date>): <title> (#<PR>)` and the note's text to the end of the mod's MODLOG;
  3. deletes the note.
Then it updates the version table in docs/vault/Home.md and regenerates docs/codemap/ (also kept out of PRs).
"""
import datetime
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = {  # note folder -> (name, properties file, MODLOG, row name on docs/vault/Home.md)
    "powers": ("Powers", "gradle.properties", "MODLOG.md", "Jaymo's Powers (and the pack)"),
    "kingdoms": ("Kingdoms", "kingdoms/gradle.properties", "kingdoms/MODLOG.md", "Jaymo's Kingdoms"),
    "kitchen": ("Kitchen", "kitchen/gradle.properties", "kitchen/MODLOG.md", "Jaymo's Kitchen"),
}
VERSION = re.compile(r"^(version\s*=\s*)(\S+)", re.M)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True).stdout.strip()


def pending():
    """[(added-at, mod, path)] for every note, oldest first (by the commit that added it; uncommitted ones last)."""
    notes = []
    for mod in MODS:
        for f in sorted((ROOT / "changes" / mod).glob("*.md")):
            rel = f.relative_to(ROOT).as_posix()
            when = git("log", "--diff-filter=A", "--format=%ct", "-1", "--", rel)
            notes.append((int(when) if when else 2 ** 40, mod, f))
    return sorted(notes, key=lambda n: (n[0], n[2].name))


def read_note(f):
    """(title, body). The title is the first `# ` line; without one it's the file name."""
    lines = f.read_text(encoding="utf-8").strip().splitlines()
    title = f.stem.replace("-", " ").replace("_", " ")
    if lines and lines[0].startswith("# "):
        title = lines[0][2:].strip()
        lines = lines[1:]
    return title, "\n".join(lines).strip()


def pr_number(f):
    """The PR that added the note, from its squash-merge subject `... (#1136)`."""
    m = re.search(r"\(#(\d+)\)\s*$", git("log", "--diff-filter=A", "--format=%s", "-1", "--",
                                          f.relative_to(ROOT).as_posix()))
    return m.group(1) if m else None


def bump(v):
    parts = v.split(".")
    parts[-1] = str(int(parts[-1]) + 1)
    return ".".join(parts)


def main():
    args = set(sys.argv[1:])
    notes = pending()
    if "--check" in args:
        for _, mod, f in notes:
            print(f"pending: {f.relative_to(ROOT).as_posix()}")
        return 1 if notes else 0
    dry = "--dry-run" in args
    if not notes:
        print("No change notes waiting.")
        return 0

    today = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    props, logs, released = {}, {}, {}
    for _, mod, f in notes:
        name, prop_file, log_file, _ = MODS[mod]
        if prop_file not in props:
            props[prop_file] = (ROOT / prop_file).read_text(encoding="utf-8")
            logs[log_file] = (ROOT / log_file).read_text(encoding="utf-8")
        new = bump(VERSION.search(props[prop_file]).group(2))
        props[prop_file] = VERSION.sub(lambda m: m.group(1) + new, props[prop_file], count=1)
        title, body = read_note(f)
        pr = pr_number(f)
        entry = f"## {new} ({today}): {title}" + (f" (#{pr})" if pr else "") + "\n\n" + body + "\n"
        logs[log_file] = logs[log_file].rstrip("\n") + "\n\n" + entry
        released.setdefault(mod, []).append(new)
        print(f"{name} {new}: {f.relative_to(ROOT).as_posix()}" + (f" (PR #{pr})" if pr else ""))
        if dry:
            print("    " + entry.replace("\n", "\n    ").rstrip())

    summary = ", ".join(f"{MODS[m][0]} {vs[-1]}" for m, vs in released.items())
    if dry:
        print(f"\nDry run: would release {summary}; nothing written.")
        return 0

    for path, text in {**props, **logs}.items():
        (ROOT / path).write_bytes(text.encode("utf-8"))
    for _, _, f in notes:
        f.unlink()
    home = ROOT / "docs/vault/Home.md"
    if home.exists():
        text = home.read_text(encoding="utf-8")
        for mod, vs in released.items():
            row = re.escape(MODS[mod][3])
            text = re.sub(rf"^(\| {row} \| \*\*)[^*]+(\*\*)", lambda m: m.group(1) + vs[-1] + m.group(2), text,
                          flags=re.M)
        home.write_bytes(text.encode("utf-8"))
    subprocess.run([sys.executable, str(ROOT / "tools/codemap.py")], cwd=ROOT, check=False)
    print(f"Released {summary}.")
    (ROOT / "build").mkdir(exist_ok=True)
    (ROOT / "build/release-summary.txt").write_text(summary, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
