---
status: open
for: pc-claude
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-12)
asked: 2026-11-12
thread: 1570840212869746910
claimed_by: pc-claude
pr: 1467
suggested_owns: []
---

# Join Minecraft server as a bot experiment

Andy asked if Hermes/YOUTH can join his Minecraft server as a bot.

## Verdict (Nov 13, cloud thread "Plan Minecraft server bot for Hermes")

Possible, without a Minecraft account. The earlier "not possible" (hermes-b, Nov 13 11:46) was wrong: Hermes doesn't
log in as a player; it drives an in-world body through the server console. Andy chose the body: a wispy, winged Sylvan
(Youth, the wood-wisp) that appears beside whoever asks and explains any mechanic in chat. Mineflayer is out (no
official 26.3 support, and our modded Fabric server would likely reject it).

- **Mod side: built, PR #1467** (Kingdoms `YouthWisp`, 8 GeckoLib idles plus appear/vanish/talk, `/youth summon|say|
  sayall|dismiss|status`). Waits for Andy's merge, then pbdr. Not seen in game yet.
- **Hermes side: for PC Claude.** `E:/hermes/scripts/mcbot.py` (fixed verbs over SSH + the Deck's `rcon.py`), a chat
  listener on `logs/latest.log`, a YOUTH skill `minecraft-mechanics` that answers only from our files, the safety limits.
  Steps in `Minecraft server bot plan.md` and the handoff's "Minecraft server bot experiment" section.
- Lore approved: "The wood-wisps" in Lore.md.

## Notes
- 2026-11-13 11:46 hermes-b: concurred with "not possible" (superseded by the verdict above).
