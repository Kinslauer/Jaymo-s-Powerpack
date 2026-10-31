package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
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
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.108, Scan C) Prowl, the great cat: long and low, tawny with dark stripes, a heavy jaw, round ears, a long tail
 * that twitches. 24 health, fists of 5, the quickest of the beasts. Scanned from an ocelot, or Naturalist's tiger or
 * lion ({@code XenoDna}).
 * <ul>
 * <li>R, Long Pounce: a long, low leap; the first creature it lands on is pinned (tripped, or held fast).</li>
 * <li>G, Rake: claws across the creature in reach: a dirty cut that bleeds.</li>
 * <li>V, Stalk: mark the creature you look at; near it you hit harder, and it glows for you to follow.</li>
 * <li>B, Roar: every mob round you breaks off and runs.</li>
 * </ul>
 * It moves without a sound, and while it sneaks it's hard to see. It always lands on its feet: falls under
 * {@link #SAFE_FALL} blocks don't hurt.
 */
public final class Prowl extends XenoForm {
	// ---- tuning ----
	public static final double POUNCE_OUT = 1.7, POUNCE_UP = 0.35, POUNCE_HIT = 1.2;
	public static final int POUNCE_TICKS = 25, POUNCE_COOLDOWN = 100, PIN_TICKS = 60;
	public static final float POUNCE_DAMAGE = 6.0F;
	public static final double RAKE_REACH = 3.5;
	public static final float RAKE_DAMAGE = 5.0F;
	public static final int RAKE_COOLDOWN = 40;
	public static final double STALK_REACH = 32.0, STALK_NEAR = 6.0;
	public static final int STALK_TICKS = 400, STALK_COOLDOWN = 300;
	public static final double ROAR_RADIUS = 12.0, ROAR_FLEE = 14.0, ROAR_SPEED = 1.4;
	public static final int ROAR_COOLDOWN = 400;
	/** Falls shorter than this (blocks) never hurt it; a fall's damage is about its height less 3. */
	public static final float SAFE_FALL = 12.0F;

	public Prowl() {
		super("prowl", 1.0F, 24.0F, 5.0, 0.15, 0.1, 0.0, Items.RABBIT_HIDE, 0xC8893A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Ocelot;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.setSilent(true);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		player.setSilent(false);
		player.removeEffect(MobEffects.INVISIBILITY);
		live.rush = 0;
		live.stalked = -1;
		live.stalkUntil = 0L;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && amount <= SAFE_FALL - 3.0F);
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
					Vec3 look = player.getLookAngle().horizontal();
					look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
					player.setDeltaMovement(look.x * POUNCE_OUT, POUNCE_UP, look.z * POUNCE_OUT);
					player.syncVelocity = true;
					live.rush = POUNCE_TICKS;
					sound(player, SoundEvents.OCELOT_HURT, 0.8F, 0.5F);
					cooldown(player, live, R, POUNCE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, RAKE_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), RAKE_DAMAGE);
				Bleeding.open(target, BodyPart.BODY, true);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0.0, 0.0, 0.0,
					0.0);
				live.hurt = true;
				sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.3F);
				cooldown(player, live, G, RAKE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, STALK_REACH, e -> true);
				if (target == null) {
					return;
				}
				live.stalked = target.getId();
				live.stalkUntil = level.getGameTime() + STALK_TICKS;
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, STALK_TICKS, 0, false, false), player);
				sound(player, SoundEvents.OCELOT_AMBIENT, 0.5F, 0.6F);
				cooldown(player, live, V, STALK_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					roar(player, level);
					cooldown(player, live, B, ROAR_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Every mob round you breaks off and runs. */
	private static void roar(ServerPlayer player, ServerLevel level) {
		for (LivingEntity living : around(player, ROAR_RADIUS)) {
			if (!(living instanceof Mob mob)) {
				continue;
			}
			mob.setTarget(null);
			Vec3 away = mob.position().subtract(player.position()).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			Vec3 to = mob.position().add(away.scale(ROAR_FLEE));
			mob.getNavigation().moveTo(to.x, to.y, to.z, ROAR_SPEED);
		}
		sound(player, SoundEvents.RAVAGER_ROAR, 1.6F, 1.3F);
		ring(level, ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 2.0, 16);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), ROAR_RADIUS, 0.6F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (!player.isSilent()) {
			player.setSilent(true);
		}
		// hard to see while it sneaks
		if (player.isCrouching() && player.tickCount % 10 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
		}
		if (live.rush > 0) {
			live.rush--;
			player.resetFallDistance();
			for (LivingEntity e : around(player, POUNCE_HIT + 1.0)) {
				if (e.getBoundingBox().inflate(POUNCE_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), POUNCE_DAMAGE);
					if (!Tripped.bind(e, player, PIN_TICKS)) {
						e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, 9), player);
					}
					player.setDeltaMovement(Vec3.ZERO);
					player.syncVelocity = true;
					live.hurt = true;
					live.rush = 0;
					sound(player, SoundEvents.OCELOT_HURT, 1.0F, 0.4F);
					break;
				}
			}
			if (player.onGround() && live.rush < POUNCE_TICKS - 3) {
				live.rush = 0;
			}
		}
		if (live.stalked >= 0) {
			Entity e = level.getEntity(live.stalked);
			if (!(e instanceof LivingEntity prey) || !prey.isAlive() || level.getGameTime() > live.stalkUntil) {
				live.stalked = -1;
			} else if (prey.distanceTo(player) <= STALK_NEAR && player.tickCount % 10 == 0) {
				// close on what it stalks: it strikes harder
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20, 1, false, false));
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CRIT, player.position().add(0.0, 1.0, 0.0), 30, 0.8, 0.1);
		sound(player, SoundEvents.RAVAGER_ROAR, 1.2F, 1.6F);
		return true;
	}
}
