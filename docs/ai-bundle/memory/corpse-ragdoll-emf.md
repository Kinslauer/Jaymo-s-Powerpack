---
name: corpse-ragdoll-emf
description: Player corpse ragdolls broke because EMF/Fresh Animations animates anything baked from vanilla ModelLayers.PLAYER; fixed with own layers (PR #236)
metadata:
  type: project
  modified: 2026-10-15T09:28:06.303Z
---
2026-10-15 Andy: "player corpse ragdolls are broken". Cause: Entity Model Features runs Fresh Animations: Player Extension on any model baked from ModelLayers.PLAYER/PLAYER_SLIM, at final draw (after our prepareModel poses), so Ragdoll/Flop limb poses got overwritten.
Fix PR #236 MERGED 2026-10-15 09:4xZ (pack 0.11.39 / Kitchen 0.4.42, not playtested): Kitchen CorpseRenderer.PLAYER_BODY(_SLIM) = jaymoskitchen:player_corpse layers; Powers SeveredPartRenderer and Kitchen ZombiePlayerRenderer (zombie arms) use them too. Echoes/Z rogues left on vanilla layers on purpose (they move like players).
**How to apply:** any renderer that hand-poses a player model (ChronoEchoRenderer, ZFighterRogueRenderer...) should bake from its own layer, not ModelLayers.PLAYER, or FA will animate it. See [[physics-mod]], [[fresh-animations-player]].
2026-10-15 Andy: "bows dont animate correctly" (arms at sides, no draw). FA's a_player_variables.jpm detects bow/crossbow poses only when arm yRot equals vanilla's within 0.0001 (vs torad(head_yaw)); MCA body/our tweaks miss it, so FA idles the arms. PR #239 MERGED 2026-10-15 10:0xZ as pack 0.11.43: PoseTakeover.aiming pauses EMF while BOW_AND_ARROW/CROSSBOW_*/THROW_TRIDENT. Not playtested; vanilla aim pose instead of FA's.
2026-10-15 Andy: "make the corpses show the user's villager skins". PR #264 MERGED (pack 0.11.79 / Kitchen 0.4.44): Kitchen client/corpse/McaLooks gets MCAClient.getVillagerRendererData(owner); CorpseRenderer + Powers SeveredPartRenderer draw that MCA stand-in villager via the mob-body path; player skin fallback. Zombie players too (ZombiePlayerRenderer.extractLook, MCA infection 1.0, ARMS_OUT); McaLooks only asks MCA for known players (future zombies have random ids).
2026-10-15 Andy: "falling corpses should render regardless if they had been falling for more than 10s". Cause: vanilla Entity.shouldRenderAtSqrDistance = 64 x box size (corpse 1x0.4 -> ~50 blocks), so long falls vanished. PR #269 MERGED 2026-10-16 02:3xZ (pack 0.11.88 / Kitchen 0.4.45): airborne corpses skip distance + frustum-box culling.
