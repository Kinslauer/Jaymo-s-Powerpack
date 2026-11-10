"""Runs a Jaymo's Powerpack task on the Powerpack Builder, a Claude Managed Agent that works in Anthropic's cloud
sandbox (README.md beside this). Meant for YOUTH (Hermes) on Andy's PC; works from any shell with the two keys.

    py tools/cloud-agent/run.py credentials            # once: put the GitHub token in the vault
    py tools/cloud-agent/run.py task <inbox-task>      # do an Inbox task; keeps its status in the vault
    py tools/cloud-agent/run.py next                   # do the oldest open task for `cloud` or `prometheus`
    py tools/cloud-agent/run.py prompt "<what to do>"  # do something that isn't an Inbox task
    py tools/cloud-agent/run.py checktoken             # can GITHUB_TOKEN push to the repo? (pushes nothing)
    py tools/cloud-agent/run.py retoken <session-id>   # give a running session the current GITHUB_TOKEN for git
    py tools/cloud-agent/run.py hunt <area> [...]      # Bug hunter, only when Andy asks: an area is a folder, a
                                                       # file, or a word from docs/codemap (e.g. `hunt Downed`);
                                                       # each confirmed bug becomes an Inbox task for Hephaestus.
                                                       # --dry-run: only show what it would read; --big: allow
                                                       # more than HUNT_MAX_LINES; --budget 80: dollars

Needs ANTHROPIC_API_KEY and GITHUB_TOKEN in the environment (never in a file; on Windows, when the shell that runs this
has them stripped, as Hermes does for every command, they're read from the user's saved environment variables),
`pip install anthropic`, and the IDs
`ant apply` wrote to tools/cloud-agent/claude-lock.json. Prints the agent's messages as it works and the session's
Console link; exits 0 with a PR, 2 when the agent needs Andy, 1 on anything else.
"""
import json
import os
import re
import subprocess
import sys

import anthropic

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
REPO = "kinslauer/jaymo-s-powerpack"
BRANCH = "master"
MCP_URL = "https://api.githubcopilot.com/mcp/"
# The vault agent name the task is claimed under
WHO = "claude-cloud"
# `next` takes open Inbox tasks addressed to these (`for:`): its own, and the modelling bot's, which it shares
TAKES = ("cloud", "prometheus", "hermes-modeller")
# Most a session may spend, in US cents at list price (it pauses there; nothing is lost)
BUDGET_CENTS = "2000"
# How many times the grader may send the work back
MAX_ITERATIONS = 3
# Bug hunter: most a hunt may spend (US cents), and the most lines one hunt reads without --big. Its instructions cap a
# run at 20 slices of about 1,500 lines, so 30,000 lines is one ordinary run; a whole mod is ten times that or more.
HUNT_BUDGET_CENTS = "5000"
HUNT_MAX_LINES = 30000
HUNT_SLICE_LINES = 1500
# Who the confirmed bugs go to: Hephaestus, the fixer bot (hermes-fixer)
HUNT_FOR = "hephaestus"
CODE_DIRS = ("src", "kingdoms/src", "kitchen/src")
CODEMAPS = ("docs/codemap/powers.md", "docs/codemap/kingdoms.md", "docs/codemap/kitchen.md")

# Starter rubric: what "done" means for every task. Tune it as the runs show what the grader misses.
RUBRIC = """# Powerpack task rubric (starter)
1. The work is on a new branch named `cloud/<task-name>`, pushed, with a pull request open from it into `master`
   on kinslauer/jaymo-s-powerpack; the last message ends with `PR: <number>`.
2. The change does everything the task asks (each point under "What's wanted" and each numbered step, if it has them).
3. `bash gradlew build` ran in every mod folder the change touches, and the final run of each says BUILD SUCCESSFUL.
4. `python3 tools/pr_checks.py` passes on the final commit.
5. There is a change note `changes/<powers|kingdoms|kitchen>/<branch-name>.md` for each mod changed, and no change to
   a `version=` line, a MODLOG or `docs/codemap/`.
6. Anything ported from another mod or artist has its licence checked and its credit added, as `CLAUDE.md` says.
7. The PR description says what a player will see, what changed, how it was checked, and that it wasn't played.
If the agent stopped because a decision is Andy's to make, criterion 1 is met instead by a last line
`BLOCKED: <a specific question>`, and the rest are judged on the work done so far.
"""


KEYS = ("ANTHROPIC_API_KEY", "GITHUB_TOKEN")


def load_keys():
    """Each key from the environment, or, on Windows when it's missing there, from the user's saved environment
    variables (the registry's current-user Environment key). Hermes strips API keys and tokens from every command YOUTH runs, by
    design, so this is how YOUTH's runs get them; they're only set in this process, and never printed or written."""
    missing = [k for k in KEYS if not os.environ.get(k)]
    if missing and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as env:
                for k in missing:
                    try:
                        value, _ = winreg.QueryValueEx(env, k)
                    except OSError:
                        continue
                    if value:
                        os.environ[k] = str(value)
        except OSError:
            pass
    still = [k for k in KEYS if not os.environ.get(k)]
    if still:
        raise SystemExit(f"Missing {', '.join(still)}: set it as a user environment variable (README.md, One-time setup)")


def checktoken():
    """Whether GITHUB_TOKEN may push to the repo, asked of GitHub the way `git push` asks (the receive-pack handshake,
    which GitHub refuses with a 403 when the token can't write), without sending anything. The token goes in an
    environment-only git setting, never on the command line."""
    import base64
    basic = base64.b64encode(f"x-access-token:{os.environ['GITHUB_TOKEN']}".encode()).decode()
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_CONFIG_COUNT="1",
               GIT_CONFIG_KEY_0="http.https://github.com/.extraheader", GIT_CONFIG_VALUE_0=f"AUTHORIZATION: basic {basic}")
    out = subprocess.run(["git", "-c", "credential.helper=", "push", "--dry-run", "--porcelain",
                          f"https://github.com/{REPO}.git", "HEAD:refs/heads/cloud/token-check"],
                         cwd=ROOT, env=env, capture_output=True, text=True, encoding="utf-8")
    if out.returncode == 0:
        print(f"GITHUB_TOKEN can push to {REPO}.")
        return 0
    print(f"GITHUB_TOKEN can't push to {REPO}:\n{(out.stderr or out.stdout).strip()}\n"
          "Give it Contents: Read and write on this repository (README.md, One-time setup), then run this again.")
    return 1


def retoken(client, session_id):
    """Swaps the token git uses for the repo in a running session for the current GITHUB_TOKEN (a session keeps the
    token it was started with, so fixing the token's permissions afterwards doesn't reach it)."""
    done = 0
    for r in client.beta.sessions.resources.list(session_id):
        if getattr(r, "type", "") == "github_repository":
            client.beta.sessions.resources.update(r.id, session_id=session_id, authorization_token=os.environ["GITHUB_TOKEN"])
            print(f"{session_id}: {getattr(r, 'url', r.id)} now uses the current GITHUB_TOKEN")
            done += 1
    if not done:
        print(f"{session_id} has no GitHub repository attached, so git there doesn't use a token we can swap.")
        return 1
    return 0


def lock_id(path):
    with open(os.path.join(HERE, "claude-lock.json"), encoding="utf-8") as f:
        resources = json.load(f)["resources"]
    entry = resources.get("./" + path) or resources.get(path)
    if not entry:
        raise SystemExit(f"{path} isn't in claude-lock.json: run `ant apply` in tools/cloud-agent first (README.md)")
    return entry["id"]


def vault(*args, stdin=None):
    """tools/vault_sync.py (Inbox status), run from the repo root."""
    out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "vault_sync.py"), *args], cwd=ROOT,
                         capture_output=True, text=True, encoding="utf-8", input=stdin)
    if out.returncode != 0:
        raise SystemExit(f"vault_sync {' '.join(args)} failed:\n{out.stdout}{out.stderr}")
    return out.stdout


def props_of(text):
    """A task's front matter (the lines between the first two `---`), as vault_sync --show prints it."""
    props, lines = {}, text.split("\n")
    if lines and lines[0].strip() == "---":
        for line in lines[1:]:
            if line.strip() == "---":
                break
            if ":" in line and not line.startswith(" "):
                key, _, value = line.partition(":")
                props[key.strip()] = value.strip()
    return props


def next_task():
    """The oldest open Inbox task addressed to the cloud agent or the modelling bot (they share them), or None."""
    found = []
    for name in vault("--tasks", "open").split():
        props = props_of(vault("--show", name))
        if props.get("status") == "open" and props.get("for", "").lower() in TAKES:
            found.append((props.get("asked", ""), name))
    return min(found)[1] if found else None


def credentials(client):
    token = os.environ["GITHUB_TOKEN"]
    vault_id = lock_id("vaults/powerpack-github.yaml")
    client.beta.vaults.credentials.create(
        vault_id=vault_id,
        display_name="GitHub MCP (Powerpack)",
        auth={"type": "static_bearer", "mcp_server_url": MCP_URL, "token": token},
    )
    print(f"GitHub token stored in vault {vault_id} for {MCP_URL}")


def approve(event):
    """Unattended: only a pull request on this repository, never anything else that asks."""
    if getattr(event, "name", "") != "create_pull_request":
        return False
    args = getattr(event, "input", None) or {}
    return f"{args.get('owner', '')}/{args.get('repo', '')}".lower() == REPO and args.get("base", BRANCH) == BRANCH


def task_files(task_text):
    """Private files a task names (`file_...` ids from the Files API, e.g. Andy's video clips), mounted read-only at
    /mnt/session/uploads/clips/<name>. A line reads: - `file_01ABC...` name.mp4"""
    files = []
    for m in re.finditer(r"`(file_[A-Za-z0-9]+)` *([^\s`]+)?", task_text):
        name = m.group(2) or m.group(1)
        files.append({"type": "file", "file_id": m.group(1), "mount_path": f"/clips/{os.path.basename(name)}"})
    return files


def run(client, title, task_text):
    session = client.beta.sessions.create(
        agent=lock_id("agents/powerpack-builder.md"),
        environment_id=lock_id("environments/powerpack-build.yaml"),
        title=title,
        vault_ids=[lock_id("vaults/powerpack-github.yaml")],
        resources=[{
            "type": "github_repository",
            "url": f"https://github.com/{REPO}",
            "authorization_token": os.environ["GITHUB_TOKEN"],
            "checkout": {"type": "branch", "name": BRANCH},
        }] + task_files(task_text),
        budget={"type": "limit", "max_list_cost": {"amount": BUDGET_CENTS, "currency": "USD"}},
    )
    print(f"Session {session.id}: https://platform.claude.com/workspaces/default/sessions/{session.id}", flush=True)
    last = []
    with client.beta.sessions.events.stream(session_id=session.id) as stream:
        client.beta.sessions.events.send(session_id=session.id, events=[
            {"type": "user.message", "content": [{"type": "text", "text": task_text}]},
            {"type": "user.define_outcome", "description": f"Do this task and open its pull request: {title}",
             "rubric": {"type": "text", "content": RUBRIC}, "max_iterations": MAX_ITERATIONS},
        ])
        for event in stream:
            kind = event.type
            if kind == "agent.message":
                text = "".join(b.text for b in event.content if b.type == "text")
                last.append(text)
                print(text, flush=True)
            elif kind in ("agent.tool_use", "agent.mcp_tool_use") and getattr(event, "evaluated_permission", None) == "ask":
                ok = approve(event)
                print(f"[{'allowed' if ok else 'denied'}: {getattr(event, 'name', '?')}]", flush=True)
                confirm = {"type": "user.tool_confirmation", "tool_use_id": event.id, "result": "allow" if ok else "deny"}
                if not ok:
                    confirm["deny_message"] = "Not allowed in an unattended run. Find another way, or stop with BLOCKED: <why>."
                client.beta.sessions.events.send(session_id=session.id, events=[confirm])
            elif kind == "span.outcome_evaluation_end":
                print(f"[grader, round {event.iteration + 1}: {event.result}] {event.explanation}", flush=True)
            elif kind == "session.error":
                print(f"[session error] {getattr(getattr(event, 'error', None), 'message', event)}", flush=True)
            elif kind == "session.status_idle":
                reason = event.stop_reason.type
                if reason == "budget_reached":
                    print(f"[stopped at the ${int(BUDGET_CENTS) / 100:.2f} budget: raise it on the session to go on]")
                    break
                if reason != "requires_action":
                    break
            elif kind == "session.status_terminated":
                break
    return session.id, "\n".join(last)


def java_lines(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return sum(1 for _ in f)


def hunt_scope(words):
    """Repo-relative .java files for each word: a folder or file in the repo, else every class in the codemaps whose
    name or path contains it (case-insensitive). Returns {path: lines}, sorted by path."""
    found = {}
    for word in words:
        target = os.path.normpath(os.path.join(ROOT, word))
        hits = []
        if os.path.isfile(target) and target.endswith(".java"):
            hits = [target]
        elif os.path.isdir(target):
            hits = [os.path.join(d, n) for d, _, names in os.walk(target) for n in names if n.endswith(".java")]
        else:
            low = word.lower()
            for codemap in CODEMAPS:
                with open(os.path.join(ROOT, codemap), encoding="utf-8") as f:
                    for line in f:
                        m = re.match(r"- `([^`]+)`.*?`([^`]+\.java)`", line)
                        if m and (low in m.group(1).lower() or low in m.group(2).lower()):
                            hits.append(os.path.join(ROOT, m.group(2)))
        hits = [h for h in hits if os.path.isfile(h)
                and any(os.path.relpath(h, ROOT).replace("\\", "/").startswith(d + "/") for d in CODE_DIRS)]
        if not hits:
            raise SystemExit(f"Nothing to hunt in for `{word}`: not a folder or .java file under {', '.join(CODE_DIRS)},"
                             " and no codemap class matches it")
        for h in hits:
            found[os.path.relpath(h, ROOT).replace("\\", "/")] = java_lines(h)
    return dict(sorted(found.items()))


HUNT_ASK = """Hunt for bugs in Jaymo's Powerpack ({label}), at commit {commit} of master.

The repository is already in the sandbox at /workspace/jaymo-s-powerpack: don't clone or download it. Hunt only in
these {count} files ({lines} lines); everything else in the repository is context you may read to understand a caller:

{files}

They are Minecraft 26.3 Fabric mods in Java 25. The sandbox has no JDK and cannot download Minecraft or Gradle, so
most proofs will be "confirmed by reading"; a self-contained piece of logic (arithmetic, parsing, a loop, a
comparison) may be re-implemented in Python under /tmp/hunt to show the wrong result. Don't report anything that rests
only on what you believe a Minecraft or Fabric method does: there are no sources here to check it against.

Andy asked for this hunt and approved its cost up to the session's budget of ${budget}: don't stop to ask, start the
run now. This scope needs about {slices} slices{slices_note}.

Besides /mnt/session/outputs/bug-report.md, write /mnt/session/outputs/bugs.json: a JSON list with one object per bug
you report as confirmed (demonstrated or confirmed by reading; nothing rejected or unchecked), worst first, with these
keys, all strings: "title" (a few plain words), "severity" (high, medium or low), "proof" (demonstrated or confirmed
by reading), "file" (repository-relative path), "line", "function", "input", "expected", "actual", "test" (the
failing test's code, or for one confirmed by reading the exact lines and why they are wrong), "fix" (the smallest fix,
as a unified diff against the repository file), "player" (one sentence: what a player would notice, or "nothing
visible"). Write [] if nothing survived.
"""

BUG_TASK = """# Bug: {title}

## What's wanted

The Bug hunter (cloud session {session}) found and checked this bug. Fix it with the smallest change that makes the
check below pass, then open a PR as usual (change note, PR checks, build).

- **Severity:** {severity} ({proof})
- **Where:** `{file}` line {line}, `{function}`
- **Input:** {input}
- **Should happen:** {expected}
- **Happens instead:** {actual}
- **What a player sees:** {player}

First confirm it is real by reading the code at master: the hunter worked without Minecraft's sources or a build. If
it isn't a bug, don't change anything: add a note saying why and set the task `blocked` for Andy.

## Failing test (from the hunter's sandbox)

```
{test}
```

## Suggested fix

```diff
{fix}
```

## Notes
"""


def hunt(client, words, dry_run=False, big=False, budget_cents=HUNT_BUDGET_CENTS):
    files = hunt_scope(words)
    lines = sum(files.values())
    label = ", ".join(words)
    print(f"Hunt `{label}`: {len(files)} files, {lines} lines")
    for path, n in files.items():
        print(f"  {n:6}  {path}")
    if lines > HUNT_MAX_LINES and not big:
        raise SystemExit(f"That's {lines} lines, more than one ordinary run ({HUNT_MAX_LINES}). Hunt a smaller area, or"
                         " add --big once Andy has agreed to a bigger run and a higher --budget.")
    if dry_run:
        return 0
    commit = subprocess.run(["git", "-C", ROOT, "rev-parse", "--short", f"origin/{BRANCH}"], capture_output=True,
                            text=True).stdout.strip() or BRANCH
    slices = max(4, -(-lines // HUNT_SLICE_LINES))
    ask = HUNT_ASK.format(
        label=label, commit=commit, count=len(files), lines=lines, budget=f"{int(budget_cents) / 100:.0f}",
        files="\n".join(f"- {p} ({n} lines)" for p, n in files.items()), slices=slices,
        slices_note="" if slices <= 20 else f": Andy agreed to more than 20 for this hunt, so use up to {slices}")
    session = client.beta.sessions.create(
        agent=lock_id("agents/bug-hunter.md"),
        environment_id=lock_id("environments/bug-hunt.yaml"),
        title=f"Bug hunt: {label}"[:80],
        resources=[{
            "type": "github_repository",
            "url": f"https://github.com/{REPO}",
            "authorization_token": os.environ["GITHUB_TOKEN"],
            "checkout": {"type": "branch", "name": BRANCH},
        }],
        budget={"type": "limit", "max_list_cost": {"amount": str(budget_cents), "currency": "USD"}},
    )
    print(f"Session {session.id}: https://platform.claude.com/workspaces/default/sessions/{session.id}", flush=True)
    open_runs, stopped = set(), None
    with client.beta.sessions.events.stream(session_id=session.id) as stream:
        client.beta.sessions.events.send(session_id=session.id, events=[
            {"type": "user.message", "content": [{"type": "text", "text": ask}]}])
        for event in stream:
            kind = event.type
            if kind == "agent.message":
                print("".join(b.text for b in event.content if b.type == "text"), flush=True)
            elif kind == "workflow_run.created":
                open_runs.add(event.workflow_run_id)
                print(f"[run {event.workflow_run_id} started: {len(event.phases or [])} phases]", flush=True)
            elif kind == "workflow_run.status_ended":
                open_runs.discard(event.workflow_run_id)
                print(f"[run {event.workflow_run_id} ended: {event.result.type}]", flush=True)
            elif kind == "workflow_run.error":
                print(f"[run error] {event.error.message}", flush=True)
            elif kind in ("agent.tool_use", "agent.mcp_tool_use") and getattr(event, "evaluated_permission", None) == "ask":
                client.beta.sessions.events.send(session_id=session.id, events=[{
                    "type": "user.tool_confirmation", "tool_use_id": event.id, "result": "deny",
                    "deny_message": "Nothing needs asking in a hunt."}])
            elif kind == "session.error":
                print(f"[session error] {getattr(getattr(event, 'error', None), 'message', event)}", flush=True)
            elif kind == "session.status_idle":
                reason = event.stop_reason.type
                if reason == "budget_reached":
                    stopped = f"stopped at the ${int(budget_cents) / 100:.2f} budget (raise it on the session to go on)"
                    break
                if reason == "retries_exhausted":
                    stopped = "the agent gave up on an error"
                    break
                if reason == "end_turn" and not open_runs:
                    break
            elif kind == "session.status_terminated":
                break
    if stopped:
        print(f"[{stopped}]")
    return hunt_results(client, session.id, label)


def hunt_results(client, session_id, label):
    """Download the report and bugs.json, keep them under hunts/, and make an Inbox task per confirmed bug."""
    import time
    outputs = {}
    for _ in range(5):  # outputs show up a few seconds after the session goes idle
        for f in client.beta.files.list(scope_id=session_id, betas=["managed-agents-2026-04-01"]):
            outputs[f.filename] = f.id
        if "bugs.json" in outputs:
            break
        time.sleep(3)
    folder = os.path.join(HERE, "hunts", time.strftime("%Y-%m-%d-%H%M") + "-" + re.sub(r"[^a-z0-9]+", "-", label.lower())[:40])
    os.makedirs(folder, exist_ok=True)
    for name, file_id in outputs.items():
        with open(os.path.join(folder, os.path.basename(name)), "wb") as out:
            out.write(client.beta.files.download(file_id).read())
    print(f"Saved {', '.join(outputs) or 'nothing'} in {folder}")
    if "bugs.json" not in outputs:
        print("No bugs.json: the hunt didn't finish (see the session). No tasks made.")
        return 1
    with open(os.path.join(folder, "bugs.json"), encoding="utf-8") as f:
        bugs = json.load(f)
    made = []
    for bug in bugs:
        bug = {k: str(bug.get(k, "") or "").strip() for k in
               ("title", "severity", "proof", "file", "line", "function", "input", "expected", "actual", "test", "fix", "player")}
        base = "bug-" + re.sub(r"[^a-z0-9]+", "-", bug["title"].lower()).strip("-")[:50]
        for n in range(1, 10):
            name = base if n == 1 else f"{base}-{n}"
            try:
                vault("--new", name, f"for={HUNT_FOR}", f"from=Bug hunter ({label}, session {session_id})",
                      stdin=BUG_TASK.format(session=session_id, **bug))
                made.append(name)
                break
            except SystemExit as e:
                if "already exists" not in str(e):
                    raise
    print(f"{len(bugs)} confirmed bug(s); Inbox tasks for {HUNT_FOR}: {', '.join(made) or 'none'}")
    return 0


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("credentials", "task", "prompt", "next", "hunt", "checktoken", "retoken"):
        raise SystemExit(__doc__)
    if sys.argv[1] == "hunt":
        args = sys.argv[2:]
        budget = HUNT_BUDGET_CENTS
        if "--budget" in args:
            i = args.index("--budget")
            budget = str(int(float(args[i + 1]) * 100))
            del args[i:i + 2]
        words = [a for a in args if not a.startswith("--")]
        if not words:
            raise SystemExit("run.py hunt <area> [...]: a folder, a .java file, or a word from docs/codemap")
        if "--dry-run" in args:
            return hunt(None, words, dry_run=True, big="--big" in args)
        load_keys()
        return hunt(anthropic.Anthropic(), words, big="--big" in args, budget_cents=budget)
    load_keys()
    if sys.argv[1] == "checktoken":
        return checktoken()
    client = anthropic.Anthropic()
    if sys.argv[1] == "retoken":
        if len(sys.argv) < 3:
            raise SystemExit("run.py retoken <session-id>: the sesn_... ID from the session's Console page")
        return retoken(client, sys.argv[2])
    if sys.argv[1] == "credentials":
        return credentials(client)
    if sys.argv[1] == "prompt":
        _, text = run(client, sys.argv[2][:80], sys.argv[2])
        return 0 if re.search(r"^PR: *#?\d+", text, re.M) else 1
    if sys.argv[1] == "next":
        task = next_task()
        if not task:
            print("No task waiting for the cloud agent.")
            return 3
    else:
        task = sys.argv[2]
    text = vault("--show", task)
    props = props_of(text)
    if props.get("status") != "open" and props.get("claimed_by") != WHO:
        print(f"{task} is {props.get('status')} (claimed by {props.get('claimed_by') or 'nobody'}): not taking it.")
        return 1
    vault("--set", task, "status=claimed", f"claimed_by={WHO}")
    session_id, said = run(client, task, f"The Inbox task `{task}` (its branch: `cloud/{task}`):\n\n{text}")
    pr = re.search(r"^PR: *#?(\d+)", said, re.M)
    blocked = re.search(r"^BLOCKED: *(.+)", said, re.M)
    if pr:
        vault("--note", task, f"Cloud agent session {session_id} opened PR #{pr.group(1)}.")
        vault("--set", task, "status=in-review", f"pr={pr.group(1)}")
        print(f"{task}: in review, PR #{pr.group(1)}")
        return 0
    if blocked:
        vault("--note", task, f"Question: {blocked.group(1)} (cloud agent session {session_id})")
        vault("--set", task, "status=blocked")
        print(f"{task}: blocked on a question for Andy")
        return 2
    vault("--note", task, f"Cloud agent session {session_id} ended without a PR; see its transcript.")
    vault("--set", task, "status=open", "claimed_by=")
    print(f"{task}: no PR; handed back to the Inbox")
    return 1


if __name__ == "__main__":
    sys.exit(main())
