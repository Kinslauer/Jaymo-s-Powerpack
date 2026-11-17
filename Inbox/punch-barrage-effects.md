---
status: in-review
for: any
from: Andy (Claude Code thread, Ragdolls and Cover pick-list)
asked: 2026-11-16
thread: 
claimed_by: hermes-b
pr: 1665
suggested_owns:
  - src/main/java/com/jaymospowers/power/chrono/ChronoPunch.java
  - kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritBarrage.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/BarrageClient.java
writer: hermes-writer
text_pass: done
---
# Punch and barrage effects

**Andy's words:** "The punch and barrage effects are what i want" (about
https://www.curseforge.com/minecraft/mc-mods/panda-cursed-fate-addon). Picked rows pb-punch and pb-barrage on the
Ragdolls and Cover pick-list (https://claude.ai/artifact/6v9SAjDsK47H9ay6MqgcPF); pb-ref (match a clip) left out.
Then: "put the punch and barrage effects on the tasks board instead."

**What's wanted:**
- **pb-punch, heavy punch impact:** a hit lands with a shockwave ring, a dust wave along the ground and a short
  screen shake. On spirits' Spirit Punch, Gravity's Inversion (GravityGutPunch), the Z-Fighter rushes (ZFighterRush)
  and Chrono Punch's last hit.
- **pb-barrage, barrage flurry:** dozens of blurred fists in front of you while it lasts, sparks where they land. On
  Chrono Punch's flurry and Kingdoms' barrage clashes (SpiritBarrage), where two flurries meet with sparks between them.

**Where to start:** the add-on is a Forge 1.20.1 add-on for Cursed Fate (not in our pack, licence unknown): watch its
page's videos for the look, copy no code or art. Build only from typed effects (docs/MagicFx.md: ring, wave, stroke,
mote, spark through MagicFx; Kingdoms: Fx), varying colour, size and time. Classes: Powers ChronoPunch,
GravityGutPunch, ZFighterRush; Kingdoms SpiritPunch, SpiritBarrage, BarrageClient.

## Notes
- Not to be built in the Claude Code thread that made the pick-list (Andy, 2026-11-16).
- Builds go on the Deck or CI, never Andy's PC.
- 2026-11-16 20:36 Text pass on PR #1665 punch-barrage-effects (commit 384419d35). Player-visible text is only changes/powers/claude-punch-barrage-effects.md; it already follows the change-note template (title, bullets, class list, not-tested line), plain and brief, so no rewrites were needed. The three Java edits (ChronoPunch, GravityGutPunch, ZFighterRush) add MagicFx ring/wave/spark particles and Shakes only - no lang, chat, dialogue, hint or UI strings.
- 2026-11-16 20:49 Review (head 384419d): changes needed. CI is green and the MagicFx calls are right (ring, wave, stroke, spark exist with those signatures), but half the task is missing.
1. Kingdoms half not built: the task asks for the heavy impact on spirits' Spirit Punch (kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritPunch.java) and for flurry fists + sparks between the two flurries in barrage clashes (SpiritBarrage.java, client/spirits/BarrageClient.java). The PR touches no Kingdoms file. Build them with Kingdoms' Fx and add changes/kingdoms/claude-punch-barrage-effects.md.
2. changes/powers/claude-punch-barrage-effects.md:2-3 says Spirit Punch and Spirit Barrage clashes got the effects. They didn't in this PR; a Powers note should only list Powers changes (Kingdoms' go in its own note).
3. Ring and dust wave float at chest height: ChronoPunch.java:122-124, GravityGutPunch.java:165-166 and ZFighterRush.java:278-279 start them at the target's body centre. MagicFx.ring is a shockwave over the ground at `at` and Andy asked for a dust wave along the ground: start both at the target's feet (target.position(), a little above), not getBoundingBox().getCenter().
4. ChronoPunch.java:100: one 0.7-block stroke per punch at the same point is 10 identical marks, not dozens of blurred fists in front of you. Paint several strokes per punch, scattered across the flurry's cone (offset sideways/up from the fist, along look), so it reads as a blur of fists.
5. ChronoPunch.java:134: the sparks go out through level.sendParticles; every other particle in hit() goes through ChronoFx.particles(level, player, ...) so the owner's Chrono style (corrupted clones) applies. Use ChronoFx.particles here too.
6. ChronoPunch.java:135: the loop's closing brace is indented one tab too far.
7. Tuning numbers inline in three files (ring 1.5F, wave 5.0 / 1.5F / 10 ticks, colours 0xFFFFFF and 0xD0D0D0, stroke 0.7 / 0.35F, 6 sparks): CLAUDE.md wants them as static final constants at the top of each class.
8. Time Flow's barrage (ChronoFlow.barrage, what C does in Time Flow) gets none of the flurry effects; it is Chrono Punch's barrage form, so give it the same fists and sparks.
