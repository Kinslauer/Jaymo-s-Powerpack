---
status: claimed
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
text_pass: 
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
- 2026-11-16 21:17 hermes-b's last run stopped before finishing; handed back as changes so it can carry on.
- 2026-11-16 22:08 Text pass: rewrote changes/powers/claude-punch-barrage-effects.md, the only player-visible text in PR #1665: added that Time Flow's barrage (ChronoFlow) also throws blurred fists and sparks, added ChronoFlow to the classes list, and smoothed the Z-Fighter Rush finisher wording. The PR's four Java edits are MagicFx particle calls only - no lang, chat, dialogue, hint or UI strings. pr_checks passed.
- 2026-11-17 04:29 Review (head e7af3cf, PR #1665; head db19a9a, PR #1667): changes needed. #1665 fixed review points 2-8 (feet, blur of fists, ChronoFx sparks, constants, Time Flow barrage) and CI is green; the Kingdoms half moved to #1667, which is broken.
1. #1667 fails CI (Build Kingdoms): kingdoms/.../spirits/SpiritBarrage.java:444 uses Fx without importing com.jaymoskingdoms.Fx.
2. #1667 kingdoms/.../Fx.java:58 looks up MagicFx.wave with 6 parameters; MagicFx.wave (MagicFx.java:265) takes 7 (..., int color, int ticks). findStatic throws, and the catch at Fx.java:60 nulls every handle, so ALL of Kingdoms' typed FX (wisp, spark, ring, beam, orb...) lose Powers' renderers whenever Powers is loaded. Add int.class for ticks and pass distance as a double (drop the (float) cast at :195).
3. #1667 Fx.java:191-201: wave() has no fallback when Powers is absent (every other Fx method draws something), its closing brace is mis-indented, and :187 (ring's fallback line) lost its indentation. Add a dust fallback and restore the indentation.
4. #1667 SpiritPunch.java:192-193: ring and wave start at the target's body centre (hit). Same fix as #1665 point 3: target.position() plus 0.1. Move 1.5F, 0xFFFFFF, 5.0, 0xD0D0D0, 10 into static final constants at the top of the class.
5. #1667 Kingdoms barrage clashes only gain sparks (SpiritBarrage.java:444); the task also wants blurred fists in front of each side while the flurries meet (BarrageClient or strokes from SpiritBarrage, as #1665 did for Chrono).
6. #1665 ChronoFlow.java:1029-1042: the barrage's new numbers are inline (1.4, -0.3, 0.18, 0.12, 0.15, 0.7, 0.35F, 0xFFE08A, 6 sparks). Make them constants at the top of ChronoFlow, as ChronoPunch now does.
