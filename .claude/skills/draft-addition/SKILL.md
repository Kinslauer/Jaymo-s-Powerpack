---
name: draft-addition
description: Turn Andy's rough idea for a new addition (a feature, mob, people's way of life, structure, item) into a plan doc he can pick from, before any code. Use when Andy says "draft", "plan out", "I want to add...", "new addition", or asks to think an idea through first. Five passes: plan, sceptic, keep what works, decide, self-check.
---
# Draft an addition

Andy gives a rough idea. You hand back a plan doc in `docs/<Name>-Plan.md` and a short list of questions. No code, no
branch, no PR in this skill: building it is the next task, after he answers.

Five passes, in order. Each one is a section of the doc. Adapted from five prompts Andy shared (2026-11-23): an action
plan, a sceptical adviser, improving without starting over, an expert's decision, and checking your own answer.

## 1. Examine, place it, then plan

Before any steps, write down:
- **The problem.** What Andy wants players to see or do, in his words (quote him).
- **Who it's for.** Which players, which heritage or class, which part of the game (early, Wildlands, sea, Nether...).
- **Where it lives in the lore.** Read `Lore.md` (the live vault; cloud: `docs/vault/Live/Lore.md`). Who made it, where,
  whose people or faith. Find every paragraph it touches and quote the line. If it fits nothing, reshape it or ask.
- **What we have to build with.** The codemap (`docs/codemap/<mod>.md`) first, by every likely name, then the classes
  it names. Mods in the pack (`E:/CurseForge/Instances/Jaymo's Powers/mods`), only through a compat class.
- **The three rules for living things** (CLAUDE.md) if it's an animal, plant or people's way of life: sympathy,
  feedback loops, stewardship. Say how each is met.
- **Obstacles.** Engine limits (check the 26.3 sources), performance (ticks, chunk loads), other tracks' files.

Then the roadmap: steps ordered by impact (what players notice most first), each with what it should achieve and the
check that proves it. Mark the **simplest first step**: the smallest slice that's playable on its own.

## 2. The sceptic

Don't agree with the idea just because Andy said it, and don't agree with your own plan. In its own section:
- Assumptions in the ask or the plan that might be wrong.
- Where it clashes with the lore canon, an approved decision (`Decisions.md`) or code that already does the opposite.
  Name the paragraph or class. Never smooth a clash over: put it in front of Andy.
- Risks: balance, lag, griefing, a feature nobody will find, too big for one PR.
- Opportunities missed: something already in the mods that would make it better.

Anything you need from Andy goes in **Questions for Andy** at the end, each with your recommended answer. Stop at the
draft: don't decide what's his to decide.

## 3. Keep what works

When the addition touches existing code or lore, don't redesign it. First list what's already there and must stay (with
`path:line` or the lore paragraph), then what holds it back, then focused changes in order of impact, each naming the
file and method it goes in. New code goes beside old, in the old style.

## 4. Decide like a specialist

For each real choice (two ways to build it, two readings of Andy's words):
- The factors someone who builds this for a living would weigh (for a mod: fun, fit with the lore, cost to build, lag,
  how it plays with friends on one server, what other mods do).
- Each option against them, the trade-offs, and the one you recommend.
- What Andy could tell you that would change the pick.

## 5. Check your own draft

Reread the doc against Andy's words, line by line. Then follow "Check, don't assume" in CLAUDE.md, and end the doc with:
- **Confirmed**: what you saw (file and line, lore paragraph, jar listing).
- **Assumed or inferred**: what you believe but didn't check, and why.
- **Not checked**: what you couldn't (no game run, a mod's code you didn't open).

Fix anything that fails before you hand it over.

## Hand-off

- The doc, with a `(new)` lore placement paragraph in it. Append that paragraph to `Lore.md` too, marked `(new)`,
  without editing approved paragraphs.
- Your agent note in `E:/Jaymo Vault Live/Agents/` (from `Templates/Agent.md`).
- Reply to Andy in a few lines: the simplest first step, the clashes, and the questions. Offer to make a pick-list page
  if there are many choices.
