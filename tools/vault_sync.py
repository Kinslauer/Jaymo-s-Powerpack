"""Sync the vault's live folder with the `vault-live` branch on GitHub, so cloud sessions see it too.

    python tools/vault_sync.py              # commit local changes, merge the branch, push
    python tools/vault_sync.py --setup      # once: make the live folder a checkout of vault-live (keeps your files)
    python tools/vault_sync.py --status     # print the running/in-review/blocked agents and the open Inbox tasks
    python tools/vault_sync.py --if-older 60   # skip if it synced under 60 s ago
    python tools/vault_sync.py --hook       # for Claude Code hooks: only after vault edits, never fails

Inbox tasks (each syncs first and after, so everyone sees the change at once):
    python tools/vault_sync.py --claim hermes-a        # take the oldest task sent back to hermes-a, else the oldest
                                                       # open one for hermes-a, hermes or any; print it; exit 3 if none
    python tools/vault_sync.py --claim hermes-fixer --fixes   # a fixer bot: the oldest task sent back to anyone, else
                                                       # the oldest open one for it (hermes-fixer or hephaestus)
    python tools/vault_sync.py --tasks in-review       # task names with that status, one per line
    python tools/vault_sync.py --show <task>           # print a task
    python tools/vault_sync.py --set <task> status=in-review pr=1234   # change a task's properties
    python tools/vault_sync.py --note <task> "Reviewer: ..."           # add a line under the task's Notes
    python tools/vault_sync.py --new <task> for=hephaestus from=... < body.md   # a new open task, body from stdin
    python tools/vault_sync.py --send <task>           # reviewed and passed: hand it to the coordinator (status: ready)
    python tools/vault_sync.py --reviewer-seen         # the PC reviewer, every round: "I'm here" (Reviewer.md)
    python tools/vault_sync.py --reviewer-away [30]    # in-review tasks, but only if the reviewer hasn't been seen
                                                       # for 30 minutes: the coordinator then reviews them itself

Task statuses: open -> claimed -> in-review -> (changes -> in-review)* -> ready -> sent -> done; blocked = waiting on Andy (moved to Inbox/Done/).
Kanban.md: every sync rewrites a board of the Inbox (Waiting / Being built / In review / Needs Andy / Done) for
Obsidian's Kanban plugin. It is read-only: change tasks with --set, not by dragging cards.
Stale work: a claim or running agent note with no sign of work for 30 minutes is handed back or marked stalled, and a running
note whose task is done is moved to Done (tidy_stale), so dead runs don't sit in Being built.
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
TASK_COMMANDS = ("--claim", "--review-queue", "--tasks", "--show", "--set", "--note", "--send", "--new", "--reviewer-seen", "--reviewer-away")
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
    tidy_stale(live)


# (Andy, 2026-11-10: "claims should clear after being stale for 30m") minutes with no sign of work before a claim or
# a running note counts as dead. Signs of work: the note itself, the agent's note for it, and the task's worktree
# (E:/wt/<task>, E:/wt/<task>-text: last commit or any uncommitted file), so a long build doesn't count as stale.
STALE_MINUTES = 30
CLOUD_STALE_MINUTES = 120  # cloud runs (claude-cloud) leave no local worktree to watch: give them longer
WORKTREES = "E:/wt"


def last_activity(live, task, *paths):
    """Newest sign of work on `task` (epoch seconds): the given note files, its agent note, its worktrees."""
    times = [os.path.getmtime(p) for p in paths if os.path.isfile(p)]
    agent = os.path.join(live, "Agents", task + ".md")
    if os.path.isfile(agent):
        times.append(os.path.getmtime(agent))
    for wt in (os.path.join(WORKTREES, task), os.path.join(WORKTREES, task + "-text")):
        if not os.path.isdir(wt):
            continue
        last = run(["git", "-C", wt, "log", "-1", "--format=%ct"], check=False).stdout.strip()
        if last.isdigit():
            times.append(int(last))
        changed = run(["git", "-C", wt, "status", "--porcelain"], check=False).stdout.splitlines()
        for line in changed[:200]:
            f = os.path.join(wt, line[3:].strip().strip('"').split(" -> ")[-1])
            if os.path.exists(f):
                times.append(os.path.getmtime(f))
    return max(times) if times else 0


def stale(live, task, path, props):
    who = (props.get("claimed_by") or "").strip().lower()
    if not who and props.get("status") == "writing":  # a text pass: the writer holds it
        who = (props.get("writer") or "").strip().lower()
    if who.startswith("hermes") and not os.path.isdir(WORKTREES):
        # A Hermes bot builds in E:/wt on Andy's PC. A cloud VM can't see that worktree, so it would hand back a claim
        # the bot is still building (and the bot then gives up). Only the PC judges Hermes claims.
        return False
    limit = CLOUD_STALE_MINUTES if who == "claude-cloud" else STALE_MINUTES
    return time.time() - last_activity(live, task, path) > limit * 60


def tidy_stale(live):
    """Clears what dead work leaves behind (Andy, 2026-11-10: "lots of mistakes and wasted time on stale processes"):
    - an agent note still `running` whose Inbox task is already done: done, moved to Agents/Done/;
    - an agent note `running` with no sign of work for STALE_MINUTES: `stalled` (shown under Needs Andy, not Being built);
    - an Inbox task `claimed` with no sign of work for STALE_MINUTES: handed back (`open`, or `changes` when it has
      a PR) with its claim cleared, so the next bot or agent takes it instead of everyone skipping it;
    - one `writing` (a text pass) with no sign of work: back to `in-review` with no text pass, so the writer takes it again."""
    agents, inbox = os.path.join(live, "Agents"), os.path.join(live, "Inbox")
    done_tasks = {n[:-3] for n in os.listdir(os.path.join(inbox, "Done"))} if os.path.isdir(os.path.join(inbox, "Done")) else set()
    for name in sorted(os.listdir(agents)) if os.path.isdir(agents) else []:
        path = os.path.join(agents, name)
        if not name.endswith(".md") or not os.path.isfile(path):
            continue
        props = frontmatter(path)
        if props.get("status") != "running":
            continue
        task = (props.get("task", "").split() or [name[:-3]])[0]
        if task in done_tasks or name[:-3] in done_tasks:
            set_props(path, {"status": "done"})
            add_note(path, f"Its Inbox task `{task}` is done; moved to Agents/Done.")
            os.makedirs(os.path.join(agents, "Done"), exist_ok=True)
            os.replace(path, os.path.join(agents, "Done", name))
        elif stale(live, task, path, props):
            set_props(path, {"status": "stalled"})
            add_note(path, f"No sign of work for {STALE_MINUTES} min: marked stalled (whoever ran it stopped).")
    for name in sorted(os.listdir(inbox)) if os.path.isdir(inbox) else []:
        path = os.path.join(inbox, name)
        if not name.endswith(".md") or not os.path.isfile(path):
            continue
        props = frontmatter(path)
        if props.get("status") not in ("claimed", "writing") or not stale(live, name[:-3], path, props):
            continue
        if props.get("status") == "writing":  # a text pass that died: back in the writer's queue, not a builder's
            back, who = "in-review", props.get("writer") or "the writer"
            set_props(path, {"status": back, "text_pass": ""})
        else:
            back = "changes" if props.get("pr", "").strip() else "open"
            who = props.get("claimed_by") or "someone"
            set_props(path, {"status": back, "claimed_by": ""})
        add_note(path, f"{who}'s claim had no sign of work for {STALE_MINUTES} min: handed back as {back}.")


KANBAN_LANES = (  # (lane, Inbox statuses); Obsidian's Kanban plugin shows Kanban.md as a board
    ("Waiting for a bot", ("open",)),
    ("Being built", ("claimed", "changes", "writing")),  # being built or text-pass being written
    ("In review", ("in-review",)),
    ("Needs Andy", ("ready", "sent", "blocked")),
)
KANBAN_DONE = 8  # how many finished tasks the Done lane shows
# Who's on a card, at a glance (Andy, 2026-11-09: "the api and youth's bots need differentiators"): Hermes bots by
# the names Andy gave them, the cloud agent (Anthropic API, tools/cloud-agent) and Claude Code threads each marked.
# Each worker has its own emoji (Andy: "each should have a different emoji").
HERMES_BOTS = {"hermes-a": "☀️ Apollo", "hermes-b": "🍇 Bacchus", "hermes-fixer": "🔨 Hephaestus",
               "hermes-modeller": "🔥 Prometheus", "hermes-writer": "🪶 Quill"}
CLOUD_AGENT = "claude-cloud"


def worker_label(who):
    """`hermes-a` -> `☀️ Apollo`, `claude-cloud` -> `☁️ Cloud`, `claude` -> `💻 Odysseus`; anything else unchanged."""
    key = (who or "").strip().lower()
    names = {**HERMES_BOTS, **{v.split(" ", 1)[1].lower(): v for v in HERMES_BOTS.values()}}
    if key in names:
        return names[key]
    if key in (CLOUD_AGENT, "cloud"):
        return "☁️ Cloud"
    if key == "hermes":
        return "🪽 any Hermes bot"
    if key.startswith("claude"):
        return "💻 Odysseus"  # Andy named the cloud Claude Odysseus (2026-11-09)
    return who


def write_kanban(live):
    """Rewrite Kanban.md (a read-only board for Obsidian's Kanban plugin) from the Inbox tasks and running agents.
    It is regenerated on every sync, so drag-and-drop changes on it don't stick: change tasks with --set."""
    def card(folder, name, p, extra=""):
        if p.get("status") == "writing" and p.get("writer"):
            who = worker_label(p["writer"])  # Quill's text pass: the writer has it, not the builder
        elif p.get("claimed_by"):
            who = worker_label(p["claimed_by"])
        elif folder == "Agents":
            who = "💻 Odysseus"  # an agent note is a Claude Code thread's own work (Odysseus, the cloud Claude)
        else:
            who = ("for " + worker_label(p["for"])) if p.get("for") else ""
        bits = [b for b in (who, f"#{p['pr']}" if p.get("pr") else "", p.get("status", ""), extra) if b]
        return f"- [ ] [[{folder}/{name}|{name}]] · " + " · ".join(bits)
    lanes = {lane: [] for lane, _ in KANBAN_LANES}
    tasks_seen = set()
    for _, name, p in sorted(tasks(live)):
        tasks_seen.add(name)
        for lane, statuses in KANBAN_LANES:
            status = p.get("status")  # no text-pass lane: tasks keep their real status
            if status in statuses:
                lanes[lane].append(card("Inbox", name, p, "fixing review notes" if status == "changes" else ""))
    agents = os.path.join(live, "Agents")
    for name in sorted(os.listdir(agents)) if os.path.isdir(agents) else []:
        if name.endswith(".md") and name[:-3] not in tasks_seen:
            p = frontmatter(os.path.join(agents, name))
            # (a thread's PR that passed review is "ready" or "sent": it waits on Andy like a blocked one)
            if p.get("status") in ("running", "in-review", "ready", "sent", "blocked", "stalled"):
                lane = {"running": "Being built", "in-review": "In review", "ready": "Needs Andy", "sent": "Needs Andy",
                        "stalled": "Needs Andy",
                        "blocked": "Needs Andy"}[p["status"]]
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
FIXER_NAMES = ("hermes-fixer", "hephaestus")  # open tasks the fixer bot takes when nothing was sent back (Bug hunter)


def meant_for(props, agent):
    """`for: hermes` covers every Hermes builder named hermes-<something>; `for: any` (or blank) covers everyone.
    Specialist bots (the modeller, the writer) only take tasks addressed to them, by bot name or nickname."""
    target = (props.get("for", "any") or "any").strip().lower()
    if agent in SPECIALISTS:
        return target in (agent, SPECIALISTS[agent])
    return target in (agent, "any") or agent.startswith(target + "-")


def awaiting_text_pass(props):
    """In-review tasks go to the writer bot before review: its text pass sets text_pass (done or none)."""
    return props.get("status") == "in-review" and not (props.get("text_pass") or "").strip()


def review_queue(live):
    """In-review tasks the writer has finished with: the ones a reviewer may take."""
    return sorted(t for t in tasks(live, ("in-review",)) if not awaiting_text_pass(t[2]))


def claim_text_pass(live, agent):
    """The writer bot: its own unfinished pass (a run that died mid-pass), else the oldest in-review task with no
    text pass yet. Sets status `writing` so no reviewer
    takes it mid-edit; the writer sets it back to in-review with text_pass=done (or none). Exit 3 if none."""
    mine = sorted(t for t in tasks(live, ("writing",)) if t[2].get("writer") == agent)  # a pass it didn't finish
    waiting = mine or sorted(t for t in tasks(live, ("in-review",)) if awaiting_text_pass(t[2]))
    if not waiting:
        print("No task waiting.")
        sys.exit(3)
    name = waiting[0][1]
    path = task_path(live, name)
    set_props(path, {"status": "writing", "writer": agent})
    sync(live)
    if frontmatter(path).get("writer") != agent:
        print("No task waiting.")
        sys.exit(3)
    sys.stdout.reconfigure(encoding="utf-8")
    print(f"Task: {name}  ({path})\n")
    print(open(path, encoding="utf-8").read())


def claim(live, agent, fixes=False):
    """The oldest task sent back to this agent, else the oldest open one meant for it. With fixes=True (a fixer
    bot), the oldest task sent back to anyone, which it takes over. Exit 3 if none."""
    back = tasks(live, ("changes",))
    if fixes:
        mine = [t for t in tasks(live, ("open",)) if (t[2].get("for") or "").strip().lower() in FIXER_NAMES]
        pick = sorted(back)[0] if back else (sorted(mine)[0] if mine else None)
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


def new_task(live, name, props, body):
    """A new open Inbox task (for scripts such as tools/cloud-agent/run.py hunt). Refuses a name already taken."""
    name = re.sub(r"[^a-z0-9-]+", "-", name.lower()).strip("-")[:60]
    path = os.path.join(live, "Inbox", name + ".md")
    if os.path.exists(path) or os.path.exists(os.path.join(live, "Inbox", "Done", name + ".md")):
        raise SystemExit(f"An Inbox task called {name} already exists")
    head = {"status": "open", "for": "any", "from": "", "asked": time.strftime("%Y-%m-%d"), "claimed_by": "", "pr": ""}
    head.update({k: v.replace("\n", " ") for k, v in props.items()})
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("---\n" + "".join(f"{k}: {v}\n" for k, v in head.items()) + "---\n\n" + body.strip() + "\n")
    sync(live)
    print(f"{name}: open")


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
        for _, name, _ in review_queue(live):
            print(name)
        return
    if cmd == "--review-queue":
        for _, name, _ in review_queue(live):
            print(name)
        return
    if cmd == "--claim":
        names = [a for a in rest if not a.startswith("--")]
        if "--writes" in rest:
            return claim_text_pass(live, names[0] if names else "hermes-writer")
        return claim(live, names[0] if names else "any", fixes="--fixes" in rest)
    if cmd == "--tasks":
        for _, name, _ in sorted(tasks(live, tuple(rest) or None)):
            print(name)
        return
    if cmd == "--new":
        return new_task(live, rest[0], dict(kv.split("=", 1) for kv in rest[1:]), sys.stdin.read())
    path = task_path(live, rest[0])
    if cmd == "--show":
        sys.stdout.reconfigure(encoding="utf-8")
        return print(open(path, encoding="utf-8").read())
    if cmd == "--set":
        props = dict(kv.split("=", 1) for kv in rest[1:])
        if props.get("status") == "changes":
            props.setdefault("text_pass", "")  # the fixes get their own text pass before the next review
        set_props(path, props)
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
