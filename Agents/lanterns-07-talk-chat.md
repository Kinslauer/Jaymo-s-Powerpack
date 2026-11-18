---
status: running
task: lanterns-07-talk-chat
branch: claude/lanterns-07-talk-chat
with: Claude Code (PC)
pr: 1767
mods: kingdoms, powers
versions: 
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/ChatTalk.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/ProximityChat.java (hook lines)
  - kingdoms/src/main/java/com/jaymoskingdoms/McaCompat.java (chatAI at the end)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/chat-talk.json
  - src/main/java/com/jaymospowers/power/xenoform/DoubleOrders.java (new)
  - src/main/java/com/jaymospowers/power/xenoform/XenoDouble.java (order state, goal, init line)
  - src/main/resources/assets/jaymospowers/lang/parts/double_orders.json
  - changes/kingdoms/lanterns-07-talk-chat.md, changes/powers/lanterns-07-talk-chat.md
---
# lanterns-07-talk-chat

**Task:** Lanterns patch 7: talk to villagers through chat; Duplex doubles take spoken orders.

## Notes
- The topic matcher (ChatTalk.Topic) is the small one patch 9 can grow.

## Done
