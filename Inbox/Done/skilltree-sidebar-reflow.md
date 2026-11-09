---
status: done
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-a
pr: 1190
suggested_owns: [kingdoms/src/client/java/com/jaymoskingdoms/client/SkillScreen.java, kingdoms/src/client/java/com/jaymoskingdoms/client/SkillWeb.java]
writer: hermes-writer
text_pass: 
---

# Skill tree: the sidebar should get out of the way

The Attributes and Jobs panel sits on the left of the skill tree and stays there, over the web.

## What's wanted

Tiv, in the Tasks thread: "the sidebar needs to move out of the way in the skilltree. its just sitting there on the left."

## Notes

- `SkillScreen` keeps the Attributes (and Jobs) panel at a fixed `PANEL_W` of 340 on the left of the screen, over the web; the web is dragged and zoomed underneath it.
- Settled with Tiv: it can duck away. The panel gets out of the way rather than moving somewhere else — he'll say if he doesn't like the result, so keep the change small enough to try something else instead.
- Whatever the answer, the web must stay fully usable: you can see and reach perks behind where the panel used to be.
- Don't change what the panel shows — only where it sits and whether it can get out of the way.
- Ducking away still has to be found again: whatever the gesture, getting the Attributes and Jobs back on screen should be obvious without a tooltip.
- 2026-11-09 11:31 Text pass: reviewed gui-skills.json — two new keys 'gui.jaymoskingdoms.skills.panel.expand' ("Expand panel") and 'gui.jaymoskingdoms.skills.panel.collapse' ("Collapse panel") are clear, concise, and follow UI conventions. No rewrites needed.
- 2026-11-09 11:45 Review (head f8ff802): changes needed. 1) SkillScreen.java:870 mouseClicked still runs statAt() while the panel is collapsed, so clicking the web where an invisible Attributes row sits raises that stat (spends points unseen). Skip statAt/jobAt and the list scroll (mouseScrolled ~934) when panelCollapsed. 2) The web isn't usable when collapsed: mouseClicked/mouseDragged/mouseScrolled only drag, zoom or learn perks when onWeb(), which is false on the Attributes/Jobs tabs, so the web you uncover is a picture you can't move or click. Tiv's ask was that perks behind the panel can be seen and reached. Simplest fix: make 'collapse' show the real web view (the web tab already does all of this) with a tab to bring the panel back, instead of drawing the web inside the stats/jobs branch. 3) init() ~176: the collapse button is 24 px wide but labelled 'Collapse panel'/'Expand panel' (the text won't fit), is panelH() tall, and is still added at left()-28 when collapsed, so there are two expand controls (that button and the drawn strip at x=4). Use one small arrow button and keep the label for its tooltip/narration. 4) No change note: add changes/kingdoms/claude-skilltree-sidebar-reflow.md (the Change notes check is red). 5) '(0.7.650)' version tags in six new comments; CLAUDE.md forbids them. The lang keys are in lang/parts/gui-skills.json, which is right.
- 2026-11-09 12:04 PR #1190 is merged into master; moved to Inbox/Done.
