---
status: claimed
for: coordinator
from: Andy (desktop session "Andy's three clips", 2026-11-23)
asked: 2026-11-23
claimed_by: codex-bleed-out-coma
pr:
suggested_owns:
  - src/main/java/com/jaymospowers/core/wounds/ (a new class for the coma; small hooks in Downed, BleedOut)
  - src/main/resources/assets/jaymospowers/lang/parts/wounds.json
---

# Bleeding out is a death; brought back from the brink, a coma until tended

Part 1 only; NPC item handoff is now [[bleed-out-npc-items]], its own Kingdoms PR. Comes after PR #2059 (finite blood: every stain a living body
spills comes out of its own blood, `BleedOut#spill` / `BleedOut#dry`). Start from fresh origin/master; verify #2059 is merged before implementing.

## Andy's words (2026-11-23)
On #2059's side effect (a badly cut fighter can run dry): "a player who bleeds out suffers a death, they can be
brought back from the brink but will be unconcious until they recover, and will have to be put in bed and given food
and drink. an effective 5m coma"

His answers to three questions:
- **Nobody brings them back:** a normal death roll (`Mortality`: left for dead, or final), with bleeding dry as what starts it.
- **Bringing them back:** a friend does the usual revive (sneak + hold use) within about 60 seconds of them running
  dry, "as well as bandaging them afterward to stop the death and start the coma".
- **Ending the coma:** "they need to be given food and water, then when they are in bed, they can wake whenever they
  are ready." No timer: fed, watered, in a bed, then they wake when they choose (hold sneak, as rising).

## Part 1 (Powers): the brink and the coma
- A downed player whose blood runs out (`BleedOut.dry`) is on the brink: out cold, can't mash awake, a ~60 s clock.
- Clock runs out: they die, through `Mortality` as any death (cause: bleeding, so the bleeding damage type).
- Within the clock a friend revives them, and binds every open cut (`Bleeding` bandage/stitch: no bleeding mask left).
  Both done before it ends: the death is stopped and the coma starts.
- In the coma: out cold, no bleed-out death, no mashing awake. A friend gives them food and a drink: use the item on
  the body (`Downed.onUseEntity`, before the body menu). The comatose player consumes it (food points; Kitchen thirst
  for drinks, `Thirst.drink` or the item's own consume; the empty bottle/skin goes back to the giver). Once they've had
  both and lie in a bed (`DownedStruggle` beds; carried there with `Carry`), holding sneak wakes them.
- Their blood refills as `BleedOut` already does once they're up.
- Check: the bled-dry bed/fire rules in `DownedStruggle` (line ~299) and `Resting` (~161) already treat dry players;
  fold them into this rather than leaving two rules.

## Related task
NPC handoff requirements moved intact to [[bleed-out-npc-items]].

## Done means
Each part its own PR with a change note, Auto-fix on, CI green, not merged; what wasn't played said plainly.
Lore check: none needed for part 1 (mechanics); part 2's lines fit the villagers' temperaments (`Temperaments`).

## Notes
