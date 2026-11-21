# hints as tip cards - text hints show top right instead of over the hotbar
- Andy (2026-11-20): "All hints in text should be reworked as tooltips in the new menu, top right. Hints like, you must kneel to revive, or skill not unlocked".
- **Hint cards** (`client.hud.HintCards`): an action-bar line that is a hint (how to do something, what it needs first, a skill, perk or ability not learned yet) now comes up as a tip card in the top right, titled Hint, and is kept on the menu's Notices page like the other cards. 315 lines across Powers, Kingdoms and Kitchen are hints, listed by key in `assets/jaymospowers/hint_keys.txt`; every other line (what just happened, counts, cooldowns, gauges, callouts like "Sneak attack!") stays on the bar.
- A hint sent again and again (each tick of a hold, each try) makes one card, and the same hint isn't carded again for 30 seconds.
- Lines built from a key at run time (about 250 of them) and chat messages aren't covered yet.
- Built; not played.
