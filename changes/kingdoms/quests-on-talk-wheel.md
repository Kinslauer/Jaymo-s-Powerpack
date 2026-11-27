# Quests on the talk wheel's first ring, with a tip
- Talking to a villager or king with a gold **!** (work on offer) or a **?** (one of your quests) over their head, **Quests** is now on the talk wheel's first ring, right after the reply spoke. It is still under Ask of Them... as well.
- A tip sits where your line goes (bottom right, while you're not pointing at a spoke): "They have work for you. Pick Quests on the wheel to hear it", or for a ? "Pick Quests on the wheel to check on it or hand it in". It stops after you've picked Quests from the wheel 3 times (kept in `config/jaymoskingdoms-tips.properties`), and never shows with `/hints off` (or `/tips off`).
- `TalkFirst` (Quests spoke, `questTip`), new `QuestTip`, `QuestClient.mark`, `McaDialogueBoxMixin` (draws the tip).
- Built (compileJava); not played.
