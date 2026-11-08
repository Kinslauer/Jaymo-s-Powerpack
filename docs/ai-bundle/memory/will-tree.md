---
name: will-tree
description: Strength's Will tree (counter magic + presence), Andy's 17 picks and rulings; PR #673 MERGED 2026-10-29 (pack 0.17.187 / Kingdoms 0.7.345)
metadata:
  type: project
  modified: 2026-10-29T05:36:15.118Z
---
2026-10-29 Andy: Strength needs a second tree beside Combat Arts to combat magic, called Will. Picks doc (Claude Doc,
tickable): https://claude.ai/artifact/M4ix3g39twRuPAXZEF3yCA..
Picked all except Unbent (no mind-effect resistance): Dread Presence, No Getting Up, Stare Down, Broken Morale, Name That
Carries, Spellbreaker Blow, Grounded, Shrug It Off, Snap the Thread, Will Over Flesh, Witch Hunter, Grim Hand, No Mercy,
Last Look, Unbroken Roar (final, one heart, once per full day), Refuse to Fall, Second Breath.
Rulings:
- "Weaker" = foe max health below yours (power level for Z Fighters) OR any enemy from a world tier below yours
  (goblins, skeletons = weak tier; Claude places the rest).
- No Mercy kills weaker foes outright, except sneaking or less-than-lethal weapons (fists, clubs) still down them.
- Dread Presence and the roar affect other players too.
Andy likes picking from Claude Docs checklists (markdown files can't be ticked in the app). Related: [[combat-arts]],
[[skill-tree]], [[downed-guards]], [[apotheosis-world-tiers]], [[kingdoms-sorcery]].

PR #673 MERGED 2026-10-29 06:1xZ on Andy's "merge" as pack 0.17.187 / Kingdoms 0.7.345, built, not playtested: Kingdoms `Will` (perks, weaker,
kindTier), Powers `core/wounds/WillSkill` (reflection; hooks in Downed spared/getUp/revives, Dismember sever/behead,
Powers actor for Will Over Flesh, `WillPushMixin`). Strength pages 2 "Will" (tiers 8-11) and 3 "Killing Intent" (12-15);
Strength max level still 25, so Will competes with Combat Arts for points. Hearth-kind mobs always count as weaker.
