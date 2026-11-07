"""Write the vault's live "Open PRs" note from GitHub.

    python3 tools/vault_prs.py            # writes <main checkout>/docs/vault/Live/Open PRs.md
    python3 tools/vault_prs.py --print    # prints it instead

Run it from any worktree: it finds the main checkout through git, because Live/ exists only there (git ignores it).
Uses `gh api` over REST (GraphQL is blocked here). For each open PR: title, branch, last update, its checks, and the
agent note in Live/Agents/ that claims it (by `pr:`).
"""
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone

REPO = "Kinslauer/Jaymo-s-Powerpack"


def gh(path):
    out = subprocess.run(["gh", "api", path], capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"gh api {path} failed: {out.stderr.strip()}")
    return json.loads(out.stdout)


def main_checkout():
    common = subprocess.run(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
                            capture_output=True, text=True, check=True).stdout.strip()
    return os.path.dirname(common)


def checks(sha):
    runs = gh(f"repos/{REPO}/commits/{sha}/check-runs?per_page=100").get("check_runs", [])
    if not runs:
        return "no checks"
    failed = [r["name"] for r in runs if r.get("conclusion") in ("failure", "timed_out", "cancelled", "action_required")]
    pending = [r for r in runs if r.get("status") != "completed"]
    if failed:
        return "❌ failing: " + ", ".join(failed)
    if pending:
        return f"⏳ {len(pending)} of {len(runs)} running"
    return f"✅ {len(runs)} passed"


def agent_notes(live):
    """Map PR number -> agent note name, from the `pr:` property of each live agent note."""
    found = {}
    folder = os.path.join(live, "Agents")
    for root, _, files in os.walk(folder):
        for f in files:
            if not f.endswith(".md"):
                continue
            with open(os.path.join(root, f), encoding="utf-8") as fh:
                m = re.search(r"^pr:\s*#?(\d+)\s*$", fh.read(), re.M)
            if m:
                found[int(m.group(1))] = f[:-3]
    return found


def render(prs, notes):
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    lines = ["---", f"updated: {now}", "---", "# Open PRs", "",
             f"{len(prs)} open, as of {now}. Made by `python3 tools/vault_prs.py`; run it again to refresh.",
             "Andy merges only on \"merge\", one at a time.", ""]
    if not prs:
        lines.append("Nothing open.")
    for p in sorted(prs, key=lambda p: p["number"]):
        n = p["number"]
        agent = f"[[{notes[n]}]]" if n in notes else "no agent note"
        updated = datetime.fromisoformat(p["updated_at"].replace("Z", "+00:00"))
        days = (datetime.now(timezone.utc) - updated).days
        age = "today" if days == 0 else f"{days} day{'s' if days != 1 else ''} ago"
        draft = " (draft)" if p.get("draft") else ""
        lines += [f"## [#{n}]({p['html_url']}) {p['title']}{draft}",
                  f"- **Branch:** `{p['head']['ref']}` · **Updated:** {updated:%Y-%m-%d} ({age})",
                  f"- **Checks:** {checks(p['head']['sha'])}",
                  f"- **Agent:** {agent}", ""]
    return "\n".join(lines)


def main():
    prs = gh(f"repos/{REPO}/pulls?state=open&per_page=100")
    live = os.path.join(main_checkout(), "docs", "vault", "Live")
    text = render(prs, agent_notes(live))
    if "--print" in sys.argv:
        sys.stdout.reconfigure(encoding="utf-8")
        print(text)
        return
    os.makedirs(live, exist_ok=True)
    path = os.path.join(live, "Open PRs.md")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print(f"{len(prs)} open PRs -> {path}")


if __name__ == "__main__":
    main()
