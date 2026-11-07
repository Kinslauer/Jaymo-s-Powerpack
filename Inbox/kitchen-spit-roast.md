---
status: open
for: hermes
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Check open work against Hermes fixes" (asked alongside the fishing send-back, PR #1114)
claimed_by: 
pr: 
suggested_owns:
  - kitchen/src/main/java/com/jaymoskitchen/cooking/spit/ (new)
  - kitchen/src/client/java/com/jaymoskitchen/client/spit/ (new)
  - kitchen/src/main/resources/assets/jaymoskitchen/lang/parts/spit.json (new, or the Kitchen lang file if Kitchen has no parts)
---
# Spit roast over an open fire

**Andy's words (2026-11-07 17:33):** "You should also be able to cook it on an open fire, similar to other animals. Add
a spit crafted with sticks that animates the animal over the fire, and players can either rip chunks off, (animated)
or get the cooked product after"

**What's wanted:**
- A spit block crafted from sticks, placed over a campfire or other open fire (Kitchen's `jaymoskitchen:heat_sources`
  tag, lit).
- Put a fish (from the new fishing, #1114) or another animal carcass (Kitchen corpses) on it: the body shows on the
  spit and turns slowly over the flames, cooking over time (raw, cooked, burnt, with visible colour change).
- While it roasts or once cooked, a player can rip chunks off by hand (an animated tearing motion, gives a cooked
  piece each time), or take the whole cooked product when it's done (cooked fish/fillets, cooked meat in the amounts
  butchering would give).
- Tuning numbers (cook time, chunks per animal) as `static final` constants.

**Where to start:** Kitchen `cooking/Station.java` (campfire is a grill station), `cooking/Grill.java` and
`client/GrillView.java` (campfire cooking), `client/LarderGames.java` (there is already a spit-roasting minigame in
the plain larder: reuse ideas, not the minigame), `corpse/Corpses.java` + `CorpseKind` (FISH) and the butcher table
for meat yields. Ragdoll/corpse rendering is in Kitchen's corpse package. Coordinate with the fishing task
(fishing-real-tug) only through Kitchen's fish items; don't edit its files.

## Notes
