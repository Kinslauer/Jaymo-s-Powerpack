package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Call of the wild (Pack Leader's second gift): out of shape, call a wild beast of a kind studied out of the brush, at the edge of
 * sight, even when none is about. It fights beside the Druid for {@link #STAY_TICKS} and then goes back to the wild. It costs a
 * shape's use.
 */
final class BeastCall {
	/** How long it stays (ticks), and how many tries finding the spot takes. */
	static final int STAY_TICKS = 20 * 60, TRIES = 30;
	/** How far off it comes from (blocks, nearest and furthest), and how far it sees a foe. */
	static final double FAR_MIN = 14.0, FAR_MAX = 22.0, SIGHT = 16.0;
	/** A flier appears this high above the ground (blocks). */
	static final int AIR_HEIGHT = 6;

	private record Called(Mob beast, long until) {
	}

	private static final Map<UUID, Called> CALLED = new HashMap<>();

	private BeastCall() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BeastCall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CALLED.clear());
	}

	/** The kind a form calls: its entity type, or null (the elder shapes are not beasts of the brush). */
	private static @Nullable EntityType<?> kind(String form) {
		if (BeastForms.isMob(form)) {
			return BeastForms.typeOf(form);
		}
		var kin = Druids.KIN.get(form);
		if (kin != null) {
			for (String path : kin) {
				EntityType<?> type = ShapeFx.typeOfKind(path);
				if (type != null) {
					return type;
				}
			}
		}
		return null;
	}

	/** Calls a beast of {@code form}'s kind to {@code player}'s side. */
	static void call(ServerPlayer player, String form) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!WildShapes.has(player, WildShapes.PACK)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.call_locked"));
			return;
		}
		EntityType<?> type = kind(form);
		if (type == null || !(type.create(level, EntitySpawnReason.EVENT) instanceof PathfinderMob beast)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.call_none"));
			return;
		}
		Vec3 at = spot(player, level, beast);
		if (at == null) {
			beast.discard();
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.call_none"));
			return;
		}
		if (!Rests.spend(player, Rests.SHAPE, 1, Druids.shapes(player))) {
			beast.discard();
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.tired"));
			return;
		}
		Called old = CALLED.remove(player.getUUID());
		if (old != null) {
			old.beast.discard();
		}
		beast.setPos(at.x, at.y, at.z);
		beast.setPersistenceRequired();
		beast.addTag(BeastForms.TEMP_TAG);
		level.addFreshEntity(beast);
		CALLED.put(player.getUUID(), new Called(beast, level.getGameTime() + STAY_TICKS));
		level.sendParticles(Fx.wisp(ShapeFx.WISP_GREEN, 1.2F), at.x, at.y + 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.02);
		beast.playAmbientSound();
		ShapeFx.vines(player, true);
	}

	/** A place at the edge of sight, ahead of the Druid, where {@code beast} stands (water for a swimmer, sky for a flier), or null. */
	private static @Nullable Vec3 spot(ServerPlayer player, ServerLevel level, Mob beast) {
		MobCategory category = beast.getType().getCategory();
		boolean water = category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT || category == MobCategory.UNDERGROUND_WATER_CREATURE;
		boolean air = BeastForms.has(BeastForms.form(beast.getType()), BeastForms.Trait.FLY);
		for (int i = 0; i < TRIES; i++) {
			double turn = Math.toRadians(player.getYRot() + (level.getRandom().nextDouble() - 0.5) * 140.0);
			double d = FAR_MIN + level.getRandom().nextDouble() * (FAR_MAX - FAR_MIN);
			int x = (int) Math.floor(player.getX() - Math.sin(turn) * d), z = (int) Math.floor(player.getZ() + Math.cos(turn) * d);
			if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (water) {
				feet = feet.below();
				if (!level.getFluidState(feet).is(FluidTags.WATER)) {
					continue;
				}
			} else if (level.getFluidState(feet.below()).is(FluidTags.WATER) || level.getBlockState(feet.below()).isAir()) {
				continue;
			} else if (air) {
				feet = feet.above(AIR_HEIGHT);
			}
			beast.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			if (level.noCollision(beast)) {
				return beast.position();
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (CALLED.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		CALLED.entrySet().removeIf(e -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
			Mob beast = e.getValue().beast;
			if (beast.isRemoved()) {
				return true;
			}
			if (owner == null || !owner.isAlive() || owner.level() != beast.level() || now >= e.getValue().until) {
				// back to the wild
				if (beast.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.POOF, beast.getX(), beast.getY(0.5), beast.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
				}
				beast.discard();
				return true;
			}
			if ((now + beast.getId()) % 10 == 0 && beast instanceof PathfinderMob p) {
				LivingEntity foe = owner.getLastHurtMob() != null && owner.getLastHurtMob().isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() < 200
					? owner.getLastHurtMob() : owner.getLastHurtByMob();
				WildShapes.aid(owner.level(), p, owner, foe, SIGHT);
			}
			return false;
		});
	}
}
