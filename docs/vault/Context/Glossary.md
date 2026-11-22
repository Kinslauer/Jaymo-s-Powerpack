# Glossary

Andy's words for things, and what they're called in the code and the vault. Read it when a request names something
you can't find by that name. Add a row whenever you learn that Andy's word for something differs from ours (in your
PR, like any reference note), and only from something Andy actually said or confirmed: never a guess.

## Asks and the way we work

| Andy says | Means |
|---|---|
| "merge", "merge pr", "merge it to master", "merge all" | Go ahead and merge (only Andy says it; nobody merges without it). |
| "pbdr" | Pull, build, deploy, restart the Steam Deck server (`.claude/skills/pbdr`). |
| "pbdr new world", "new map" | The same, and start a fresh world. |
| a bare "More" | Add a few more ideas of your own in the same vein; don't ask what he means. |
| a bare clip (video, no words) | Something's wrong in it: start a thread to find out what. |
| "continue" (on a job split into patches) | Build the next patch of it. |
| "the origin folder" | `E:\Origins Mod`: the main checkout of the repo plus his local files. |
| "the vault" | `docs/vault` in Obsidian, with the live folder `E:/Jaymo Vault Live` showing as `Live/`. |
| "the board", "Kanban" | `Live/Kanban.md` (read-only, rebuilt on every sync). |
| "the reviewer" | Claude Code on Andy's PC running `/loop 15m /vault-review`. |
| "the coordinator" | The Claude project chat Andy talks to; it posts Inbox tasks and hands PRs to him. |
| Odysseus | The cloud Claude (Claude Code threads and the coordinator). Shown as 💻 Odysseus on the board. |
| Apollo, Bacchus | The Hermes builder bots `hermes-a` and `hermes-b`. |
| Hephaestus | The fixer bot, `hermes-fixer` (takes sent-back tasks). |
| Prometheus | The modeller bot, `hermes-modeller` (art through `tools/art-agent`). |
| Quill | The writer bot, `hermes-writer` (text pass on every Hermes PR). |
| YOUTH | The default Hermes bot on Discord: it only files tasks and starts jobs, it never builds. |
| "the Deck" | The Steam Deck that runs the server (`~/jaymos-server`). |
| "the Testing profile" | `E:\CurseForge\Instances\Testing`. |
| "tickable page", "pick list" | A claude.ai artifact page with one checkbox per idea; his ticks are what gets built. |

## In the game

| Andy says | In the code |
|---|---|
| "iron greatsword" | The Armory **Claymore** (`armory_assets.py`, `FIRST_PERSON`). |
| "ranger hood" | Kingdoms' **Scoundrel's Hood** (`scoundrel_helmet`). |
| "the H menu" | The character menu, key H. |
| "G+B" (and other key pairs) | Press G, then B: the second key used while the first ability is out (Crush released inside your own Gravity Well). |
| R / G / V / B, C, Z, ' | Abilities 1-4, the ultimate, the mode key, the costume mask (default keys). |
| "downed" | Knocked down, not dead: the body stays in the world until it comes to (bodies can be carried). |
| "the human bloods" | Heartlander, Nightborn, Earthling, Stormborn, Dawnborn (`Faiths.human`); every other blood meets prejudice. |
| "Grim keeps", "citadels" (Winterhold and the like) | Grim strongholds: only ever fiefs or allies of small villages, never kingdoms. |
| "Epic villages" | The only villages that found kingdoms. |
