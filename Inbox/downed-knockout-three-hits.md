---
status: open
for: any
from: survival picks thread (session intelligent-montalcini-982486)
asked: 2026-11-22
thread: survival picks
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/wounds/Downed.java
  - changes/powers/<branch>.md
---
# Downed: knocked out by a hit, dead after 3 more

**Andy's words** (2026-11-22): "make the downed players and mobs go unconcious after a hit, then 3 hits while downed
kills them, or any execution or unblockable (red) attack. this should change how fights end, as of now i get tossed
around for 30s"

**What's wanted:** a fight ends fast once someone is down, players and mobs alike:
- Someone downed (player or mob) who takes a hit **passes out** (out cold: lies still, the darker screen for a player).
- **3 hits while downed kills them** (count from going down, so the hit that knocks them out is the first; check the
  reading with Andy if unsure, see Notes).
- **Any execution (finisher) or unblockable (red) attack on the downed kills at once.**
- No more being knocked about on the ground for ~30 s: hits on the downed shouldn't throw the body around.

**Where to start** (Powers, `core/wounds/`; grep `docs/codemap/powers.md` first):
- `Downed.java` (master): `DOWNED_PLAYER_HEALTH = 20` (a downed player has 10 hearts that every blow eats; Andy
  2026-10: "dealing that much damage would kill them, not strictly 10 hits"). This is likely why it takes ~30 s.
  `DOWNED_HEALTH = 6` for mobs; `KILL_BLOW = 6` (Andy 2026-10-29: 3 hearts or more on the downed kills, applied in the
  `ALLOW_DAMAGE` handler near line 486, which today skips the out-cold unless crushed: `(!outCold(entity) || crushed)`);
  `WOUNDS` attachment already counts players' hits on a downed one (hits in the low 4 bits); `OUT` is the passed-out
  state (players pass out at `BLEED_OUT_TICKS`); `outCold(...)`; `HOLD_HITS` (hits that break off a rise or revive).
- Red attacks: `combat/IncomingBlows.java` and `AttackTells` (the red cue = "too heavy: block or dodge"); find where a
  blow is marked red/heavy so the downed check can read it. Executions: `core/finisher/Finishers.java`,
  `PowerFinishers`, `Takedowns`, `FINISHING` set in `Downed`.
- Knockback on the downed: look for where knockback applies to downed bodies (`LivingEntity.knockback` / a mixin);
  `DownedCrawl.hit`, `ExecutionReact` (client).
- Kingdoms reads the shared tags (`jaymospowers_downed`, `_revive`, `_bandaged`) and has Last Stand / Unflinching
  (`WillSkill#holdHits`): check nothing there breaks.

## Notes
- Conflicts to say plainly in the PR: this replaces the 10-heart downed health and changes the 3-hearts kill rule
  (Decisions 2026-10-29, `KILL_BLOW`): weak hits now count toward the 3, and passed-out bodies are no longer spared.
  Andy's 2026-11-22 survival ruling (only a big head blow while standing kills outright; everything else downs first)
  still stands: this task is only about what happens after the down.
- **Andy's answers (2026-11-22):** any damage counts as a hit ("any damage": arrows, fire, falls, not only
  `Downed.attack(source)`; mind fire and bleeding ticks, which would count per damage event); a friend can still revive
  someone passed out ("yes", sneak + hold use as today); the count of three resets when they're revived and go down
  again ("yes").
- Change note in `changes/powers/<branch>.md`; also update the Wounds rows in `docs/Abilities.md`.
- Nothing started: this note is the whole brief.
