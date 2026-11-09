---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/SkillScreen.java, kingdoms/src/client/java/com/jaymoskingdoms/client/SkillWeb.java]
---

# Skill tree: the sidebar should get out of the way

The Attributes and Jobs panel sits on the left of the skill tree and stays there, over the web.

## What's wanted

Tiv, in the Tasks thread: "the sidebar needs to move out of the way in the skilltree. its just sitting there on the left."

## Notes

- `SkillScreen` keeps the Attributes (and Jobs) panel at a fixed `PANEL_W` of 340 on the left of the screen, over the web; the web is dragged and zoomed underneath it.
- What "out of the way" means is still open: a tab that slides the panel in and out, auto-hide when the pointer is over the web, or the panel moving to a side that isn't in the way. Pick what plays best and say why in the PR.
- Whatever the answer, the web must stay fully usable: you can see and reach perks behind where the panel used to be.
- Don't change what the panel shows — only where it sits and whether it can get out of the way.
- Ask Tiv before building if the choice matters to him; he raised it as a look-and-feel complaint, not a spec.
