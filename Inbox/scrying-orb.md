---
status: open
for: any
from: coordinator
asked: 2026-11-18
thread: cmsg_01638ouFWZKwEZ1ET74kBk9B9itiE9SZUhU3wrwbx6xdLi
claimed_by: 
pr: 
suggested_owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Scrying.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ScryingOrb.java
  - kingdoms/src/main/java/com/jaymoskingdoms/SightedShard.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/ScryingOrbRenderer.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/scrying-orb.json
---
# Scrying orb (people and places)

**Andy's words:** "A scrying orb sounds cool" (after asking how Frameless Portals' see-through effect works), then "it should work for places too, put the plan on the board".

**What's wanted:** a crystal orb on a stand. An attuned player wakes it with a lock of someone's hair (`Hair`) or a sighted amethyst shard (a marked spot), and the land around that person or spot rises inside the orb as a living miniature: real blocks at about 1/24 scale, with the folk and monsters moving as small figures, for 30 s. Anyone in the room sees it. It must work with Sodium, Iris and Complementary, so it is ordinary block models drawn in a block entity renderer, never a second camera or render-to-texture.

**Where to start:** the full plan is the Claude Doc https://claude.ai/artifact/GwV4pbSFcUYJARVDkZq8w9 (summary below). Read `Scrying.java` + `client/ScryScreen.java` (the bowl this extends), `Hair.java`, `Mana.java` (attunement), and `client/PlanGhosts.java` (how we already submit arbitrary block-state models).

## Plan
1. **Block:** `ScryingOrb` block + block entity in Kingdoms; glass sphere on a carved stand, own 16px model with `#variable` face textures; soft glow while awake.
2. **Waking:** use with a lock of hair or a sighted shard while attuned. People: reuse Scrying's illusion save (orb only clouds), chill + sound for the target, their land must already be loaded. Places: no save, no chill.
3. **Sighted shard:** sneak-use a plain amethyst shard on a block -> `SightedShard` storing dimension + pos, faint glow, anvil name shown over the picture, never used up, sneak-use again to move it. Add a `Target.person` / `Target.place` kind to `Scrying` so the bowl gets places too.
4. **Unloaded places:** while a place-sight is held, a short-lived ticket keeps the 3x3 chunks round the spot loaded, dropped when it ends. Each orb rests 1 min after a place-sight; at most 4 place-sights server-wide.
5. **Packets:** box of block states 17x17 wide, 11 tall round the target, cut to a sphere; first as palette + indices (~3 KB), then changed blocks only, each second. Figures 4x a second (kind, pos, facing, walking), up to 32. Only to players within 16 blocks of the orb.
6. **Drawing:** BlockEntityRenderer draws the box at 1/24 inside the sphere via the PlanGhosts block-model submit path; skip air and hidden blocks; slow turn; dim at night (as the bowl does); use the target world's light (Nether red, Aether white).
7. **Figures:** first cut = small lit motes in the bowl's colours (white watched, blue folk, red monsters, green animals), bobbing as they walk. Real tiny models are a later follow-up.
8. **FX:** `Fx` motes in the glass + rune ring on the stand on waking, miasma swirl when it clouds. No redstone dust or fireworks.
9. **Getting one:** court wizards sell one; alchemy infusion altar recipe (glass, amethyst shards, Scrying Lens) through a compat check, no grid recipe; some court wizard towers get one placed.
10. Change note `changes/kingdoms/<branch>.md`; lang in its own part.

## Checks
- Cloud compile check against the 26.3 jars; renderer + submit calls checked against 26.3 sources.
- Villager's hair: their house shows and the villager's figure moves. Marked gate with nobody near: the gate shows, chunks unload after.
- Complementary on: miniature lit, not purple or invisible. 3 awake orbs cost under 1 ms a frame.

## Notes
- Lore paragraph "The scrying orb" is in Lore.md (Daily life, after the everburning mote), approved by Andy 2026-11-18.
- Open questions for Andy (ask in the thread, don't block on them): mote figures first or real tiny models from the start; any way for the watched to notice beyond the chill; can a keep or wizard's tower be warded so it shows only cloud.
- 2026-11-18 17:03 Claude (cloud session, Andy's project thread): claimed; branch claude/scrying-orb. Immersive Portals port checked and rejected (see Decisions.md); building the miniature as planned.
- 2026-11-18 18:45 scrying-orb's claim had no sign of work for 30 min: handed back as open.
- 2026-11-18 18:59 PC Claude skipped it (2026-11-18 evening): the cloud session's 17:03 claim may still be working unpushed. Coordinator: confirm whether it's free before anyone else starts.
