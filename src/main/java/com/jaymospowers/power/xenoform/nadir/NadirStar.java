package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.gravity.GravityMeteor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Nadir's Star (sneak + C, looking at the sun or the moon), from the Hand of God mod (ideas only; it is All Rights
 * Reserved): Nadir reaches up and pulls the light down out of the sky. It comes down over {@link #PULL_TICKS} and hangs
 * over the hand, a ball of burning gold (the sun) or cold silver (the moon); press sneak + C again and it is thrown
 * where you look. Where it lands it strikes like our largest meteor (Andy, 2026-11-25: "the sun and moon throw should
 * deal damage equivalent to the largest meteor or spirit bomb"): {@link GravityMeteor#fullStrike}, the crater stays.
 * Only outdoors, with the light above the horizon; once every {@link #COOLDOWN}.
 */
public final class NadirStar {
	// ---- tuning ----
	/** How near the view must be to the light (degrees), and the least height above the horizon (sine). */
	static final double AIM_DEGREES = 20.0, ABOVE_HORIZON = 0.05;
	/** How far off it is drawn when the pull begins (blocks), and how long the pull takes (ticks). */
	static final double SKY_DISTANCE = 90.0;
	static final int PULL_TICKS = 50;
	/** The longest it is held, the throw's speed (blocks a tick) and its longest flight (ticks). */
	static final int HOLD_TICKS = 600, FLIGHT_TICKS = 120;
	static final double THROW_SPEED = 2.2;
	/** Its size held (radius), and the cooldown (ticks) once thrown or let go. */
	static final float SIZE = 2.6F;
	public static final int COOLDOWN = 3600;
	/** The sun's colours (heart, glow) and the moon's. */
	static final int SUN = 0xFFF2C0, SUN_GLOW = 0xFFA030, MOON = 0xF2F6FF, MOON_GLOW = 0x9FB4E8;

	private enum Phase { PULL, HELD, THROWN }

	private static final class Star {
		final UUID owner;
		final boolean sun;
		final ServerLevel level;
		Phase phase = Phase.PULL;
		long since;
		Vec3 at;
		final Vec3 from;
		Vec3 heading = Vec3.ZERO;

		Star(UUID owner, boolean sun, ServerLevel level, Vec3 from, long since) {
			this.owner = owner;
			this.sun = sun;
			this.level = level;
			this.from = from;
			this.at = from;
			this.since = since;
		}
	}

	private static final Map<UUID, Star> STARS = new HashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private NadirStar() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(NadirStar::tick);
	}

	/**
	 * Sneak + C: pulls the sun or moon down if none is held, throws the one held. Returns the message key to show
	 * ({@code star_*}), or null if it went.
	 */
	public static @Nullable String press(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Star star = STARS.get(player.getUUID());
		if (star != null) {
			if (star.phase == Phase.HELD) {
				star.phase = Phase.THROWN;
				star.since = now;
				star.heading = player.getViewVector(1.0F).scale(THROW_SPEED);
				level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0F, 0.4F);
				level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 2.0F, 0.5F);
				NEXT.put(player.getUUID(), now + COOLDOWN);
				return null;
			}
			return star.phase == Phase.PULL ? "star_coming" : "star_thrown";
		}
		long next = NEXT.getOrDefault(player.getUUID(), 0L);
		if (now < next) {
			return "star_wait";
		}
		if (!level.dimensionType().hasSkyLight() || !level.canSeeSky(player.blockPosition().above())) {
			return "star_no_sky";
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 sun = skyward(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, eye));
		Vec3 moon = skyward(level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, eye));
		double cos = Math.cos(Math.toRadians(AIM_DEGREES));
		boolean sunSeen = sun.y > ABOVE_HORIZON && sun.dot(look) >= cos;
		boolean moonSeen = moon.y > ABOVE_HORIZON && moon.dot(look) >= cos;
		if (!sunSeen && !moonSeen) {
			return "star_none";
		}
		boolean isSun = sunSeen && (!moonSeen || sun.dot(look) >= moon.dot(look));
		Vec3 from = eye.add((isSun ? sun : moon).scale(SKY_DISTANCE));
		STARS.put(player.getUUID(), new Star(player.getUUID(), isSun, level, from, now));
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.4F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8F, 0.5F);
		return null;
	}

	/**
	 * The way to the sun (or moon) for its sky angle (degrees): the sky draws it at the top of a circle turned about
	 * the east-west line, so at 0 it is straight overhead.
	 */
	static Vec3 skyward(float degrees) {
		double a = Math.toRadians(degrees);
		return new Vec3(-Math.sin(a), Math.cos(a), 0.0);
	}

	/** Lets go of a star being pulled or held (Nadir going): it fades back into the sky. */
	public static void end(ServerPlayer player) {
		Star star = STARS.get(player.getUUID());
		if (star != null && star.phase != Phase.THROWN) {
			STARS.remove(player.getUUID());
			fade(star);
		}
	}

	private static Vec3 hand(ServerPlayer player) {
		return player.getEyePosition().add(0.0, SIZE + 1.2, 0.0).add(player.getViewVector(1.0F).horizontal().scale(0.6));
	}

	private static void tick(MinecraftServer server) {
		if (STARS.isEmpty()) {
			return;
		}
		for (Iterator<Star> it = STARS.values().iterator(); it.hasNext(); ) {
			Star star = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(star.owner);
			long age = star.level.getGameTime() - star.since;
			if (owner == null || (star.phase != Phase.THROWN && owner.level() != star.level)) {
				it.remove();
				fade(star);
				continue;
			}
			switch (star.phase) {
				case PULL -> {
					float t = Mth.clamp(age / (float)PULL_TICKS, 0.0F, 1.0F);
					// slow at first, then rushing down
					float eased = t * t * (3.0F - 2.0F * t);
					star.at = star.from.lerp(hand(owner), eased);
					draw(star, SIZE * (0.4F + 0.6F * t));
					if (age % 6 == 0) {
						star.level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.5F + t);
					}
					if (age >= PULL_TICKS) {
						star.phase = Phase.HELD;
						star.since = star.level.getGameTime();
						Shakes.around(star.level, star.at, 24.0, 0.4F);
						star.level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 2.0F, 0.5F);
					}
				}
				case HELD -> {
					star.at = star.at.lerp(hand(owner), 0.5);
					draw(star, SIZE);
					if (age % 30 == 0) {
						star.level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.6F);
					}
					if (age >= HOLD_TICKS) {
						it.remove();
						fade(star);
						NEXT.put(star.owner, star.level.getGameTime() + COOLDOWN);
					}
				}
				case THROWN -> {
					Vec3 next = star.at.add(star.heading);
					Vec3 hit = landing(star, owner, next);
					if (hit != null || age >= FLIGHT_TICKS) {
						it.remove();
						strike(star, owner, hit != null ? hit : next);
						continue;
					}
					star.at = next;
					draw(star, SIZE);
				}
			}
		}
	}

	/** Where the thrown star meets a block or a creature on its way to {@code next}, or null. */
	private static @Nullable Vec3 landing(Star star, ServerPlayer owner, Vec3 next) {
		BlockHitResult block = star.level.clip(new ClipContext(star.at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		if (block.getType() == HitResult.Type.BLOCK) {
			return block.getLocation();
		}
		List<net.minecraft.world.entity.LivingEntity> near = NadirAim.around(owner, next, SIZE);
		return near.isEmpty() ? null : near.get(0).getBoundingBox().getCenter();
	}

	/** It lands: our largest meteor's strike, the sun's or the moon's stone thrown up. */
	private static void strike(Star star, ServerPlayer owner, Vec3 at) {
		List<Integer> debris = star.sun
			? List.of(Block.getId(Blocks.MAGMA_BLOCK.defaultBlockState()), Block.getId(Blocks.BLACKSTONE.defaultBlockState()),
				Block.getId(Blocks.NETHERRACK.defaultBlockState()))
			: List.of(Block.getId(Blocks.STONE.defaultBlockState()), Block.getId(Blocks.ANDESITE.defaultBlockState()),
				Block.getId(Blocks.TUFF.defaultBlockState()));
		MagicFx.blast(star.level, at, SIZE * 6.0F, star.sun ? SUN : MOON, 30);
		MagicFx.blast(star.level, at, SIZE * 3.0F, star.sun ? SUN_GLOW : MOON_GLOW, 40);
		GravityMeteor.fullStrike(star.level, owner, at, debris);
	}

	/** Let go of: it goes back up, fading. */
	private static void fade(Star star) {
		MagicFx.blast(star.level, star.at, SIZE * 1.5F, star.sun ? SUN_GLOW : MOON_GLOW, 20);
		star.level.playSound(null, star.at.x, star.at.y, star.at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0F, 0.5F);
	}

	/** The light itself: a white-hot heart in a glow, motes thrown off (embers for the sun, frost-pale for the moon). */
	private static void draw(Star star, float size) {
		ServerLevel level = star.level;
		Vec3 at = star.at;
		MagicFx.orb(level, at, size, star.sun ? SUN : MOON, 2);
		MagicFx.orb(level, at, size * 1.6F, star.sun ? SUN_GLOW : MOON_GLOW, 2);
		level.sendParticles(MagicFx.mote(star.sun ? SUN_GLOW : MOON_GLOW, 1.4F), at.x, at.y, at.z, 3, size * 0.6, size * 0.6, size * 0.6, 0.08);
		if (star.sun && level.getRandom().nextInt(3) == 0) {
			level.sendParticles(MagicFx.EMBER, at.x, at.y, at.z, 2, size * 0.5, size * 0.5, size * 0.5, 0.05);
		}
	}
}
