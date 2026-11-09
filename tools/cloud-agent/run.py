"""Runs a Jaymo's Powerpack task on the Powerpack Builder, a Claude Managed Agent that works in Anthropic's cloud
sandbox (README.md beside this). Meant for YOUTH (Hermes) on Andy's PC; works from any shell with the two keys.

    py tools/cloud-agent/run.py credentials            # once: put the GitHub token in the vault
    py tools/cloud-agent/run.py task <inbox-task>      # do an Inbox task; keeps its status in the vault
    py tools/cloud-agent/run.py next                   # do the oldest open task for `cloud` or `prometheus`
    py tools/cloud-agent/run.py prompt "<what to do>"  # do something that isn't an Inbox task

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


def lock_id(path):
    with open(os.path.join(HERE, "claude-lock.json"), encoding="utf-8") as f:
        resources = json.load(f)["resources"]
    entry = resources.get("./" + path) or resources.get(path)
    if not entry:
        raise SystemExit(f"{path} isn't in claude-lock.json: run `ant apply` in tools/cloud-agent first (README.md)")
    return entry["id"]


def vault(*args):
    """tools/vault_sync.py (Inbox status), run from the repo root."""
    out = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "vault_sync.py"), *args], cwd=ROOT,
                         capture_output=True, text=True, encoding="utf-8")
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
        }],
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


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("credentials", "task", "prompt", "next"):
        raise SystemExit(__doc__)
    load_keys()
    client = anthropic.Anthropic()
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
