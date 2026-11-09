---
status: blocked
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-a
pr:
suggested_owns: []
writer: hermes-writer
text_pass: none
---

# Realistic Water: one test launch

Not a mod for the pack yet — a trial. Find out whether Realistic Water (Fabric 26.3, 1.3.2) runs beside our renderer at all.

## What's wanted

From the pick-list section 1. Andy: "we can try it", on the understanding that the block physics and the volumetric water are one system — there is no physics-only mode.

The gate is graphics: the mod asks the game's Graphics API to be set to Vulkan, and our Sodium / Iris / EMF stack is unknown against that.

## Notes

- **Do it on a copy of a world, never the live one.** Nothing goes into `modpack/mods` until Andy has seen the result.
- Report in plain terms: does it launch, does Sodium still work, what the frame rate looks like with a body of water on screen (`/water stats`), and whether Streams Reflowing still makes its rivers.
- If Sodium or Iris breaks, say so and stop — that is the answer, not a workaround to chase.
- Latest file is on GitHub releases, not CurseForge.
- 2026-11-09 00:18 Review: blocked, needs Andy. No PR and no answer to any of the four questions (launch, Sodium/Iris, FPS with /water stats, Streams Reflowing rivers): the bot ran headless and its Loom cache broke, so it only checked the jar compiles. A bot can't do this one; it needs a launch on Andy's PC with a display, on a copy of a world, with the Graphics API set to Vulkan. Its suggestion to put the jar in modpack/mods goes against the task (nothing goes in modpack/mods until Andy has seen it). Nothing was pushed.
- 2026-11-09 15:53 3:55 PM: launched for Andy as a separate CurseForge instance "Jaymo's Powers - Water Trial" (a copy of his instance and its Life world, with realistic-water-1.3.2.jar added). It loads to the menu on OpenGL beside Sodium ("Sodium present"). Andy to switch the Graphics API to Vulkan, restart, and check Sodium/Iris, /water stats FPS and Streams Reflowing rivers.
- 2026-11-09 16:04 Andy tried it: "way too laggy, scratch this". Dropped; not going in the pack.
