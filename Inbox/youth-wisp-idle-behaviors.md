---
status: claimed
for: hermes
from: tiv (Discord, Questions thread, 2026-11-16)
asked: 2026-11-16
thread: 1569189294512537671
claimed_by: hermes-a
pr:
suggested_owns: [kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/YouthWisp.java, kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/YouthCommand.java, kingdoms/src/main/java/com/jaymoskingdoms/inhabitants/Inhabitants.java]
---

# Youth the wisp: more idle behaviors (stay, follow, avoid gaze)

Tiv, in the Questions thread: "I want youth to animate differently in game randomly. For example he might idle in the spot you called him, or he'll travel along with you staying / drifting within your pov. Or he will actively avoid your gaze playfully, id imagine you can implement even more that make sense."

## What's wanted

Youth the wood-wisp (a Sylvan GeckoLib mob summoned with `/youth summon`, speaks with `/youth say`) currently drifts slowly about its spot and turns to face whoever is nearest. Tiv wants it to pick idle behaviors at random, including:

- **Stay put** — idle in the spot where it was called, with little or no drifting.
- **Follow the player** — travel along, staying/drifting within the player's POV (in front, at head height, moving with them).
- **Avoid gaze playfully** — when the player looks at it, it turns or drifts away; when the player looks away, it drifts back.
- **More behaviors that make sense** — anything else that fits a playful wood-wisp.

## What exists

- `YouthWisp.java` — the wisp entity. Drifts about its spot (DRIFT 0.6 blocks, FOLLOW 0.12 gap share per tick), turns to face its asker or the nearest player, wanders between 8 idle animations (hover, flutter, loop, sit, stretch, peer, twirl, cup). Vanishes after 2 minutes of silence or when its asker is >16 blocks away/in another dimension. Max 4 wisps. Never fights, never saved, can't be hurt or pushed.
- `YouthCommand.java` — the `/youth` commands: status, summon, dismiss, say.
- `Inhabitants.java` — registers the YOUTH_WISP entity type.
- `YouthWispRenderer.java` — client-side GeckoLib renderer.
- `youth_wisp.animation.json` — 8 idle animations plus appear/vanish/talk.
- `youth_wisp.bbmodel` + `youth_wisp.png` — model and texture.
- mcbot.py verbs: summon, dismiss, say, status, who, where — Hermes talks to the wisp over RCON.

## Notes

- This is mod work (code), routed to hermes (builders). Not a model, art, or writing task itself.
- The wisp's behavior lives in `YouthWisp.tick()` (drift + face) and `nextIdle()` (animation picker). New behaviors would be modes the wisp picks at random or switches between.
- "Actively avoid gaze" needs the wisp to know where the player is looking — compare the player's look angle to the direction to the wisp.
- "Stay put" vs "follow" vs "avoid gaze" are mutually exclusive modes; the wisp could pick one when summoned or switch over time.
- Ask Tiv: should the player be able to choose a mode (e.g. `/youth summon stay`), or should Youth pick at random each time?
