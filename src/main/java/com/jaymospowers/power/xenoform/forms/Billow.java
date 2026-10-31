package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.117, Scan F) Billow, the cloud: a big soft body of white cloud, greyer underneath, with a calm round face. 40
 * health, fists of 2, slow. It floats: jump to rise, sneak to sink, and otherwise it hangs where it is; falls never
 * hurt it. Up to {@link #SEATS} friends can ride on its back (right-click it; sneak to get off), sat as on a happy
 * ghast ({@code BillowSeatsMixin}). Scanned from a happy ghast, or the Aether's aerwhale ({@code XenoDna}).
 * <ul>
 * <li>R, Downpour: rain falls round you for {@link #RAIN_TICKS} ticks, putting out fire on blocks and creatures.</li>
 * <li>G, Thunderhead: a bolt of lightning on the creature you aim at (it starts no fires).</li>
 * <li>V, Gust: everything in front of you is blown back.</li>
 * <li>B, Fog: for {@link #FOG_TICKS} ticks a fog round you blinds creatures and they lose you.</li>
 * </ul>
 */
public final class Billow extends XenoForm {
	// ---- tuning ----
	public static final int SEATS = 3;
	public static final double RISE = 0.25, SINK = -0.2, DRIFT_EASE = 0.6;
	public static final int RAIN_TICKS = 120, RAIN_COOLDOWN = 300, RAIN_RADIUS = 4;
	public static final double BOLT_REACH = 32.0;
	public static final float BOLT_DAMAGE = 8.0F;
	public static final int BOLT_COOLDOWN = 160;
	public static final double GUST_RANGE = 8.0, GUST_PUSH = 1.6;
	public static final int GUST_COOLDOWN = 60;
	public static final int FOG_TICKS = 200, FOG_COOLDOWN = 500;
	public static final double FOG_RADIUS = 8.0;

	public Billow() {
		super("billow", 1.6F, 40.0F, 2.0, -0.03, 0.5, 0.0, Items.GHAST_TEAR, 0xE8F0F8);
		// friends climb onto its back
		UseEntityCallback.EVENT.register((rider, world, hand, target, hit) -> {
			if (world.isClientSide() || !(rider instanceof ServerPlayer friend) || !(target instanceof ServerPlayer cloud) || friend.isShiftKeyDown()
				|| friend.isPassenger() || !"billow".equals(cloud.getAttached(XenoformState.FORM)) || cloud.getPassengers().size() >= SEATS) {
				return InteractionResult.PASS;
			}
			if (friend.startRiding(cloud, true, true)) {
				cloud.level().playSound(null, cloud.getX(), cloud.getY(), cloud.getZ(), SoundEvents.WOOL_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof HappyGhast;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		player.ejectPassengers();
		live.rain = live.fog = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
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
					live.rain = RAIN_TICKS;
					sound(player, SoundEvents.WEATHER_RAIN_ABOVE, 1.6F, 1.0F);
					cooldown(player, live, R, RAIN_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, BOLT_REACH, e -> true);
				if (target == null) {
					return;
				}
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(target.getX(), target.getY(), target.getZ());
					bolt.setVisualOnly(true);
					bolt.setCause(player);
					level.addFreshEntity(bolt);
				}
				target.hurtServer(level, player.damageSources().lightningBolt(), BOLT_DAMAGE);
				beam(level, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), target.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 12);
				cooldown(player, live, G, BOLT_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					Vec3 look = player.getLookAngle();
					for (LivingEntity e : around(player, GUST_RANGE)) {
						Vec3 to = e.position().subtract(player.position());
						if (to.normalize().dot(look) < 0.4 || player.getPassengers().contains(e)) {
							continue;
						}
						e.setDeltaMovement(e.getDeltaMovement().add(look.x * GUST_PUSH, 0.4, look.z * GUST_PUSH));
						e.syncVelocity = true;
					}
					for (int i = 1; i <= 6; i++) {
						Vec3 p = player.getEyePosition().add(look.scale(i * 1.2));
						level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 4, 0.3 * i * 0.3, 0.3, 0.3 * i * 0.3, 0.05);
					}
					sound(player, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, 0.7F);
					cooldown(player, live, V, GUST_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.fog = FOG_TICKS;
					sound(player, SoundEvents.WEATHER_RAIN_ABOVE, 1.0F, 0.5F);
					cooldown(player, live, B, FOG_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.resetFallDistance();
		// it floats: jump rises, sneak sinks, otherwise it hangs
		if (!player.onGround() || player.getLastClientInput().jump()) {
			Vec3 m = player.getDeltaMovement();
			double want = player.getLastClientInput().jump() ? RISE : player.isShiftKeyDown() ? SINK : 0.0;
			player.setDeltaMovement(m.x, m.y + (want - m.y) * DRIFT_EASE, m.z);
			player.syncVelocity = true;
		}
		if (player.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + player.getBbHeight() * 0.4, player.getZ(), 1, 0.6, 0.4, 0.6, 0.0);
		}
		for (Entity rider : player.getPassengers()) {
			if (rider instanceof LivingEntity living) {
				living.resetFallDistance();
			}
		}
		if (live.rain > 0) {
			live.rain--;
			downpour(player, level);
		}
		if (live.fog > 0) {
			live.fog--;
			if (live.fog % 4 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 12, FOG_RADIUS * 0.5, 1.0, FOG_RADIUS * 0.5, 0.0);
			}
			if (live.fog % 10 == 0) {
				for (LivingEntity e : around(player, FOG_RADIUS)) {
					if (e instanceof Player || player.getPassengers().contains(e)) {
						continue;
					}
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false), player);
					if (e instanceof Mob mob && (mob.getTarget() == player || mob.getTarget() != null && player.getPassengers().contains(mob.getTarget()))) {
						mob.setTarget(null);
					}
				}
			}
		}
	}

	/** Rain round and below it: fire on blocks and creatures goes out. */
	private static void downpour(ServerPlayer player, ServerLevel level) {
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getY(), player.getZ(), 10, RAIN_RADIUS * 0.5, 0.2, RAIN_RADIUS * 0.5, 0.0);
		}
		if (player.tickCount % 5 != 0) {
			return;
		}
		BlockPos base = player.blockPosition();
		for (BlockPos at : BlockPos.betweenClosed(base.offset(-RAIN_RADIUS, -8, -RAIN_RADIUS), base.offset(RAIN_RADIUS, 1, RAIN_RADIUS))) {
			if (level.getBlockState(at).is(BlockTags.FIRE)) {
				level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				level.sendParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RAIN_RADIUS, 8.0, RAIN_RADIUS), LivingEntity::isOnFire)) {
			e.clearFire();
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return true;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return !player.onGround();
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 60, 1.2, 0.05);
		sound(player, SoundEvents.HAPPY_GHAST_AMBIENT, 1.6F, 0.7F);
		return true;
	}
}
