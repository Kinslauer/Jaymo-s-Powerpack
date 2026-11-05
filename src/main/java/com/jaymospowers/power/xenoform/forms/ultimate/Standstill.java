package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.chrono.ChronoFx;
import com.jaymospowers.power.chrono.ChronoSpeed;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Velox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.21) Velox's Ultimate, Standstill: the raptor grown plated and fanged, with a health bar of its own on top of Velox's. Its model is
 * {@code UltimateStandstill}.
 * <ul>
 * <li>R, Overdrive: Velocity at {@link #OVERDRIVE_MULT} times its speed; creatures you run into are hit and shouldered aside.</li>
 * <li>G, Thousand Blows: {@link #BLOWS} blows, one a tick; the last knocks the target back.</li>
 * <li>V, Vortex: a wider cyclone that lifts up to {@link #VORTEX_LIFTED} creatures, holds the projectiles it catches, and flings it all
 * outward when it ends.</li>
 * <li>B, Flicker Storm: flicker between up to {@link #FLICKER_TARGETS} foes, then back to where you started.</li>
 * <li>C, Standstill: everything living within {@link #STANDSTILL_RADIUS} crawls and every projectile hangs in the air for
 * {@link #STANDSTILL_TICKS}, while you run at Speed III in a light blue streak.</li>
 * </ul>
 */
public final class Standstill extends XenoUltimate {
	// ---- tuning ----
	/** Overdrive: Velocity's speed bonus times this; what a creature you run into takes, how hard it is shouldered (out, up), how often one can be hit again (ticks), and the speed (blocks/tick, squared) it takes to count as running into it. */
	public static final double OVERDRIVE_MULT = 1.5, RUN_SHOVE_OUT = 0.9, RUN_SHOVE_UP = 0.3, RUN_MIN_SPEED_SQ = 0.04;
	public static final float RUN_DAMAGE = 3.0F;
	public static final int RUN_HIT_GAP = 10;
	/** Thousand Blows: blows, ticks between blows, reach, damage each, cooldown, and the last blow's knockback (out, up). */
	public static final int BLOWS = 12, BLOW_SPACING = 1, BLOWS_COOLDOWN = 120;
	public static final double BLOW_REACH = 4.0, LAST_BLOW_OUT = 1.6, LAST_BLOW_UP = 0.5;
	public static final float BLOW_DAMAGE = 2.5F;
	/** Vortex: ticks it spins, cooldown, radius, how fast things are swept round, the lift a tick and the highest it lifts (blocks), how many it lifts, damage at the start, the throw at the end (out, up) and the speed a projectile is thrown at. */
	public static final int VORTEX_TICKS = 30, VORTEX_COOLDOWN = 200, VORTEX_LIFTED = 6;
	public static final double VORTEX_RADIUS = 9.0, VORTEX_SWEEP = 0.6, VORTEX_LIFT = 0.22, VORTEX_MAX_RISE = 3.5, VORTEX_THROW_OUT = 1.5,
		VORTEX_THROW_UP = 0.35, VORTEX_PROJECTILE_SPEED = 1.6, VORTEX_PROJECTILE_HELD = 0.05;
	public static final float VORTEX_DAMAGE = 3.0F;
	/** Flicker Storm: cooldown, foes, ticks between flickers, range, damage each, and ticks after the last one before you are back. */
	public static final int FLICKER_COOLDOWN = 240, FLICKER_TARGETS = 8, FLICKER_SPACING = 2, FLICKER_RETURN = 2;
	public static final double FLICKER_RANGE = 14.0;
	public static final float FLICKER_DAMAGE = 7.0F;
	/** Standstill: how long it holds (8 s), C's cooldown (30 s), how far it reaches, how hard it drags (share of velocity kept, share of a projectile's kept), the Slowness level, and your Speed level (II is 1, III is 2). */
	public static final int STANDSTILL_TICKS = 160, STANDSTILL_COOLDOWN = 600, STANDSTILL_SLOWNESS = 6, STANDSTILL_SPEED = 2;
	public static final double STANDSTILL_RADIUS = 24.0, STANDSTILL_DRAG = 0.1, STANDSTILL_PROJECTILE = 0.05;
	/** The after-image trail: how far you must move in a tick to leave one. */
	public static final double TRAIL_MIN_SQ = 0.04;

	private static final Identifier SAFE_FALL_ID = JaymosPowers.id("xenoform_blur_ultimate_fall");
	private static final Identifier OVERDRIVE_ID = JaymosPowers.id("xenoform_blur_ultimate_overdrive");

	/** What each Standstill has under way beyond {@code Live} (server thread only). */
	private static final class Work {
		/** Overdrive: the game time each creature may be hit again. */
		final Map<Integer, Long> hitUntil = new HashMap<>();
		/** Vortex: the creatures lifted, everything swept (to be thrown), and the projectiles held. */
		final Set<Integer> lifted = new HashSet<>();
		final Set<Integer> swept = new HashSet<>();
		final Map<Integer, XenoformPower.Held> vortexHeld = new HashMap<>();
		/** Flicker Storm: where it started, and ticks to go before you are put back (0: not returning). */
		Vec3 origin;
		ServerLevel originLevel;
		int returnIn;
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();
	/** (0.20.21) Each Standstill's live state, kept here so a server stop can let everything go before the players are saved. */
	private record Running(Standstill form, XenoformPower.Live live) {}

	private static final Map<UUID, Running> LIVES = new HashMap<>();

	static {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Map.Entry<UUID, Running> entry : new ArrayList<>(LIVES.entrySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					entry.getValue().form().onExit(player, entry.getValue().live());
				}
			}
			LIVES.clear();
			WORK.clear();
			Standstill.HELD_UUIDS.clear(); // (0.20.21)
		});
	}

	public Standstill(XenoForm base) {
		super(base, 1.15F, 16.0F, 5.0, 0.9, 0.1, 0.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.RABBIT_FOOT, Items.IRON_SWORD, Items.WIND_CHARGE, Items.ENDER_PEARL, Items.CLOCK};
	}

	/** The change-in film zooms on these in turn (model part names): the sickle claws, the tail's blade, the crest's quills, the visor. */
	@Override
	public String[] morphParts() {
		return new String[] {"sickle_r+sickle_l", "tail4", "crest", "visor"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		LIVES.put(player.getUUID(), new Running(this, live));
		// (0.20.21) a change-in mid Rapid Punches, Cyclone or Chain Strike must not carry on as the Ultimate's version
		live.punches = live.punchTick = 0;
		live.punchTarget = -1;
		live.cyclone = 0;
		live.chain.clear();
		live.chainTick = 0;
		player.getAttribute(Attributes.SAFE_FALL_DISTANCE)
			.addOrUpdateTransientModifier(new AttributeModifier(SAFE_FALL_ID, 4.0, AttributeModifier.Operation.ADD_VALUE));
		ServerLevel level = player.level();
		ring(level, ParticleTypes.END_ROD, player.position(), 3.0, 24);
		burst(level, ChronoSpeed.SKY_DUST, player.position().add(0.0, 1.0, 0.0), 24, 0.5, 0.05);
		sound(player, SoundEvents.BELL_RESONATE, 1.2F, 0.6F);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		var fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		if (fall != null) {
			fall.removeModifier(SAFE_FALL_ID);
		}
		LIVES.remove(player.getUUID());
		overdriveOff(player, live, false);
		endStandstill(player, live, false);
		Work work = WORK.remove(player.getUUID());
		if (work != null) {
			for (XenoformPower.Held held : work.vortexHeld.values()) {
				if (held.projectile().isAlive()) {
					release(held.projectile(), held.caught());
				} else {
					releaseCopy(player.level().getServer(), held);
				}
			}
			if (player.isAlive() && (!live.chain.isEmpty() || work.returnIn > 0)) {
				comeBack(player, work);
			}
		}
		live.chain.clear();
		live.chainTick = 0;
		live.punches = live.punchTick = 0;
		live.punchTarget = -1;
		live.cyclone = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				// Overdrive: a toggle
				if (live.velocity) {
					overdriveOff(player, live, true);
				} else if (ready(player, live, R)) {
					overdriveOn(player, live);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && live.punches <= 0) {
					LivingEntity target = aimed(player, BLOW_REACH, e -> canHit(player, e));
					if (target != null) {
						live.punches = BLOWS;
						live.punchTarget = target.getId();
						live.punchTick = 0;
						cooldown(player, live, G, BLOWS_COOLDOWN);
					}
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && live.cyclone <= 0) {
					vortex(player, live);
					cooldown(player, live, V, VORTEX_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && live.chain.isEmpty() && work(player).returnIn <= 0 && flicker(player, live)) {
					cooldown(player, live, B, FLICKER_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: Standstill. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		startStandstill(player, live);
		cooldown(player, live, C, STANDSTILL_COOLDOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		LIVES.put(player.getUUID(), new Running(this, live));
		Work work = WORK.get(player.getUUID());
		// Overdrive: Velocity's run at 1.5x, light blue, shouldering aside whatever it runs into
		if (live.velocity) {
			overdriveSpeed(player);
			ChronoSpeed.tick(player, level, true, ChronoSpeed.SKY);
			shoulder(player, work(player));
		} else if (live.standstill > 0) {
			trail(player, level);
		}
		// Thousand Blows, one a tick
		if (live.punches > 0) {
			Entity target = level.getEntity(live.punchTarget);
			if (!(target instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(player) > BLOW_REACH * 1.5) {
				live.punches = 0;
			} else if (--live.punchTick <= 0) {
				live.punchTick = BLOW_SPACING;
				blow(player, live, living);
			}
		}
		// the vortex spins on
		if (live.cyclone > 0) {
			live.cyclone--;
			sweep(player, live, work(player));
			if (live.cyclone == 0) {
				endVortex(player, work(player));
			}
		}
		// Flicker Storm, one flicker at a time, then back to where it began
		if (!live.chain.isEmpty() && ++live.chainTick >= FLICKER_SPACING) {
			live.chainTick = 0;
			Work w = work(player);
			Entity next = level.getEntity(live.chain.remove(0));
			if (next instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= FLICKER_RANGE * FLICKER_RANGE) {
				strike(player, living);
			}
			if (live.chain.isEmpty()) {
				w.returnIn = FLICKER_RETURN;
			}
		} else if (work != null && work.returnIn > 0 && --work.returnIn == 0) {
			comeBack(player, work);
		}
		holdStandstill(player, live);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.get(player.getUUID());
		return live.velocity && player.getAttached(ChronoSpeed.WALL_RUN) != null || !live.chain.isEmpty() || work != null && work.returnIn > 0;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.get(player.getUUID());
		return live.velocity || !live.chain.isEmpty() || live.chainTick > 0 || work != null && work.returnIn > 0;
	}

	private static Work work(ServerPlayer player) {
		return WORK.computeIfAbsent(player.getUUID(), id -> new Work());
	}

	// ---- R: Overdrive ----

	private static void overdriveOn(ServerPlayer player, XenoformPower.Live live) {
		live.velocity = true;
		overdriveSpeed(player);
		ChronoSpeed.tick(player, player.level(), true, ChronoSpeed.SKY);
		burst(player.level(), ChronoSpeed.SKY_DUST, player.position().add(0.0, 1.0, 0.0), 24, 0.4, 0.05);
		burst(player.level(), ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 1.0, 0.0), 14, 0.4, 0.2);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8F, 2.0F);
		sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 1.3F);
	}

	/** Overdrive's speed bonus, put on if it isn't (transient: a relog drops it, so the tick puts it back). */
	private static void overdriveSpeed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(OVERDRIVE_ID)) {
			speed.addTransientModifier(new AttributeModifier(OVERDRIVE_ID, Velox.VELOCITY_SPEED * OVERDRIVE_MULT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Overdrive off: back to the Ultimate's own speed, and (switched off with R) the cooldown starts. */
	private static void overdriveOff(ServerPlayer player, XenoformPower.Live live, boolean byKey) {
		boolean was = live.velocity;
		live.velocity = false;
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(OVERDRIVE_ID);
		}
		ChronoSpeed.clear(player);
		if (was && byKey) {
			cooldown(player, live, R, Velox.VELOCITY_COOLDOWN);
			burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 8, 0.3, 0.03);
			sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.7F);
		}
	}

	/** (0.20.21) {@link #around} without the players this one may not hurt (pvp off). */
	private static List<LivingEntity> foes(ServerPlayer player, double radius) {
		List<LivingEntity> list = new ArrayList<>(around(player, radius));
		list.removeIf(e -> !canHit(player, e));
		return list;
	}

	private static boolean canHit(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}

	/** Running into creatures at speed: each takes a hit and is shouldered aside, and is not hit again for {@link #RUN_HIT_GAP} ticks. */
	private static void shoulder(ServerPlayer player, Work work) {
		if (player.getKnownMovement().horizontalDistanceSqr() < RUN_MIN_SPEED_SQ) { // (0.20.21) the server-side delta stays ~0 for a running player
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (work.hitUntil.size() > 64) {
			work.hitUntil.values().removeIf(until -> until <= now);
		}
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.4),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player) && !spared(player, e) && canHit(player, e))) {
			if (work.hitUntil.getOrDefault(living.getId(), 0L) > now) {
				continue;
			}
			work.hitUntil.put(living.getId(), now + RUN_HIT_GAP);
			living.hurtServer(level, player.damageSources().playerAttack(player), RUN_DAMAGE);
			shove(living, player.position(), RUN_SHOVE_OUT, RUN_SHOVE_UP);
			burst(level, ParticleTypes.CRIT, living.getBoundingBox().getCenter(), 6, 0.3, 0.2);
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8F, 1.4F);
		}
	}

	// ---- G: Thousand Blows ----

	private static void blow(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		ServerLevel level = player.level();
		live.punches--;
		player.swing(live.punches % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), BLOW_DAMAGE);
		target.hurtTime = 0;
		burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 5, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, net.minecraft.sounds.SoundSource.PLAYERS, 0.7F,
			1.2F + (BLOWS - live.punches) * 0.03F);
		if (live.punches == 0) {
			shove(target, player.position(), LAST_BLOW_OUT, LAST_BLOW_UP);
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.9F);
			burst(level, ParticleTypes.EXPLOSION, target.getBoundingBox().getCenter(), 1, 0.0, 0.0);
		}
	}

	// ---- V: Vortex ----

	private static void vortex(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Work work = work(player);
		live.cyclone = VORTEX_TICKS;
		work.lifted.clear();
		work.swept.clear();
		List<LivingEntity> near = foes(player, VORTEX_RADIUS);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		for (LivingEntity living : near) {
			living.hurtServer(level, player.damageSources().playerAttack(player), VORTEX_DAMAGE);
			if (work.lifted.size() < VORTEX_LIFTED) {
				work.lifted.add(living.getId());
			}
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.7F);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
	}

	/** One tick of the vortex: everything near is swept round you, the nearest few lifted, and the projectiles caught are held. */
	private static void sweep(ServerPlayer player, XenoformPower.Live live, Work work) {
		ServerLevel level = player.level();
		Vec3 centre = player.position();
		boolean lifting = live.cyclone > VORTEX_TICKS / 2;
		for (LivingEntity living : foes(player, VORTEX_RADIUS)) {
			work.swept.add(living.getId());
			Vec3 rel = living.position().subtract(centre).horizontal();
			double d = rel.length();
			Vec3 tangent = d < 0.5 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-rel.z / d, 0.0, rel.x / d);
			// round, and a little in so the ring holds
			Vec3 inward = d < 0.5 ? Vec3.ZERO : rel.scale(-0.08 / d);
			Vec3 push = tangent.scale(VORTEX_SWEEP).add(inward);
			boolean up = lifting && work.lifted.contains(living.getId()) && living.getY() - centre.y < VORTEX_MAX_RISE;
			double rise = up ? VORTEX_LIFT : lifting && work.lifted.contains(living.getId()) ? 0.0 : Math.min(0.0, living.getDeltaMovement().y);
			living.setDeltaMovement(push.x, lifting && work.lifted.contains(living.getId()) ? rise : living.getDeltaMovement().y, push.z);
			living.syncVelocity = true;
		}
		// projectiles inside are caught and held the way Standstill holds them
		double r2 = VORTEX_RADIUS * VORTEX_RADIUS;
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(VORTEX_RADIUS),
			p -> p.isAlive() && p.getOwner() != player && (p.getOwner() == null || !spared(player, p.getOwner())) && p.distanceToSqr(player) <= r2
				&& !live.held.containsKey(p.getId()))) {
			XenoformPower.Held held = work.vortexHeld.get(projectile.getId());
			if (held == null) {
				held = catchProjectile(projectile);
				if (held == null) {
					continue; // (0.20.21) someone else already holds it
				}
				work.vortexHeld.put(projectile.getId(), held);
			}
			double[] caught = held.caught();
			projectile.setDeltaMovement(caught[0] * VORTEX_PROJECTILE_HELD, caught[1] * VORTEX_PROJECTILE_HELD, caught[2] * VORTEX_PROJECTILE_HELD);
			projectile.syncVelocity = true;
		}
		work.vortexHeld.values().removeIf(held -> {
			Projectile projectile = held.projectile();
			if (!projectile.isAlive()) {
				releaseCopy(level.getServer(), held); // (0.20.21) a copy through a portal is let go too
				return true;
			}
			if (projectile.level() != level || projectile.distanceToSqr(player) > r2) {
				release(projectile, held.caught());
				return true;
			}
			return false;
		});
		// cloud in a rising spiral
		double t = (VORTEX_TICKS - live.cyclone) / (double)VORTEX_TICKS;
		for (int arm = 0; arm < 4; arm++) {
			double a = (VORTEX_TICKS - live.cyclone) * 0.7 + arm * Math.PI * 0.5;
			double r = 1.0 + t * (VORTEX_RADIUS - 1.5);
			level.sendParticles(arm % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.END_ROD, centre.x + Math.cos(a) * r, centre.y + 0.3 + t * VORTEX_MAX_RISE,
				centre.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
		}
		if (live.cyclone % 8 == 0) {
			sound(player, SoundEvents.WIND_CHARGE_THROW, 0.8F, 0.6F);
		}
	}

	/** The vortex ends: everything it swept is thrown outward, and the projectiles it held fly out with it. */
	private static void endVortex(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 centre = player.position();
		for (int id : work.swept) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= VORTEX_RADIUS * VORTEX_RADIUS * 1.5) {
				shove(living, centre, VORTEX_THROW_OUT, VORTEX_THROW_UP);
			}
		}
		for (XenoformPower.Held held : work.vortexHeld.values()) {
			Projectile projectile = held.projectile();
			if (projectile.isAlive()) {
				Vec3 away = projectile.position().subtract(centre).horizontal();
				away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
				HELD_UUIDS.remove(projectile.getUUID()); // (0.20.21) a thrown projectile can be caught again
				projectile.setNoGravity(held.caught()[3] > 0.5);
				projectile.setDeltaMovement(away.x * VORTEX_PROJECTILE_SPEED, 0.1, away.z * VORTEX_PROJECTILE_SPEED);
				projectile.syncVelocity = true;
			} else {
				releaseCopy(level.getServer(), held);
			}
		}
		work.vortexHeld.clear();
		work.swept.clear();
		work.lifted.clear();
		ring(level, ParticleTypes.CLOUD, centre.add(0.0, 0.3, 0.0), VORTEX_RADIUS * 0.6, 28);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.4F, 0.6F);
	}

	// ---- B: Flicker Storm ----

	private static boolean flicker(ServerPlayer player, XenoformPower.Live live) {
		List<LivingEntity> targets = foes(player, FLICKER_RANGE);
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		live.chain.clear();
		for (LivingEntity target : targets) {
			if (live.chain.size() >= FLICKER_TARGETS) {
				break;
			}
			live.chain.add(target.getId());
		}
		if (live.chain.isEmpty()) {
			return false;
		}
		Work work = work(player);
		work.origin = player.position();
		work.originLevel = player.level();
		work.returnIn = 0;
		live.chainTick = FLICKER_SPACING;
		sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 2.0F);
		return true;
	}

	/** One flicker: beside (behind) the target, and a blow. */
	private static void strike(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 flat = target.getLookAngle().horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 behind = target.position().subtract(flat.scale(1.3));
		burst(level, ParticleTypes.CLOUD, player.position().add(0.0, 0.5, 0.0), 6, 0.3, 0.02);
		float yaw = (float)(Math.toDegrees(Math.atan2(-(target.getX() - behind.x), target.getZ() - behind.z)));
		player.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), yaw, player.getXRot(), false);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.hurtServer(level, player.damageSources().playerAttack(player), FLICKER_DAMAGE);
		target.hurtTime = 0;
		burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 8, 0.3, 0.2);
		burst(level, ChronoSpeed.SKY_DUST, behind.add(0.0, 0.9, 0.0), 8, 0.3, 0.02);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	/** The storm is over: back to where it began, in the same level. */
	private static void comeBack(ServerPlayer player, Work work) {
		work.returnIn = 0;
		if (work.origin == null || work.originLevel != player.level()) {
			return;
		}
		ServerLevel level = player.level();
		burst(level, ParticleTypes.CLOUD, player.position().add(0.0, 0.5, 0.0), 8, 0.3, 0.02);
		player.teleportTo(level, work.origin.x, work.origin.y, work.origin.z, Set.of(), player.getYRot(), player.getXRot(), false);
		burst(level, ChronoSpeed.SKY_DUST, work.origin.add(0.0, 0.9, 0.0), 14, 0.4, 0.03);
		sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.7F, 1.8F);
		work.origin = null;
	}

	// ---- C: Standstill ----

	private static void startStandstill(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.standstill = STANDSTILL_TICKS;
		MobEffectInstance had = player.getEffect(MobEffects.SPEED);
		live.savedSpeed = had == null ? null : new MobEffectInstance(had);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, STANDSTILL_TICKS, STANDSTILL_SPEED, false, false));
		ring(level, ParticleTypes.END_ROD, player.position(), 4.0, 24);
		sound(player, SoundEvents.BELL_RESONATE, 1.4F, 0.5F);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
	}

	/** The light blue after-images where you just were (Velocity's streak, without its run). */
	private static void trail(ServerPlayer player, ServerLevel level) {
		Vec3 was = new Vec3(player.xo, player.yo, player.zo);
		Vec3 moved = player.position().subtract(was);
		if (moved.lengthSqr() > TRAIL_MIN_SQ) {
			for (int i = 0; i < 3; i++) {
				Vec3 at = was.add(moved.scale(i / 3.0));
				ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.3 + level.getRandom().nextDouble() * 1.2, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			ChronoFx.particles(level, player, ChronoSpeed.SKY_DUST, was.x, was.y + 0.9, was.z, 2, 0.2, 0.4, 0.2, 0.0);
		}
	}

	/** One tick of Standstill: everything living near crawls, every projectile hangs, and the air holds still. */
	private static void holdStandstill(ServerPlayer player, XenoformPower.Live live) {
		if (live.standstill <= 0) {
			return;
		}
		ServerLevel level = player.level();
		double r2 = STANDSTILL_RADIUS * STANDSTILL_RADIUS;
		Work work = WORK.get(player.getUUID());
		for (LivingEntity living : foes(player, STANDSTILL_RADIUS)) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, STANDSTILL_SLOWNESS, false, false));
			living.setDeltaMovement(living.getDeltaMovement().scale(STANDSTILL_DRAG));
			living.syncVelocity = true;
		}
		// projectiles caught in it hang almost still
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(STANDSTILL_RADIUS),
			p -> p.isAlive() && p.getOwner() != player && (p.getOwner() == null || !spared(player, p.getOwner())) && p.distanceToSqr(player) <= r2
				&& (work == null || !work.vortexHeld.containsKey(p.getId())))) {
			XenoformPower.Held held = live.held.get(projectile.getId());
			if (held == null) {
				held = catchProjectile(projectile);
				if (held == null) {
					continue; // (0.20.21) someone else already holds it
				}
				live.held.put(projectile.getId(), held);
			}
			double[] caught = held.caught();
			projectile.setDeltaMovement(caught[0] * STANDSTILL_PROJECTILE, caught[1] * STANDSTILL_PROJECTILE, caught[2] * STANDSTILL_PROJECTILE);
			projectile.syncVelocity = true;
		}
		// the ones that got out of range, or ended, are let go
		live.held.values().removeIf(held -> {
			Projectile projectile = held.projectile();
			if (!projectile.isAlive()) {
				// through a portal it is replaced by a copy with the same UUID in the other dimension: let that one go
				releaseCopy(level.getServer(), held);
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
			level.sendParticles(ParticleTypes.END_ROD, player.getX() + (level.getRandom().nextDouble() - 0.5) * STANDSTILL_RADIUS,
				player.getY() + level.getRandom().nextDouble() * 4.0, player.getZ() + (level.getRandom().nextDouble() - 0.5) * STANDSTILL_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (--live.standstill <= 0) {
			endStandstill(player, live, true);
		}
	}

	/** Time moves again: the projectiles fly on from where they hang, with their gravity back, and the Speed it replaced comes back. */
	private static void endStandstill(ServerPlayer player, XenoformPower.Live live, boolean fx) {
		boolean was = live.standstill > 0;
		int elapsed = STANDSTILL_TICKS - live.standstill;
		live.standstill = 0;
		MobEffectInstance had = live.savedSpeed;
		for (XenoformPower.Held held : live.held.values()) {
			if (held.projectile().isAlive()) {
				release(held.projectile(), held.caught());
			} else {
				releaseCopy(player.level().getServer(), held);
			}
		}
		live.held.clear();
		live.savedSpeed = null;
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

	/** Holds {@code projectile}: its velocity and gravity as they were are kept to give back. */
	private static XenoformPower.Held catchProjectile(Projectile projectile) {
		if (!HELD_UUIDS.add(projectile.getUUID())) {
			return null; // (0.20.21) already held by another Standstill or Vortex: its saved values stay the only ones
		}
		Vec3 v = projectile.getDeltaMovement();
		XenoformPower.Held held = new XenoformPower.Held(projectile, new double[] {v.x, v.y, v.z, projectile.isNoGravity() ? 1.0 : 0.0});
		projectile.setNoGravity(true);
		return held;
	}

	/** A projectile that went through a portal is replaced by a copy with the same UUID in the other dimension: let that one go. */
	private static void releaseCopy(net.minecraft.server.MinecraftServer server, XenoformPower.Held held) {
		Projectile projectile = held.projectile();
		HELD_UUIDS.remove(projectile.getUUID());
		if (projectile.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION) {
			for (ServerLevel other : server.getAllLevels()) {
				if (other.getEntity(projectile.getUUID()) instanceof Projectile copy && copy != projectile) {
					release(copy, held.caught());
				}
			}
		}
	}

	/** (0.20.21) Projectiles any Standstill or Vortex holds right now, so a second holder never saves the held (slowed, weightless) state. */
	private static final java.util.Set<java.util.UUID> HELD_UUIDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void release(Projectile projectile, double[] caught) {
		HELD_UUIDS.remove(projectile.getUUID());
		projectile.setNoGravity(caught[3] > 0.5);
		projectile.setDeltaMovement(caught[0], caught[1], caught[2]);
		projectile.syncVelocity = true;
	}
}
