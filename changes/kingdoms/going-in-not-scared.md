# Villagers going in at night aren't "Running scared"
- Andy's clip: a villager walking home at night showed "Running scared" with its activity "rest" and no foe anywhere. It
  wasn't frightened: being out under the open sky at night (or in heavy rain) sends people in (`TakeCover`), and that
  was handled as a fright (`Frights.shelter`), so Jade and the spectate label called it running scared, and it hurried.
- Going in is now its own kind (`Frights.cover`): at night they walk in at an ordinary pace (in rain they still hurry),
  don't yelp, and Jade says "Going home", "Resting at home" or "Sheltering" (`NpcTasks`). A monster that turns up while
  they're going in frightens them at once, with the cry, and they run from it; before, being on their way in held off
  the fright (and the cry) for up to 15 seconds. War alarms and twisters are real frights, as before.
- Built; not played.
