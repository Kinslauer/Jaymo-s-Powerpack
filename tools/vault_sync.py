"""Sync the vault's live folder with the `vault-live` branch on GitHub, so cloud sessions see it too.

    python tools/vault_sync.py              # commit local changes, merge the branch, push
    python tools/vault_sync.py --setup      # once: make the live folder a checkout of vault-live (keeps your files)
    python tools/vault_sync.py --status     # print the running/in-review/blocked agents and the open Inbox tasks
    python tools/vault_sync.py --if-older 60   # skip if it synced under 60 s ago
    python tools/vault_sync.py --hook       # for Claude Code hooks: only after vault edits, never fails

The live folder is docs/vault/Live in the main checkout: on Andy's PC a junction to E:/Jaymo Vault Live (outside git),
in a cloud session a plain folder that --setup clones the branch into. Either way it is its own small git checkout of
the orphan branch `vault-live`, which holds only live notes: Agents/, Inbox/ (tasks from the coordinator),
Decisions.md and Open PRs.md. Nothing in it ever touches master.

Conflicts: each agent note has one owner and Inbox tasks are claimed by one agent, so they rarely clash.
Decisions.md merges by keeping both sides' lines (.gitattributes merge=union). Anything else that clashes keeps the
local copy (merge -X ours), and the other side's version stays in the branch history.
"""
import json
import os
import socket
import subprocess
import sys
import time

BRANCH = "vault-live"
SEED = {  # written by --setup when the branch doesn't exist yet
    ".gitattributes": "Decisions.md merge=union\n*.md text eol=lf\n",
    ".gitignore": ".obsidian/\n.trash/\n.sync-stamp\n.sync-lock\n",
    "Decisions.md": "# Decisions\n\nEvery decision Andy makes, newest last (template: Templates/Decision.md).\n",
    "Agents/Done/.keep": "",
    "Inbox/Done/.keep": "",
}


def run(args, cwd=None, check=True):
    out = subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8")
    if check and out.returncode != 0:
        raise SystemExit(f"{' '.join(args)} failed: {(out.stderr or out.stdout).strip()}")
    return out


def git(live, *args, check=True):
    return run(["git", "-C", live, *args], check=check)


def main_checkout():
    common = run(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"]).stdout.strip()
    return os.path.dirname(common)


def live_dir():
    return os.path.join(main_checkout(), "docs", "vault", "Live")


def remote_url():
    return run(["git", "-C", main_checkout(), "remote", "get-url", "origin"]).stdout.strip()


def is_checkout(live):
    return os.path.isdir(os.path.join(live, ".git"))


def setup(live):
    """Make the live folder a checkout of vault-live. Local files win over the branch's on first sync."""
    os.makedirs(live, exist_ok=True)
    if is_checkout(live):
        print(f"{live} is already set up.")
        return
    git(live, "init", "-q")
    git(live, "remote", "add", "origin", remote_url())
    git(live, "config", "core.autocrlf", "false")
    identity(live)
    git(live, "symbolic-ref", "HEAD", f"refs/heads/{BRANCH}")
    exists = git(live, "ls-remote", "--heads", "origin", BRANCH).stdout.strip()
    if exists:
        git(live, "fetch", "-q", "origin", BRANCH)
        git(live, "reset", "-q", f"origin/{BRANCH}")  # index = branch, files on disk untouched
        git(live, "checkout", "-q", "--", ".gitattributes", ".gitignore", check=False)
        # Files the branch has but this folder doesn't: bring them in (never overwrite local ones).
        for path in git(live, "ls-files", "--deleted").stdout.split("\n"):
            if path:
                git(live, "checkout", "-q", "--", path)
    for path, text in SEED.items():
        full = os.path.join(live, path)
        if not os.path.exists(full):
            os.makedirs(os.path.dirname(full), exist_ok=True)
            with open(full, "w", encoding="utf-8", newline="\n") as fh:
                fh.write(text)
    git(live, "branch", f"--set-upstream-to=origin/{BRANCH}", check=False)
    sync(live)
    print(f"{live} now syncs with the {BRANCH} branch.")


def identity(live):
    if not git(live, "config", "user.name", check=False).stdout.strip():
        name = run(["git", "-C", main_checkout(), "config", "user.name"], check=False).stdout.strip()
        email = run(["git", "-C", main_checkout(), "config", "user.email"], check=False).stdout.strip()
        git(live, "config", "user.name", name or "vault-sync")
        git(live, "config", "user.email", email or "vault-sync@localhost")


def sync(live):
    lock = os.path.join(live, ".sync-lock")
    try:
        fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        os.close(fd)
    except FileExistsError:
        if time.time() - os.path.getmtime(lock) < 120:
            return "busy"  # another agent is syncing right now
        os.remove(lock)
        return sync(live)
    try:
        git(live, "add", "-A")
        if git(live, "diff", "--cached", "--quiet", check=False).returncode != 0:
            git(live, "commit", "-q", "-m", f"vault sync from {socket.gethostname()}")
        for attempt in range(3):
            if git(live, "ls-remote", "--heads", "origin", BRANCH).stdout.strip():
                git(live, "fetch", "-q", "origin", BRANCH)
                if git(live, "rev-parse", "-q", "--verify", "HEAD", check=False).returncode != 0:
                    git(live, "reset", "-q", "--hard", f"origin/{BRANCH}")
                else:
                    merged = git(live, "merge", "-q", "--no-edit", "-X", "ours", "--allow-unrelated-histories",
                                 f"origin/{BRANCH}", check=False)
                    if merged.returncode != 0:  # e.g. a note deleted on one side, edited on the other: keep local
                        git(live, "add", "-A")
                        git(live, "commit", "-q", "--no-edit", check=False)
            if git(live, "push", "-q", "origin", f"HEAD:refs/heads/{BRANCH}", check=False).returncode == 0:
                break
            time.sleep(2 ** attempt)
        with open(os.path.join(live, ".sync-stamp"), "w") as fh:
            fh.write(str(time.time()))
        return "synced"
    finally:
        try:
            os.remove(lock)
        except OSError:
            pass


def frontmatter(path):
    props, lines = {}, open(path, encoding="utf-8").read().split("\n")
    if not lines or lines[0].strip() != "---":
        return props
    key = None
    for line in lines[1:]:
        if line.strip() == "---":
            break
        if line.startswith("  - ") and key:
            props[key] = (props[key] + ", " if props[key] else "") + line[4:].strip()
        elif ":" in line:
            key, _, value = line.partition(":")
            key, props[key] = key.strip(), value.strip()
    return props


def status(live):
    for folder, wanted in (("Agents", ("running", "in-review", "blocked")), ("Inbox", ("open", "claimed"))):
        print(f"## {folder}")
        root = os.path.join(live, folder)
        shown = 0
        for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
            if name.endswith(".md"):
                p = frontmatter(os.path.join(root, name))
                if p.get("status") in wanted:
                    shown += 1
                    print(f"- {name[:-3]}: " + " · ".join(f"{k}: {v}" for k, v in p.items() if v))
        if not shown:
            print("- (none)")


def hook_wants_sync():
    try:
        event = json.loads(sys.stdin.read() or "{}")
    except ValueError:
        return True
    path = (event.get("tool_input") or {}).get("file_path")
    if not path:
        return True  # session start
    path = os.path.normcase(os.path.normpath(path)).replace("\\", "/")
    live = os.path.normcase(os.path.realpath(live_dir())).replace("\\", "/")
    return path.startswith(live + "/") or "/docs/vault/live/" in path.lower()


def main():
    live = live_dir()
    if "--setup" in sys.argv:
        return setup(live)
    if "--hook" in sys.argv and not hook_wants_sync():
        return
    if not is_checkout(live):
        if "--hook" in sys.argv:
            return
        raise SystemExit(f"{live} isn't set up yet: run python tools/vault_sync.py --setup")
    if "--if-older" in sys.argv:
        seconds = int(sys.argv[sys.argv.index("--if-older") + 1])
        try:
            if time.time() - os.path.getmtime(os.path.join(live, ".sync-stamp")) < seconds:
                return
        except OSError:
            pass
    result = sync(live)
    if "--status" in sys.argv:
        sys.stdout.reconfigure(encoding="utf-8")
        status(live)
    elif "--hook" not in sys.argv:
        print(f"{result}: {live} <-> {BRANCH}")


if __name__ == "__main__":
    try:
        main()
    except (SystemExit, Exception):
        if "--hook" in sys.argv:  # a hook must never get in an agent's way
            sys.exit(0)
        raise
