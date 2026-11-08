#!/usr/bin/env python3
"""Moves an older branch onto change notes (changes/README.md). Run in the branch's worktree, then commit.

    py -3 tools/change_note.py convert [base-ref]     (default origin/master)

For each mod it takes the MODLOG entries this branch added (since its merge base with master), writes each one as
changes/<mod>/<branch>.md (version dropped from the heading), puts master's MODLOG back, sets `version=` back to
master's and puts master's docs/codemap/ back. Check the notes it wrote, then `py -3 tools/pr_checks.py`.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = {"powers": ("gradle.properties", "MODLOG.md"),
        "kingdoms": ("kingdoms/gradle.properties", "kingdoms/MODLOG.md"),
        "kitchen": ("kitchen/gradle.properties", "kitchen/MODLOG.md")}
HEADING = re.compile(r"^##\s*\d+(?:\.\d+)+\s*(?:\([^)]*\))?\s*:?\s*(.*)$")
VERSION = re.compile(r"^(version\s*=\s*)(\S+)", re.M)


def git(*args, check=True):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=check).stdout


def added_lines(base, path):
    """The lines this branch added to path, in order (pure additions only; enough for appended entries)."""
    out = []
    for line in git("diff", "-U0", base, "HEAD", "--", path).splitlines():
        if line.startswith("+") and not line.startswith("+++"):
            out.append(line[1:])
    return out


def main():
    if len(sys.argv) < 2 or sys.argv[1] != "convert":
        print(__doc__)
        return 2
    base = git("merge-base", sys.argv[2] if len(sys.argv) > 2 else "origin/master", "HEAD").strip()
    slug = re.sub(r"[^\w.-]+", "-", git("rev-parse", "--abbrev-ref", "HEAD").strip().split("/")[-1]).strip("-")
    for mod, (props, log) in MODS.items():
        entries, cur = [], None
        for line in added_lines(base, log):
            m = HEADING.match(line)
            if m:
                cur = [m.group(1).strip() or slug, []]
                entries.append(cur)
            elif cur:
                cur[1].append(line)
            elif line.strip():
                print(f"{log}: added text outside an entry, check it by hand: {line[:80]}")
        for i, (title, body) in enumerate(entries):
            note = ROOT / "changes" / mod / (slug + (f"-{i + 1}" if i else "") + ".md")
            note.parent.mkdir(parents=True, exist_ok=True)
            note.write_bytes(("# " + title + "\n" + "\n".join(body).strip("\n") + "\n").encode("utf-8"))
            print(f"wrote {note.relative_to(ROOT).as_posix()}")
        if entries:
            git("checkout", base, "--", log)
        old = VERSION.search(git("show", f"{base}:{props}"))
        text = (ROOT / props).read_text(encoding="utf-8")
        if old and VERSION.search(text).group(2) != old.group(2):
            (ROOT / props).write_bytes(VERSION.sub(lambda m: m.group(1) + old.group(2), text, count=1).encode("utf-8"))
            print(f"{props}: version back to {old.group(2)}")
    if git("diff", "--name-only", base, "HEAD", "--", "docs/codemap"):
        git("checkout", base, "--", "docs/codemap")
        print("docs/codemap: back to master's (the release step regenerates it)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
