package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Velox, the raptor speedster (scanned from a horse): a little smaller than you, 20 health, 80% faster, lands softly,
 * and runs across still water while sprinting ({@link #runsOnWater}).
 * <ul>
 * <li>R (hold), Speed Burst: while held, up to {@link #BURST_MAX_TICKS}, you are far faster, step up whole blocks and
 * run straight up any wall you meet (up to {@link #WALL_RUN_TICKS}).</li>
 * <li>G, Rapid Punches: {@link #PUNCH_HITS} blows on whoever you aim at, one every {@link #PUNCH_SPACING} ticks.</li>
 * <li>V, Cyclone: sweeps everything within {@link #CYCLONE_RADIUS} round you in a tight circle and lifts it.</li>
 * <li>B, Chain Strike: flicker between up to {@link #CHAIN_TARGETS} foes within {@link #CHAIN_RANGE}, striking each.</li>
 * <li>C (ascended), Standstill: for {@link #STANDSTILL_TICKS} everything living within {@link #STANDSTILL_RADIUS} crawls
 * and every projectile hangs in the air; C again every {@link #STANDSTILL_COOLDOWN}.</li>
 * </ul>
 * Corrupted: when a burst ends your legs won't stop (up to {@link #CORRUPT_RUNAWAY_TICKS}, or until a wall or a creature).
 */
public final class Velox extends XenoForm {
	// ---- tuning ----
	/** Speed Burst: the longest it lasts held, its cooldown, the speed bonus (a share of your speed) and the extra step height. */
	public static final int BURST_MAX_TICKS = 120, BURST_COOLDOWN = 60;
	public static final double BURST_SPEED = 1.2, BURST_STEP = 1.0;
	public static final int WALL_RUN_TICKS = 40;
	public static final double WALL_RUN_CLIMB = 0.32;
	/** Rapid Punches: blows, reach, ticks between blows, damage each, and the cooldown. */
	public static final int PUNCH_HITS = 6, PUNCH_SPACING = 2, PUNCH_COOLDOWN = 100;
	public static final double PUNCH_REACH = 3.5;
	public static final float PUNCH_DAMAGE = 2.0F;
	/** Cyclone: how long it spins, how wide, the damage (once, at the start), the cooldown, how fast things are swept round, and how high they rise. */
	public static final int CYCLONE_TICKS = 30, CYCLONE_COOLDOWN = 200;
	public static final double CYCLONE_RADIUS = 6.0, CYCLONE_SWEEP = 0.55, CYCLONE_LIFT = 0.22, CYCLONE_MAX_RISE = 3.5;
	public static final float CYCLONE_DAMAGE = 3.0F;
	public static final int CHAIN_COOLDOWN = 240, CHAIN_TARGETS = 5, CHAIN_SPACING = 3;
	public static final double CHAIN_RANGE = 8.0;
	public static final float CHAIN_DAMAGE = 5.0F;
	/** Standstill: how long it holds, C's cooldown between uses, how far it reaches, and how hard it drags (Slowness level, share of velocity kept, share of a projectile's kept). */
	public static final int STANDSTILL_TICKS = 120, STANDSTILL_COOLDOWN = 600;
	public static final double STANDSTILL_RADIUS = 24.0, STANDSTILL_DRAG = 0.1, STANDSTILL_PROJECTILE = 0.05;
	public static final int STANDSTILL_SLOWNESS = 6;
	/** Corrupted: a run that won't stop goes at this speed for at most this long, and hits for this much. */
	public static final double CORRUPT_RUNAWAY_SPEED = 1.1;
	public static final int CORRUPT_RUNAWAY_TICKS = 60;
	public static final float CORRUPT_RUNAWAY_DAMAGE = 4.0F;

	private static final Identifier SAFE_FALL_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_fall");
	private static final Identifier BURST_SPEED_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_burst");
	private static final Identifier BURST_STEP_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_burst_step");

	public Velox() {
		super("blur", 0.9F, 20.0F, 4.0, 0.8, 0.0, 0.0, Items.SUGAR, 0x4C8DFF);
	}

	/** Whether this player runs across water right now (both sides read it): a Velox, sprinting, not sneaking. */
	public static boolean runsOnWater(Player player) {
		return "blur".equals(player.getAttached(XenoformState.FORM)) && player.isSprinting() && !player.isShiftKeyDown();
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof AbstractHorse;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.getAttribute(Attributes.SAFE_FALL_DISTANCE)
			.addOrUpdateTransientModifier(new AttributeModifier(SAFE_FALL_ID, 4.0, AttributeModifier.Operation.ADD_VALUE));
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		var fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		if (fall != null) {
			fall.removeModifier(SAFE_FALL_ID);
		}
		burstModifiers(player, false);
		releaseAll(live);
		live.bursting = false;
		live.standstill = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1) {
			// the speed burst: held
			if (down) {
				if (!live.bursting && ready(player, live, R)) {
					live.bursting = true;
					live.burstTicks = 0;
					live.wallRun = 0;
					burstModifiers(player, true);
					burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 12, 0.4, 0.05);
					sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 1.6F);
					sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 1.5F);
				}
			} else if (live.bursting) {
				endBurst(player, live);
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_2 -> {
				if (ready(player, live, G) && live.punches <= 0) {
					LivingEntity target = aimed(player, PUNCH_REACH, e -> true);
					if (target != null) {
						live.punches = PUNCH_HITS;
						live.punchTarget = target.getId();
						live.punchTick = 0;
						cooldown(player, live, G, PUNCH_COOLDOWN);
					}
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					cyclone(player, live);
					cooldown(player, live, V, CYCLONE_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && chain(player, live)) {
					cooldown(player, live, B, CHAIN_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the speed burst: until you let go, or it runs out
		if (live.bursting) {
			live.burstTicks++;
			if (live.burstTicks >= BURST_MAX_TICKS || !com.jaymospowers.core.PowerInput.isDown(player, PowerKey.ABILITY_1)) {
				endBurst(player, live);
			} else {
				Vec3 motion = player.getDeltaMovement();
				if (motion.horizontalDistanceSqr() > 0.04) {
					// a streak behind you
					Vec3 back = motion.horizontal().normalize().scale(-0.6);
					burst(level, ParticleTypes.CLOUD, player.position().add(back).add(0.0, 0.4, 0.0), 2, 0.2, 0.01);
					if (live.burstTicks % 3 == 0) {
						burst(level, ParticleTypes.CRIT, player.position().add(back).add(0.0, 0.8, 0.0), 1, 0.2, 0.05);
					}
				}
				// into a wall: straight up it
				if (player.horizontalCollision && live.wallRun < WALL_RUN_TICKS && !player.isInWater()) {
					live.wallRun++;
					player.setDeltaMovement(motion.x, WALL_RUN_CLIMB, motion.z);
					player.syncVelocity = true;
					player.resetFallDistance();
					if (live.wallRun % 4 == 0) {
						burst(level, ParticleTypes.CLOUD, player.position(), 2, 0.2, 0.01);
						sound(player, SoundEvents.HORSE_GALLOP, 0.5F, 1.8F);
					}
				} else if (player.onGround()) {
					live.wallRun = 0;
				}
			}
		}
		// corrupted: the legs won't stop
		if (live.runaway > 0) {
			live.runaway--;
			List<LivingEntity> hit = around(player, 1.2);
			if (player.horizontalCollision || !hit.isEmpty() || live.runaway == 0) {
				for (LivingEntity living : hit) {
					living.hurtServer(level, player.damageSources().playerAttack(player), CORRUPT_RUNAWAY_DAMAGE);
					shove(living, player.position(), 1.0, 0.4);
				}
				live.runaway = 0;
				player.setDeltaMovement(Vec3.ZERO);
				player.syncVelocity = true;
				sound(player, SoundEvents.HORSE_ANGRY, 0.8F, 1.4F);
				com.jaymospowers.core.Shakes.heavy(player, player.position(), 10.0, 0.9F);
			} else {
				Vec3 look = player.getLookAngle().horizontal();
				look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
				player.setDeltaMovement(look.scale(CORRUPT_RUNAWAY_SPEED).add(0.0, player.getDeltaMovement().y, 0.0));
				player.syncVelocity = true;
				burst(level, ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 2, 0.2, 0.01);
			}
		}
		// the rapid punches, one at a time
		if (live.punches > 0) {
			Entity target = level.getEntity(live.punchTarget);
			if (!(target instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(player) > PUNCH_REACH * 1.5) {
				live.punches = 0;
			} else if (--live.punchTick <= 0) {
				live.punchTick = PUNCH_SPACING;
				punch(player, live, living);
			}
		}
		// the cyclone sweeps on
		if (live.cyclone > 0) {
			live.cyclone--;
			sweep(player, live);
		}
		// the chain strike, one flicker at a time
		if (!live.chain.isEmpty() && ++live.chainTick >= CHAIN_SPACING) {
			live.chainTick = 0;
			Entity next = level.getEntity(live.chain.remove(0));
			if (next instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= CHAIN_RANGE * CHAIN_RANGE * 2.0) {
				strike(player, living);
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.bursting && live.wallRun > 0 && live.wallRun <= WALL_RUN_TICKS || !live.chain.isEmpty();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.bursting || live.runaway > 0 || !live.chain.isEmpty() || live.chainTick > 0;
	}

	// ---- Speed Burst ----

	/** The burst's speed and step height, on or off. */
	private static void burstModifiers(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(BURST_SPEED_ID, BURST_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (step != null) {
				step.addOrUpdateTransientModifier(new AttributeModifier(BURST_STEP_ID, BURST_STEP, AttributeModifier.Operation.ADD_VALUE));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(BURST_SPEED_ID);
			}
			if (step != null) {
				step.removeModifier(BURST_STEP_ID);
			}
		}
	}

	/** Let go (or time's up): back to normal speed, the cooldown starts, and corrupted legs run on. */
	private static void endBurst(ServerPlayer player, XenoformPower.Live live) {
		live.bursting = false;
		live.burstTicks = 0;
		live.wallRun = 0;
		burstModifiers(player, false);
		cooldown(player, live, R, BURST_COOLDOWN);
		if (live.corrupted) {
			live.runaway = CORRUPT_RUNAWAY_TICKS;
		}
		burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 8, 0.3, 0.03);
	}

	// ---- Rapid Punches ----

	private static void punch(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		ServerLevel level = player.level();
		live.punches--;
		player.swing(live.punches % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), PUNCH_DAMAGE);
		target.hurtTime = 0;
		burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 6, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F,
			1.2F + live.punches * 0.05F);
		if (live.punches == 0) {
			shove(target, player.position(), 1.2, 0.4);
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
		}
	}

	// ---- Cyclone ----

	private static void cyclone(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.cyclone = CYCLONE_TICKS;
		for (LivingEntity living : around(player, CYCLONE_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), CYCLONE_DAMAGE);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.8F);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.0F);
	}

	/** One tick of the cyclone: everything near is swept round you and lifted, and the wind shows. */
	private static void sweep(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 centre = player.position();
		boolean lifting = live.cyclone > CYCLONE_TICKS / 2;
		for (LivingEntity living : around(player, CYCLONE_RADIUS)) {
			Vec3 rel = living.position().subtract(centre).horizontal();
			double d = rel.length();
			Vec3 tangent = d < 0.5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-rel.z / d, 0.0, rel.x / d);
			// round, and a little in so the ring holds
			Vec3 inward = d < 0.5 ? Vec3.ZERO : rel.scale(-0.08 / d);
			double rise = lifting && living.getY() - centre.y < CYCLONE_MAX_RISE ? CYCLONE_LIFT : Math.min(0.0, living.getDeltaMovement().y);
			Vec3 push = tangent.scale(CYCLONE_SWEEP).add(inward);
			living.setDeltaMovement(push.x, lifting ? rise : living.getDeltaMovement().y, push.z);
			living.syncVelocity = true;
		}
		// cloud in a rising spiral
		double t = (CYCLONE_TICKS - live.cyclone) / (double)CYCLONE_TICKS;
		for (int arm = 0; arm < 3; arm++) {
			double a = (CYCLONE_TICKS - live.cyclone) * 0.7 + arm * Math.PI * 2.0 / 3.0;
			double r = 1.0 + t * (CYCLONE_RADIUS - 1.5);
			level.sendParticles(ParticleTypes.CLOUD, centre.x + Math.cos(a) * r, centre.y + 0.3 + t * CYCLONE_MAX_RISE, centre.z + Math.sin(a) * r, 1, 0.1,
				0.1, 0.1, 0.0);
		}
		if (live.cyclone % 8 == 0) {
			sound(player, SoundEvents.WIND_CHARGE_THROW, 0.8F, 0.7F);
		}
	}

	// ---- Chain Strike ----

	private static boolean chain(ServerPlayer player, XenoformPower.Live live) {
		List<LivingEntity> targets = around(player, CHAIN_RANGE);
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		live.chain.clear();
		for (LivingEntity target : targets) {
			if (live.chain.size() >= CHAIN_TARGETS) {
				break;
			}
			live.chain.add(target.getId());
		}
		if (live.chain.isEmpty()) {
			return false;
		}
		live.chainTick = CHAIN_SPACING;
		sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 2.0F);
		return true;
	}

	/** One flicker: beside (behind) the target, and a blow. */
	private static void strike(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 behind = target.position().subtract(target.getLookAngle().horizontal().normalize().scale(1.2));
		Vec3 from = player.position();
		burst(level, ParticleTypes.CLOUD, from.add(0.0, 0.5, 0.0), 6, 0.3, 0.02);
		float yaw = (float)(Math.toDegrees(Math.atan2(-(target.getX() - behind.x), target.getZ() - behind.z)));
		player.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), yaw, player.getXRot(), false);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.hurtServer(level, player.damageSources().playerAttack(player), CHAIN_DAMAGE);
		target.hurtTime = 0;
		burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 8, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	// ---- Standstill ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		standstill(player, live);
		return true;
	}

	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (ultimateReady(player, live)) {
			standstill(player, live);
		}
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		if (live.standstill <= 0) {
			return;
		}
		ServerLevel level = player.level();
		double r2 = STANDSTILL_RADIUS * STANDSTILL_RADIUS;
		for (LivingEntity living : around(player, STANDSTILL_RADIUS)) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, STANDSTILL_SLOWNESS, false, false));
			living.setDeltaMovement(living.getDeltaMovement().scale(STANDSTILL_DRAG));
			living.syncVelocity = true;
		}
		// projectiles caught in it hang almost still
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(STANDSTILL_RADIUS),
			p -> p.isAlive() && p.getOwner() != player && p.distanceToSqr(player) <= r2)) {
			XenoformPower.Held held = live.held.get(projectile.getId());
			if (held == null) {
				Vec3 v = projectile.getDeltaMovement();
				held = new XenoformPower.Held(projectile, new double[] {v.x, v.y, v.z, projectile.isNoGravity() ? 1.0 : 0.0});
				live.held.put(projectile.getId(), held);
				projectile.setNoGravity(true);
			}
			double[] caught = held.caught();
			projectile.setDeltaMovement(caught[0] * STANDSTILL_PROJECTILE, caught[1] * STANDSTILL_PROJECTILE, caught[2] * STANDSTILL_PROJECTILE);
			projectile.syncVelocity = true;
		}
		// the ones that got out of range, or ended, are let go
		live.held.values().removeIf(held -> {
			Projectile projectile = held.projectile();
			if (!projectile.isAlive()) {
				return true;
			}
			if (projectile.level() != level || projectile.distanceToSqr(player) > r2) {
				release(projectile, held.caught());
				return true;
			}
			return false;
		});
		// the air itself holds still
		for (int i = 0; i < 6; i++) {
			level.sendParticles(ParticleTypes.END_ROD, player.getX() + (level.getRandom().nextDouble() - 0.5) * 2.0 * STANDSTILL_RADIUS * 0.5,
				player.getY() + level.getRandom().nextDouble() * 4.0, player.getZ() + (level.getRandom().nextDouble() - 0.5) * 2.0 * STANDSTILL_RADIUS * 0.5,
				1, 0.0, 0.0, 0.0, 0.0);
		}
		if (--live.standstill <= 0) {
			endStandstill(player, live, true);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		endStandstill(player, live, false);
	}

	private static void standstill(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.standstill = STANDSTILL_TICKS;
		ultimateCooldown(player, live, STANDSTILL_COOLDOWN);
		MobEffectInstance had = player.getEffect(MobEffects.SPEED);
		live.savedSpeed = had == null ? null : new MobEffectInstance(had);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, STANDSTILL_TICKS, 1, false, false));
		ring(level, ParticleTypes.END_ROD, player.position(), 4.0, 24);
		sound(player, SoundEvents.BELL_RESONATE, 1.4F, 0.5F);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
	}

	/** Time moves again: the projectiles fly on from where they hang, with their gravity back. */
	private static void endStandstill(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		boolean was = live.standstill > 0;
		int elapsed = STANDSTILL_TICKS - live.standstill;
		live.standstill = 0;
		MobEffectInstance had = live.savedSpeed;
		releaseAll(live);
		if (was) {
			player.removeEffect(MobEffects.SPEED);
			// the beacon or potion Speed it replaced comes back, with the time it ran down meanwhile
			if (had != null && (had.isInfiniteDuration() || had.getDuration() > elapsed)) {
				player.addEffect(had.isInfiniteDuration() ? had : new MobEffectInstance(had.getEffect(), had.getDuration() - elapsed,
					had.getAmplifier(), had.isAmbient(), had.isVisible(), had.showIcon()));
			}
			if (fx) {
				ring(player.level(), ParticleTypes.END_ROD, player.position(), 4.0, 24);
				sound(player, SoundEvents.BELL_RESONATE, 1.4F, 0.4F);
				sound(player, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.8F);
			}
		}
	}

	/** Lets go of every projectile Standstill holds. */
	private static void releaseAll(XenoformPower.Live live) {
		for (XenoformPower.Held held : live.held.values()) {
			if (held.projectile().isAlive()) {
				release(held.projectile(), held.caught());
			}
		}
		live.held.clear();
		live.savedSpeed = null;
	}

	private static void release(Projectile projectile, double[] caught) {
		projectile.setNoGravity(caught[3] > 0.5);
		projectile.setDeltaMovement(caught[0], caught[1], caught[2]);
		projectile.syncVelocity = true;
	}
}
