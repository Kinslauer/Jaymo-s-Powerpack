package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import java.util.UUID;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (Weapon Forms patch 6) Nadir's Ultimate Move, <b>Three Worlds Film</b> (C): the void shows a foe the three worlds it
 * will never see again. The caster's camera cuts to a tilted close shot of the target ({@link UltimateFilm#DUTCH}, the
 * same skippable film every ultimate has, with the bars for everyone near) while three panels of light flash up behind
 * the target, one after another: the Overworld (sky and grass), the Nether (embers and lava) and the Aether (cloud and
 * gold). Then the film ends and the blow lands.
 *
 * <p>It is only a picture: the panels are particles and light, with no portal, no dimension change and no new realm.
 * The blow lands whether or not the caster skips the film (the skip only ends their camera), but not if the target has
 * gone, changed world or died, or the caster has. An original idea written for this pack (The Awakening's Rift and
 * Command are all rights reserved: nothing of theirs is used).
 */
public final class NadirFilm {
	// ---- tuning ----
	/** How far C can pick out a target (blocks). */
	public static final double REACH = 28.0;
	/** Cooldown for the next Film (ticks). */
	public static final int COOLDOWN = 20 * 75;
	/** The film's length (ticks) and the ticks at which each world flashes up and the blow lands. */
	public static final int FILM_TICKS = 66;
	static final int[] FLASH_AT = {14, 28, 42};
	static final int BLOW_AT = 60;
	/** How far behind the target the panels hang (blocks), and their half-width and height. */
	static final double PANEL_BEHIND = 5.0, PANEL_HALF_WIDTH = 5.0, PANEL_HEIGHT = 6.0;
	/** Particles across and up one panel. */
	static final int PANEL_COLUMNS = 14, PANEL_ROWS = 7;
	/** Witnesses within this many blocks of the caster see the bars. */
	public static final double WITNESS_RANGE = 48.0;
	/** The blow: damage to the target, damage to what stands close, how close, and the shove away. */
	static final float BLOW_DAMAGE = 34.0F, SPLASH_DAMAGE = 10.0F;
	static final double SPLASH_RADIUS = 3.5, BLOW_PUSH = 1.6, BLOW_LIFT = 0.5;
	/** Shake strength of the blow. */
	static final float BLOW_SHAKE = 2.4F;

	/** The three worlds, in the order the film shows them. */
	private enum World {
		OVERWORLD(0xFF7FC8FF, 0xFF4FA83A, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F),
		NETHER(0xFFE8421C, 0xFF7A1010, SoundEvents.GHAST_AMBIENT, 0.6F),
		AETHER(0xFFFFF2B8, 0xFFBFE8FF, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F);

		final int high, low;
		final SoundEvent sound;
		final float pitch;

		World(int high, int low, SoundEvent sound, float pitch) {
			this.high = high;
			this.low = low;
			this.sound = sound;
			this.pitch = pitch;
		}
	}

	private NadirFilm() {
	}

	/** Starts the film on whatever the caster is looking at; false (nothing spent) if it's looking at nothing. */
	public static boolean start(ServerPlayer player) {
		LivingEntity target = NadirAim.aimed(player, REACH);
		if (target == null) {
			return false;
		}
		ServerLevel level = player.level();
		UUID id = target.getUUID();
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		UltimateFilm.play(player, UltimateFilm.DUTCH, FILM_TICKS, at, target, WITNESS_RANGE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6F, 1.8F);
		for (int i = 0; i < FLASH_AT.length; i++) {
			World world = World.values()[i];
			UltimateFilm.later(player, FLASH_AT[i], p -> {
				LivingEntity t = alive(p, level, id);
				if (t != null) {
					panel(p, level, t, world);
				}
			});
		}
		UltimateFilm.later(player, BLOW_AT, p -> {
			LivingEntity t = alive(p, level, id);
			if (t != null) {
				blow(p, level, t);
			}
		});
		return true;
	}

	/** The target, if it and the caster are both still there, in the world the film began in. */
	private static LivingEntity alive(ServerPlayer player, ServerLevel level, UUID id) {
		if (!player.isAlive() || player.level() != level) {
			return null;
		}
		Entity e = level.getEntity(id);
		return e instanceof LivingEntity living && living.isAlive() ? living : null;
	}

	/** One world flashes up behind the target: a lit panel of particles, a flash and a sound. */
	private static void panel(ServerPlayer player, ServerLevel level, LivingEntity target, World world) {
		Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 side = new Vec3(-away.z, 0.0, away.x);
		Vec3 base = target.position().add(away.scale(PANEL_BEHIND)).add(0.0, -0.5, 0.0);
		for (int c = 0; c < PANEL_COLUMNS; c++) {
			double across = (c / (double)(PANEL_COLUMNS - 1) - 0.5) * 2.0 * PANEL_HALF_WIDTH;
			for (int r = 0; r < PANEL_ROWS; r++) {
				double up = r / (double)(PANEL_ROWS - 1);
				Vec3 p = base.add(side.scale(across)).add(0.0, up * PANEL_HEIGHT, 0.0);
				// the sky above, the ground below
				int colour = up > 0.4 ? world.high : world.low;
				level.sendParticles(new DustParticleOptions(colour & 0xFFFFFF, 2.4F), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.0);
			}
		}
		Vec3 mid = base.add(0.0, PANEL_HEIGHT * 0.5, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, world.high), mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
		ParticleOptions detail = switch (world) {
			case OVERWORLD -> ParticleTypes.HAPPY_VILLAGER;
			case NETHER -> ParticleTypes.FLAME;
			case AETHER -> ParticleTypes.CLOUD;
		};
		level.sendParticles(detail, mid.x, mid.y - 1.0, mid.z, 60, PANEL_HALF_WIDTH * 0.6, 2.0, 0.6, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 20, PANEL_HALF_WIDTH * 0.6, 2.0, 0.6, 0.05);
		level.playSound(null, mid.x, mid.y, mid.z, world.sound, SoundSource.PLAYERS, 1.4F, world.pitch);
		Shakes.around(level, mid, WITNESS_RANGE, 0.5F);
	}

	/** The film ends and the blow lands: a heavy hit, a shove, a lesser one for what stands close. */
	private static void blow(ServerPlayer player, ServerLevel level, LivingEntity target) {
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		target.hurtServer(level, player.damageSources().playerAttack(player), BLOW_DAMAGE);
		target.setDeltaMovement(target.getDeltaMovement().add(away.x * BLOW_PUSH, BLOW_LIFT, away.z * BLOW_PUSH));
		target.syncVelocity = true;
		for (LivingEntity near : NadirAim.around(player, at, SPLASH_RADIUS)) {
			if (near != target) {
				near.hurtServer(level, player.damageSources().playerAttack(player), SPLASH_DAMAGE);
			}
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 0.5, 0.8, 0.5, 0.4);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		Shakes.heavy(player, at, WITNESS_RANGE, BLOW_SHAKE);
	}
}
