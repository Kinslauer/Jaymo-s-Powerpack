package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The suit hits the ground hard. Wearing it (or Living Armor), a charged leap kicks up dust, and so does every landing,
 * more the faster you came down; with Living Armor there's more of it, black goo spatters off, and the screen shakes.
 * Anything coming down (or thrown) fast enough also smashes the ground like a small explosion (see {@link #smash}).
 */
public final class WebImpacts {
	/** Vertical speed (blocks/tick) that counts as a leap off the ground, and as a landing worth the dust. */
	static final double LEAP_SPEED = 0.6, LAND_SPEED = 0.55;
	/** From this speed (blocks/tick), an impact breaks the ground around it. */
	public static final double SMASH_SPEED = 1.1;
	/** The smash's reach (blocks) per block/tick of speed over {@link #SMASH_SPEED}, plus a base, up to a cap. */
	static final double SMASH_RADIUS_BASE = 1.2, SMASH_RADIUS_PER_SPEED = 1.6, SMASH_RADIUS_MAX = 3.5;
	/** How hard a block the smash can break per block/tick of speed (dirt 0.5, stone 1.5, deepslate 3). */
	static final float SMASH_POWER_PER_SPEED = 1.6F;
	/** Chance a broken block drops itself (like an explosion's). */
	static final float SMASH_DROP_CHANCE = 0.3F;
	/** Screen shake per block/tick of landing speed (Living Armor), up to a cap. */
	static final float SHAKE_PER_SPEED = 0.9F, SHAKE_MAX = 2.5F;

	private record Track(double lastVy, boolean ground) {
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private WebImpacts() {
	}

	/** Every tick, for a web slinger: watch for leaps and landings while the suit is on. */
	static void tick(ServerPlayer player) {
		boolean armor = WebSymbiote.isToggled(player, WebSymbioteState.ARMOR);
		if (!armor && !WebSymbiote.suitShown(player) || player.isSpectator()) {
			TRACKS.remove(player.getUUID());
			return;
		}
		double vy = player.getY() - player.yo;
		boolean ground = player.onGround();
		Track last = TRACKS.get(player.getUUID());
		if (last != null) {
			if (last.ground() && !ground && vy > LEAP_SPEED) {
				leap(player, vy, armor);
			} else if (!last.ground() && ground && -last.lastVy() > LAND_SPEED && !player.isInWater()) {
				land(player, -last.lastVy(), armor);
			}
		}
		TRACKS.put(player.getUUID(), new Track(vy, ground));
	}

	static void forget(ServerPlayer player) {
		TRACKS.remove(player.getUUID());
	}

	private static void leap(ServerPlayer player, double speed, boolean armor) {
		ServerLevel level = player.level();
		BlockState under = under(level, player.blockPosition().below());
		int count = (int)((armor ? 24 : 12) * speed);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(),
			count, 0.5, 0.05, 0.5, 0.15);
		level.sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 0.1, player.getZ(), armor ? 8 : 4, 0.4, 0.05, 0.4, 0.03);
		if (armor) {
			goo(level, player.position(), 8);
		}
	}

	private static void land(ServerPlayer player, double speed, boolean armor) {
		ServerLevel level = player.level();
		BlockState under = under(level, player.blockPosition().below());
		int count = (int)Math.min(120, (armor ? 40 : 18) * speed);
		double spread = Math.min(2.5, 0.5 + speed * (armor ? 0.9 : 0.5));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(),
			count, spread, 0.05, spread, 0.2);
		level.sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 0.1, player.getZ(), (int)((armor ? 10 : 5) * speed), spread * 0.7,
			0.05, spread * 0.7, 0.04);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS,
			(float)Math.min(1.2, 0.3 + speed * 0.4), armor ? 0.6F : 0.9F);
		if (armor) {
			goo(level, player.position(), (int)(10 * speed));
			float shake = (float)Math.min(SHAKE_MAX, speed * SHAKE_PER_SPEED);
			ServerPlayNetworking.send(player, new WebNet.Shake(shake));
		}
		if (speed >= SMASH_SPEED) {
			smash(level, player.position().add(0.0, -0.5, 0.0), speed, player);
		}
	}

	/** Black goo spattering off the armour (it falls back down). */
	private static void goo(ServerLevel level, Vec3 at, int count) {
		level.sendParticles(WebSymbiote.dust(1.6F), at.x, at.y + 0.6, at.z, count, 0.6, 0.4, 0.6, 0.0);
		level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, at.x, at.y + 1.0, at.z, Math.max(1, count / 3), 0.5, 0.4, 0.5, 0.0);
	}

	private static BlockState under(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() ? Blocks.DIRT.defaultBlockState() : state;
	}

	/**
	 * A hard impact at {@code at}, at {@code speed} blocks/tick: like a small explosion, the blocks around it break,
	 * the softer ones more readily and further out. The faster, the wider and the harder the blocks it breaks.
	 */
	public static void smash(ServerLevel level, Vec3 at, double speed, @Nullable Entity cause) {
		if (speed < SMASH_SPEED) {
			return;
		}
		double radius = Math.min(SMASH_RADIUS_MAX, SMASH_RADIUS_BASE + (speed - SMASH_SPEED) * SMASH_RADIUS_PER_SPEED);
		float power = (float)(speed * SMASH_POWER_PER_SPEED);
		var random = level.getRandom();
		int r = Mth.ceil(radius);
		BlockPos center = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			double distance = Math.sqrt(pos.distToCenterSqr(at));
			if (distance > radius) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F) {
				continue;
			}
			// the blast weakens outward; a soft block goes where a hard one holds
			float strength = power * (float)(1.0 - distance / (radius + 0.5));
			float chance = Mth.clamp(1.0F - hardness / Math.max(0.01F, strength), 0.0F, 1.0F);
			if (random.nextFloat() < chance) {
				level.destroyBlock(pos, random.nextFloat() < SMASH_DROP_CHANCE, cause);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (float)Math.min(1.0, 0.3 + speed * 0.25), 1.3F);
	}
}
