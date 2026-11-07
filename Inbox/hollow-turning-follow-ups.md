---
status: open
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Hollow turning" (PR #1098 merged; follow-ups never started, thread ran out of usage)
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HollowBlight.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/hollow-blight.json
  - src/main/java/com/jaymospowers/core/wounds/Mortality.java
---
# Hollow turning follow-ups

**Andy's words (2026-11-07, on "The cure: a golden apple"):** "and weakness potion, also as you are turning zombie, you
should be spoken to by the zombies and not attacked". Then: "you pass out and then turn as well, make sure the eyes
animate closed too".

**What's wanted:**
1. A weakness potion (with a golden apple, like curing a zombie villager) also cures the blight. Today only eating a
   golden apple does (`HollowBlight.ateGold`, called from `GoldenAppleMixin`).
2. While the blight spreads, zombies (the Hollow you're becoming) don't attack you and speak to you instead (speech
   bubbles or sounds, no chat text: Andy's "no discovery chat text" rule).
3. When the blight finishes, you pass out first (eyes animate closed, like the downed/death black screen), then rise
   turned. Today you turn where you stand.

**Where to start:** `HollowBlight.java` (stages, `TURN_TICKS`, `ateGold`), `Mortality.java` (the mid-turn death black
screen, 14 s), the downed eye-closing used by ragdolls/corpses.

## Notes
- 
