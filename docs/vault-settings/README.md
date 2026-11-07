# Obsidian settings for the project vault

Git ignores `docs/vault/.obsidian/` because Obsidian rewrites those files whenever a setting changes, which blocked
`git pull`. These are the shared starting settings: templates in `Templates/`, new notes in `Live/Agents/`,
bookmarks for Home, Board and Decisions, and the core plugins the vault uses (Templates, Bases, Bookmarks).

To set up the vault on a new PC, or after Obsidian's settings get lost, copy them in (close Obsidian first):

```bash
mkdir -p docs/vault/.obsidian && cp docs/vault-settings/*.json docs/vault/.obsidian/
```
