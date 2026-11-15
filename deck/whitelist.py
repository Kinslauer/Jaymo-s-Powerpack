#!/usr/bin/env python3
"""The server's whitelist, for premium and offline (non-premium) accounts alike.

Usage, from ~/jaymos-server:
  ./whitelist.py on                    whitelist everyone who has ever joined (usercache.json), then switch it on
  ./whitelist.py add <name>            let one more player in: premium names are looked up with Mojang, any other
                                       name gets the offline UUID its owner will join with
  ./whitelist.py add <name> --offline  an offline friend whose name also belongs to a premium account: they're let in
                                       under the offline UUID, and EasyAuth asks them for a password instead of Mojang
  ./whitelist.py remove <name>         take a player off it
  ./whitelist.py list                  show who's on it, premium or offline

With the server running, the change applies at once (over RCON); otherwise on its next start.
Vanilla's "whitelist add" can't add an offline name while online-mode is on (Mojang doesn't know it), hence this.
"""
import hashlib
import json
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

import rcon

HERE = Path(__file__).resolve().parent
WHITELIST = HERE / "whitelist.json"
USERCACHE = HERE / "usercache.json"
PROPERTIES = HERE / "server.properties"


def offline_uuid(name):
    """The UUID an offline account joins with (Minecraft's UUIDUtil.createOfflinePlayerUUID)."""
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    digest[6] = digest[6] & 0x0F | 0x30
    digest[8] = digest[8] & 0x3F | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def premium(name):
    """(name as Mojang spells it, UUID) of the premium account with this name, or None if there isn't one."""
    try:
        with urllib.request.urlopen("https://api.mojang.com/users/profiles/minecraft/" + name, timeout=10) as reply:
            data = json.load(reply)
    except urllib.error.HTTPError as e:
        if e.code in (204, 404):
            return None
        raise
    return data["name"], str(uuid.UUID(data["id"]))


def load():
    return json.loads(WHITELIST.read_text()) if WHITELIST.exists() else []


def save(entries):
    WHITELIST.write_text(json.dumps(entries, indent=2) + "\n")


def set_property(key, value):
    lines = PROPERTIES.read_text().splitlines() if PROPERTIES.exists() else []
    for i, line in enumerate(lines):
        if line.startswith(key + "="):
            lines[i] = key + "=" + value
            break
    else:
        lines.append(key + "=" + value)
    PROPERTIES.write_text("\n".join(lines) + "\n")


def console(*commands):
    """Runs console commands if the server is up with RCON on; False if it isn't."""
    props = rcon.properties()
    if props.get("enable-rcon") != "true":
        return False
    try:
        with rcon.socket.create_connection(("127.0.0.1", int(props.get("rcon.port", "25575"))), timeout=10) as sock:
            rcon.packet(sock, 3, props.get("rcon.password", ""))
            if rcon.receive(sock)[0] == -1:
                return False
            for command in commands:
                rcon.packet(sock, 2, command)
                rcon.receive(sock)
    except OSError:
        return False
    return True


def applied(*commands):
    if console("whitelist reload", *commands):
        print("Applied to the running server.")
    else:
        print("The server isn't running (or RCON is off): it applies on the next start.")


def add(entries, name, uid):
    entries[:] = [e for e in entries if e["uuid"] != uid and e["name"].lower() != name.lower()]
    entries.append({"uuid": uid, "name": name})


def main(args):
    if not args or args[0] not in ("on", "add", "remove", "list"):
        print(__doc__.strip())
        return 2
    entries = load()
    if args[0] == "list":
        for e in sorted(entries, key=lambda e: e["name"].lower()):
            kind = "offline" if e["uuid"] == offline_uuid(e["name"]) else "premium"
            print(f"{e['name']:<17} {kind:<8} {e['uuid']}")
        print(f"{len(entries)} on the whitelist")
        return 0
    if args[0] == "on":
        seen = json.loads(USERCACHE.read_text()) if USERCACHE.exists() else []
        before = len(entries)
        for e in seen:
            if not any(x["uuid"] == e["uuid"] for x in entries):
                entries.append({"uuid": e["uuid"], "name": e["name"]})
        save(entries)
        set_property("white-list", "true")
        set_property("enforce-whitelist", "true")
        set_property("online-mode", "true")
        print(f"Whitelisted {len(entries) - before} player(s) who have joined before ({len(entries)} in all); whitelist on.")
        applied("whitelist on")
        return 0
    if len(args) < 2:
        print(__doc__.strip())
        return 2
    name = args[1]
    if args[0] == "remove":
        left = [e for e in entries if e["name"].lower() != name.lower()]
        if len(left) == len(entries):
            print(name + " isn't on the whitelist.")
            return 1
        save(left)
        print("Took " + name + " off the whitelist.")
        applied()
        return 0
    offline = "--offline" in args[2:]
    found = None if offline else premium(name)
    if found:
        name, uid = found
        add(entries, name, uid)
        save(entries)
        print(f"Added {name} (premium account, {uid}).")
        applied()
    else:
        uid = offline_uuid(name)
        add(entries, name, uid)
        save(entries)
        print(f"Added {name} (offline account, {uid}). They choose a password the first time they join.")
        # a name a premium account also has: EasyAuth must treat it as offline, or it asks Mojang and turns them away
        applied(*(["auth markAsOffline " + name] if offline else []))
        if offline and not console("list"):
            print("Once the server is up, also run: ./rcon.py auth markAsOffline " + name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
