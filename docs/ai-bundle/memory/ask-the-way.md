---
name: ask-the-way
description: Kingdoms 0.7.454 / pack 0.19.8 (PR #812, MERGED 2026-10-31): MCA Interact-page "Ask the Way" button, villagers point or guide you to healer/butcher/smith/tailor/tavern/stables/ruler
metadata:
  type: project
  modified: 2026-10-31T22:24:04.497Z
---
2026-10-31 Andy asked to ask villagers where things in town are (doctor, butcher). PR #812 (branch claude/project-thread-lvfrn6) merged 2026-10-31 23:38Z, not playtested.
Andy then said "move some of these options to the interact menu": now an Interact button (client McaScreensMixin adds it + a "way" page; McaWayPageMixin opens the page) sending MCA command `way.<place>` to `Directions` via McaCommandMixin. Pattern for moving talk options to Interact. (MCA dialogue sub-menus also work via `"next"`.) Andy then picked moving action options too: `InteractActions` (audience, pedlar wares, tribute, sworn spy/raid) are Interact buttons, gone from talk; server sends which apply on right-click (UseEntityCallback, payload Offered), client McaActionButtonsMixin hides/stacks them. Handshake kept in talk (stranger page has no Interact).
Gotcha: local `master` branch in the clone is stale; diff/checkout against origin/master.
Villagers point and tell (MCA chat), or walk you there if 10+ hearts or a child; guards/rulers only point. Bookie leading you to the speakeasy is a separate thread.
See [[villager-approaches]], [[underworld-speakeasies]].
