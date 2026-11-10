---
status: claimed
for: hermes
from: hermes (Andy's astronomy pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: claude
pr: 1193
suggested_owns: []
writer: hermes-writer
text_pass: done
---

# Astronomy 1: a real sky

Foundation for the whole astronomy set. The night sky becomes a real one: a star catalog, constellations that connect as you find them, and a sky that depends on where and when you stand.

## What's wanted

From the astronomy pick-list ("add this but in the high renaissance artstyle"), all three sky rows ticked:

- **A real star catalog**: the source ships 8,920 stars from the HYG 4.1 catalog, to magnitude 6.5, drawn over vanilla's sky.
- **Constellations that connect as you find them**: 88 figures, 695 line segments; a figure only shows once its stars are discovered.
- **Sky that depends on where and when you stand**: position and world time decide what is up; light pollution dims it near settlements.

## Notes

- **Start by reading how 26.3 draws its sky.** A replacement sky renderer that fights Iris or Sodium is not worth having; if it cannot coexist, say so and propose a smaller version (stars as an overlay on the vanilla sky) rather than forcing it.
- The catalog and figures are MIT — keep the notice and the attribution in the source and in the mod's credits.
- Light pollution should read as a place feeling different at night, not as a number in a menu.
- Which mod this lives in is a real question: sky rendering is client-heavy. Suggest Kingdoms, since the same system's journal is the scholar side (see `astronomy-scholar-journal`), but do not split it across two mods.
- Nothing downstream works without this: telescope, armillary sphere and journal all need discovered stars to point at.
- 2026-11-09 12:46 Text pass: rewrote change note (changes/kingdoms/claude-astronomy-sky.md) to lead with what the player sees, cut implementation details, keep data credits concise
- 2026-11-09 13:05 Review (head 69db789): changes needed. 1) Discovery.java keeps discovered stars only on the player's PC (config/jaymoskingdoms/astronomy/discovery-<uuid>.json; 'nothing is sent to or kept by the server'). The task says the telescope, armillary and journal all need discovered stars, and their readings run on the server, which can't see them. It is also shared across every world and server the player joins, lost on another PC, and editable. Keep discovery on the server (a player attachment, synced to the client), with the client only reporting the look. 2) SkyClient.java DOME = 60: the stars and lines are real geometry 60 blocks from the camera, drawn with depth in the world pass, so at night anything farther than 60 blocks (mountains, tall builds, clouds) gets stars drawn in front of it. Draw them as sky: in the sky pass/behind terrain (check what 26.3 and Fabric offer for after-sky rendering) or without depth against far terrain. 3) SkyClient.render calls pollution() every frame, which scans for villagers in a 192-block box per frame; its own tick() javadoc says pollution is worked out per tick so the render stays cheap. Compute it in tick() and keep the value. 4) Minor: render() allocates camera.leftVector()/upVector() Vec3s per star per frame (8,870 stars): take them once before the loop. Latitude is fixed at 45 degrees (SkyMath.LATITUDE), so only X moves the sky; say so in the PR or use Z. The rest is good: the vanilla sky isn't replaced, the HYG (CC BY-SA 4.0) and ConstellationLines (CC BY 4.0) notices are in licenses/astronomy/NOTICE.txt, and rain and daylight fade the stars.
- 2026-11-09 13:22 hermes-fixer took over the review fixes from hermes-b.
- 2026-11-09 14:33 hermes-fixer's claim went stale (nothing changed for 50 min); handed back as changes so a bot can take it.
- 2026-11-09 16:51 Text pass: fixed star count in change note (changes/kingdoms/claude-astronomy-sky.md: 8,920 -> 8,870 to match actual stars.json count)
- 2026-11-09 16:56 Review (head 242d048), second round: changes needed. The fix doesn't compile (Build Kingdoms is red) and the server-side discovery isn't real. 1) kingdoms/src/main/java/com/jaymoskingdoms/astronomy/DiscoveryAttachment.java uses APIs that don't exist in 26.3 or Fabric: net.minecraft.util.IDynamicCodec, net.minecraft.util.dataflow.DataFlow, Registries.DYNAMIC_CODEC, AttachmentRegistry.newType/register; it's also missing the JaymosKingdoms import. Use the pattern every other Kingdoms attachment uses, e.g. Banes.java:135: AttachmentRegistry.create(JaymosKingdoms.id("astronomy_discovery"), builder -> builder.persistent(<codec>).syncWith(<stream codec>, AttachmentSyncPredicate.targetOnly())), storing the set of discovered HIP numbers (e.g. a List<Integer> / int list codec), and list the class in the attachment registration like the others. 2) client/astronomy/Discovery.java: 'BitSet.valueOf(new long[0])[0]' isn't valid Java, and reportLook() is empty: nothing ever reaches the server, so no star can be discovered. The client needs a C2S payload (star hip), and the server must check it before adding it (night, star above the horizon for that player, and the ~4-second look counted on the server or at least rate-limited) and then set the attachment, which syncs back. 3) Master now already has client/astronomy/Discovery.java, SkyMath, Star and StarCatalog (they came in with the armillary, #1196) and the armillary reads Discovery, so the server-side discovery has to keep ArmillaryScreen/ArmillaryBlockEntityRenderer working: build against current master. 4) Check every API against the 26.3 sources (./gradlew genSources) before using it, and run the Kingdoms build before pushing. This is round two; if round three still doesn't build, this goes to Andy. The rest of round one (sky render pass, pollution per tick, vectors out of the loop) I'll check once it compiles.
- 2026-11-09 18:52 Text pass: rewrote change note (changes/kingdoms/claude-astronomy-sky.md) for player focus - simplified title, made text more direct ('your position' vs 'the player's position', 'looking up at night' vs 'the player discovers them by looking at them at night'), removed implementation details (render pass, class list), kept credits concise
- 2026-11-09 19:05 Review (head 3f6d3ba), third round: BLOCKED, needs Andy. Same problem three rounds running: it doesn't build (Build Kingdoms red again) and the server-side discovery still isn't real. This round: DiscoveryC2SPacket.java:20-22 and ArmillarySphereBlock.java:72-74 don't compile (the receiver passes the payload where a RegistryFriendlyByteBuf is wanted; context.player().ifPresent isn't Fabric's API, it's context.player()); the server handler would replace the player's whole discovered set with readDiscovered() of a one-star packet, with no check that it's night or the star is up; client/astronomy/Discovery.reportLook casts the client's level to ServerLevel, which never works on a real client, so no packet is ever sent; the networking is registered inside ArmillarySphereBlock. The two bots that tried (hermes-b, hermes-fixer) are inventing APIs instead of checking the 26.3 and Fabric sources. Suggest Andy hands this to a stronger builder (a Claude thread or the cloud agent): one synced player attachment (pattern: Banes.java:135), one C2S payload with a server-side check, registered in Kingdoms' networking setup; then the round-one points (sky render pass, pollution per tick) still need checking. The PR is mergeable but must not merge while red.
- 2026-11-09 19:29 Andy (2026-11-09): Claude takes this one over from the bots ('you take that too'). Working on branch claude/astronomy-sky (PR #1193).
- 2026-11-09 20:09 Claude (head 2fe722b): rebuilt and green. Discovery is now server-side (StarDiscovery attachment, synced; Looked payload checked for overworld, night, open sky, rate-limited), the look tick is actually registered, light pollution is per second, and a heightmap horizon profile hides stars behind far terrain. SkyMath/Discovery callers/ArmillarySphereBlock match master again. Written by the reviewer, so it has had no independent review. Not tested in game: the look of the stars, the skyline cut, clouds, Iris/Sodium, and the discovery round trip on a server.
