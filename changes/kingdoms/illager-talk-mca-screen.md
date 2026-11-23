# Talking with an illager looks and works like MCA's villager screen
- Andy: "these pillager interactions should look and work just like MCA". Sneak-using an illager you can talk with now
  opens a screen laid out as MCA's villager screen (as Kingdoms shapes it) instead of the gold card with a list of
  buttons: their name, what they are, mood (coloured), nature and how they take you down the top left in MCA's
  tooltip-style lines, MCA's heart under them (black when they bear you ill will, gold at 100; point at it for the
  count), and the buttons in one column on the right in the dark purple panel, with MCA's `<<` back arrow.
- The first page is Talk, Interact and Trade, as MCA's. Talk: ask their name, Talk a while, Ask what they want, Ask
  about their hold, Goodbye. Interact: Gift, Talk Them Round, and buying off the posters when you're wanted. Gift is
  MCA's gift mode: the buttons go grey, the scroll wheel walks your hotbar (the top left says what you hold), and Enter
  or the right mouse button gives it; `<<` or Escape leaves gift mode. The old "Give them something (hold it first)" is
  gone.
- The screen stays open from one answer to the next, as MCA's does, on the page you were on; Goodbye, a parley's way
  out and a threat shut it. A hostile one's parley (calm, pay off, bluff, back away), the trade list and Talk Them
  Round's moves stand straight in the column, the last two with `<<` back.
- With MCA in the pack the villager talk camera runs for illagers too (letterbox, their words typed into the box low on
  the left, the column up once they've said them). Without MCA the screen draws the words box itself.
- Not done like MCA: MCA's real screen can't be opened for an illager (it is made for MCA's villagers and asks MCA's
  server about them), so this one is rebuilt to match; Trade is still the buy/sell list (coin prices, not a vanilla
  trade window); Talk's answers are a column, not the answer wheel on a villager's dialogue.
- Classes: `IllagerCard` (new payload), `IllagerTalk` (`card`), `Menus.open`, client `IllagerTalkScreen`;
  `DialogueCinematic` and `SpeechBubbles` know the new screen.
- Built by CI; not played.
