---
name: shared-history-talk
description: PR #574 (Kingdoms 0.7.254 / pack 0.17.44, MERGED 2026-10-27): SharedHistory MCA menu options Thank / Apologise / Clear the air / Ask how they are, plus first-impression introductions
metadata:
  type: project
  modified: 2026-10-27T10:48:27.281Z
---
2026-10-27 Andy: "add context based talk options with villagers" (thank a villager who revived you; awkward intro when a guard's first contact was a shove/search).
PR #574 (Kingdoms 0.7.254 / pack 0.17.44; branch claude/project-thread-t13ncr), built in cloud, not playtested, MERGED 2026-10-27 11:2xZ (dd558219).
`SharedHistory` reads [[villager-memory]] (Recollections). New Kinds: REVIVED_YOU, LED_YOU, SHOVED_YOU, SEARCHED_YOU, HALTED_YOU (done to you) + THANKED, APOLOGISED, SPURNED, AIRED, ASKED_AFTER (answers; detail = translatable history.jaymoskingdoms.for.<kind>, matched by key).
Answers jaymoskingdoms_thank/apologise/clear_air/ask_after show only when open. Accepted apology forgets the wrong; one try per wrong; killed kin never. Reviver lingers 12 s. Introductions.askName uses first memory for awkward/warm intro lines.
Related: [[villager-approaches]], [[paranoid-guards]], [[downed-guards]].
