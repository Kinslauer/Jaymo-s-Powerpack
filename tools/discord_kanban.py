"""The vault's Kanban as one Discord message: a markdown board with a Move menu, kept alone in its thread.

    python tools/discord_kanban.py          # run it on the PC (it stays running); needs discord.py 2.4+

What it does:
  - Reads Kanban.md in the live vault folder (the board tools/vault_sync.py writes) and shows it as markdown in one
    message in the Discord thread, edited in place whenever Kanban.md changes (checked every 20 s).
  - Under the board: "Move a card..." lists the Inbox tasks; pick one, then pick its new lane. That sets the task's
    status with the same rules the vault uses, adds a Notes line, syncs the vault and redraws the board.
  - Deletes every other message posted in that thread, so the board is the only thing in it (the thread's
    opening post stays: deleting it would delete the thread).

Settings (environment variables):
  KANBAN_BOT_TOKEN   the bot's token (falls back to DISCORD_BOT_TOKEN). Never print it.
  KANBAN_THREAD_ID   the thread the board lives in (default: the parthenon "Kanban" post).
  KANBAN_MOVERS      Discord user ids allowed to move cards, comma-separated. Nobody else can.
  KANBAN_CLEAN=0     keep other messages in the thread instead of deleting them.

The bot needs View Channel, Send Messages in Threads, Read Message History and Manage Messages in that thread.
Stop the old board job (E:/hermes/scripts/discord_board.py, cron discord-kanban) before running this, or the two
will fight over the thread. Remembers its board message in ~/.discord_kanban.json.
"""
import asyncio
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time

import discord

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vault_sync  # noqa: E402  (frontmatter, set_props, add_note, live_dir)

THREAD_ID = int(os.environ.get("KANBAN_THREAD_ID") or 1569368728901255309)  # parthenon > "Kanban"
STATE = os.path.join(os.path.expanduser("~"), ".discord_kanban.json")
POLL_SECONDS = 20  # how often Kanban.md is checked for changes
MESSAGE_LIMIT = 2000  # Discord's limit for a message's text
MENU_LIMIT = 25  # Discord's limit for options in one menu

# Where a card may be moved, and what that sets on the task (same rules as the board's lanes in vault_sync.py).
# needs_pr: only a task with a PR can go there. Lanes not listed (Text pass, anything new) are the bots' to fill.
DROPS = {
    "Waiting for a bot": {"props": {"status": "open", "claimed_by": ""}, "say": "handed back to the bots"},
    "Being built": {"props": {"status": "changes"}, "needs_pr": True, "say": "sent back for fixes"},
    "In review": {"props": {"status": "in-review"}, "needs_pr": True, "say": "sent to review"},
    "Needs Andy": {"props": {"status": "blocked"}, "say": "held for Andy"},
    "Done": {"props": {"status": "done"}, "to_done": True, "say": "marked done"},
}
CARD = re.compile(r"^- \[( |x)\] \[\[([^|\]]+)\|([^\]]+)\]\](.*)$")


def clock(t=None):
    """12-hour clock, as Andy wants every time written: 3:05 PM."""
    return time.strftime("%I:%M %p", time.localtime(t)).lstrip("0")


def live():
    return vault_sync.live_dir()


def parse_kanban(text):
    """Kanban.md -> [(lane, [card])], card = {path, name, meta, movable}. Any lane the file has is shown."""
    lanes, lane = [], None
    for line in text.split("%% kanban:settings")[0].split("\n"):
        head = re.match(r"^## (.+?)\s*$", line)
        if head:
            lane = (head.group(1), [])
            lanes.append(lane)
            continue
        m = lane and CARD.match(line)
        if m:
            path = m.group(2)
            lane[1].append({"path": path, "name": m.group(3), "meta": m.group(4).lstrip(" ·").strip(),
                            "movable": bool(re.fullmatch(r"Inbox/[^/]+", path))})  # agent notes and Done stay put
    return lanes


def read_board():
    path = os.path.join(live(), "Kanban.md")
    text = open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    body = text.split("-->", 1)[-1]  # ignore the "written at" stamp, so only a real change redraws
    return parse_kanban(text), hashlib.sha1(body.encode("utf-8")).hexdigest()


def render(lanes):
    """The board as Discord markdown, under 2000 characters: long lanes end in "...and N more"."""
    def lane_text(name, cards, shown):
        lines = [f"**{name}** ({len(cards)})"]
        for c in cards[:shown]:
            lines.append(f"- `{c['name']}`" + (f" · {c['meta']}" if c["meta"] else ""))
        if not cards:
            lines.append("-# empty")
        elif shown < len(cards):
            lines.append(f"-# ...and {len(cards) - shown} more")
        return "\n".join(lines)

    head = f"## Kanban\n-# From the vault's Kanban.md · updated {clock()} · move a card with the menu below\n"
    shown = {name: len(cards) for name, cards in lanes}
    while True:
        text = head + "\n\n".join(lane_text(n, c, shown[n]) for n, c in lanes)
        if len(text) <= MESSAGE_LIMIT:
            return text
        longest = max(shown, key=shown.get)  # trim the longest lane first, one card at a time
        if shown[longest] == 0:
            return text[:MESSAGE_LIMIT - 1] + "…"
        shown[longest] -= 1


def run_sync():
    subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vault_sync.py")],
                   capture_output=True, text=True, encoding="utf-8")


def move_card(name, lane, who):
    """Set an Inbox task's status for a move to `lane`, note it, sync. Returns what to tell the mover."""
    drop = DROPS[lane]
    run_sync()  # start from the newest vault, so a bot's change isn't overwritten
    path = os.path.join(live(), "Inbox", name + ".md")
    if not os.path.exists(path):
        return f"`{name}` isn't an open Inbox task any more (a bot may have just finished it)."
    props = vault_sync.frontmatter(path)
    if drop.get("needs_pr") and not props.get("pr", "").strip():
        return f"`{name}` has no PR yet, so it can't go to {lane}."
    vault_sync.set_props(path, drop["props"])
    vault_sync.add_note(path, f"{who} moved it to {lane} on the Discord board at {clock()}: {drop['say']}.")
    if drop.get("to_done"):
        os.makedirs(os.path.join(live(), "Inbox", "Done"), exist_ok=True)
        shutil.move(path, os.path.join(live(), "Inbox", "Done", name + ".md"))
    run_sync()  # commits the change and rewrites Kanban.md
    return f"Moved `{name}` to **{lane}**: {drop['say']}."


def movers():
    return {int(x) for x in re.findall(r"\d+", os.environ.get("KANBAN_MOVERS", ""))}


class BoardView(discord.ui.View):
    """The menu under the board: pick a card, then (in a reply only you see) pick its new lane."""

    def __init__(self, bot, lanes):
        super().__init__(timeout=None)
        self.bot = bot
        options = []
        for lane, cards in lanes:
            for c in cards:
                if c["movable"] and len(options) < MENU_LIMIT:
                    options.append(discord.SelectOption(label=c["name"][:100], value=c["path"][len("Inbox/"):][:100],
                                                        description=f"{lane} · {c['meta']}"[:100]))
        self.lane_of = {c["path"][len("Inbox/"):]: lane for lane, cards in lanes for c in cards}
        self.has_pr = {c["path"][len("Inbox/"):]: bool(re.search(r"(^|· )#\d+", c["meta"])) for _, cards in lanes
                       for c in cards}
        if not options:
            options = [discord.SelectOption(label="No Inbox tasks to move", value="-")]
        menu = discord.ui.Select(custom_id="kanban:card", placeholder="Move a card...", options=options)
        menu.callback = self.pick_card
        self.add_item(menu)

    async def pick_card(self, interaction):
        name = interaction.data["values"][0]
        if interaction.user.id not in movers():
            return await interaction.response.send_message("Only Andy can move cards on this board.", ephemeral=True)
        if name == "-":
            return await interaction.response.send_message("Nothing to move right now.", ephemeral=True)
        here = (self.lane_of.get(name) or "").lower()
        lanes = [lane for lane, d in DROPS.items()
                 if lane.lower() != here and (self.has_pr.get(name) or not d.get("needs_pr"))]
        view = discord.ui.View(timeout=300)
        menu = discord.ui.Select(placeholder=f"Move {name} to...", options=[
            discord.SelectOption(label=lane, value=lane, description=DROPS[lane]["say"]) for lane in lanes])

        async def pick_lane(inner):
            lane = inner.data["values"][0]
            await inner.response.edit_message(content=f"Moving `{name}` to **{lane}**...", view=None)
            said = await asyncio.to_thread(move_card, name, lane, inner.user.display_name)
            await inner.edit_original_response(content=said)
            await self.bot.refresh(force=True)

        menu.callback = pick_lane
        view.add_item(menu)
        await interaction.response.send_message(f"Where should `{name}` go?", view=view, ephemeral=True)


class KanbanBot(discord.Client):
    def __init__(self):
        intents = discord.Intents.none()
        intents.guilds = True
        intents.guild_messages = True  # to see (and clear) other messages posted in the board's thread
        super().__init__(intents=intents, allowed_mentions=discord.AllowedMentions.none())
        self.board = None
        self.lanes = []
        self.seen = None
        self.lock = asyncio.Lock()

    async def setup_hook(self):
        self.loop.create_task(self.watch())

    def remember(self, message_id):
        with open(STATE, "w", encoding="utf-8") as fh:
            json.dump({"thread": THREAD_ID, "message": message_id}, fh)

    async def find_board(self, thread):
        """The message to keep editing: the one we remembered, else the thread's opening post or our latest
        message if this bot wrote it, else a new one."""
        try:
            saved = json.load(open(STATE, encoding="utf-8"))
            if saved.get("thread") == THREAD_ID:
                return await thread.fetch_message(saved["message"])
        except (OSError, ValueError, KeyError, discord.NotFound):
            pass
        try:
            first = await thread.fetch_message(thread.id)  # a forum post's opening message shares the thread's id
            if first.author.id == self.user.id:
                return first
        except discord.NotFound:
            pass
        async for m in thread.history(limit=200):
            if m.author.id == self.user.id:
                return m
        return None

    async def refresh(self, force=False):
        async with self.lock:
            lanes, seen = await asyncio.to_thread(read_board)
            if not force and seen == self.seen and self.board:
                return
            self.lanes, self.seen = lanes, seen
            thread = self.get_channel(THREAD_ID) or await self.fetch_channel(THREAD_ID)
            text, view = render(lanes), BoardView(self, lanes)
            if getattr(thread, "archived", False):
                await thread.edit(archived=False)  # forum posts archive themselves when quiet; edits need it open
            if self.board is None:
                self.board = await self.find_board(thread)
            edited = False
            if self.board is not None:
                try:
                    await self.board.edit(content=text, embeds=[], attachments=[], view=view)
                    edited = True
                except discord.NotFound:
                    pass
            if not edited:
                self.board = await thread.send(text, view=view)
            self.remember(self.board.id)

    async def clear_thread(self):
        if os.environ.get("KANBAN_CLEAN", "1") == "0":
            return
        thread = self.get_channel(THREAD_ID) or await self.fetch_channel(THREAD_ID)
        async for m in thread.history(limit=None):
            if m.id not in (self.board.id, thread.id):
                try:
                    await m.delete()
                except discord.HTTPException as e:
                    print(f"couldn't delete message {m.id}: {e}", file=sys.stderr)

    async def on_ready(self):
        print(f"{clock()} Kanban board running in thread {THREAD_ID}")
        await self.refresh(force=True)
        await self.clear_thread()

    async def on_message(self, message):
        if message.channel.id == THREAD_ID and message.author.id != self.user.id \
                and message.id != THREAD_ID and os.environ.get("KANBAN_CLEAN", "1") != "0":
            try:
                await message.delete()
            except discord.HTTPException:
                pass

    async def watch(self):
        await self.wait_until_ready()
        while not self.is_closed():
            await asyncio.sleep(POLL_SECONDS)
            try:
                await self.refresh()
            except Exception as e:  # a hiccup (network, a half-written file) shouldn't stop the board
                print(f"{clock()} refresh failed: {e}", file=sys.stderr)


def main():
    token = os.environ.get("KANBAN_BOT_TOKEN") or os.environ.get("DISCORD_BOT_TOKEN")
    if not token:
        raise SystemExit("Set KANBAN_BOT_TOKEN (or DISCORD_BOT_TOKEN) to the bot's token.")
    if not movers():
        print("KANBAN_MOVERS is empty: the board shows, but nobody can move cards.", file=sys.stderr)
    KanbanBot().run(token, log_handler=None)


if __name__ == "__main__":
    main()
