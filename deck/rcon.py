#!/usr/bin/env python3
"""Sends one command to the server's console over RCON (local only) and prints the reply.

Usage, from ~/jaymos-server:  ./rcon.py stop    ./rcon.py list    ./rcon.py "say hello"
The port and password come from server.properties (enable-rcon=true), so the password is never typed or shown.
"""
import socket
import struct
import sys
from pathlib import Path


def properties():
    out = {}
    for line in (Path(__file__).resolve().parent / "server.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            out[key.strip()] = value.strip()
    return out


def packet(sock, kind, body, ident=1):
    data = struct.pack("<ii", ident, kind) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)


def receive(sock):
    size = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < size:
        data += sock.recv(size - len(data))
    ident, kind = struct.unpack("<ii", data[:8])
    return ident, data[8:-2].decode("utf-8", "replace")


def main():
    if len(sys.argv) < 2:
        print(__doc__.strip())
        return 2
    props = properties()
    if props.get("enable-rcon") != "true":
        print("RCON is off in server.properties")
        return 1
    with socket.create_connection(("127.0.0.1", int(props.get("rcon.port", "25575"))), timeout=10) as sock:
        packet(sock, 3, props.get("rcon.password", ""))
        ident, _ = receive(sock)
        if ident == -1:
            print("RCON refused the password")
            return 1
        packet(sock, 2, " ".join(sys.argv[1:]))
        _, reply = receive(sock)
        print(reply or "(no reply)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
