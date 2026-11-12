package com.jaymospowers.power.zfighter;

import com.jaymospowers.armory.ThrowingWeaponItem;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.ground.ThrownRocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ki in a thrown thing. A Z Fighter holding a throw drawn back for long enough pours ki into it (a loose rock held at its
 * full charge in the off hand, {@link ThrownRocks}; a held-back dagger, hatchet or spear, {@link ThrowingWeaponItem}):
 * {@link #KI_PER_TICK} a tick, up to {@link #MAX_TICKS}, a glow gathering at his hand. Let go, and the thrown thing
 * trails ki and bursts where it lands (or is stopped) in a small blast that hurts and throws whoever is near
 * ({@link #BASE_DAMAGE} to {@link #MAX_DAMAGE}, times his ki power). The blast never touches a block.
 * <p>
 * The ring: a charged rock let go <i>while sneaking</i> is not thrown but set circling him: up to {@link #RING_MAX} rocks
 * (the one thrown and what else is in the off-hand stack) orbit at {@link #RING_RADIUS} for {@link #RING_TICKS}, and each
 * bursts on the first enemy to come near it.
 */
public final class ZFighterKiThrow {
	// ---- tuning ----
	/** Ki a tick while charging, the most ticks of charge, the least that counts (and is let go as a charged throw). */
	static final float KI_PER_TICK = 0.5F;
	static final int MAX_TICKS = 60, MIN_TICKS = 12;
	/** A dagger is charged once held back this long (ticks), beyond the plain hold. */
	static final int DAGGER_FROM = 10;
	/** The blast: radius (blocks) and damage from a minimum charge to a full one. */
	static final double RADIUS_MIN = 1.8, RADIUS_MAX = 3.5;
	static final float BASE_DAMAGE = 4.0F, MAX_DAMAGE = 12.0F;
	static final double THROW = 0.9, LIFT = 0.4;
	/** A charged thing flies at most this long (ticks), and counts as stopped when it moves less than this a tick (blocks). */
	static final int FLIGHT_TICKS = 100;
	static final double STOPPED = 0.08;
	/** The ring: the most rocks, how far from him, how long it lasts (ticks), how fast it turns (radians a tick), how near an enemy sets one off. */
	static final int RING_MAX = 4, RING_TICKS = 160;
	static final double RING_RADIUS = 2.2, RING_TURN = 0.12, RING_TRIGGER = 1.4;
	/** The charge is forgotten when it has not been fed for this long (ticks). */
	static final int STALE = 4;

	private static final class Charge {
		int ticks;
		long last;
	}

	private record Flying(UUID owner, Entity thing, float power, long born, Vec3 last) {
	}

	private static final class Ring {
		final UUID owner;
		final ServerLevel level;
		final int rocks;
		final float power;
		final long born;
		final boolean[] gone;
		final int color;

		Ring(UUID owner, ServerLevel level, int rocks, float power, long born, int color) {
			this.owner = owner;
			this.level = level;
			this.rocks = rocks;
			this.power = power;
			this.born = born;
			this.gone = new boolean[rocks];
			this.color = color;
		}
	}

	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	private static final List<Flying> FLYING = new ArrayList<>();
	private static final List<Ring> RINGS = new ArrayList<>();
	/** A flying thing's last position, kept beside {@link #FLYING} (records are immutable). */
	private static final Map<Entity, Vec3> WHERE = new HashMap<>();

	private ZFighterKiThrow() {
	}

	/** Called once from {@link ZFighterPower}'s setup. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> flight());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CHARGES.clear();
			FLYING.clear();
			RINGS.clear();
			WHERE.clear();
		});
	}

	/** Every tick, per Z Fighter: pour ki into a throw held back, or let a stale charge go. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Charge charge = CHARGES.get(id);
		if (charging(player)) {
			if (charge == null) {
				charge = new Charge();
				CHARGES.put(id, charge);
			}
			if (charge.ticks < MAX_TICKS && (player.isCreative() || live.ki >= KI_PER_TICK) && ZFighterPower.spend(player, live, KI_PER_TICK)) {
				charge.ticks++;
				charge.last = now;
				glow(player, charge);
			} else if (charge.ticks >= MAX_TICKS) {
				charge.last = now;
				glow(player, charge);
			}
		} else if (charge != null && now - charge.last > STALE) {
			CHARGES.remove(id);
		}
	}

	static void forget(ServerPlayer player) {
		CHARGES.remove(player.getUUID());
	}

	/** Whether he is holding a throw drawn back: a rock at its full charge, or a dagger held back a while. */
	private static boolean charging(ServerPlayer player) {
		if (player.isUsingItem() && player.getUseItem().getItem() instanceof ThrowingWeaponItem) {
			return player.getTicksUsingItem() >= DAGGER_FROM;
		}
		Integer throwing = player.getAttached(ThrownRocks.THROW);
		return throwing != null && (throwing & 3) == ThrownRocks.READY && (throwing & ThrownRocks.MAIN_ARM) == 0;
	}

	private static float power(Charge charge) {
		return Math.min(1.0F, charge.ticks / (float)MAX_TICKS);
	}

	/** A glow gathers at the throwing hand. */
	private static void glow(ServerPlayer player, Charge charge) {
		ServerLevel level = player.level();
		float power = power(charge);
		int color = ZFighterPower.kiColor(player);
		Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).add(0.0, -0.45, 0.0);
		if (charge.ticks % 2 == 0) {
			level.sendParticles(MagicFx.mote(color, 0.8F + power), hand.x, hand.y, hand.z, 1, 0.12, 0.12, 0.12, 0.0);
		}
		if (charge.ticks % 10 == 1) {
			MagicFx.orb(level, hand, 0.25F + 0.35F * power, color, 14);
		}
	}

	/**
	 * A rock or dagger has just left {@code player}'s hand ({@code thing}); {@code remaining} is what is left of the stack it
	 * came from. If he had charged it, it is lit with ki and watched (or, sneaking with a rock, set circling him).
	 */
	public static void thrown(ServerPlayer player, Entity thing, ItemStack remaining) {
		Charge charge = CHARGES.remove(player.getUUID());
		long now = player.level().getGameTime();
		if (charge == null || charge.ticks < MIN_TICKS || now - charge.last > STALE) {
			return;
		}
		float power = power(charge);
		ServerLevel level = player.level();
		if (player.isShiftKeyDown() && thing instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball) {
			int extra = Math.min(RING_MAX - 1, remaining.getCount());
			if (extra > 0) {
				remaining.shrink(extra);
			}
			thing.discard();
			RINGS.add(new Ring(player.getUUID(), level, 1 + extra, power, now, ZFighterPower.kiColor(player)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
			return;
		}
		FLYING.add(new Flying(player.getUUID(), thing, power, now, thing.position()));
		WHERE.put(thing, thing.position());
		level.playSound(null, thing.getX(), thing.getY(), thing.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
	}

	private static void flight() {
		Iterator<Flying> it = FLYING.iterator();
		while (it.hasNext()) {
			Flying f = it.next();
			Entity thing = f.thing();
			if (!(thing.level() instanceof ServerLevel level)) {
				it.remove();
				WHERE.remove(thing);
				continue;
			}
			long age = level.getGameTime() - f.born();
			Vec3 last = WHERE.getOrDefault(thing, f.last());
			if (thing.isRemoved()) {
				burst(level, f, last);
				it.remove();
				WHERE.remove(thing);
				continue;
			}
			Vec3 here = thing.position();
			double moved = here.subtract(last).length();
			WHERE.put(thing, here);
			Player owner = level.getPlayerByUUID(f.owner());
			int color = owner instanceof ServerPlayer sp ? ZFighterPower.kiColor(sp) : 0xFFD54A;
			level.sendParticles(MagicFx.mote(color, 1.0F + f.power()), here.x, here.y, here.z, 2, 0.05, 0.05, 0.05, 0.0);
			if (age % 3 == 0) {
				MagicFx.orb(level, here, 0.2F + 0.2F * f.power(), color, 6);
			}
			if (age > 3 && moved < STOPPED || age > FLIGHT_TICKS) {
				burst(level, f, here);
				it.remove();
				WHERE.remove(thing);
			}
		}
		Iterator<Ring> rings = RINGS.iterator();
		while (rings.hasNext()) {
			if (!ring(rings.next())) {
				rings.remove();
			}
		}
	}

	/** The small ki blast: whoever stands within it is hurt and thrown out. Never a block. */
	private static void burst(ServerLevel level, Flying f, Vec3 at) {
		Player owner = level.getPlayerByUUID(f.owner());
		blast(level, owner, at, f.power(), owner instanceof ServerPlayer sp ? ZFighterPower.kiColor(sp) : 0xFFD54A);
	}

	private static void blast(ServerLevel level, Player owner, Vec3 at, float power, int color) {
		double radius = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * power;
		float damage = BASE_DAMAGE + (MAX_DAMAGE - BASE_DAMAGE) * power;
		if (owner instanceof ServerPlayer sp) {
			damage *= ZFighterPower.kiPower(sp, ZFighterPower.live(sp));
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> foe(owner, e))) {
			Vec3 center = e.getBoundingBox().getCenter();
			double d = center.distanceTo(at);
			if (d > radius + e.getBbWidth() * 0.5) {
				continue;
			}
			float falloff = (float)(1.0 - 0.5 * Math.min(1.0, d / radius));
			e.setInvulnerableTime(0);
			var source = owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner);
			if (e.hurtServer(level, source, damage * falloff)) {
				ZFighterKi.push(e, center.subtract(at), THROW, LIFT);
			}
		}
		MagicFx.blast(level, at, (float)radius, color);
		MagicFx.ring(level, at, (float)radius, color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F + power, 1.4F);
		Shakes.around(level, at, 24.0, 0.3F + 0.4F * power);
	}

	/** Anyone but the thrower and his side; players only where he may harm them. */
	private static boolean foe(Player owner, LivingEntity e) {
		if (!e.isAlive() || e.isSpectator() || e instanceof ArmorStand || owner != null && (e == owner || owner.isAlliedTo(e))) {
			return false;
		}
		return !(e instanceof Player other) || !other.isCreative() && (owner == null || owner.canHarmPlayer(other));
	}

	/** The ring turns round him; each rock bursts on the first enemy near it. False when it is over. */
	private static boolean ring(Ring ring) {
		ServerLevel level = ring.level;
		long age = level.getGameTime() - ring.born;
		Player owner = level.getPlayerByUUID(ring.owner);
		if (age > RING_TICKS || owner == null || !owner.isAlive() || owner.level() != level) {
			return false;
		}
		boolean any = false;
		for (int i = 0; i < ring.rocks; i++) {
			if (ring.gone[i]) {
				continue;
			}
			any = true;
			double a = age * RING_TURN + Math.PI * 2.0 * i / ring.rocks;
			Vec3 at = owner.position().add(Math.cos(a) * RING_RADIUS, 1.0 + 0.25 * Math.sin(a * 2.0), Math.sin(a) * RING_RADIUS);
			level.sendParticles(MagicFx.mote(ring.color, 1.2F), at.x, at.y, at.z, 1, 0.04, 0.04, 0.04, 0.0);
			if (age % 8 == 0) {
				MagicFx.orb(level, at, 0.3F, ring.color, 10);
			}
			boolean near = !level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RING_TRIGGER),
				e -> foe(owner, e) && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == owner || e instanceof Player)
					&& e.getBoundingBox().getCenter().distanceTo(at) <= RING_TRIGGER + e.getBbWidth() * 0.5).isEmpty();
			if (near) {
				ring.gone[i] = true;
				blast(level, owner, at, ring.power, ring.color);
			}
		}
		return any;
	}
}
