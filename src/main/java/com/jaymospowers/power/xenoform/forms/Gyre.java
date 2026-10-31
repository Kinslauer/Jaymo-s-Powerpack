package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.102, Xeno-Band patch 5) Gyre, the wind-shell turtle: broad, a domed shell on its back with vents that hum, 28
 * health, fists of 4, a little slow. Scanned from a turtle (or a breeze, {@code XenoDna}). It swims well.
 * <ul>
 * <li>R, Shell Spin: tuck in and spin, bowling forward for {@link #SPIN_TICKS} ticks; every creature met takes
 * {@link #SPIN_DAMAGE} and a toss, once each.</li>
 * <li>G, Gust: a cone of wind ahead: shoves everything, puts out burning creatures and the fires in its way.</li>
 * <li>V, Whirlwind: a small whirlwind where you look that drags creatures round and up for {@link #WHIRL_TICKS} ticks.</li>
 * <li>B (hold), Shell: pull in; shots glance off, but you crawl.</li>
 * </ul>
 */
public final class Gyre extends XenoForm {
	// ---- tuning ----
	public static final int SPIN_TICKS = 30, SPIN_COOLDOWN = 80;
	public static final double SPIN_SPEED = 0.6, SPIN_REACH = 1.5, SPIN_TOSS = 1.1;
	public static final float SPIN_DAMAGE = 5.0F;
	public static final double GUST_REACH = 8.0, GUST_CONE = 0.7, GUST_PUSH = 1.6;
	public static final int GUST_COOLDOWN = 40;
	public static final double WHIRL_REACH = 16.0, WHIRL_RADIUS = 4.0, WHIRL_PULL = 0.18, WHIRL_LIFT = 0.12;
	public static final int WHIRL_TICKS = 40, WHIRL_COOLDOWN = 160;
	public static final float WHIRL_DAMAGE = 3.0F;

	public Gyre() {
		super("gyre", 1.0F, 28.0F, 4.0, -0.05, 0.4, 0.5, Items.TURTLE_SCUTE, 0x5FA37A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Turtle;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.spin = live.whirlTicks = 0;
		live.spun.clear();
		live.whirl = null;
		live.rolled = false;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if ((live.rolled || live.spin > 0) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			sound(player, SoundEvents.TURTLE_HURT, 0.8F, 1.6F);
			return false;
		}
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			if (live.rolled != down) {
				live.rolled = down;
				sound(player, SoundEvents.TURTLE_SHAMBLE, 1.0F, 0.7F);
				if (down) {
					cooldown(player, live, B, 1);
				}
			}
			return;
		}
		if (!down || live.rolled) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && live.spin == 0) {
					live.spin = SPIN_TICKS;
					live.spun.clear();
					sound(player, SoundEvents.BREEZE_WHIRL, 1.0F, 0.8F);
					cooldown(player, live, R, SPIN_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					gust(player, level);
					cooldown(player, live, G, GUST_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					BlockPos at = aimBlock(player, WHIRL_REACH);
					LivingEntity target = aimed(player, WHIRL_REACH, e -> true);
					Vec3 eye = target != null ? target.position()
						: at != null ? Vec3.atBottomCenterOf(at.above()) : player.getEyePosition().add(player.getLookAngle().scale(8.0));
					live.whirl = eye;
					live.whirlTicks = WHIRL_TICKS;
					sound(player, SoundEvents.BREEZE_SHOOT, 1.0F, 0.6F);
					cooldown(player, live, V, WHIRL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** The Gust: a cone of wind that shoves, and puts out what's burning. */
	private static void gust(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (LivingEntity e : around(player, GUST_REACH)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.normalize().dot(look) < GUST_CONE) {
				continue;
			}
			e.clearFire();
			e.setDeltaMovement(e.getDeltaMovement().add(look.scale(GUST_PUSH)).add(0.0, 0.3, 0.0));
			e.syncVelocity = true;
		}
		player.clearFire();
		// fires in the cone go out
		BlockPos base = player.blockPosition();
		int r = (int)GUST_REACH;
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-r, -2, -r), base.offset(r, 3, r))) {
			Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
			if (to.lengthSqr() > GUST_REACH * GUST_REACH || to.normalize().dot(look) < GUST_CONE) {
				continue;
			}
			if (level.getBlockState(pos).is(BlockTags.FIRE)) {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
			}
		}
		for (int i = 1; i <= 8; i++) {
			Vec3 p = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.GUST, p.x, p.y, p.z, 1, 0.2 * i / 4.0, 0.2, 0.2 * i / 4.0, 0.0);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
		}
		if (live.rolled) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 3, false, false));
		}
		if (live.spin > 0) {
			live.spin--;
			Vec3 look = player.getLookAngle().horizontal();
			look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
			player.setDeltaMovement(look.x * SPIN_SPEED, player.getDeltaMovement().y, look.z * SPIN_SPEED);
			player.syncVelocity = true;
			level.sendParticles(ParticleTypes.SMALL_GUST, player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.5, 0.2, 0.5, 0.0);
			for (LivingEntity e : around(player, SPIN_REACH)) {
				if (live.spun.add(e.getId())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), SPIN_DAMAGE);
					shove(e, player.position(), SPIN_TOSS, 0.5);
					live.hurt = true;
					sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
				}
			}
		}
		if (live.whirlTicks > 0 && live.whirl != null) {
			live.whirlTicks--;
			Vec3 eye = live.whirl;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, eye).inflate(WHIRL_RADIUS, 3.0, WHIRL_RADIUS),
				e -> e != player && e.isAlive() && !e.isSpectator() && !spared(player, e))) {
				Vec3 in = eye.subtract(e.position()).horizontal();
				Vec3 round = new Vec3(-in.z, 0.0, in.x).normalize().scale(0.25);
				Vec3 pull = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize().scale(WHIRL_PULL);
				e.setDeltaMovement(pull.add(round).add(0.0, WHIRL_LIFT, 0.0));
				e.syncVelocity = true;
				e.resetFallDistance();
				if (live.whirlTicks % 20 == 0) {
					e.hurtServer(level, player.damageSources().playerAttack(player), WHIRL_DAMAGE);
				}
			}
			for (int i = 0; i < 3; i++) {
				double a = (player.tickCount * 0.6 + i * 2.1);
				double h = (player.tickCount % 20) / 20.0 * 3.0;
				level.sendParticles(ParticleTypes.SMALL_GUST, eye.x + Math.cos(a) * (1.0 + h * 0.6), eye.y + h, eye.z + Math.sin(a) * (1.0 + h * 0.6), 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (live.whirlTicks == 0) {
				live.whirl = null;
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.GUST, player.position().add(0.0, 1.0, 0.0), 12, 1.2, 0.0);
		sound(player, SoundEvents.BREEZE_WHIRL, 1.4F, 0.5F);
		return true;
	}
}
