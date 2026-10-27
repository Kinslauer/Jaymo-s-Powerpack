package com.jaymospowers.power.zfighter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Ring of Orbs, a quick technique (G): {@link #MIN_ORBS} to {@link #MAX_ORBS} small ki orbs
 * appear in a ring round the target (the locked one, else the one in view), hang for {@link #HANG_TICKS} (a second),
 * then all close in at once. Each that arrives hits for a little; if the target has left the ring's middle by then they
 * all miss and burst on nothing. The orbs are the same ki orbs the Ki Blast and Barrage draw ({@link ZFighterKi#orb}).
 * More Ki Power, more orbs and a little more damage each.
 */
final class ZFighterRingOfOrbs {
	static final float KI = 30.0F;
	static final int COOLDOWN = 300;
	static final int MIN_ORBS = 8, MAX_ORBS = 12;
	/** How far off the target may be (blocks), the ring's radius, and each orb's size. */
	static final double RANGE = 18.0, RING_RADIUS = 3.2, ORB_SIZE = 0.45;
	/** Ticks the ring hangs, and ticks the orbs take to close in. */
	static final int HANG_TICKS = 20, CLOSE_TICKS = 6;
	/** Damage each orb does (times Ki Power), and the poise each wears. */
	static final float DAMAGE_PER_ORB = 1.4F;
	static final float POISE_PER_ORB = 1.5F;
	/** The target must be this near the ring's middle (blocks sideways, blocks up or down) when the orbs close in. */
	static final double MIDDLE = 1.7, MIDDLE_HEIGHT = 2.0;
	/** The ring turns slowly while it hangs (radians a tick). */
	static final double TURN = 0.06;

	private static final class Ring {
		final ServerLevel level;
		final UUID owner;
		final UUID target;
		final Vec3 centre;
		final int count;
		final float damage;
		final int color;
		final int base;
		int age;

		Ring(ServerLevel level, UUID owner, UUID target, Vec3 centre, int count, float damage, int color, int base) {
			this.level = level;
			this.owner = owner;
			this.target = target;
			this.centre = centre;
			this.count = count;
			this.damage = damage;
			this.color = color;
			this.base = base;
		}
	}

	private static final List<Ring> RINGS = new ArrayList<>();
	private static int serial;

	private ZFighterRingOfOrbs() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterRingOfOrbs::tick);
	}

	static void clear() {
		RINGS.clear();
	}

	/** G: the ring forms round the mark. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		LivingEntity foe = ZFighterArts.find(player, RANGE);
		if (foe == null) {
			ZFighterArts.noFoe(player);
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		float kiPower = ZFighterPower.kiPower(player, live);
		int count = Mth.clamp(MIN_ORBS + Math.round((kiPower - 1.0F) * 8.0F), MIN_ORBS, MAX_ORBS);
		Vec3 centre = foe.getBoundingBox().getCenter();
		int color = ZFighterArts.color(live);
		RINGS.add(new Ring(level, player.getUUID(), foe.getUUID(), centre, count, DAMAGE_PER_ORB * kiPower, color, 0x50000000 | (serial++ & 0xFFFF) << 4));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.6F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.8F);
		}
		return COOLDOWN;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Ring> it = RINGS.iterator(); it.hasNext(); ) {
			Ring ring = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(ring.owner);
			if (owner == null || !owner.isAlive() || owner.level() != ring.level || !advance(owner, ring)) {
				it.remove();
			}
		}
	}

	/** One tick of the ring: false once it has closed. */
	private static boolean advance(ServerPlayer owner, Ring ring) {
		ServerLevel level = ring.level;
		ring.age++;
		boolean closing = ring.age > HANG_TICKS;
		// how far in the orbs are (1 at the ring, 0 at its middle), eased so they start slow and arrive fast
		double closed = closing ? Math.min(1.0, (ring.age - HANG_TICKS) / (double)CLOSE_TICKS) : 0.0;
		double radius = RING_RADIUS * (1.0 - closed * closed);
		double grow = Math.min(1.0, ring.age / 5.0);
		double size = ORB_SIZE * (0.3 + 0.7 * grow);
		double spin = ring.age * TURN;
		DustParticleOptions dust = new DustParticleOptions(ring.color, 1.0F);
		for (int i = 0; i < ring.count; i++) {
			double a = spin + i * Math.PI * 2.0 / ring.count;
			Vec3 at = ring.centre.add(Math.cos(a) * radius, 0.0, Math.sin(a) * radius);
			ZFighterKi.orb(level, ring.base + i, at, size, ring.color);
			if (ring.age % 2 == 0) {
				level.sendParticles(dust, true, false, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		if (ring.age == 1) {
			level.sendParticles(ParticleTypes.ENCHANT, true, false, ring.centre.x, ring.centre.y, ring.centre.z, 20, RING_RADIUS * 0.5, 0.5, RING_RADIUS * 0.5, 0.3);
		}
		if (closed < 1.0) {
			return true;
		}
		land(owner, ring);
		return false;
	}

	/** The orbs arrive: each hits the target if it still stands in the ring's middle; if not they burst on nothing. */
	private static void land(ServerPlayer owner, Ring ring) {
		ServerLevel level = ring.level;
		Vec3 centre = ring.centre;
		LivingEntity target = level.getEntity(ring.target) instanceof LivingEntity living && living.isAlive() ? living : null;
		boolean hit = target != null && target.level() == level;
		if (hit) {
			Vec3 now = target.getBoundingBox().getCenter();
			double sideways = Math.hypot(now.x - centre.x, now.z - centre.z);
			hit = sideways <= MIDDLE && Math.abs(now.y - centre.y) <= MIDDLE_HEIGHT;
		}
		DustParticleOptions dust = new DustParticleOptions(ring.color, 1.6F);
		if (hit) {
			var source = ZFighterKi.source(owner);
			for (int i = 0; i < ring.count; i++) {
				target.setInvulnerableTime(0);
				target.hurtServer(level, source, ring.damage);
				ZFighterPoise.wear(target, POISE_PER_ORB);
			}
			ZFighterKi.push(target, target.position().subtract(owner.position()), 0.25, 0.1);
			Vec3 at = target.getBoundingBox().getCenter();
			level.sendParticles(dust, true, false, at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.2);
			level.sendParticles(ParticleTypes.CRIT, true, false, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.4);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
			com.jaymospowers.core.Shakes.around(level, at, 10.0, 0.25F);
		} else {
			level.sendParticles(dust, true, false, centre.x, centre.y, centre.z, 16, 0.3, 0.3, 0.3, 0.1);
			level.sendParticles(ParticleTypes.SMOKE, true, false, centre.x, centre.y, centre.z, 6, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}
}
