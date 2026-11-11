package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Rivet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37, Ultimates patch 2) Rivet's Ultimate, Ironpour: the liquid-metal puddle that stands up to fight, grown from 0.6x to your size,
 * with riveted steel plates floating on the metal and gear-tooth spikes down its back, a health bar of its own on top of Rivet's. Its
 * model is {@code UltimateIronpour}. It keeps Rivet's shells and machines (it calls them, stronger) and adds a big move.
 * <ul>
 * <li>R, Overmeld: Meld from {@link #MELD_REACH} blocks. In the shell every shell move is {@link #SHELL_POWER} times as strong (longer fire
 * breath, a harder piston Shove, a heavier anvil Drop, more arrows a volley). Sneak + R pours you out.</li>
 * <li>G, Twin Lash: two whips of metal, each hitting a different creature, dragging it to you.</li>
 * <li>V, Molten Pour: a much faster flowing dash; nothing lands on you during it, and what you pour through is burned and slowed.</li>
 * <li>B, Overdrive Ride: Ride from further away; the machine you drive gets Resistance II and bolted plates, and rams what it runs into.</li>
 * <li>C, Foundry Slam: surge up into a tall pillar and slam both whip-arms down on everything round you.</li>
 * </ul>
 */
public final class Ironpour extends XenoUltimate {
	// ---- tuning ----
	/** Overmeld: the reach (blocks), the cooldown, and how much stronger every shell move is. */
	public static final double MELD_REACH = 8.0;
	public static final int MELD_COOLDOWN = 20;
	public static final float SHELL_POWER = 1.5F;
	/** Twin Lash: reach (blocks), how hard each creature is dragged, damage of a whip, cooldown, and how far off the look the second creature may be (dot of the directions). */
	public static final double LASH_REACH = 10.0, LASH_PULL = 1.1, LASH_CONE = 0.85;
	public static final float LASH_DAMAGE = 7.0F;
	public static final int LASH_COOLDOWN = 40;
	/** Molten Pour: dash speed (blocks/tick), cooldown, ticks nothing lands on you, how wide the wake is (blocks), damage, and the Slowness II it leaves (ticks). */
	public static final double POUR_SPEED = 2.2, POUR_WIDTH = 1.8;
	public static final int POUR_COOLDOWN = 60, POUR_GUARD = 14, POUR_SLOW_TICKS = 60;
	public static final float POUR_DAMAGE = 4.0F;
	/** Overdrive Ride: reach (blocks), cooldown, the machine's Resistance II (ticks, refreshed), and the ram: damage, ticks before the same creature can be rammed again, padding round the machine (blocks), the least speed (squared) that rams, the shove. */
	public static final double RIDE_REACH = 6.0, RAM_PAD = 0.6, RAM_MIN_SPEED_SQ = 0.008, RAM_SHOVE = 1.1;
	public static final int RIDE_COOLDOWN = 20, MACHINE_RESISTANCE_TICKS = 40, RAM_GAP = 20;
	public static final float RAM_DAMAGE = 6.0F;
	/** Foundry Slam: cooldown (20 s), radius (blocks), damage, ticks of the surge up and its speed, the slam down's speed, the most ticks it falls before it counts as landed, the fling up. */
	public static final int SLAM_COOLDOWN = 400, SLAM_RISE_TICKS = 8, SLAM_FALL_TICKS = 40;
	public static final double SLAM_RADIUS = 5.0, SLAM_RISE_SPEED = 0.5, SLAM_DROP_SPEED = 2.4, SLAM_FLING_UP = 1.0, SLAM_OUT = 0.5;
	public static final float SLAM_DAMAGE = 10.0F;
	/** Screen shake (degrees, and how far it's felt): the change-in, the slam. */
	public static final float IRONPOUR_SHAKE = 1.0F, SLAM_SHAKE = 1.5F;
	public static final double IRONPOUR_SHAKE_RANGE = 16.0, SLAM_SHAKE_RANGE = 24.0;

	/** What each Ironpour has under way (server thread only): the pour's guard and who it already burned, the slam's phase, and who the machine rammed when. */
	private static final class Work {
		int pour;
		final Set<Integer> burned = new HashSet<>();
		/** 0: none, 1: surging up, 2: slamming down. */
		int slam, slamTicks;
		final Map<Integer, Long> rams = new HashMap<>();
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();

	private final Rivet rivet;

	public Ironpour(XenoForm base) {
		super(base, 1.0F, 14.0F, 6.0, 0.15, 0.4, 0.5);
		this.rivet = (Rivet)base;
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.ANVIL, Items.IRON_CHAIN, Items.LAVA_BUCKET, Items.MINECART, Items.IRON_BLOCK};
	}

	/** The change-in film zooms on these in turn (model part names): the pool, the mound, the two arms, the head. */
	@Override
	public String[] morphParts() {
		return new String[] {"pool", "mound", "right_arm+left_arm", "head"};
	}

	/** Whether Foundry Slam is under way for this player (the surge and the slam), for the model's tall pillar. */
	public static boolean slamming(ServerPlayer player) {
		Work work = WORK.get(player.getUUID());
		return work != null && work.slam != 0;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.rolls = 0;
		ServerLevel level = player.level();
		burst(level, MagicFx.CRACKLE, player.position().add(0.0, 0.6, 0.0), 40, 0.5, 0.2);
		burst(level, ParticleTypes.LAVA, player.position().add(0.0, 0.3, 0.0), 12, 0.5, 0.0);
		sound(player, SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.2F, 0.6F);
		Shakes.heavy(player, player.position(), IRONPOUR_SHAKE_RANGE, IRONPOUR_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		WORK.remove(player.getUUID());
		// the shell is poured out, the machine climbed out of, the held keys let go
		rivet.onExit(player, live);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		Work work = WORK.get(player.getUUID());
		if (work != null && work.slam != 0) {
			return; // mid slam
		}
		if (live.shell != null) {
			// Overmeld: the shell's moves, stronger (sneak + R pours out)
			rivet.shellKey(player, live, key, down, SHELL_POWER);
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && Rivet.meld(player, live, MELD_REACH)) {
					cooldown(player, live, R, MELD_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && twinLash(player)) {
					cooldown(player, live, G, LASH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					pour(player, live);
					cooldown(player, live, V, POUR_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && Rivet.ride(player, RIDE_REACH)) {
					if (Rivet.driven(player) != null) {
						plate(player);
					}
					cooldown(player, live, B, RIDE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: Foundry Slam. Surge up into a tall pillar, then slam both whip-arms down. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work());
		if (work.slam != 0) {
			return;
		}
		if (Rivet.driven(player) != null) {
			player.stopRiding();
		}
		if (live.shell != null) {
			Rivet.setShell(player, live, null);
		}
		work.slam = 1;
		work.slamTicks = 0;
		// (0.21.37) a pour in progress ends, or the body would lie flat as a puddle through the slam
		work.pour = 0;
		live.rolls = 0;
		ServerLevel level = player.level();
		burst(level, ParticleTypes.LAVA, player.position().add(0.0, 0.3, 0.0), 14, 0.4, 0.0);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.4F, 0.5F);
		sound(player, SoundEvents.PISTON_EXTEND, 1.2F, 0.5F);
		cooldown(player, live, C, SLAM_COOLDOWN);
	}

	// ---------------------------------------------------------------- every tick

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.get(player.getUUID());
		if (work != null && work.slam != 0) {
			slamTick(player, work);
		} else {
			// Rivet's own every-tick work (Brace, Rebound, Drop, Climb, the spray, Chord, driving), the Drop's slam stronger
			rivet.shellTick(player, live, SHELL_POWER);
		}
		if (work == null) {
			work = driveWork(player);
		}
		if (work != null) {
			if (work.pour > 0) {
				pourTick(player, work);
			}
			rideTick(player, work);
			if (work.pour <= 0 && work.slam == 0 && work.rams.isEmpty() && work.burned.isEmpty()) {
				WORK.remove(player.getUUID());
			}
		}
	}

	/** Work is only kept while there is something under way; a driven machine starts it. */
	private static Work driveWork(ServerPlayer player) {
		if (Rivet.driven(player) == null) {
			return null;
		}
		return WORK.computeIfAbsent(player.getUUID(), id -> new Work());
	}

	/** Fall damage never touches liquid metal, and nothing lands on you during a pour. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		Work work = WORK.get(player.getUUID());
		return work == null || work.pour <= 0;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.get(player.getUUID());
		return work != null && (work.pour > 0 || work.slam != 0);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.get(player.getUUID());
		return work != null && work.slam == 1;
	}

	/** Whatever Ironpour hurts, the machine it drives goes for too. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		rivet.onDealtDamage(player, live, target);
	}

	// ---------------------------------------------------------------- G: Twin Lash

	/** Two whips: the creature looked at, and the nearest other one ahead of you. False when there is nothing to lash. */
	private static boolean twinLash(ServerPlayer player) {
		LivingEntity first = aimed(player, LASH_REACH, e -> harmable(player, e));
		if (first == null) {
			return false;
		}
		List<LivingEntity> foes = new ArrayList<>();
		foes.add(first);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity second = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : around(player, LASH_REACH)) {
			if (e == first || !harmable(player, e) || !player.hasLineOfSight(e)) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 1.0E-3 || to.scale(1.0 / d).dot(look) < LASH_CONE) {
				continue;
			}
			if (d < best) {
				best = d;
				second = e;
			}
		}
		if (second != null) {
			foes.add(second);
		}
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		for (LivingEntity target : foes) {
			target.hurtServer(level, player.damageSources().playerAttack(player), LASH_DAMAGE);
			player.setLastHurtMob(target);
			Vec3 pull = player.position().subtract(target.position()).normalize().scale(LASH_PULL);
			target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.3, pull.z));
			target.syncVelocity = true;
			beam(level, eye.subtract(0.0, 0.3, 0.0), target.getBoundingBox().getCenter(), ParticleTypes.CRIT, 16);
			beam(level, eye.subtract(0.0, 0.3, 0.0), target.getBoundingBox().getCenter(), MagicFx.CRACKLE, 8);
		}
		sound(player, SoundEvents.CHAIN_PLACE, 1.2F, 1.4F);
		sound(player, SoundEvents.ANVIL_USE, 0.6F, 1.6F);
		return true;
	}

	// ---------------------------------------------------------------- V: Molten Pour

	private static void pour(ServerPlayer player, XenoformPower.Live live) {
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.x * POUR_SPEED, Math.max(0.1, look.y * POUR_SPEED * 0.5), look.z * POUR_SPEED);
		player.syncVelocity = true;
		player.resetFallDistance();
		live.rolls = Rivet.POUR_TICKS;
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work());
		work.pour = POUR_GUARD;
		work.burned.clear();
		burst(player.level(), ParticleTypes.LAVA, player.position().add(0.0, 0.3, 0.0), 10, 0.3, 0.0);
		burst(player.level(), ParticleTypes.FALLING_DRIPSTONE_WATER, player.position().add(0.0, 0.3, 0.0), 12, 0.3, 0.05);
		sound(player, SoundEvents.BUCKET_EMPTY_LAVA, 1.0F, 1.2F);
	}

	/** One tick of a pour: lava in the wake, and whatever is poured through takes the damage and the slowness, once. */
	private static void pourTick(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		work.pour--;
		Vec3 at = player.position().add(0.0, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.3, 0.1, 0.3, 0.0);
		level.sendParticles(ParticleTypes.DRIPPING_LAVA, at.x, at.y + 0.4, at.z, 3, 0.35, 0.2, 0.35, 0.0);
		for (LivingEntity e : around(player, POUR_WIDTH)) {
			if (!harmable(player, e) || !work.burned.add(e.getId())) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), POUR_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, POUR_SLOW_TICKS, 1));
			level.sendParticles(ParticleTypes.LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		}
		if (work.pour == 0) {
			work.burned.clear();
		}
	}

	// ---------------------------------------------------------------- B: Overdrive Ride

	/** The bolted plates going onto the machine just climbed into. */
	private static void plate(ServerPlayer player) {
		Entity machine = Rivet.driven(player);
		if (machine == null) {
			return;
		}
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		player.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, iron), machine.getX(), machine.getY() + machine.getBbHeight() * 0.5, machine.getZ(),
			30, machine.getBbWidth() * 0.4, machine.getBbHeight() * 0.4, machine.getBbWidth() * 0.4, 0.1);
		sound(player, SoundEvents.ANVIL_PLACE, 0.8F, 1.4F);
		sound(player, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 0.7F);
	}

	/** Every tick in a machine: Resistance II on it, and it rams the creatures it runs into. */
	private static void rideTick(ServerPlayer player, Work work) {
		Entity machine = Rivet.driven(player);
		if (machine == null) {
			work.rams.clear();
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (machine instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, MACHINE_RESISTANCE_TICKS, 1, false, false));
		}
		if (player.tickCount % 10 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), machine.getX(), machine.getY() + machine.getBbHeight() * 0.6,
				machine.getZ(), 2, machine.getBbWidth() * 0.4, machine.getBbHeight() * 0.3, machine.getBbWidth() * 0.4, 0.0);
		}
		work.rams.values().removeIf(at -> now - at > 5L * RAM_GAP);
		if (machine.getDeltaMovement().horizontalDistanceSqr() < RAM_MIN_SPEED_SQ) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, machine.getBoundingBox().inflate(RAM_PAD),
			e -> e != player && e != machine && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player)
				&& !spared(player, e) && harmable(player, e))) {
			Long last = work.rams.get(e.getId());
			if (last != null && now - last < RAM_GAP) {
				continue;
			}
			work.rams.put(e.getId(), now);
			e.hurtServer(level, player.damageSources().playerAttack(player), RAM_DAMAGE);
			player.setLastHurtMob(e);
			shove(e, machine.position(), RAM_SHOVE, 0.4);
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			sound(player, SoundEvents.ANVIL_LAND, 0.8F, 1.1F);
		}
	}

	// ---------------------------------------------------------------- C: Foundry Slam

	/** One tick of the slam: the surge up, then down fast until it lands. */
	private static void slamTick(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		work.slamTicks++;
		Vec3 motion = player.getDeltaMovement();
		player.resetFallDistance();
		if (work.slam == 1) {
			player.setDeltaMovement(motion.x * 0.3, SLAM_RISE_SPEED, motion.z * 0.3);
			player.syncVelocity = true;
			level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
			level.sendParticles(MagicFx.CRACKLE, player.getX(), player.getY() + 0.5, player.getZ(), 5, 0.3, 0.8, 0.3, 0.05);
			if (work.slamTicks >= SLAM_RISE_TICKS || player.verticalCollision) {
				work.slam = 2;
				work.slamTicks = 0;
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				player.setDeltaMovement(0.0, -SLAM_DROP_SPEED, 0.0);
				player.syncVelocity = true;
				sound(player, SoundEvents.ANVIL_FALL, 1.2F, 0.7F);
			}
			return;
		}
		player.setDeltaMovement(motion.x * 0.3, Math.min(motion.y, -SLAM_DROP_SPEED), motion.z * 0.3);
		player.syncVelocity = true;
		level.sendParticles(MagicFx.CRACKLE, player.getX(), player.getY() + 0.5, player.getZ(), 3, 0.3, 0.6, 0.3, 0.05);
		if (work.slamTicks > 1 && (player.onGround() || player.isInWater() || player.isInLava()) || work.slamTicks > SLAM_FALL_TICKS) {
			work.slam = 0;
			work.slamTicks = 0;
			land(player);
		}
	}

	/** The slam lands: everything within the radius takes the damage and is thrown up, with a ring of lava and sparks. */
	private static void land(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 feet = player.position();
		for (LivingEntity e : around(player, SLAM_RADIUS)) {
			if (!harmable(player, e)) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE);
			shove(e, feet, SLAM_OUT, SLAM_FLING_UP);
		}
		for (double r = 1.5; r <= SLAM_RADIUS + 1.0E-6; r += 1.75) {
			ring(level, ParticleTypes.LAVA, feet, r, 14 + (int)(r * 4.0));
			ring(level, MagicFx.CRACKLE, feet, r, 14 + (int)(r * 4.0));
		}
		BlockPos under = player.blockPosition().below();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under)), feet.x, feet.y, feet.z, 40, SLAM_RADIUS * 0.3, 0.1, SLAM_RADIUS * 0.3, 0.2);
		sound(player, SoundEvents.ANVIL_LAND, 1.6F, 0.5F);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.8F);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.2F, 0.5F);
		Shakes.heavy(player, feet, SLAM_SHAKE_RANGE, SLAM_SHAKE);
		player.resetFallDistance();
	}

	/** A player the attacker may not hurt (pvp off) is never hit, dragged or slowed. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}
}
