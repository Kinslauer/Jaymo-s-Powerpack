---
status: changes
for: any
from: coordinator
asked: 2026-11-07
thread: Claude project thread "Hollow turning" (PR #1098 merged; follow-ups never started, thread ran out of usage)
claimed_by: hermes
pr: 1117
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
- 2026-11-07 09:24 Review (head 14bbb12): changes needed. 1) Doesn't compile: HollowBlight.java:153-174 braces are broken (the symptom 'if' and the player 'for' loop never close, so the PENDING_TURN loop and turnNow at :177 end up inside tick); CI 'Build Kingdoms' fails at :177. 2) HollowBlight.java:181: Downed.knockOut has no 6-argument overload on master (Downed.java:822 takes 3 args, :831 takes 5: level, entity, ticks, anyBody, message). 3) HollowBlight.java:154-163: the zombie stand-down sits inside the random symptom branch (stage>0 and 1 in SYMPTOM_EVERY, every 20 ticks), so zombies attack you almost all the time. Run it every check for the whole blight, and stop them re-targeting (setTarget(null) alone lets them pick you again next tick: hook their target choice instead). They should speak to you (a sound now and then), not only when they had you targeted. 4) HollowBlight.java:75: PENDING_TURN is an in-memory HashMap and the blight attachment is removed at :178, so logging out or a restart while passed out means you never turn. Keep the pending turn in a persistent attachment. 5) Kingdoms version not bumped (0.7.646 = master; the Versions check fails) and no kingdoms/MODLOG.md entry. 6) GoldenAppleMixin: only a drunk Weakness potion cures; say in the PR whether splash/lingering should too (Andy said 'weakness potion', like curing a zombie villager, which is splash). Build Kitchen's failure was a Maven 429, not this PR.
- 2026-11-07 12:01 Review (head 6219a57): changes needed. Fixed since the last round: it compiles (checks green), the pending turn is persistent (Blight.passedOut/wakeAt), Kingdoms 0.7.647 plus a MODLOG entry. Still to do: 1) Passing out doesn't happen: HollowBlight.java:191 only adds the scoreboard tag Downed.DOWNED_TAG. That tag is Powers' output (Powers' Downed.java:648 sets it after putting the DOWNED attachment on); adding it yourself doesn't knock anyone out, close their eyes or darken the screen, it only makes Kingdoms treat them as downed. Nothing removes it at :134, so after turning they stay 'downed' to Kingdoms forever. Use the bridge Kingdoms already has: PowersCompat.knockOut(level, player, TURN_DARK) (PowersCompat.java:2091), and drop the addTag. 2) Weakness cure isn't what Andy asked for: GoldenAppleMixin.java:29 makes drinking a weakness potion alone cure it, and a golden apple alone still cures too. Andy said 'and weakness potion', like curing a zombie villager: weakened AND a golden apple. Do that: in ateGold, cure only when the player has the Weakness effect (that covers drunk, splash and lingering at once), and remove the Items.POTION branch. The MODLOG entry also says splash and lingering count, which the code doesn't do. 3) Zombies still attack: the stand-down only runs once a second (:123 % 20) and only from stage 1 (:160), so for the first third of the blight (8 minutes) and between checks they hunt you; :176 setTarget(null) lets them re-target at once. Round 1 item 3 asked for this to be hooked into their target choice; it wasn't, though the MODLOG says it was. Add a mixin on Zombie target selection (or the NearestAttackableTargetGoal for players) that skips a blighted player, for the whole blight from stage 0. 4) Zombie sounds: :178 plays ZOMBIE_AMBIENT for each zombie within 16 blocks 1 time in 8 every second: a horde is constant noise. Have one or two of the nearest speak now and then (a constant like SPEAK_EVERY at the top), and make the 16.0 and 8 constants too. 5) HollowBlight.java was reformatted from tabs to spaces (170 lines now start with 4 spaces; master's file uses tabs), so the diff rewrites the whole file. Put the tabs back and keep only the real changes. This is the second review; if items 1 and 3 are still open after the next one, the task goes to Andy as blocked.
