"""Open a new Discord thread (or forum post) and post a message in it, for YOUTH.

Hermes has no agent tool that opens a Discord thread, so when someone in Discord says "open a thread for this",
YOUTH runs this from its terminal:

  py -3 tools/discord_thread.py --in <channel or thread id> --title "OmniRoute experiment" --message-file note.md

--in is where the ask came from (the id of the channel or thread YOUTH is talking in). The script works out what
that is:
  - a forum (like parthenon): makes a new post in it
  - a post or thread inside a forum: makes a new post in that same forum, next to it
  - a text channel: makes a new public thread in it
  - a thread inside a text channel: makes a new thread in that channel
It prints the new thread's link on the last line, so YOUTH can reply with it.

Env:
  KANBAN_BOT_TOKEN   YOUTH's bot token (falls back to DISCORD_BOT_TOKEN). Never print it.

Options: --tag NAME (forum tag, repeatable; needed when the forum requires one), --dry-run (shows what it would
do, posts nothing). A message over 2000 characters is split into several posts in the new thread.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

API = "https://discord.com/api/v10"
LIMIT = 2000  # Discord's message length limit

# Discord channel types
TEXT, ANNOUNCEMENT, ANNOUNCEMENT_THREAD, PUBLIC_THREAD, PRIVATE_THREAD, FORUM, MEDIA = 0, 5, 10, 11, 12, 15, 16
THREADS = {ANNOUNCEMENT_THREAD, PUBLIC_THREAD, PRIVATE_THREAD}
FORUMS = {FORUM, MEDIA}


def call(method, path, token, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method, headers={
        "Authorization": "Bot " + token,
        "Content-Type": "application/json",
        "User-Agent": "DiscordBot (https://github.com/Kinslauer/Jaymo-s-Powerpack, 1)",
    })
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read() or b"null")
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")[:500]
        hint = ""
        if e.code == 403:
            hint = " (the bot needs Create Public Threads / Create Posts and Send Messages in Threads there)"
        raise SystemExit(f"Discord said {e.code} on {method} {path}{hint}: {detail}")


def chunks(text):
    """Split text into pieces under Discord's limit, at line breaks where possible."""
    out, cur = [], ""
    for line in text.splitlines(keepends=True):
        while len(line) > LIMIT:
            if cur:
                out.append(cur)
                cur = ""
            out.append(line[:LIMIT])
            line = line[LIMIT:]
        if len(cur) + len(line) > LIMIT:
            out.append(cur)
            cur = ""
        cur += line
    if cur.strip():
        out.append(cur)
    return out or ["(empty)"]


def main():
    ap = argparse.ArgumentParser(description="Open a Discord thread or forum post and post a message in it.")
    ap.add_argument("--in", dest="where", required=True, help="channel, forum or thread id the ask came from")
    ap.add_argument("--title", required=True, help="thread title (Discord keeps the first 100 characters)")
    msg = ap.add_mutually_exclusive_group(required=True)
    msg.add_argument("--message", help="first message text")
    msg.add_argument("--message-file", help="file holding the first message (markdown)")
    ap.add_argument("--tag", action="append", default=[], help="forum tag name to apply (repeatable)")
    ap.add_argument("--dry-run", action="store_true", help="say what would happen, post nothing")
    args = ap.parse_args()

    token = os.environ.get("KANBAN_BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN")
    if not token:
        raise SystemExit("Set KANBAN_BOT_TOKEN (or DISCORD_BOT_TOKEN) to YOUTH's bot token.")
    if args.message_file:
        with open(args.message_file, encoding="utf-8") as fh:
            text = fh.read()
    else:
        text = args.message
    parts = chunks(text)
    title = args.title.strip()[:100] or "New thread"

    here = call("GET", f"/channels/{args.where}", token)
    parent = here
    if here["type"] in THREADS:  # asked from inside a thread or post: open the new one beside it
        parent = call("GET", f"/channels/{here['parent_id']}", token)
    guild = parent.get("guild_id")

    if parent["type"] in FORUMS:
        tags = []
        if args.tag:
            by_name = {t["name"].lower(): t["id"] for t in parent.get("available_tags", [])}
            missing = [t for t in args.tag if t.lower() not in by_name]
            if missing:
                raise SystemExit(f"No such tag in that forum: {', '.join(missing)}. Tags: {', '.join(by_name) or 'none'}")
            tags = [by_name[t.lower()] for t in args.tag]
        what = f"new post \"{title}\" in forum #{parent.get('name')}"
        if args.dry_run:
            print(f"Would make a {what}, {len(parts)} message(s).")
            return
        body = {"name": title, "message": {"content": parts[0]}}
        if tags:
            body["applied_tags"] = tags
        thread = call("POST", f"/channels/{parent['id']}/threads", token, body)
    elif parent["type"] in (TEXT, ANNOUNCEMENT):
        what = f"new thread \"{title}\" in #{parent.get('name')}"
        if args.dry_run:
            print(f"Would make a {what}, {len(parts)} message(s).")
            return
        kind = ANNOUNCEMENT_THREAD if parent["type"] == ANNOUNCEMENT else PUBLIC_THREAD
        thread = call("POST", f"/channels/{parent['id']}/threads", token,
                      {"name": title, "type": kind, "auto_archive_duration": 10080})
        call("POST", f"/channels/{thread['id']}/messages", token, {"content": parts[0]})
    else:
        raise SystemExit(f"Can't open a thread in channel type {parent['type']} (#{parent.get('name')}).")

    for part in parts[1:]:
        call("POST", f"/channels/{thread['id']}/messages", token, {"content": part})
    print(f"Made a {what}.")
    print(f"https://discord.com/channels/{guild}/{thread['id']}")


if __name__ == "__main__":
    sys.exit(main())
