---
status: claimed
for: hermes
from: Andy
asked: 2026-11-28
thread:
claimed_by: YOUTH
pr:
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/McaCompat.java
  - kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java
  - src/main/java/com/jaymospowers/combat/Guard.java
  - src/main/java/com/jaymospowers/mixin/GuardOverTalkMixin.java
---

# Villager dialogue screen handled within talk

**Andy's words:** "that screen should be handled within talk." — with an image of a Female Villager dialogue screen: "Let me by! I saw what you did, and the guard will hear of it." Responses: "Please. Don't tell anyone," "Here, 10 Gold Dinar. You saw nothing," "Say a word and you'll regret it," "Go on, then." Chat warns "someone is running for the guard."

**What's wanted:** The villager dialogue screen shown in the reference clip/image should be handled within the talk system (McaCompat/McaBridge) rather than MCA's default UI. Reference media: E:/Jaymo Vault Live/Assets/villager-dialogue-talk.png and E:/Jaymo Vault Live/Assets/villager-dialogue-talk-clip.mp4.

**Where to start:** kingdoms/src/main/java/com/jaymoskingdoms/McaCompat.java (talkMain, talkSay, talkReopen, talkClose), kingdoms/src/main/java/com/jaymoskingdoms/McaBridge.java (talk handlers), src/main/java/com/jaymospowers/combat/Guard.java (guardsAgainst, right-click-on-mob flow), src/main/java/com/jaymospowers/mixin/GuardOverTalkMixin.java (intercepts right-click on foes).

## Notes
- Need Andy to confirm: is this about routing all MCA villager dialogue through McaCompat.talkMain, or about a specific witnessed-villager scenario (villager saw something, wants to tell guard, player can plea/bribe/threaten/let go)?
- The talk system already opens MCA's dialogue via InteractionDialogueResponse in talkMain; GuardOverTalkMixin currently lets MCA's interact run when guardsAgainst is false.
