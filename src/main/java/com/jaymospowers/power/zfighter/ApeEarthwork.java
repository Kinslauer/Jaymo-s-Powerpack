package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.gravity.GravityShockwave;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Great Ape tears the earth open. The ape already crushes what is under his feet with every stride
 * ({@link ZFighterLineage#footfall}), swings a charged fist ({@link ApeSmash}) and, held long enough, closes his hand on a
 * foe, a tree or (a slab of the ground, {@link com.jaymospowers.power.xenoform.forms.TowerSlab}, shared with Towerback)
 * and throws it ({@link ApeGrab}). This adds the stomp itself, on the keys the ape leaves free:
 * <ul>
 * <li><b>G, the earth-splitter</b>: he brings a foot down and a crack runs out ahead of him in a line, lumps of the ground
 * heaving up on either side of it, throwing everything along it into the air.</li>
 * <li><b>B, the ring-breaker</b>: both feet, and the ground breaks outward round him in widening rings of lumps.</li>
 * </ul>
 * Cracks fade and the lumps are lingering debris that crumbles away ({@link GravityShockwave#lump}); no block is lost.
 */
public final class ApeEarthwork {
	// ---- tuning ----
	/** The line: how far it runs (blocks, at full size), how fast (blocks a tick), how wide it hurts (blocks either side). */
	static final double LINE_LENGTH = 26.0, LINE_SPEED = 2.0, LINE_WIDTH = 1.8;
	/** A crack every this many blocks along the line, lumps every this many (alternating sides) and how far to the side. */
	static final int CRACK_EVERY = 3, LUMP_EVERY = 2;
	static final double LUMP_SIDE = 1.6;
	/** The ring: how far it spreads (blocks, at full size), how fast, the radius of its first lump ring and the gap between them. */
	static final double RING_RADIUS = 11.0, RING_SPEED = 1.1, RING_FIRST = 3.0, RING_STEP = 2.6, RING_BAND = 1.6;
	/** Lumps in a ring: one every this many blocks of its arc. */
	static final double RING_SPACING = 3.0;
	/** The blow on everything the quake runs through: the ape's attack damage times this, and the throw (outward, up). */
	static final float LINE_DAMAGE = 1.2F, RING_DAMAGE = 1.0F;
	static final double THROW = 0.8, LIFT = 0.75;
	/** Cooldowns (ticks): the G slot and the B slot of the HUD. */
	static final int LINE_COOLDOWN = 100, RING_COOLDOWN = 160;
	static final float SHAKE = 1.0F;
	static final double SHAKE_RANGE = 48.0;
	static final int COLOR = 0xB07A48;

	private static final class Quake {
		final ServerLevel level;
		final UUID by;
		final Vec3 origin;
		final Vec3 dir;
		final boolean line;
		final float size;
		final long start;
		final Set<UUID> hit = new HashSet<>();
		/** How far the front had got last tick, and the next lump ring's radius (ring). */
		double reached;
		double nextRing = RING_FIRST;
		int lumps;

		Quake(ServerLevel level, UUID by, Vec3 origin, Vec3 dir, boolean line, float size, long start) {
			this.level = level;
			this.by = by;
			this.origin = origin;
			this.dir = dir;
			this.line = line;
			this.size = size;
			this.start = start;
		}
	}

	private static final List<Quake> QUAKES = new ArrayList<>();

	private ApeEarthwork() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QUAKES.clear());
	}

	/** G ({@code line}) or B pressed as the Great Ape: the stomp, if he can and it is ready. */
	static void stomp(ServerPlayer player, ZFighterPower.Live live, boolean line) {
		int slot = line ? ZFighterState.BLAST : ZFighterState.STEP;
		if (live.apeSize < 0.5F || !player.onGround() || ZFighterLineage.crippled(player) || player.hasAttached(ApeRage.FIT)
			|| ApeGrab.grabbing(player) || !ZFighterPower.ready(player, slot)) {
			return;
		}
		ZFighterPower.cooldown(player, slot, line ? LINE_COOLDOWN : RING_COOLDOWN);
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 dir = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
		Vec3 origin = player.position();
		QUAKES.add(new Quake(level, player.getUUID(), origin, dir, line, Math.min(1.0F, live.apeSize), level.getGameTime()));
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.4F);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.5F, 0.5F);
		Shakes.heavy(player, origin, SHAKE_RANGE, SHAKE * live.apeSize);
		GroundCracks.crack(level, BlockPos.containing(origin.x, origin.y - 0.5, origin.z), level.getRandom(), 6);
		if (line) {
			double length = LINE_LENGTH * live.apeSize;
			MagicFx.wave(level, origin.add(0.0, 0.2, 0.0), dir, length, 2.5F, COLOR, 14);
		} else {
			MagicFx.ring(level, origin.add(0.0, 0.2, 0.0), (float)(RING_RADIUS * live.apeSize), COLOR);
		}
	}

	private static void tick() {
		Iterator<Quake> it = QUAKES.iterator();
		while (it.hasNext()) {
			Quake quake = it.next();
			ServerLevel level = quake.level;
			if (!(level.getEntity(quake.by) instanceof LivingEntity by) || !by.isAlive()) {
				it.remove();
				continue;
			}
			double max = (quake.line ? LINE_LENGTH : RING_RADIUS) * quake.size;
			double front = Math.min(max, (level.getGameTime() - quake.start + 1) * (quake.line ? LINE_SPEED : RING_SPEED));
			if (quake.line) {
				line(level, by, quake, front);
			} else {
				ring(level, by, quake, front);
			}
			quake.reached = front;
			if (front >= max) {
				it.remove();
			}
		}
	}

	/** The crack runs out along the line from where it had got to as far as {@code front}. */
	private static void line(ServerLevel level, LivingEntity by, Quake quake, double front) {
		Vec3 side = new Vec3(-quake.dir.z, 0.0, quake.dir.x);
		for (int d = (int)Math.floor(quake.reached) + 1; d <= (int)Math.floor(front); d++) {
			Vec3 at = quake.origin.add(quake.dir.scale(d));
			if (d % CRACK_EVERY == 0) {
				GroundCracks.crack(level, BlockPos.containing(at.x, at.y - 0.5, at.z), level.getRandom(), 3);
			}
			if (d % LUMP_EVERY == 0) {
				double s = (quake.lumps++ % 2 == 0 ? 1.0 : -1.0) * LUMP_SIDE;
				double angle = Math.atan2(side.z * Math.signum(s), side.x * Math.signum(s));
				GravityShockwave.lump(level, at.x + side.x * s, at.y, at.z + side.z * s, angle);
			}
			dust(level, at);
			for (LivingEntity victim : victims(level, by, quake, at, LINE_WIDTH)) {
				Vec3 away = victim.position().subtract(at).multiply(1.0, 0.0, 1.0);
				hurt(level, by, quake, victim, LINE_DAMAGE, away.lengthSqr() < 1.0E-4 ? side : away);
			}
		}
	}

	/** The ground breaks outward: rings of lumps as the front passes their radius, and everything it passes thrown. */
	private static void ring(ServerLevel level, LivingEntity by, Quake quake, double front) {
		while (quake.nextRing <= front) {
			double r = quake.nextRing;
			quake.nextRing += RING_STEP;
			int n = Math.max(6, (int)Math.round(Math.PI * 2.0 * r / RING_SPACING));
			double turn = level.getRandom().nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < n; i++) {
				double a = turn + Math.PI * 2.0 * i / n;
				GravityShockwave.lump(level, quake.origin.x + Math.cos(a) * r, quake.origin.y, quake.origin.z + Math.sin(a) * r, a);
			}
			if (level.getRandom().nextInt(2) == 0) {
				double a = turn + 0.5;
				GroundCracks.crack(level, BlockPos.containing(quake.origin.x + Math.cos(a) * r, quake.origin.y - 0.5, quake.origin.z + Math.sin(a) * r),
					level.getRandom(), 4);
			}
		}
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(quake.origin, quake.origin).inflate(front + RING_BAND, 3.0, front + RING_BAND),
			e -> eligible(by, quake, e))) {
			Vec3 flat = victim.position().subtract(quake.origin).multiply(1.0, 0.0, 1.0);
			double distance = flat.length();
			if (distance >= front - RING_BAND && distance <= front + RING_BAND && victim.onGround()) {
				hurt(level, by, quake, victim, RING_DAMAGE, flat.lengthSqr() < 1.0E-4 ? quake.dir : flat);
			}
		}
	}

	private static List<LivingEntity> victims(ServerLevel level, LivingEntity by, Quake quake, Vec3 at, double width) {
		return level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(width, 2.0, width), e -> eligible(by, quake, e));
	}

	private static boolean eligible(LivingEntity by, Quake quake, LivingEntity e) {
		return e != by && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !quake.hit.contains(e.getUUID()) && !by.isPassengerOfSameVehicle(e)
			&& !(e instanceof Player other && other.isCreative());
	}

	private static void hurt(ServerLevel level, LivingEntity by, Quake quake, LivingEntity victim, float share, Vec3 away) {
		quake.hit.add(victim.getUUID());
		float damage = (float)by.getAttributeValue(Attributes.ATTACK_DAMAGE) * share;
		if (victim.hurtServer(level, ZFighterLineage.blow(level, by), damage)) {
			ZFighterKi.push(victim, away, THROW, LIFT);
		}
	}

	private static void dust(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, at.x, at.y + 0.2, at.z, 3, 0.6, 0.1, 0.6, 0.02);
	}
}
