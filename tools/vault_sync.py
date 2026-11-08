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
    python tools/vault_sync.py --reviewer-seen         # the PC reviewer, every round: "I'm here" (Reviewer.md)
    python tools/vault_sync.py --reviewer-away [30]    # in-review tasks, but only if the reviewer hasn't been seen
                                                       # for 30 minutes: the coordinator then reviews them itself

Task statuses: open -> claimed -> in-review -> (changes -> in-review)* -> ready -> sent -> done; blocked = waiting on Andy (moved to Inbox/Done/).
Kanban.md: every sync rewrites a board of the Inbox (Waiting / Being built / In review / Needs Andy / Done) for
Obsidian's Kanban plugin. It is read-only: change tasks with --set, not by dragging cards.
Merged PRs: every sync marks an agent note or Inbox task whose pr: is merged into master as done and moves it to
Agents/Done/ or Inbox/Done/, so the Board stays true whoever merged it.

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
import re
import socket
import subprocess
import sys
import time

BRANCH = "vault-live"
TASK_COMMANDS = ("--claim", "--tasks", "--show", "--set", "--note", "--send", "--reviewer-seen", "--reviewer-away")
REVIEWER_AWAY_MINUTES = 30  # no reviewer round for this long: the coordinator reviews in-review tasks itself
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


def merged_prs():
    """PR numbers squash-merged into origin/master (GitHub puts "(#1234)" at the end of the commit title). Empty if
    master can't be fetched (offline): then nothing is tidied this time."""
    repo = main_checkout()
    if run(["git", "-C", repo, "fetch", "-q", "origin", "master"], check=False).returncode != 0:
        return set()
    log = run(["git", "-C", repo, "log", "-400", "--format=%s", "origin/master"], check=False).stdout
    return {int(m) for m in re.findall(r"\(#(\d+)\)\s*$", log, re.M)}


def tidy_merged(live):
    """Once a note's PR is on master, mark it done and move it to Done/, whoever merged it (a bot, the reviewer,
    the coordinator or Andy). Agent notes go to Agents/Done/, Inbox tasks to Inbox/Done/."""
    merged = None
    for folder in ("Agents", "Inbox"):
        root = os.path.join(live, folder)
        for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
            path = os.path.join(root, name)
            if not name.endswith(".md") or not os.path.isfile(path):
                continue
            props = frontmatter(path)
            prs = {int(n) for n in re.findall(r"\d+", props.get("pr", ""))}
            if not prs or props.get("status") == "done":
                continue
            if merged is None:
                merged = merged_prs()
            hit = sorted(prs & merged)
            if not hit:
                continue
            set_props(path, {"status": "done"})
            add_note(path, f"PR #{hit[0]} is merged into master; moved to {folder}/Done.")
            os.makedirs(os.path.join(root, "Done"), exist_ok=True)
            os.replace(path, os.path.join(root, "Done", name))


KANBAN_LANES = (  # (lane, Inbox statuses); Obsidian's Kanban plugin shows Kanban.md as a board
    ("Waiting for a bot", ("open",)),
    ("Being built", ("claimed", "changes")),
    ("In review", ("in-review",)),
    ("Needs Andy", ("ready", "sent", "blocked")),
)
KANBAN_DONE = 8  # how many finished tasks the Done lane shows


def write_kanban(live):
    """Rewrite Kanban.md (a read-only board for Obsidian's Kanban plugin) from the Inbox tasks and running agents.
    It is regenerated on every sync, so drag-and-drop changes on it don't stick: change tasks with --set."""
    def card(folder, name, p, extra=""):
        who = p.get("claimed_by") or (("for " + p["for"]) if p.get("for") else "")
        bits = [b for b in (who, f"#{p['pr']}" if p.get("pr") else "", p.get("status", ""), extra) if b]
        return f"- [ ] [[{folder}/{name}|{name}]] · " + " · ".join(bits)
    lanes = {lane: [] for lane, _ in KANBAN_LANES}
    tasks_seen = set()
    for _, name, p in sorted(tasks(live)):
        tasks_seen.add(name)
        for lane, statuses in KANBAN_LANES:
            if p.get("status") in statuses:
                lanes[lane].append(card("Inbox", name, p, "fixing review notes" if p.get("status") == "changes" else ""))
    agents = os.path.join(live, "Agents")
    for name in sorted(os.listdir(agents)) if os.path.isdir(agents) else []:
        if name.endswith(".md") and name[:-3] not in tasks_seen:
            p = frontmatter(os.path.join(agents, name))
            if p.get("status") in ("running", "in-review", "blocked"):
                lane = {"running": "Being built", "in-review": "In review", "blocked": "Needs Andy"}[p["status"]]
                lanes[lane].append(card("Agents", name[:-3], p))
    done_dir = os.path.join(live, "Inbox", "Done")
    done = sorted((os.path.getmtime(os.path.join(done_dir, n)), n[:-3]) for n in os.listdir(done_dir)
                  if n.endswith(".md")) if os.path.isdir(done_dir) else []
    out = ["---", "", "kanban-plugin: board", "", "---", "",
           f"<!-- Written by tools/vault_sync.py on every sync ({time.strftime('%Y-%m-%d %H:%M')}). Don't edit. -->", ""]
    for lane, _ in KANBAN_LANES:
        out += [f"## {lane}", ""] + (lanes[lane] or []) + ["", ""]
    out += ["## Done", "", "**Complete**"]
    out += [f"- [x] [[Inbox/Done/{n}|{n}]]" for _, n in reversed(done[-KANBAN_DONE:])]
    out += ["", "", "%% kanban:settings", "```", '{"kanban-plugin":"board","list-collapse":[false,false,false,false,false]}',
            "```", "%%", ""]
    text = "\n".join(out)
    path = os.path.join(live, "Kanban.md")
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    if old.split("-->", 1)[-1] != text.split("-->", 1)[-1]:  # only rewrite when a card changed, not just the time
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)


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
        try:
            tidy_merged(live)
        except Exception as e:  # never let tidying stop a sync
            print(f"(tidy skipped: {e})", file=sys.stderr)
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
            try:  # the board reflects the merged state, so it's written after the merge
                write_kanban(live)
                git(live, "add", "Kanban.md")
                if git(live, "diff", "--cached", "--quiet", check=False).returncode != 0:
                    git(live, "commit", "-q", "-m", "Kanban board")
            except Exception as e:
                print(f"(kanban skipped: {e})", file=sys.stderr)
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


SPECIALISTS = {"hermes-modeller": "prometheus", "hermes-writer": "quill"}  # bot name -> the name Andy calls it


def meant_for(props, agent):
    """`for: hermes` covers every Hermes builder named hermes-<something>; `for: any` (or blank) covers everyone.
    Specialist bots (the modeller, the writer) only take tasks addressed to them, by bot name or nickname."""
    target = (props.get("for", "any") or "any").strip().lower()
    if agent in SPECIALISTS:
        return target in (agent, SPECIALISTS[agent])
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
    cmd = next(a for a in args if a in TASK_COMMANDS)
    rest = args[args.index(cmd) + 1:]
    sync(live)
    if cmd == "--reviewer-seen":
        with open(os.path.join(live, "Reviewer.md"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(f"---\nlast_seen: {int(time.time())}\nseen_at: {time.strftime('%Y-%m-%d %H:%M')}\n---\n"
                     "The PC reviewer writes this every round (/vault-review). If it's old, the coordinator reviews.\n")
        sync(live)
        return
    if cmd == "--reviewer-away":
        minutes = int(rest[0]) if rest else REVIEWER_AWAY_MINUTES
        seen = frontmatter(os.path.join(live, "Reviewer.md")).get("last_seen", "0") \
            if os.path.exists(os.path.join(live, "Reviewer.md")) else "0"
        if time.time() - int(seen or 0) < minutes * 60:
            return  # the reviewer is around: leave in-review tasks to it
        for _, name, _ in sorted(tasks(live, ("in-review",))):
            print(name)
        return
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
    if any(a in sys.argv for a in TASK_COMMANDS):
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
