---
status: running
task: lanterns-09-talk-typed
branch: claude/lanterns-09-talk-typed
with: Claude Code (PC)
pr: 1770
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/AskTopics.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/ChatTalk.java (new topics; from lanterns-07)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/TalkScreen.java (typing box, follow-up panel)
  - kingdoms/src/main/java/com/jaymoskingdoms/TalkErrands.java (Ask something option)
  - kingdoms/src/main/java/com/jaymoskingdoms/TalkNet.java (register line)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/ask.json
  - changes/kingdoms/lanterns-09-talk-typed.md
---
# lanterns-09-talk-typed

**Task:** Lanterns patch 9: an ask-a-question box on a villager's talk screen, with suggested follow-ups.

## Notes
- Stacked on lanterns-07 (#1767): grows its ChatTalk topic matcher.
- Entry is "Ask something..." in Town business (TalkErrands): a top-level MCA menu entry would need McaBridge, owned by dialogue-letterbox-pov.

## Done
