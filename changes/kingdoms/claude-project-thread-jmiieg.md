# villager talk buttons legible
- **MCA's villager screen, right-hand buttons**: every button in the column is now as wide as its longest label needs
  (it still grows leftwards from the right edge, up to 150 or a third of the screen), so Ask about Money, Talk Them
  Round, Where Were You and the rest read in full instead of scrolling cut off. A label still too long keeps its
  scroll and shows its full text as a tooltip. The shown buttons stack with no gaps where hidden ones sat, the back
  arrow moves out beside the wider column, and a dark panel in the purple of the info tags sits behind the column so
  it reads over a busy world. Every page (main, Interact, Ask the Way, Professions, Work, Locations, a stranger's
  page) gets it; every button keeps its own action, so all options and chat interactions work as before.
- `client/mixin/mca/McaButtonColumnMixin` (after the other page mixins, priority 1100).
- Built; not played.
