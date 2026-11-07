"""Sync the vault's live folder with the `vault-live` branch on GitHub, so cloud sessions see it too.

    python tools/vault_sync.py              # commit local changes, merge the branch, push
    python tools/vault_sync.py --setup      # once: make the live folder a checkout of vault-live (keeps your files)
    python tools/vault_sync.py --status     # print the running/in-review/blocked agents and the open Inbox tasks
    python tools/vault_sync.py --if-older 60   # skip if it synced under 60 s ago
    python tools/vault_sync.py --hook       # for Claude Code hooks: only after vault edits, never fails

Inbox tasks (each syncs first and after, so everyone sees the change at once):
    python tools/vault_sync.py --claim hermes-a        # take the oldest task sent back to hermes-a, else the oldest
                                                       # open one for hermes-a, hermes or any; print it; exit 3 if none
    python tools/vault_sync.py --claim hermes-fixer --fixes   # a fixer bot: the oldest task sent back to anyone
    python tools/vault_sync.py --tasks in-review       # task names with that status, one per line
    python tools/vault_sync.py --show <task>           # print a task
    python tools/vault_sync.py --set <task> status=in-review pr=1234   # change a task's properties
    python tools/vault_sync.py --note <task> "Reviewer: ..."           # add a line under the task's Notes
    python tools/vault_sync.py --send <task>           # reviewed and passed: hand it to the coordinator (status: ready)

Task statuses: open -> claimed -> in-review -> (changes -> in-review)* -> ready -> sent -> done; blocked = waiting on Andy (moved to Inbox/Done/).

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


def task_path(live, name):
    name = name[:-3] if name.endswith(".md") else name
    path = os.path.join(live, "Inbox", name + ".md")
    if not os.path.exists(path):
        raise SystemExit(f"No Inbox task called {name}")
    return path


def set_props(path, changes):
    """Set frontmatter properties (single-line values) in place, adding any that are missing."""
    text = open(path, encoding="utf-8").read()
    lines = text.split("\n")
    if lines[0].strip() != "---":
        lines = ["---", "---"] + lines
    end = lines.index("---", 1)
    for key, value in changes.items():
        for i in range(1, end):
            if lines[i].split(":", 1)[0].strip() == key and not lines[i].startswith(" "):
                lines[i] = f"{key}: {value}"
                break
        else:
            lines.insert(end, f"{key}: {value}")
            end += 1
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines))


def add_note(path, note):
    text = open(path, encoding="utf-8").read().rstrip("\n")
    stamp = time.strftime("%Y-%m-%d %H:%M")
    if "\n## Notes" not in text:
        text += "\n\n## Notes"
    if text.endswith("\n- ") or text.endswith("\n-"):  # the template's empty bullet
        text = text[:text.rfind("\n")]
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text + f"\n- {stamp} {note}\n")


def tasks(live, wanted=None):
    root = os.path.join(live, "Inbox")
    found = []
    for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
        if name.endswith(".md"):
            p = frontmatter(os.path.join(root, name))
            if wanted is None or p.get("status") in wanted:
                found.append((os.path.getmtime(os.path.join(root, name)), name[:-3], p))
    return found


def meant_for(props, agent):
    """`for: hermes` covers every Hermes bot named hermes-<something>; `for: any` (or blank) covers everyone."""
    target = props.get("for", "any") or "any"
    return target in (agent, "any") or agent.startswith(target + "-")


def claim(live, agent, fixes=False):
    """The oldest task sent back to this agent, else the oldest open one meant for it. With fixes=True (a fixer
    bot), the oldest task sent back to anyone, which it takes over. Exit 3 if none."""
    back = tasks(live, ("changes",))
    if fixes:
        pick = sorted(back)[0] if back else None
    else:
        mine = [t for t in back if t[2].get("claimed_by", "") == agent]
        fresh = [t for t in tasks(live, ("open",)) if meant_for(t[2], agent)]
        pick = sorted(mine)[0] if mine else (sorted(fresh)[0] if fresh else None)
    if not pick:
        print("No task waiting.")
        sys.exit(3)
    path = task_path(live, pick[1])
    before = pick[2].get("claimed_by", "")
    set_props(path, {"status": "claimed", "claimed_by": agent})
    if fixes and before and before != agent:
        add_note(path, f"{agent} took over the review fixes from {before}.")
    sync(live)
    if frontmatter(path).get("claimed_by") != agent:  # someone else won the race
        print("No task waiting.")
        sys.exit(3)
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"Task: {pick[1]}  ({path})\n")
    print(open(path, encoding="utf-8").read())


def task_command(live):
    args = sys.argv[1:]
    cmd = next(a for a in args if a in ("--claim", "--tasks", "--show", "--set", "--note", "--send"))
    rest = args[args.index(cmd) + 1:]
    sync(live)
    if cmd == "--claim":
        names = [a for a in rest if not a.startswith("--")]
        return claim(live, names[0] if names else "any", fixes="--fixes" in rest)
    if cmd == "--tasks":
        for _, name, _ in sorted(tasks(live, tuple(rest) or None)):
            print(name)
        return
    path = task_path(live, rest[0])
    if cmd == "--show":
        sys.stdout.reconfigure(encoding="utf-8")
        return print(open(path, encoding="utf-8").read())
    if cmd == "--set":
        set_props(path, dict(kv.split("=", 1) for kv in rest[1:]))
    elif cmd == "--note":
        add_note(path, " ".join(rest[1:]))
    elif cmd == "--send":
        set_props(path, {"status": "ready"})
        add_note(path, "Reviewed; Andy allowed sending it to the coordinator.")
    sync(live)
    print(f"{rest[0]}: {frontmatter(path).get('status')}")


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
    if any(a in sys.argv for a in ("--claim", "--tasks", "--show", "--set", "--note", "--send")):
        return task_command(live)
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
