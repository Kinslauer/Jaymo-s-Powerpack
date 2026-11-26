---
status: done
for: any
from: Andy (Claude chat, 2026-11-23)
asked: 2026-11-23
claimed_by: claude-patron-colossus
pr: 2103
---

# Patron colossus: a giant figure as a class attack, and the hunt for commandment breakers

## What's wanted

Andy: "I want that figure to appear, larger than any giant, as a single attack for any sufficiently progressed paladin
cleric or warlock. and the punishment as you break more and more commandments is seeing the figure, (replaced by the
respective patron) at varying distances, appearing periodically as a type of intimidation, culminating in a real hunt,
the player dying if the patron encounters them. it moves extremely slow. but starts closer every reappearance. only the
pious can see punishment patrons. the three classes' attacks are visible to all."

1. **The attack**: a Paladin, Cleric or Warlock far enough along (pick the progression gate: class tier, patron tier
   or favour; propose it) gets one attack that calls up the figure, larger than any giant in the pack, to strike once.
   Everyone sees it.
2. **The punishment**: breaking a patron's commandments (Kingdoms `Commandments`, `docs/Patrons-Plan.md`) brings that
   patron's own colossal figure. It appears now and then at a distance, as intimidation, closer each time the more
   commandments are broken, and ends in a real hunt: it moves extremely slowly, each reappearance starts nearer, and if
   it reaches the player the player dies. Only the pious (define: players with a patron or faith; propose) can see a
   punishment patron; others see nothing.

## Notes

- **Ask Andy for "that figure"**: he was looking at a reference (a picture or clip) when he wrote this; it isn't attached
  here. Get it from him before modelling the figure. Our own model and art only.
- Existing pieces: patrons, commandments, boons, champions (`ChampionModel`, `PatronChampion`), PactArts / Renege.
  Each patron's figure should read as that patron.
- The hunt must be fair: a clear tell when it starts, a way to end it (atone, a rite, outrun it to a holy place?):
  propose and ask Andy. Keep it cheap on the Deck server (one entity per hunted player, nothing ticking for the rest).
- Magic effects through MagicFx / Kingdoms Fx only.
- 2026-11-24 16:12 PR #2103 is merged into master; moved to Inbox/Done.
