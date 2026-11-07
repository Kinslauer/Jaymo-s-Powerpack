package com.jaymospowers.water;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.phys.Vec3;

/**
 * (Motion patch 2) Going into water from a height, after Actions &amp; Stuff's dives. Fall from {@link #MIN_FALL} blocks or
 * more into water at least {@link #MIN_DEPTH} deep and how you went in shows:
 * <ul>
 * <li><b>Cannonball:</b> Sneak held from well before the water ({@link #TUCK_TICKS} or more): a big splash, a boom, and
 * the fish nearby scatter.</li>
 * <li><b>Clean dive:</b> Sneak pressed just before the water (within {@link #TUCK_TICKS}): a small, neat splash and you
 * slip deeper, faster.</li>
 * <li><b>Belly flop:</b> neither. A slap and a splash, and from {@link #FLOP_FALL} blocks up it stings (1-2 damage).</li>
 * </ul>
 * The splash is our own (vanilla splash and bubble particles), sized by how far you fell and how big you are. Water
 * already takes fall damage away; nothing here adds any but the flop's sting.
 */
public final class Dives {
	// ===== the knobs ================================================================================================
	/** Fell at least this far (blocks) into water at least this deep (blocks). */
	static final double MIN_FALL = 4.0;
	static final int MIN_DEPTH = 2;
	/** Sneak held this many ticks or more before the water is a cannonball; pressed later, a dive. */
	static final int TUCK_TICKS = 8;
	/** A belly flop from this high (blocks) stings, this much at that height and at most this much. */
	static final double FLOP_FALL = 12.0;
	static final float FLOP_DAMAGE = 1.0F, FLOP_MAX = 2.0F;
	/** A clean dive carries you down this much faster (blocks a tick). */
	static final double DIVE_PUSH = 0.6;
	/** The splash: drops per block fallen (times body width), at most this many; how far a cannonball scatters fish. */
	static final int DROPS_PER_BLOCK = 6, MAX_DROPS = 160;
	static final double SCATTER_RANGE = 8.0, SCATTER_PUSH = 0.6;
	// ================================================================================================================

	enum Entry {
		CANNONBALL, DIVE, FLOP
	}

	private static final class Watch {
		double fall;
		boolean inWater;
		int sneakTicks;
	}

	private static final Map<UUID, Watch> WATCH = new HashMap<>();

	private Dives() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Dives::tick);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Watch w = WATCH.computeIfAbsent(player.getUUID(), id -> new Watch());
			boolean inWater = player.isInWater();
			if (inWater && !w.inWater && w.fall >= MIN_FALL && !player.isSpectator() && !player.isPassenger() && deep(player)) {
				Entry entry = !player.isShiftKeyDown() ? Entry.FLOP : w.sneakTicks >= TUCK_TICKS ? Entry.CANNONBALL : Entry.DIVE;
				enter(player, entry, w.fall);
			}
			w.inWater = inWater;
			w.fall = inWater || player.onGround() ? 0.0 : player.fallDistance;
			w.sneakTicks = player.isShiftKeyDown() ? w.sneakTicks + 1 : 0;
		}
		if (server.getTickCount() % 1200 == 0) {
			WATCH.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** Water at least {@link #MIN_DEPTH} deep from the feet down. */
	private static boolean deep(ServerPlayer player) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		for (int i = 0; i < MIN_DEPTH; i++) {
			if (!player.level().getFluidState(pos).is(FluidTags.WATER)) {
				return false;
			}
			pos.move(0, -1, 0);
		}
		return true;
	}

	static void enter(ServerPlayer player, Entry entry, double fall) {
		ServerLevel level = player.level();
		Vec3 at = new Vec3(player.getX(), surface(player), player.getZ());
		float size = Math.max(0.6F, player.getBbWidth()) / 0.6F;
		int drops = (int)Math.min(MAX_DROPS, fall * DROPS_PER_BLOCK * size * switch (entry) {
			case CANNONBALL -> 2.0;
			case DIVE -> 0.35;
			case FLOP -> 1.0;
		});
		double spread = player.getBbWidth() * (entry == Entry.CANNONBALL ? 1.2 : entry == Entry.DIVE ? 0.3 : 0.9);
		double lift = entry == Entry.CANNONBALL ? 0.6 : entry == Entry.DIVE ? 0.15 : 0.35;
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, drops, spread, 0.1, spread, lift);
		level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y - 0.6, at.z, drops / 3, spread, 0.4, spread, 0.05);
		switch (entry) {
			case CANNONBALL -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.6F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.25F, 1.8F);
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, drops / 2, spread * 0.5, 0.6, spread * 0.5, 0.9);
				for (WaterAnimal fish : level.getEntitiesOfClass(WaterAnimal.class, player.getBoundingBox().inflate(SCATTER_RANGE))) {
					Vec3 away = fish.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() < 1.0E-4) {
						continue;
					}
					fish.setDeltaMovement(fish.getDeltaMovement().add(away.normalize().scale(SCATTER_PUSH)));
					fish.needsSync = true;
				}
			}
			case DIVE -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.5F);
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.min(v.y, 0.0) - DIVE_PUSH, v.z);
				player.needsSync = true;
			}
			case FLOP -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.9F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
				if (fall >= FLOP_FALL && !player.isCreative()) {
					float damage = Mth.clamp(FLOP_DAMAGE + (float)((fall - FLOP_FALL) / FLOP_FALL), FLOP_DAMAGE, FLOP_MAX);
					player.hurtServer(level, level.damageSources().fall(), damage);
				}
			}
		}
	}

	/** The top of the water the player went into. */
	private static double surface(ServerPlayer player) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		for (int i = 0; i < 4 && player.level().getFluidState(pos.above()).is(FluidTags.WATER); i++) {
			pos.move(0, 1, 0);
		}
		return pos.getY() + player.level().getFluidState(pos).getHeight(player.level(), pos);
	}
}
