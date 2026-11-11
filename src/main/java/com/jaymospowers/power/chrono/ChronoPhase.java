package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Overclock's held phase (V at a wall): hold V with a wall right ahead and you vibrate through blocks for up to
 * {@link #PHASE_TICKS} ticks, walls and ceilings no longer stopping you (the floor under your feet still holds, and
 * unbreakable blocks always do). Let go, or run dry, inside a block and you are put out at the nearest clear spot within
 * {@link #OUT_RADIUS} blocks, or where you last stood clear; running dry inside one costs {@link #DRY_DAMAGE}. A cooldown
 * follows, longer the longer you held ({@link #COOLDOWN} plus {@link #COOLDOWN_SHARE} of it). It replaced the old blink
 * through a wall up to four blocks thick. A shaking gold crackle marks it; the meter shows under the gear chip.
 *
 * <p>The collision itself is {@code ChronoHeldPhaseMixin}, which asks {@link #phasing}; written so another held phase
 * (any power) could ask the same thing of one attachment.
 */
public final class ChronoPhase {
	// ---- tuning ----
	/** How long it can be held (ticks), how near the wall must be to start it (blocks), and the cooldown after. */
	static final int PHASE_TICKS = 100;
	static final double REACH = 1.5;
	static final int COOLDOWN = 30;
	static final float COOLDOWN_SHARE = 0.75F;
	/** Ending inside a block: how far to look for a clear spot (blocks), and what running dry there costs. */
	static final int OUT_RADIUS = 3;
	static final float DRY_DAMAGE = 4.0F;

	/** On a player while phasing, for both sides' collision. */
	public static final AttachmentType<Boolean> PHASING = AttachmentRegistry.create(JaymosPowers.id("chrono_phasing"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Set while the phase itself asks the world "is this spot clear?", so it gets the true answer. */
	private static final ThreadLocal<Boolean> ASKING = ThreadLocal.withInitial(() -> false);

	private static final class Run {
		int ticks;
		Vec3 free;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Integer> READY = new HashMap<>();

	private ChronoPhase() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Player player && phasing(player)
				&& (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_FALL))));
	}

	/** Whether {@code player} is phasing right now and the collision should let it through (either side). */
	public static boolean phasing(Player player) {
		return !ASKING.get() && player.getAttached(PHASING) != null;
	}

	/** Stasis Field winds the cooldown back too. */
	static void resetCooldown(ServerPlayer player) {
		READY.remove(player.getUUID());
	}

	/** V at a wall: start holding the phase. True if it started. */
	static boolean start(ServerPlayer player) {
		ServerLevel level = player.level();
		if (RUNS.containsKey(player.getUUID()) || READY.getOrDefault(player.getUUID(), 0) > player.tickCount || player.isPassenger()) {
			return false;
		}
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			return false;
		}
		look = look.normalize();
		AABB box = player.getBoundingBox();
		boolean wall = false;
		for (double d = 0.25; d <= REACH; d += 0.25) {
			if (!level.noCollision(player, box.move(look.scale(d)))) {
				wall = true;
				break;
			}
		}
		if (!wall) {
			return false;
		}
		Run run = new Run();
		run.free = player.position();
		RUNS.put(player.getUUID(), run);
		player.setAttached(PHASING, true);
		Vec3 at = player.position();
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 1.2F, 0xFFD070);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 2.0F);
		return true;
	}

	/** V let go (or any reason to stop): the phase ends. */
	static void release(ServerPlayer player) {
		if (RUNS.containsKey(player.getUUID())) {
			end(player, false);
		}
	}

	/** Each tick (server): the phase runs down, remembers where it last stood clear, and ends when spent or when Overclock does. */
	static void tick(ServerPlayer player, ServerLevel level, boolean overclocked) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			if (player.getAttached(PHASING) != null) {
				player.removeAttached(PHASING);
			}
			ChronoMeters.set(player, ChronoMeters.PHASE, -1.0F);
			return;
		}
		if (!overclocked || !player.isAlive() || player.isSpectator()) {
			end(player, false);
			return;
		}
		run.ticks++;
		if (clear(level, player, player.position())) {
			run.free = player.position();
		}
		if (run.ticks % 3 == 0) {
			level.sendParticles(MagicFx.CRACKLE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.7, 0.3, 0.1);
		}
		ChronoMeters.set(player, ChronoMeters.PHASE, 1.0F - run.ticks / (float)PHASE_TICKS);
		if (run.ticks >= PHASE_TICKS) {
			end(player, true);
		}
	}

	private static boolean clear(ServerLevel level, ServerPlayer player, Vec3 at) {
		ASKING.set(true);
		try {
			return level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())));
		} finally {
			ASKING.set(false);
		}
	}

	private static void end(ServerPlayer player, boolean spent) {
		Run run = RUNS.remove(player.getUUID());
		if (run == null) {
			return;
		}
		ServerLevel level = player.level();
		player.removeAttached(PHASING);
		ChronoMeters.set(player, ChronoMeters.PHASE, -1.0F);
		READY.put(player.getUUID(), player.tickCount + COOLDOWN + Math.round(run.ticks * COOLDOWN_SHARE));
		if (!clear(level, player, player.position())) {
			Vec3 out = nearestClear(level, player);
			if (out == null) {
				out = run.free;
			}
			player.teleportTo(level, out.x, out.y, out.z, Set.of(), player.getYRot(), player.getXRot(), false);
			if (spent && !clear(level, player, player.position())) {
				player.hurtServer(level, level.damageSources().generic(), DRY_DAMAGE);
			}
		}
		Vec3 at = player.position();
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 1.2F, 0xFFD070);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 2.0F);
	}

	/** The clear spot nearest to {@code player} within {@link #OUT_RADIUS}, or null. */
	private static Vec3 nearestClear(ServerLevel level, ServerPlayer player) {
		List<Vec3> spots = new ArrayList<>();
		for (int dx = -OUT_RADIUS; dx <= OUT_RADIUS; dx++) {
			for (int dy = -OUT_RADIUS; dy <= OUT_RADIUS; dy++) {
				for (int dz = -OUT_RADIUS; dz <= OUT_RADIUS; dz++) {
					spots.add(player.position().add(dx, dy, dz));
				}
			}
		}
		spots.sort(java.util.Comparator.comparingDouble(s -> s.distanceToSqr(player.position())));
		for (Vec3 spot : spots) {
			if (level.getWorldBorder().isWithinBounds(spot.x, spot.z) && clear(level, player, spot)) {
				return spot;
			}
		}
		return null;
	}
}
