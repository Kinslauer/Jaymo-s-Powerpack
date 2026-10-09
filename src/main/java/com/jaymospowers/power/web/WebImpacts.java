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
	/** Screen shake (degrees) per block/tick of landing speed, up to a cap: Living Armor, and the plain suit. */
	static final float SHAKE_PER_SPEED = 3.5F, SHAKE_MAX = 9.0F, SUIT_SHAKE = 0.4F;
	/**
	 * Landing dust: the fall-damage puff (block particles of what you land on), far more of it. Per block/tick of
	 * speed with the suit, and with Living Armor; capped.
	 */
	static final int SUIT_DUST = 70, ARMOR_DUST = 220, DUST_MAX = 600;

	private record Track(double lastY, double lastVy, boolean ground) {
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
		// our own record of where they were (yo is already reset to now by the time a power ticks)
		Track last = TRACKS.get(player.getUUID());
		double vy = last == null ? 0.0 : player.getY() - last.lastY();
		boolean ground = player.onGround();
		if (last != null) {
			if (last.ground() && !ground && vy > LEAP_SPEED) {
				leap(player, vy, armor);
			} else if (!last.ground() && ground && -last.lastVy() > LAND_SPEED && !player.isInWater()) {
				land(player, -last.lastVy(), armor);
			}
		}
		TRACKS.put(player.getUUID(), new Track(player.getY(), vy, ground));
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
		int count = (int)Math.min(DUST_MAX, (armor ? ARMOR_DUST : SUIT_DUST) * speed);
		double spread = Math.min(armor ? 3.5 : 2.0, 0.5 + speed * (armor ? 1.4 : 0.7));
		// the fall-damage puff (the same block particles), many times over and thrown wide
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(),
			count, spread * 0.5, 0.05, spread * 0.5, 0.15);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, under), player.getX(), player.getY() + 0.1, player.getZ(),
			count / 4, spread * 0.6, 0.05, spread * 0.6, 0.3);
		level.sendParticles(ParticleTypes.DUST_PLUME, player.getX(), player.getY() + 0.1, player.getZ(), (int)((armor ? 24 : 8) * speed), spread * 0.7,
			0.05, spread * 0.7, 0.04);
		if (armor) {
			com.jaymospowers.core.Debris.ring(level, player.blockPosition().below(), (int)Math.min(4, 1 + speed * 1.5), (float)Math.min(2.5, speed));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS,
			(float)Math.min(1.2, 0.3 + speed * 0.4), armor ? 0.6F : 0.9F);
		if (armor) {
			goo(level, player.position(), (int)(30 * speed));
		}
		float shake = (float)Math.min(SHAKE_MAX, speed * SHAKE_PER_SPEED) * (armor ? 1.0F : SUIT_SHAKE);
		ServerPlayNetworking.send(player, new WebNet.Shake(shake));
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

	/** A hard impact (see {@link com.jaymospowers.core.Debris#smash}); below {@link #SMASH_SPEED} nothing breaks. */
	public static void smash(ServerLevel level, Vec3 at, double speed, @Nullable Entity cause) {
		if (speed >= SMASH_SPEED) {
			com.jaymospowers.core.Debris.smash(level, at, speed, cause);
		}
	}
}
