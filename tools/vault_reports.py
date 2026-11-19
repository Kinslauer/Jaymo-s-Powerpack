"""The reports tools/vault_sync.py writes into the live folder after every sync, and its --health, --weekly and --next
commands. Ideas borrowed from imisic/claude-obsidian-vault (MIT): a health report that only finds problems and never
fixes them, a home page made of short live lists, and a weekly review (Andy's picks, 2026-11-19).

    python tools/vault_sync.py --health          # print the problems Health.md lists (detect only: nothing is fixed)
    python tools/vault_sync.py --weekly [days]   # print a review of the last 7 (or N) days
    python tools/vault_sync.py --next hermes-a   # resume this bot's own task or claim the next one, then print the
                                                 # task and the exact steps for it (exit 3 = nothing waiting)

Written on every sync (each only rewritten when its content changes, so syncs don't churn commits):
- Dashboard.md: what waits on Andy (PRs to merge, questions), what's stuck, and the merged work nobody has played yet
  (from the MODLOGs on origin/master). Andy ticks a line once he's played it; the next sync records the tick in
  Played.md and drops the line.
- Health.md: problems found in the live notes (unknown statuses, stalled notes, claims and reviews sitting too long,
  questions waiting on Andy, broken [[links]], the project memory index growing past its read limit).
- Weekly/<year>-W<week>.md: once a week, the week before: what merged, Andy's decisions, the asks still open.

Every function takes `vs`, the vault_sync module, for its note helpers (frontmatter, tasks, ...).
"""
import datetime
import os
import re
import sys
import time

INBOX_STATUSES = {"open", "claimed", "in-review", "writing", "changes", "ready", "sent", "done", "blocked"}
AGENT_STATUSES = {"running", "in-review", "blocked", "stalled", "ready", "sent", "merged", "done"}
CLAIM_HOURS = 6        # a claim with no change for this long is reported (the PC hands back after 30 min of no work)
REVIEW_DAYS = 2        # in-review this long without a review
WAITING_DAYS = 3       # blocked (a question for Andy), sent (waiting on "merge") or stalled this long
MEMORY_LIMIT = 16 * 1024  # the project memory index is only read this far
MEMORY_LINE = 300      # an index line longer than this belongs in a topic file
NOT_PLAYED_DAYS = 30   # how far back the Dashboard looks for merged, unplayed work
NOT_PLAYED_MAX = 60    # at most this many lines (newest first)
MODLOGS = (("Powers", "MODLOG.md"), ("Kingdoms", "kingdoms/MODLOG.md"), ("Kitchen", "kitchen/MODLOG.md"))
NOT_PLAYED = re.compile(r"not (been )?played|haven'?t been played|hasn'?t been played|not playtested|unplaytested", re.I)
ENTRY = re.compile(r"^## (\S+) \((\d{4}-\d{2}-\d{2})\): (.+?)(?: \(#(\d+)\))?\s*$")
LINK = re.compile(r"\[\[([^\]|#]+)(?:#[^\]|]*)?(?:\|[^\]]*)?\]\]")
MEMORY_PATHS = ("/tmp/claude/memory/team/silo/MEMORY.md",)  # the cloud project memory; VAULT_MEMORY_INDEX overrides


# ---------------------------------------------------------------- shared helpers

def notes(live, folder, vs):
    """(name, path, props) of the top-level notes in Live/<folder> (not Done/)."""
    root = os.path.join(live, folder)
    out = []
    for name in sorted(os.listdir(root)) if os.path.isdir(root) else []:
        path = os.path.join(root, name)
        if name.endswith(".md") and os.path.isfile(path):
            out.append((name[:-3], path, vs.frontmatter(path)))
    return out


def changed_at(live, vs):
    """Path (relative to the live folder, with /) -> when git last saw it change (epoch seconds). File times mean
    nothing in a fresh clone, so ages come from the branch history. A file with local changes counts as now."""
    times = {}
    log = vs.git(live, "log", "-400", "--format=@%ct", "--name-only", check=False).stdout
    stamp = 0
    for line in log.splitlines():
        if line.startswith("@"):
            stamp = int(line[1:] or 0)
        elif line.strip() and line not in times:
            times[line.strip()] = stamp
    for line in vs.git(live, "status", "--porcelain", check=False).stdout.splitlines():
        times[line[3:].strip().strip('"')] = int(time.time())
    return times


def day(epoch):
    return time.strftime("%Y-%m-%d", time.localtime(epoch)) if epoch else "unknown"


def last_note(path, width=160):
    """The newest line under ## Notes (or ''), cut to `width`."""
    text = open(path, encoding="utf-8").read()
    if "\n## Notes" not in text:
        return ""
    lines = [l[2:].strip() for l in text.split("\n## Notes", 1)[1].splitlines() if l.startswith("- ") and l[2:].strip()]
    if not lines:
        return ""
    line = lines[-1]
    return line if len(line) <= width else line[:width - 1] + "…"


def task_link(folder, name):
    return f"[[{folder}/{name}|{name}]]"


def write_if_changed(path, text):
    """Write `text` unless only its first line (the time stamp) differs from what's there."""
    old = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    if old.split("\n", 1)[-1] != text.split("\n", 1)[-1]:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        return True
    return False


# ---------------------------------------------------------------- merged work, from the MODLOGs on origin/master

def modlog_entries(vs):
    """[(date, pr or None, title, mod, body)] for every MODLOG entry. Read from origin/master (what's merged) when
    git can, else from the files on disk."""
    repo = vs.main_checkout()
    out = []
    for mod, rel in MODLOGS:
        shown = vs.run(["git", "-C", repo, "show", f"origin/master:{rel}"], check=False)
        text = shown.stdout if shown.returncode == 0 else ""
        if not text and os.path.exists(os.path.join(repo, rel)):
            text = open(os.path.join(repo, rel), encoding="utf-8").read()
        head, body = None, []
        for line in text.splitlines() + ["## end (0000-00-00): end"]:
            m = ENTRY.match(line)
            if m:
                if head:
                    out.append((head[1], head[3], head[2], mod, "\n".join(body)))
                head, body = (m.group(1), m.group(2), m.group(3), int(m.group(4)) if m.group(4) else None), []
            elif head:
                body.append(line)
    return out


def merged_work(vs, since, until=None):
    """Merged entries dated since..until (YYYY-MM-DD), one per PR (a PR that changed two mods lists both):
    [(date, pr, title, [mods], not_played)], newest first."""
    by_key = {}
    for date, pr, title, mod, body in modlog_entries(vs):
        if date < since or (until and date > until):
            continue
        key = pr or (date, title)
        if key in by_key:
            if mod not in by_key[key][3]:
                by_key[key][3].append(mod)
            by_key[key][4] = by_key[key][4] or bool(NOT_PLAYED.search(body))
        else:
            by_key[key] = [date, pr, title, [mod], bool(NOT_PLAYED.search(body))]
    return sorted((tuple(v) for v in by_key.values()), key=lambda v: (v[0], v[1] or 0), reverse=True)


def played(live):
    """PR numbers Andy has ticked as played (Played.md)."""
    path = os.path.join(live, "Played.md")
    return {int(n) for n in re.findall(r"#(\d+)", open(path, encoding="utf-8").read())} if os.path.exists(path) else set()


def record_ticks(live):
    """Lines Andy ticked under "Merged, not played yet" on the Dashboard go into Played.md before it's rebuilt."""
    dash = os.path.join(live, "Dashboard.md")
    if not os.path.exists(dash):
        return
    section = open(dash, encoding="utf-8").read().split("## Merged, not played yet", 1)
    if len(section) < 2:
        return
    ticked = re.findall(r"^- \[[xX]\] .*?#(\d+)(.*)$", section[1].split("\n## ", 1)[0], re.M)
    new = [(n, rest) for n, rest in ticked if int(n) not in played(live)]
    if not new:
        return
    path = os.path.join(live, "Played.md")
    head = "" if os.path.exists(path) else ("# Played\n\nMerged PRs Andy has played (ticked on the [[Dashboard]]). "
                                           "Remove a line to put it back on the list.\n\n")
    with open(path, "a", encoding="utf-8", newline="\n") as fh:
        fh.write(head + "".join(f"- {time.strftime('%Y-%m-%d')} #{n}{rest.rstrip()}\n" for n, rest in new))


# ---------------------------------------------------------------- health

def health(live, vs):
    """[(section, line)]: what's wrong in the live notes. Detect only: nothing here changes a note."""
    now = time.time()
    seen = changed_at(live, vs)

    def age(folder, name):
        rel = f"{folder}/{name}.md"
        if rel not in seen:  # older than the log read above: ask git about this one file
            last = vs.git(live, "log", "-1", "--format=%ct", "--", rel, check=False).stdout.strip()
            seen[rel] = int(last) if last.isdigit() else now
        return now - seen[rel]
    found = []
    inbox, agents = notes(live, "Inbox", vs), notes(live, "Agents", vs)
    done_tasks = {n[:-3] for n in os.listdir(os.path.join(live, "Inbox", "Done"))} \
        if os.path.isdir(os.path.join(live, "Inbox", "Done")) else set()

    for folder, known, items in (("Inbox", INBOX_STATUSES, inbox), ("Agents", AGENT_STATUSES, agents)):
        for name, path, p in items:
            s = p.get("status", "")
            if s not in known:
                found.append(("Statuses the pipeline doesn't know",
                              f"{task_link(folder, name)}: `status: {s or '(none)'}` (known: {', '.join(sorted(known))})"))

    for name, path, p in agents:
        s, since = p.get("status"), day(now - age("Agents", name))
        task = (p.get("task", "").split() or [""])[0]
        if s == "stalled" and task not in done_tasks:
            found.append(("Stalled agent notes", f"{task_link('Agents', name)}: stalled, last change {since}. Restart "
                          "it, or set it done and move it to Agents/Done/ if the work is finished or dropped."))
        if s in ("running", "in-review", "blocked", "stalled") and task and task in done_tasks:
            found.append(("Agent notes whose task is done", f"{task_link('Agents', name)}: still `{s}`, but its task "
                          f"`{task}` is in Inbox/Done/."))
        if s == "in-review" and not p.get("pr"):
            found.append(("Missing PR numbers", f"{task_link('Agents', name)}: in review with no `pr:`."))
        if s == "blocked" and age("Agents", name) > WAITING_DAYS * 86400:
            found.append(("Questions waiting on Andy", f"{task_link('Agents', name)}: blocked since {since}."))

    for name, path, p in inbox:
        s, a, since = p.get("status"), age("Inbox", name), day(now - age("Inbox", name))
        who = p.get("claimed_by") or "nobody"
        if s == "claimed" and a > CLAIM_HOURS * 3600:
            found.append(("Claims sitting too long", f"{task_link('Inbox', name)}: claimed by {who}, no change since "
                          f"{since}. The PC hands back dead claims after 30 min; a cloud sync can't see Hermes' worktrees."))
        if s == "in-review" and a > REVIEW_DAYS * 86400:
            found.append(("Reviews sitting too long", f"{task_link('Inbox', name)}: in review since {since}"
                          + (f" (PR #{p['pr']})" if p.get("pr") else "") + "."))
        if s in ("in-review", "changes", "ready", "sent") and not p.get("pr"):
            found.append(("Missing PR numbers", f"{task_link('Inbox', name)}: `{s}` with no `pr:`."))
        if s == "blocked" and a > WAITING_DAYS * 86400:
            found.append(("Questions waiting on Andy", f"{task_link('Inbox', name)}: blocked since {since}: "
                          + (last_note(path) or "no question written under Notes.")))
        if s == "sent" and a > WAITING_DAYS * 86400:
            found.append(("Waiting on \"merge\"", f"{task_link('Inbox', name)}: sent to Andy {since}, PR #{p.get('pr', '?')}."))
        target = (p.get("for") or "any").strip().lower()
        if not known_worker(vs, target):
            found.append(("Tasks for nobody", f"{task_link('Inbox', name)}: `for: {p.get('for')}` matches no bot or "
                          "agent, so no one will claim it."))

    found += broken_links(live)
    found += memory_index()
    return found


def known_worker(vs, target):
    names = {"any", "hermes", "claude", "cloud", vs.CLOUD_AGENT, *vs.HERMES_BOTS, *vs.SPECIALISTS.values(),
             *vs.FIXER_NAMES}
    names |= {v.split(" ", 1)[1].lower() for v in vs.HERMES_BOTS.values()}
    return target in names or target.startswith(("hermes-", "claude"))


def broken_links(live):
    """[[links]] in the live notes and the vault's own notes that point at no note."""
    roots = [r for r in (live, vault_dir()) if os.path.isdir(r)]
    names = set()
    for root in roots:
        for dirpath, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if not d.startswith(".")]
            for f in files:
                stem, ext = os.path.splitext(f)
                names.add(stem.lower())
                names.add(f.lower())
                rel = os.path.relpath(os.path.join(dirpath, stem), root).replace("\\", "/").lower()
                names.add(rel)
    found = []
    targets = [(os.path.join(live, f), f) for f in sorted(os.listdir(live)) if f.endswith(".md")]
    for folder in ("Agents", "Inbox"):
        root = os.path.join(live, folder)
        targets += [(os.path.join(root, f), f"{folder}/{f}") for f in sorted(os.listdir(root)) if f.endswith(".md")] \
            if os.path.isdir(root) else []
    vroot = vault_dir()
    targets += [(os.path.join(dp, f), os.path.relpath(os.path.join(dp, f), vroot).replace("\\", "/"))
                for dp, dirs, files in os.walk(vroot) if "Live" not in os.path.relpath(dp, vroot).split(os.sep)
                for f in files if f.endswith(".md")] if os.path.isdir(vroot) else []
    skip = {"Dashboard.md", "Health.md", "Kanban.md"}  # written by these tools
    for path, shown in targets:
        if os.path.basename(path) in skip:
            continue
        text = re.sub(r"```.*?```|`[^`\n]*`", "", open(path, encoding="utf-8").read(), flags=re.S)
        bad = sorted({t.strip() for t in LINK.findall(text)
                      if t.strip() and not re.search(r"\.\w{2,5}$", t.strip()) and "{{" not in t
                      and t.strip().lower() not in names and t.strip().split("/")[-1].lower() not in names})
        if bad:
            found.append(("Broken [[links]]", f"`{shown}`: " + ", ".join(f"[[{b}]]" for b in bad[:8])
                          + (f" and {len(bad) - 8} more" if len(bad) > 8 else "")))
    return found


def vault_dir():
    return os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "vault")


def memory_index():
    """The project memory index (cloud sessions only) is read only up to MEMORY_LIMIT bytes."""
    path = os.environ.get("VAULT_MEMORY_INDEX") or next((p for p in MEMORY_PATHS if os.path.exists(p)), "")
    if not path or not os.path.exists(path):
        return []
    data = open(path, encoding="utf-8").read()
    found = []
    size = len(data.encode("utf-8"))
    if size > MEMORY_LIMIT:
        found.append(("Project memory", f"The memory index is {size // 1024} KB; only the first {MEMORY_LIMIT // 1024} KB "
                      "load in a new thread. Shorten entries to one line and move detail into topic files."))
    long = [i + 1 for i, l in enumerate(data.splitlines()) if len(l) > MEMORY_LINE]
    if long:
        found.append(("Project memory", f"{len(long)} index lines are over {MEMORY_LINE} characters (lines "
                      + ", ".join(map(str, long[:10])) + "): move their detail into topic files."))
    return found


def health_text(found):
    out = [f"<!-- Written by tools/vault_sync.py on every sync ({time.strftime('%Y-%m-%d %H:%M')}). Don't edit. -->",
           "# Vault health", "",
           "Problems in the live notes, found on every sync. Nothing here is fixed by itself: whoever reads it (the "
           "coordinator's hourly check, a bot, Andy) fixes the note, and the line goes away on the next sync. "
           "`python tools/vault_sync.py --health` prints the same list.", ""]
    if not found:
        return "\n".join(out + ["Nothing wrong.", ""])
    sections = {}
    for section, line in found:
        sections.setdefault(section, []).append(line)
    out.append(f"**{len(found)} problem{'s' if len(found) != 1 else ''}.**")
    for section, lines in sections.items():
        out += ["", f"## {section}", ""] + [f"- {l}" for l in lines]
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- dashboard

def dashboard_text(live, vs, found):
    inbox, agents = notes(live, "Inbox", vs), notes(live, "Agents", vs)
    task_names = {n for n, _, _ in inbox}
    out = [f"<!-- Written by tools/vault_sync.py on every sync ({time.strftime('%Y-%m-%d %H:%M')}). Only ticking "
           "boxes under \"Merged, not played yet\" sticks; anything else you change here is rewritten. -->",
           "# Dashboard", "",
           "Start here. The board of every task is [[Kanban]]; versions and the vault's guides are on [[Home]].", ""]

    out += ["## Waiting on you", ""]
    merge = [(n, p) for n, _, p in inbox if p.get("status") in ("ready", "sent")]
    merge += [(n, p) for n, _, p in agents if p.get("status") in ("ready", "sent") and p.get("task") not in task_names]
    done_tasks = {n[:-3] for n in os.listdir(os.path.join(live, "Inbox", "Done"))} \
        if os.path.isdir(os.path.join(live, "Inbox", "Done")) else set()
    asks = [(f, n, path, p) for f, items in (("Inbox", inbox), ("Agents", agents)) for n, path, p in items
            if p.get("status") == "blocked"
            and not (f == "Agents" and ({p.get("task"), n} & (task_names | done_tasks)))]
    if merge:
        out.append("**Ready to merge** (say \"merge\" in the project chat):")
        out += [f"- {task_link('Inbox' if n in task_names else 'Agents', n)} · "
                + (f"PR #{p['pr']}" if p.get("pr") else "no PR number on the task") + f" · {p.get('status')}"
                for n, p in merge]
        out.append("")
    if asks:
        out.append("**Questions for you:**")
        out += [f"- {task_link(f, n)} · " + (last_note(path) or "see the note") for f, n, path, p in asks]
        out.append("")
    if not merge and not asks:
        out += ["Nothing right now.", ""]

    stuck = [line for section, line in found if section in (
        "Stalled agent notes", "Claims sitting too long", "Reviews sitting too long", "Agent notes whose task is done")]
    building = sum(1 for _, _, p in inbox if p.get("status") in ("claimed", "changes", "writing"))
    reviewing = sum(1 for _, _, p in inbox if p.get("status") == "in-review")
    waiting = sum(1 for _, _, p in inbox if p.get("status") == "open")
    out += ["## Stuck", ""] + ([f"- {l}" for l in stuck] or ["Nothing stuck."]) + [""]
    out += ["## On the board", "",
            f"{waiting} waiting for a bot, {building} being built, {reviewing} in review: see [[Kanban]].", ""]

    since = (datetime.date.today() - datetime.timedelta(days=NOT_PLAYED_DAYS)).isoformat()
    done = played(live)
    rows = [w for w in merged_work(vs, since) if w[4] and w[1] and w[1] not in done]
    out += ["## Merged, not played yet", "",
            f"Merged in the last {NOT_PLAYED_DAYS} days and marked \"not played\" in the MODLOGs, newest first. Tick a "
            "line once you've played it: the next sync moves it to [[Played]].", ""]
    out += [f"- [ ] #{pr} {title} ({', '.join(mods)}, {date})" for date, pr, title, mods, _ in rows[:NOT_PLAYED_MAX]]
    if len(rows) > NOT_PLAYED_MAX:
        out.append(f"- …and {len(rows) - NOT_PLAYED_MAX} older ones.")
    if not rows:
        out.append("Nothing waiting to be played.")
    out += ["", "## Health", "",
            (f"{len(found)} problem{'s' if len(found) != 1 else ''} in the notes: [[Health]]." if found
             else "Nothing wrong: [[Health]]."), ""]
    weeks = sorted(f[:-3] for f in os.listdir(os.path.join(live, "Weekly"))) \
        if os.path.isdir(os.path.join(live, "Weekly")) else []
    if weeks:
        out += ["## Last week", "", f"[[Weekly/{weeks[-1]}|{weeks[-1]}]]", ""]
    return "\n".join(out)


# ---------------------------------------------------------------- weekly review

def decisions(live, since, until):
    """Decision lines dated since..until from Decisions.md (bullets "- 2026-11-18 ..." or "### 2026-11-18: ...")."""
    path = os.path.join(live, "Decisions.md")
    if not os.path.exists(path):
        return []
    out, current = [], None
    for line in open(path, encoding="utf-8").read().splitlines():
        m = re.match(r"^(?:- |### )(\d{4}-\d{2}-\d{2})", line)
        if m:
            current = line[2:] if line.startswith("- ") else line[4:]
            if since <= m.group(1) <= until:
                out.append(current)
            else:
                current = None
        elif current and line.startswith("- **Decided:**") and out and out[-1] == current:
            out[-1] = current + ": " + line[len("- **Decided:**"):].strip()
    return out


def weekly_text(live, vs, since, until, title):
    out = [f"<!-- Written by tools/vault_sync.py ({time.strftime('%Y-%m-%d %H:%M')}). -->", f"# {title}", "",
           f"{since} to {until}. What merged, what Andy decided, and what's still asked for.", ""]
    merged = merged_work(vs, since, until)
    done = played(live)
    picks = decisions(live, since, until)
    still = [(n, p) for n, _, p in notes(live, "Inbox", vs) if p.get("status") not in ("done", None, "")]
    prs = [w for w in merged if w[1]]
    per_mod = {mod: sum(1 for w in merged if mod in w[3]) for mod, _ in MODLOGS}
    unplayed = sum(1 for w in merged if w[4] and not (w[1] and w[1] in done))
    out += [f"**{len(prs)} PRs merged** ({', '.join(f'{n} {m}' for m, n in per_mod.items() if n)} changes), "
            f"{unplayed} marked not played. **{len(picks)} decisions.** **{len(still)} asks still open.**", ""]
    out += [f"## Andy's decisions ({len(picks)})", ""]
    out += [f"- {d}" for d in picks] or ["None logged in [[Decisions]]."]
    out += ["", f"## Still open ({len(still)})", ""]
    out += [f"- {task_link('Inbox', n)} · {p.get('status')}" + (f" · PR #{p['pr']}" if p.get("pr") else "")
            + (f" · asked {p['asked']}" if p.get("asked") else "") for n, p in sorted(still, key=lambda t: t[1].get("status", ""))] \
        or ["Nothing open."]
    out += ["", f"## Merged ({len(merged)} changes)", ""]
    out += [f"- #{pr} {t} ({', '.join(m)})" + ("" if not np or (pr and pr in done) else ", not played")
            if pr else f"- {t} ({', '.join(m)})" for _, pr, t, m, np in merged] or ["Nothing merged."]
    return "\n".join(out) + "\n"


def write_weekly(live, vs):
    """Once a week: the review of the last full week (Monday to Sunday), if it isn't written yet."""
    today = datetime.date.today()
    monday = today - datetime.timedelta(days=today.weekday() + 7)
    year, week, _ = monday.isocalendar()
    path = os.path.join(live, "Weekly", f"{year}-W{week:02d}.md")
    if os.path.exists(path):
        return None
    write_if_changed(path, weekly_text(live, vs, monday.isoformat(), (monday + datetime.timedelta(days=6)).isoformat(),
                                       f"Week {week}, {year}"))
    return path


# ---------------------------------------------------------------- after every sync

def write_all(live, vs):
    """Called by vault_sync.sync() after the Kanban. Returns the paths it changed (for the commit)."""
    changed = []
    record_ticks(live)
    if os.path.exists(os.path.join(live, "Played.md")):
        changed.append("Played.md")
    found = health(live, vs)
    if write_if_changed(os.path.join(live, "Health.md"), health_text(found)):
        changed.append("Health.md")
    weekly = write_weekly(live, vs)
    if weekly:
        changed.append(os.path.relpath(weekly, live).replace("\\", "/"))
    if write_if_changed(os.path.join(live, "Dashboard.md"), dashboard_text(live, vs, found)):
        changed.append("Dashboard.md")
    return changed


# ---------------------------------------------------------------- --next: a bot's next job in one command

def next_job(live, vs, agent):
    """Resume this agent's own claimed task, else claim the next one (vault_sync.claim), then print the steps."""
    if agent in ("hermes-writer", "quill"):
        return vs.claim_text_pass(live, "hermes-writer")
    mine = sorted(t for t in vs.tasks(live, ("claimed",)) if (t[2].get("claimed_by") or "") == agent)
    if mine:
        name = mine[0][1]
        sys.stdout.reconfigure(encoding="utf-8")
        print(f"Resuming your claimed task: {name}  ({vs.task_path(live, name)})\n")
        print(open(vs.task_path(live, name), encoding="utf-8").read())
    else:
        name = vs.claim(live, agent, fixes=agent in vs.FIXER_NAMES)
    print(steps(live, vs, agent, name))


def steps(live, vs, agent, name):
    p = vs.frontmatter(vs.task_path(live, name))
    owns = [o.strip() for o in (p.get("suggested_owns") or "").split(",") if o.strip()]
    pr = (p.get("pr") or "").strip()
    wt = f"{vs.WORKTREES}/{name}"
    if pr:
        tree = (f"It already has PR #{pr}: work on that PR's own branch. `gh pr view {pr} --json headRefName`, then "
                f"`git fetch origin && git worktree add {wt} origin/<head>` (or `git -C {wt} pull` if it exists). "
                "Never force-push or rebase it.")
    else:
        tree = f"`git fetch origin && git worktree add {wt} -b {agent}/{name} origin/master` (your own worktree, never the main checkout)."
    lines = [
        "", "---", f"Next steps for {agent} on {name}:", "",
        f"1. Worktree: {tree}",
        f"2. Agent note: copy docs/vault/Templates/Agent.md to `E:/Jaymo Vault Live/Agents/{name}.md` with `task: {name}`, "
        "your branch, `status: running` and the files you'll edit under `owns:`. Don't edit a file another running "
        "agent owns (`python tools/vault_sync.py --status`).",
        "3. Files to own: " + (", ".join(owns) if owns else "none suggested: list the ones you edit as you go."),
        "4. Read CLAUDE.md first (lore first, change notes, no versions/MODLOGs/codemap). If the task was sent back, "
        "fix every numbered point in the newest review note.",
        "5. Check, don't assume: prove each part is done with a real check (build, grep, a test), never invent a class, "
        "number or file you haven't seen, and search before saying something doesn't exist. Go through the task's "
        "ask line by line before you call it done; name anything you couldn't do and why.",
        "6. Before the PR: the \"Before you open a PR\" checklist in docs/AI-HANDBOOK.md, a change note per mod "
        "(changes/<mod>/<branch>.md), `python tools/pr_checks.py`.",
        f"7. PR up: `python tools/vault_sync.py --set {name} status=in-review pr=<number>`. Question for Andy instead: "
        f"`python tools/vault_sync.py --note {name} \"Question: ...\"` then `--set {name} status=blocked`. Never merge.",
    ]
    return "\n".join(lines)
