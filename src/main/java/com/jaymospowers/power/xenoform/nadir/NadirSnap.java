package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir's Snap (sprint + G), the matter unmaking of the Hand of God mod (ideas only; it is All Rights Reserved): a snap
 * of the fingers, and the creature stops where it stands and comes apart into dust from the head down, the dust carried
 * off on the wind, until nothing is left. At a creature, it alone; at nothing, half of everything Nadir may hit within
 * {@link #AREA} (chosen at random), each a moment after the last. It deals {@link #DAMAGE}: a great foe that lives
 * through it is only shaken loose of some dust. It leaves no body (its drops fall).
 */
public final class NadirSnap {
	// ---- tuning ----
	/** How far it reaches at a creature, and round you at nothing (blocks). */
	public static final double REACH = 24.0, AREA = 16.0;
	/** What it deals, and the cooldowns (G's): one creature, and the area. */
	static final float DAMAGE = 60.0F;
	public static final int COOLDOWN = 200, AREA_COOLDOWN = 1200;
	/** Ticks from the snap until a creature begins to go (the area's are spread from the least to the most), and how long it takes to go. */
	static final int DELAY = 8, AREA_DELAY_MIN = 10, AREA_DELAY_MAX = 70, CRUMBLE = 40;
	/** The dust: its colour, how many puffs a tick, how hard the wind takes it. */
	static final int DUST = 0x6F665C, PUFFS = 5;
	static final double WIND = 0.09;
	/** Jaymo's Kitchen leaves no body for a creature with this tag (Corpses.NO_CORPSE_TAG). */
	static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";

	private static final class Going {
		final UUID owner;
		final LivingEntity body;
		final long start;
		final Vec3 wind;
		boolean begun;

		Going(UUID owner, LivingEntity body, long start, Vec3 wind) {
			this.owner = owner;
			this.body = body;
			this.start = start;
			this.wind = wind;
		}
	}

	private static final List<Going> GOING = new ArrayList<>();

	private NadirSnap() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(NadirSnap::tick);
	}

	/** The snap: at the creature under the crosshair, or (at nothing) half of those round you. Returns the cooldown to start, or 0 if nothing was taken. */
	public static int snap(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.5)).add(0.0, -0.3, 0.0);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.2F, 1.9F);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.sendParticles(MagicFx.mote(0xE4DCFF, 1.2F), hand.x, hand.y, hand.z, 6, 0.05, 0.05, 0.05, 0.05);
		double angle = random.nextDouble() * Mth.TWO_PI;
		Vec3 wind = new Vec3(Math.cos(angle), 0.25, Math.sin(angle)).normalize().scale(WIND);
		long now = level.getGameTime();
		LivingEntity target = NadirAim.aimed(player, REACH);
		if (target != null) {
			GOING.add(new Going(player.getUUID(), target, now + DELAY, wind));
			return COOLDOWN;
		}
		List<LivingEntity> near = new ArrayList<>(NadirAim.around(player, player.position(), AREA));
		near.removeIf(e -> GOING.stream().anyMatch(g -> g.body == e));
		net.minecraft.util.Util.shuffle(near, random);
		int half = near.size() / 2 + (near.size() % 2 == 1 && random.nextBoolean() ? 1 : 0);
		for (int i = 0; i < half; i++) {
			GOING.add(new Going(player.getUUID(), near.get(i), now + Mth.nextInt(random, AREA_DELAY_MIN, AREA_DELAY_MAX), wind));
		}
		return half > 0 ? AREA_COOLDOWN : 0;
	}

	private static void tick(MinecraftServer server) {
		if (GOING.isEmpty()) {
			return;
		}
		for (Iterator<Going> it = GOING.iterator(); it.hasNext(); ) {
			Going g = it.next();
			LivingEntity body = g.body;
			if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long age = level.getGameTime() - g.start;
			if (age < 0) {
				continue;
			}
			if (!g.begun) {
				g.begun = true;
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
			}
			// it stands still while it goes
			body.setDeltaMovement(Vec3.ZERO);
			body.syncVelocity = true;
			if (body instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			float share = Mth.clamp(age / (float)CRUMBLE, 0.0F, 1.0F);
			dust(level, body, share, g.wind);
			if (age % 10 == 0) {
				level.playSound(null, body.getX(), body.getY() + body.getBbHeight() * (1.0 - share), body.getZ(), SoundEvents.SAND_STEP,
					SoundSource.HOSTILE, 0.8F, 0.5F + share * 0.3F);
			}
			if (age >= CRUMBLE) {
				it.remove();
				finish(server, level, g);
			}
		}
	}

	/** Dust off the body at the line it has crumbled down to ({@code share} of its height from the top), taken by the wind. */
	private static void dust(ServerLevel level, LivingEntity body, float share, Vec3 wind) {
		RandomSource random = level.getRandom();
		double y = body.getY() + body.getBbHeight() * (1.0 - share);
		double w = body.getBbWidth() * 0.5;
		for (int i = 0; i < PUFFS; i++) {
			double x = body.getX() + (random.nextDouble() * 2.0 - 1.0) * w;
			double z = body.getZ() + (random.nextDouble() * 2.0 - 1.0) * w;
			double dy = y + (random.nextDouble() - 0.5) * 0.3;
			// count 0: the "spread" is the puff's own drift, the wind's
			level.sendParticles(MagicFx.miasma(DUST, 1.0F + random.nextFloat()), x, dy, z, 0, wind.x, wind.y, wind.z, 1.0);
			if (random.nextInt(2) == 0) {
				level.sendParticles(ParticleTypes.ASH, x, dy, z, 0, wind.x * 2.0, wind.y, wind.z * 2.0, 1.0);
			}
		}
	}

	private static void finish(MinecraftServer server, ServerLevel level, Going g) {
		LivingEntity body = g.body;
		ServerPlayer owner = server.getPlayerList().getPlayer(g.owner);
		boolean leavesNothing = !(body instanceof Player);
		if (leavesNothing) {
			body.addTag(NO_CORPSE_TAG);
		}
		body.setInvulnerableTime(0);
		body.hurtServer(level, owner != null ? owner.damageSources().playerAttack(owner) : level.damageSources().magic(), DAMAGE);
		Vec3 c = body.getBoundingBox().getCenter();
		level.sendParticles(MagicFx.miasma(DUST, 2.2F), c.x, c.y, c.z, 0, g.wind.x, g.wind.y, g.wind.z, 1.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.0F, 0.4F);
		if (leavesNothing && body.isDeadOrDying()) {
			body.remove(Entity.RemovalReason.KILLED);
		} else if (leavesNothing) {
			body.removeTag(NO_CORPSE_TAG);
		}
	}
}
