package com.jaymospowers.power.wind;

import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.armory.ThrownWeapon;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Three throws for a war hammer. A Stormbound Wind user holding a hammer (the Armory's warhammer, any hammer or mace)
 * holds the hurl key to wind up; sneaking while winding up switches the throw:
 * <ul>
 * <li><b>Lightning</b>: it flies as a thrown weapon, and where it lands (or strikes) a bolt comes down (WindBolts' own strike).</li>
 * <li><b>Ride</b>: you are carried with it. It bursts where it lands, then bounces from foe to foe while you stay off the
 * ground; sneak to step off. It lies where the chain ends.</li>
 * <li><b>Spin</b>: it stays in your hand, spun overhead for as long as you hold the key. Double-tap Jump to fly on it for
 * a short time, at a cost in hunger. It falls when the flight ends, and the fall hurts (fall damage applies).</li>
 * </ul>
 * A thrown hammer is bound to the player (see {@link StormRecall}) and lies where it lands until it is called home.
 */
final class StormHammer {
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 1, SPIN_SLOT = 2;
	static final int LIGHTNING = 0, RIDE = 1, SPIN = 2, MODES = 3;
	/** Held at least this long to throw (ticks), and the ticks a throw's full strength takes. */
	static final int MIN_HOLD = 6, FULL_HOLD = 20;
	static final float SPEED_MIN = 1.3F, SPEED_MAX = 2.4F;
	static final int COOLDOWN = 40, SPIN_COOLDOWN = 100;
	/** What a thrown hammer does where it strikes a creature, and the bolt where it lands (damage, reach). */
	static final float HAMMER_DAMAGE = 9.0F, BOLT_DAMAGE = 8.0F;
	static final double BOLT_REACH = 3.0;
	/** A hammer left to land is watched this long (ticks). */
	static final int WATCH_TICKS = 1200;
	/** Ride: how fast it flies (blocks/tick), how near a target counts as hit, how far the next foe may be, how many bursts, the burst's reach and damage. */
	static final double RIDE_SPEED = 1.5, ARRIVE = 1.8, AIM_RANGE = 36.0, BOUNCE_RANGE = 14.0, BURST_RADIUS = 3.5;
	static final int MAX_BURSTS = 5, RIDE_TICKS = 400;
	static final float BURST_DAMAGE = 8.0F;
	/** Spin: ticks to wind up before it spins, the window for the double-tap on Jump, the flight's length, its speed, its hunger a tick. */
	static final int SPIN_UP = 8, DOUBLE_TAP = 10, FLY_TICKS = 100;
	static final double FLY_SPEED = 0.85;
	static final float SPIN_EXHAUSTION = 0.03F, FLY_EXHAUSTION = 0.12F;
	static final int MIN_FOOD = 6;
	/** Falls hurt in full for this long after a ride or a flight ends (ticks). */
	static final int HARD_FALL_TICKS = 300;
	static final float THROW_EXHAUSTION = 3.0F;

	private static final class Hold {
		long since;
		boolean sneaked;
		boolean spinning, flying;
		boolean jumped;
		long lastJump = -1000L;
		int flyLeft;
	}

	private static final class Ride {
		UUID weapon;
		int bursts;
		long since;
		@Nullable UUID target;
		Vec3 point;
		final Set<UUID> hit = new HashSet<>();
	}

	private record Watch(UUID weapon, long until) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();
	private static final Map<UUID, Integer> MODE = new HashMap<>();
	private static final Map<UUID, Ride> RIDES = new HashMap<>();
	private static final Map<UUID, List<Watch>> WATCHES = new HashMap<>();

	private StormHammer() {
	}

	/** The Armory's warhammer, a mace, or any hammer or mace by kind. */
	static boolean hammer(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (HammerSlam.isWarhammer(stack.getItem()) || stack.is(Items.MACE)) {
			return true;
		}
		WeaponKind kind = WeaponKind.of(stack);
		return kind == WeaponKind.HAMMER || kind == WeaponKind.MACE;
	}

	static int mode(ServerPlayer player) {
		return MODE.getOrDefault(player.getUUID(), LIGHTNING);
	}

	static boolean charging(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	static boolean spinning(ServerPlayer player) {
		Hold h = HOLDS.get(player.getUUID());
		return h != null && (h.spinning || h.flying);
	}

	static boolean riding(ServerPlayer player) {
		return RIDES.containsKey(player.getUUID());
	}

	static void press(ServerPlayer player) {
		if (HOLDS.containsKey(player.getUUID()) || RIDES.containsKey(player.getUUID())) {
			return;
		}
		if (!hammer(player.getMainHandItem())) {
			StormNet.say(player, "no_hammer");
			return;
		}
		int slot = mode(player) == SPIN ? SPIN_SLOT : SLOT;
		if (!PickKit.ready(TABLE, player, slot)) {
			StormNet.say(player, "cooling");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			StormNet.say(player, "hungry");
			return;
		}
		Hold h = new Hold();
		h.since = player.level().getGameTime();
		h.sneaked = player.isShiftKeyDown();
		HOLDS.put(player.getUUID(), h);
	}

	static void release(ServerPlayer player) {
		Hold h = HOLDS.remove(player.getUUID());
		if (h == null) {
			return;
		}
		ServerLevel level = player.level();
		long held = level.getGameTime() - h.since;
		if (h.spinning || h.flying) {
			endSpin(player, h);
			return;
		}
		if (held < MIN_HOLD || !hammer(player.getMainHandItem()) || mode(player) == SPIN) {
			return;
		}
		float speed = SPEED_MIN + (SPEED_MAX - SPEED_MIN) * Math.min(1.0F, (held - MIN_HOLD) / (float)(FULL_HOLD - MIN_HOLD));
		throwIt(player, level, speed, mode(player) == RIDE);
	}

	// ---------------------------------------------------------------- the throw

	private static void throwIt(ServerPlayer player, ServerLevel level, float speed, boolean ride) {
		ItemStack thrown = player.getMainHandItem().consumeAndReturn(1, player);
		StormRecall.bind(player, thrown);
		ThrownWeapon w = Projectile.spawnProjectileFromRotation((l, owner, stack) -> new ThrownWeapon(l, owner, stack), level, thrown, player, 0.0F,
			ride ? 0.0F : speed, 0.5F);
		w.lasting();
		w.hurled(HAMMER_DAMAGE, false);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		player.causeFoodExhaustion(THROW_EXHAUSTION);
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		if (!ride) {
			WATCHES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Watch(w.getUUID(), level.getGameTime() + WATCH_TICKS));
			return;
		}
		w.setDeltaMovement(Vec3.ZERO);
		w.setNoGravity(true);
		w.setNoPhysics(true);
		w.spent();
		w.setPos(player.getX(), player.getY(), player.getZ());
		if (!player.startRiding(w, true, true)) {
			w.discard();
			player.getInventory().add(thrown);
			return;
		}
		Ride r = new Ride();
		r.weapon = w.getUUID();
		r.since = level.getGameTime();
		Vec3 eye = player.getEyePosition();
		LivingEntity aimed = PickKit.aimed(player, AIM_RANGE);
		if (aimed != null) {
			r.target = aimed.getUUID();
			r.point = aimed.getBoundingBox().getCenter();
		} else {
			Vec3 end = eye.add(player.getLookAngle().scale(AIM_RANGE));
			var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			r.point = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getLookAngle().scale(0.6));
		}
		RIDES.put(player.getUUID(), r);
		StormNet.hardFall(player, HARD_FALL_TICKS);
	}

	// ---------------------------------------------------------------- every tick

	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Hold h = HOLDS.get(player.getUUID());
		if (h != null) {
			holdTick(player, level, h, now);
		}
		Ride r = RIDES.get(player.getUUID());
		if (r != null) {
			rideTick(player, level, r, now);
		}
		List<Watch> watches = WATCHES.get(player.getUUID());
		if (watches != null) {
			watches.removeIf(w -> watchTick(player, level, w, now));
			if (watches.isEmpty()) {
				WATCHES.remove(player.getUUID());
			}
		}
	}

	private static void holdTick(ServerPlayer player, ServerLevel level, Hold h, long now) {
		if (!hammer(player.getMainHandItem()) || player.isSleeping()) {
			HOLDS.remove(player.getUUID());
			if (h.spinning || h.flying) {
				endSpin(player, h);
			}
			return;
		}
		boolean sneak = player.isShiftKeyDown();
		if (sneak && !h.sneaked && !h.spinning && !h.flying) {
			MODE.put(player.getUUID(), (mode(player) + 1) % MODES);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
			StormNet.say(player, "mode_" + mode(player));
		}
		h.sneaked = sneak;
		boolean jump = player.getLastClientInput().jump();
		boolean tapped = jump && !h.jumped;
		h.jumped = jump;
		if (!h.spinning && !h.flying) {
			if (mode(player) == SPIN && now - h.since >= SPIN_UP) {
				h.spinning = true;
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0F, 0.8F);
			} else if (mode(player) != SPIN && now % 4 == 0) {
				// winding up: the head crackles
				Vec3 over = player.position().add(0.0, 2.0, 0.0);
				level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.7F), over.x, over.y, over.z, 2, 0.3, 0.2, 0.3, 0.0);
			}
			return;
		}
		if (h.spinning) {
			spinTick(player, level, h, now, tapped);
		} else {
			flyTick(player, level, h, now);
		}
	}

	/** Spun overhead: a ring of the hammer's wind above the head, and a double-tap on Jump takes off. */
	private static void spinTick(ServerPlayer player, ServerLevel level, Hold h, long now, boolean tapped) {
		player.causeFoodExhaustion(SPIN_EXHAUSTION);
		if (now % 2 == 0) {
			MagicFx.ring(level, player.position().add(0.0, 2.2, 0.0), new Vec3(0.0, 1.0, 0.0), 1.1F, StormDraw.BOLT, 4);
		}
		if (Soundscape.due(player, "storm_spin", 5, 9)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS,
				Soundscape.Layer.of(0.5F, 0.8F, 0.6F, 0.9F, SoundEvents.BREEZE_WHIRL, SoundEvents.BREEZE_IDLE_AIR));
		}
		if (tapped) {
			if (now - h.lastJump <= DOUBLE_TAP) {
				h.spinning = false;
				h.flying = true;
				h.flyLeft = FLY_TICKS;
				h.lastJump = -1000L;
				StormNet.hardFall(player, HARD_FALL_TICKS);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
				MagicFx.blast(level, player.position().add(0.0, 2.0, 0.0), 1.5F, StormDraw.GLOW);
			} else {
				h.lastJump = now;
			}
		}
	}

	/** Carried along the look on the hammer's wind until the time, the hunger or the key runs out. */
	private static void flyTick(ServerPlayer player, ServerLevel level, Hold h, long now) {
		boolean hungry = !player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD;
		boolean landed = player.onGround() && h.flyLeft < FLY_TICKS - 4;
		if (--h.flyLeft <= 0 || hungry || landed || player.isInWater() || player.horizontalCollision && h.flyLeft < FLY_TICKS - 4) {
			HOLDS.remove(player.getUUID());
			endSpin(player, h);
			return;
		}
		player.causeFoodExhaustion(FLY_EXHAUSTION);
		player.setDeltaMovement(player.getLookAngle().scale(FLY_SPEED));
		player.syncVelocity = true;
		player.resetFallDistance();
		if (now % 2 == 0) {
			level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
		}
		if (now % 5 == 0) {
			MagicFx.ring(level, player.position().add(0.0, 0.3, 0.0), new Vec3(0.0, 1.0, 0.0), 1.4F, StormDraw.GLOW, 6);
		}
	}

	private static void endSpin(ServerPlayer player, Hold h) {
		boolean flew = h.flying;
		h.spinning = false;
		h.flying = false;
		HOLDS.remove(player.getUUID());
		PickKit.spend(TABLE, player, SPIN_SLOT, SPIN_COOLDOWN);
		if (flew) {
			// it falls from here: whatever the drop, it hurts
			player.resetFallDistance();
			StormNet.hardFall(player, HARD_FALL_TICKS);
		}
	}

	// ---------------------------------------------------------------- lightning where it lands

	/** True once the watch is over. */
	private static boolean watchTick(ServerPlayer player, ServerLevel level, Watch watch, long now) {
		if (now >= watch.until) {
			return true;
		}
		Entity e = level.getEntity(watch.weapon);
		if (e == null) {
			return false;
		}
		if (!(e instanceof ThrownWeapon w) || !w.isAlive()) {
			return true;
		}
		if (!w.lying() && !w.struck()) {
			return false;
		}
		WindBolts.strike(player, w.position(), BOLT_DAMAGE, BOLT_REACH);
		level.playSound(null, w.getX(), w.getY(), w.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.3F);
		return true;
	}

	// ---------------------------------------------------------------- ride

	private static void rideTick(ServerPlayer player, ServerLevel level, Ride r, long now) {
		Entity found = level.getEntity(r.weapon);
		if (!(found instanceof ThrownWeapon w) || !w.isAlive() || player.getVehicle() != w || now - r.since > RIDE_TICKS) {
			endRide(player, found instanceof ThrownWeapon lost ? lost : null);
			return;
		}
		Vec3 pos = w.position();
		if (r.target != null) {
			Entity t = level.getEntity(r.target);
			if (t instanceof LivingEntity living && living.isAlive()) {
				r.point = living.getBoundingBox().getCenter();
			}
		}
		Vec3 to = r.point.subtract(pos);
		double dist = to.length();
		if (dist < ARRIVE) {
			burst(player, level, w, r, pos);
			return;
		}
		Vec3 step = to.normalize().scale(Math.min(RIDE_SPEED, dist));
		var wall = level.clip(new ClipContext(pos, pos.add(step.scale(2.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w));
		if (wall.getType() != HitResult.Type.MISS) {
			burst(player, level, w, r, pos);
			return;
		}
		w.setDeltaMovement(step);
		w.needsSync = true;
		player.causeFoodExhaustion(0.04F);
		if (now % 2 == 0) {
			level.sendParticles(MagicFx.spark(StormDraw.BOLT, 0.9F), pos.x, pos.y, pos.z, 3, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/** It lands: the burst, then on to the nearest foe it hasn't struck, or the end of the ride. */
	private static void burst(ServerPlayer player, ServerLevel level, ThrownWeapon w, Ride r, Vec3 at) {
		MagicFx.blast(level, at, (float)BURST_RADIUS, StormDraw.BOLT);
		MagicFx.ring(level, at, (float)BURST_RADIUS, StormDraw.GLOW);
		DamageSource source = level.damageSources().playerAttack(player);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, BURST_RADIUS), c -> PickKit.foe(player, c))) {
			if (e.position().distanceTo(at) <= BURST_RADIUS + 0.5 && r.hit.add(e.getUUID())) {
				e.hurtServer(level, source, BURST_DAMAGE * WindBolts.sky(level));
				e.push(0.0, 0.5, 0.0);
				e.syncVelocity = true;
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2F, 1.2F);
		Shakes.around(level, at, 20.0, 0.9F);
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		if (++r.bursts < MAX_BURSTS) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, PickKit.around(at, BOUNCE_RANGE), c -> PickKit.foe(player, c) && !r.hit.contains(c.getUUID()))) {
				Vec3 centre = e.getBoundingBox().getCenter();
				double d = centre.distanceToSqr(at);
				if (d < best && level.clip(new ClipContext(at, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, w)).getType() == HitResult.Type.MISS) {
					best = d;
					next = e;
				}
			}
		}
		if (next == null) {
			endRide(player, w);
			return;
		}
		r.target = next.getUUID();
		r.point = next.getBoundingBox().getCenter();
	}

	/** The ride is over: the rider steps off where the hammer is, and it lies there. */
	private static void endRide(ServerPlayer player, @Nullable ThrownWeapon w) {
		RIDES.remove(player.getUUID());
		player.stopRiding();
		StormNet.hardFall(player, HARD_FALL_TICKS);
		if (w != null) {
			w.setNoPhysics(false);
			w.setNoGravity(false);
			w.setDeltaMovement(Vec3.ZERO);
			w.needsSync = true;
		}
	}

	/** The player left: their chosen throw is forgotten too. */
	static void disconnected(ServerPlayer player) {
		MODE.remove(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		Hold h = HOLDS.remove(player.getUUID());
		if (h != null && (h.spinning || h.flying)) {
			StormNet.hardFall(player, HARD_FALL_TICKS);
		}
		Ride r = RIDES.remove(player.getUUID());
		if (r != null) {
			player.stopRiding();
			if (player.level().getEntity(r.weapon) instanceof ThrownWeapon w) {
				w.setNoPhysics(false);
				w.setNoGravity(false);
			}
		}
		WATCHES.remove(player.getUUID());
	}
}
