# Villagers stop to talk and reply from their own shot
- **Talking to a villager is a cutscene start to finish** (Andy's clip: after a Chat pick the villager walked off mid-sentence and the camera came straight back). From the moment their talk menu opens they stand still, turned to you, and stay that way until the talk is over, not only while the menu is up.
- **The reply gets its shot**: MCA shuts its menu as soon as a chat, joke or story is picked. Now the letterbox plays on: your line on camera for two seconds, then the villager's reply from their eyes for as long as their words are over their head (2 to 12 seconds). Only then does the camera come back and they go back to their work.
- As in every cutscene: you are held in place and can't be hurt while it runs. It breaks off if either of you is hurt, you end up more than 10 blocks apart, or either of you falls out of play. Leaving the menu with nothing said ends it at once, as before.
- `Conversations` (server hold), `DialogueCinematic` (reply phase), `DialogueCue.Done` (client tells the server the talk is over), `McaTalkHoldMixin` (menu opening starts it).
- Built; not played.
