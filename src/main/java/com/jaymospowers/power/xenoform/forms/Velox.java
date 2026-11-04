package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.chrono.ChronoSpeed;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import com.jaymospowers.power.xenoform.forms.ultimate.Standstill;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Velox, the raptor speedster (scanned from a horse): a little smaller than you, 20 health, 80% faster, lands softly,
 * and runs across still water while sprinting ({@link #runsOnWater}).
 * <ul>
 * <li>R, Velocity (0.15.97, after Chrono's Overclock; it replaced the held Speed Burst): a toggle. While it's on you are
 * far faster ({@link #VELOCITY_SPEED}, and Overclock's sprint on top), stride up whole blocks, run straight up walls
 * holding forward, across water and lava sprinting, take no fall damage, and leave Overclock's streak, light blue
 * ({@link ChronoSpeed#SKY}). Shift for walking speed. R again switches it off, then {@link #VELOCITY_COOLDOWN}.</li>
 * <li>G, Rapid Punches: {@link #PUNCH_HITS} blows on whoever you aim at, one every {@link #PUNCH_SPACING} ticks.</li>
 * <li>V, Cyclone: sweeps everything within {@link #CYCLONE_RADIUS} round you in a tight circle and lifts it.</li>
 * <li>B, Chain Strike: flicker between up to {@link #CHAIN_TARGETS} foes within {@link #CHAIN_RANGE}, striking each.</li>
 * <li>C (ascended), Standstill: (0.20.21) its Ultimate is its own evolved form,
 * {@link com.jaymospowers.power.xenoform.forms.ultimate.Standstill}.</li>
 * </ul>
 */
public final class Velox extends XenoForm {
	// ---- tuning ----
	/** Velocity: the speed bonus while it's on (a share of your speed, like Overclock's Speed V), and R's wait once it's off. */
	public static final double VELOCITY_SPEED = 1.0;
	public static final int VELOCITY_COOLDOWN = 40;
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
	private static final Identifier SAFE_FALL_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_fall");
	private static final Identifier VELOCITY_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_velocity");

	public Velox() {
		super("blur", 0.9F, 20.0F, 4.0, 0.8, 0.0, 0.0, Items.SUGAR, 0x4C8DFF);
	}

	/** Whether this player runs across water right now (both sides read it): a Velox, sprinting, not sneaking. */
	public static boolean runsOnWater(Player player) {
		return "blur".equals(player.getAttached(XenoformState.FORM)) && player.isSprinting() && !player.isShiftKeyDown();
	}

	@Override
	public boolean scans(Entity creature) {
		// (0.18.98) Kingdoms' wind dragons and war elephants are built on the horse, but they're no horses
		return creature instanceof AbstractHorse && !"jaymoskingdoms".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(creature.getType()).getNamespace());
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
		velocityOff(player, live, false);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				// Velocity: a toggle
				if (live.velocity) {
					velocityOff(player, live, true);
				} else if (ready(player, live, R)) {
					velocityOn(player, live);
				}
			}
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
		// Velocity: Overclock's run (sprint, step, wall run, water run, streak), light blue, until R switches it off
		if (live.velocity) {
			velocitySpeed(player);
			ChronoSpeed.tick(player, level, true, ChronoSpeed.SKY);
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
			if (next instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= CHAIN_RANGE * CHAIN_RANGE) {
				strike(player, living);
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.velocity && player.getAttached(ChronoSpeed.WALL_RUN) != null || !live.chain.isEmpty();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.velocity || !live.chain.isEmpty() || live.chainTick > 0;
	}

	// ---- Velocity ----

	private static void velocityOn(ServerPlayer player, XenoformPower.Live live) {
		live.velocity = true;
		velocitySpeed(player);
		ChronoSpeed.tick(player, player.level(), true, ChronoSpeed.SKY);
		burst(player.level(), ChronoSpeed.SKY_DUST, player.position().add(0.0, 1.0, 0.0), 20, 0.4, 0.05);
		burst(player.level(), ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 1.0, 0.0), 12, 0.4, 0.2);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8F, 2.0F);
		sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 1.5F);
	}

	/** Velocity's speed bonus, put on if it isn't (transient: a relog drops it, so the tick puts it back). */
	private static void velocitySpeed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(VELOCITY_ID)) {
			speed.addTransientModifier(new AttributeModifier(VELOCITY_ID, VELOCITY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Velocity off: back to Velox's own speed, and (switched off with R) the cooldown starts. */
	private static void velocityOff(ServerPlayer player, XenoformPower.Live live, boolean byKey) {
		boolean was = live.velocity;
		live.velocity = false;
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(VELOCITY_ID);
		}
		ChronoSpeed.clear(player);
		if (was && byKey) {
			cooldown(player, live, R, VELOCITY_COOLDOWN);
			burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 8, 0.3, 0.03);
			sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.7F);
		}
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
		Vec3 flat = target.getLookAngle().horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 behind = target.position().subtract(flat.scale(1.2));
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

	private final XenoUltimate standstill = new Standstill(this);

	/** (0.20.21) Standstill is its own evolved form now: the Ultimate move, health bar, model and kit are in {@code Standstill}. */
	@Override
	public XenoUltimate evolved() {
		return standstill;
	}
}
