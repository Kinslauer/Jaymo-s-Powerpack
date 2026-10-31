package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.112, Scan E of {@code docs/ScanGaps-Plan.md}) Hive, the swarm: a small, striped amber-and-black body, a
 * bulbous abdomen, two pairs of clear wings on its back, antennae and big compound eyes. 16 health, fists of 3. Falls
 * never hurt it. Scanned from a bee, or Naturalist's ant, butterfly, caterpillar or dragonfly ({@code XenoDna}).
 * <ul>
 * <li>R, Scatter: for {@link #SCATTER_TICKS} ticks you break into a cloud of insects: unseen, blows pass through the
 * cloud, and holding jump lifts you in short flights.</li>
 * <li>G, Sting: a stab that poisons.</li>
 * <li>V, Honey Glue: the creature you aim at is stuck fast in honey.</li>
 * <li>B, Swarm: a cloud of insects is sent at the creature you aim at, stinging it for {@link #SWARM_TICKS} ticks.</li>
 * </ul>
 */
public final class Hive extends XenoForm {
	// ---- tuning ----
	public static final int SCATTER_TICKS = 80, SCATTER_COOLDOWN = 300;
	public static final double SCATTER_LIFT = 0.25, SCATTER_DRIFT = 0.35, SCATTER_EASE = 0.3;
	public static final double STING_REACH = 3.0;
	public static final float STING_DAMAGE = 3.0F;
	public static final int STING_POISON = 100, STING_COOLDOWN = 50;
	public static final double GLUE_REACH = 12.0;
	public static final int GLUE_TICKS = 80, GLUE_COOLDOWN = 160;
	public static final double SWARM_REACH = 24.0;
	public static final int SWARM_TICKS = 120, SWARM_EVERY = 10, SWARM_COOLDOWN = 300;
	public static final float SWARM_DAMAGE = 1.5F;

	private static final DustParticleOptions SPECK = new DustParticleOptions(0x2A2010, 0.6F);
	private static final DustParticleOptions AMBER = new DustParticleOptions(0xE0A020, 0.5F);

	public Hive() {
		super("hive", 0.8F, 16.0F, 3.0, 0.05, 0.0, 0.0, Items.HONEYCOMB, 0xE0A020);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Bee;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		if (live.scatter > 0) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
		live.scatter = live.latchTicks = 0;
		live.latched = -1;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		// a blow passes through the cloud
		return live.scatter <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					live.scatter = SCATTER_TICKS;
					player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SCATTER_TICKS, 0, false, false));
					for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0), m -> m.getTarget() == player)) {
						mob.setTarget(null);
					}
					burst(level, SPECK, player.position().add(0.0, 0.8, 0.0), 40, 0.6, 0.05);
					sound(player, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.4F, 1.2F);
					cooldown(player, live, R, SCATTER_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, STING_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), STING_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.POISON, STING_POISON, 1), player);
				live.hurt = true;
				sound(player, SoundEvents.BEE_STING, 1.0F, 1.0F);
				cooldown(player, live, G, STING_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, GLUE_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GLUE_TICKS, 9), player);
				target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, GLUE_TICKS, 250, false, false), player);
				target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
				target.syncVelocity = true;
				beam(level, player.getEyePosition(), target.position().add(0.0, 0.3, 0.0), ParticleTypes.FALLING_HONEY, 8);
				level.sendParticles(ParticleTypes.FALLING_HONEY, target.getX(), target.getY() + 0.3, target.getZ(), 20, 0.4, 0.2, 0.4, 0.0);
				sound(player, SoundEvents.HONEY_BLOCK_PLACE, 1.2F, 0.8F);
				cooldown(player, live, V, GLUE_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (!ready(player, live, B)) {
					return;
				}
				LivingEntity target = aimed(player, SWARM_REACH, e -> true);
				if (target == null) {
					return;
				}
				live.latched = target.getId();
				live.latchTicks = SWARM_TICKS;
				beam(level, player.getEyePosition(), target.getBoundingBox().getCenter(), SPECK, 12);
				sound(player, SoundEvents.BEE_LOOP_AGGRESSIVE, 1.6F, 0.9F);
				cooldown(player, live, B, SWARM_COOLDOWN);
			}
			default -> {
			}
		}
	}

	private static boolean lifting(ServerPlayer player, XenoformPower.Live live) {
		return live.scatter > 0 && player.getLastClientInput().jump() && !player.isInWater();
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.resetFallDistance();
		if (live.scatter > 0) {
			live.scatter--;
			if (lifting(player, live)) {
				Vec3 want = player.getLookAngle().horizontal().scale(SCATTER_DRIFT).add(0.0, SCATTER_LIFT, 0.0);
				player.setDeltaMovement(player.getDeltaMovement().lerp(want, SCATTER_EASE));
				player.syncVelocity = true;
			}
			level.sendParticles(SPECK, player.getX(), player.getY() + 0.8, player.getZ(), 6, 0.5, 0.6, 0.5, 0.02);
			if (player.tickCount % 3 == 0) {
				level.sendParticles(AMBER, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.5, 0.6, 0.5, 0.02);
			}
			if (live.scatter == 0) {
				burst(level, SPECK, player.position().add(0.0, 0.8, 0.0), 30, 0.4, 0.02);
			}
		}
		if (live.latched >= 0) {
			swarm(player, live, level);
		}
	}

	/** The swarm on its quarry: a cloud round it, and a sting every {@link #SWARM_EVERY} ticks. */
	private static void swarm(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Entity e = level.getEntity(live.latched);
		if (!(e instanceof LivingEntity quarry) || !quarry.isAlive() || quarry.distanceTo(player) > SWARM_REACH * 2.0 || --live.latchTicks <= 0) {
			live.latched = -1;
			live.latchTicks = 0;
			return;
		}
		level.sendParticles(SPECK, quarry.getX(), quarry.getY() + quarry.getBbHeight() * 0.6, quarry.getZ(), 5, 0.5, 0.5, 0.5, 0.03);
		if (live.latchTicks % SWARM_EVERY == 0) {
			quarry.setInvulnerableTime(0);
			quarry.hurtServer(level, player.damageSources().playerAttack(player), SWARM_DAMAGE);
			quarry.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0), player);
			level.playSound(null, quarry.getX(), quarry.getY(), quarry.getZ(), SoundEvents.BEE_STING, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.3F);
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.scatter > 0;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return lifting(player, live);
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return lifting(player, live) ? XenoformState.FLY_WINGS : XenoformState.FLY_NONE;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), AMBER, player.position().add(0.0, 1.0, 0.0), 60, 1.0, 0.05);
		sound(player, SoundEvents.BEE_LOOP_AGGRESSIVE, 2.0F, 0.7F);
		return true;
	}
}
