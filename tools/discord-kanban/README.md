# Vault kanban as a Discord Activity (parked)

A drag-and-drop copy of the vault board (`Kanban.md` on `vault-live`) that opens inside Discord as an Activity,
hosted on a Cloudflare Worker. A drop changes the task the way `vault_sync.py --set` does, in one commit on
`vault-live`; only the Discord ids in `MOVER_IDS` may move cards.

Parked on 2026-11-10: Andy chose the markdown board with a Move menu (YOUTH's `discord_board.py`) instead. Kept here
in case he wants drag-and-drop later. Built and tested (`npm install && npm test`, `npm run build`), never deployed.

Setup it would need: a Discord app with Activities on and a URL mapping `/` to the Worker; a Cloudflare account
(`npx wrangler deploy`); secrets `DISCORD_CLIENT_SECRET` and a fine-grained `GITHUB_TOKEN` (this repo, Contents
read/write) via `npx wrangler secret put`; `DISCORD_CLIENT_ID` and `MOVER_IDS` in `wrangler.toml`.
