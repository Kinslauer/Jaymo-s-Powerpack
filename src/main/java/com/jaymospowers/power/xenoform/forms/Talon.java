package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
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
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.108, Scan C) Talon, the raptor: light and long-armed, brown feathers barred paler, a white-feathered head with a
 * hooked yellow beak, broad wings folded down its arms, hooked talons. 20 health, fists of 4. Scanned from a parrot, or
 * Naturalist's vulture or songbirds ({@code XenoDna}). It flies: hold jump in the air to beat up and where you look;
 * let go and it glides; sneak and it drops. Falls never hurt it.
 * <ul>
 * <li>R, Stoop: a dive where you look; the first creature small enough is snatched in the talons and carried.</li>
 * <li>G: let go of what you carry (from high up, the fall does the rest); with nothing held, a talon strike.</li>
 * <li>V, Far Sight: every monster within {@link #SIGHT_RANGE} blocks shows through fog and walls, and you see in the
 * dark.</li>
 * <li>B, Feather Darts: a fan of hard quills.</li>
 * </ul>
 */
public final class Talon extends XenoForm {
	// ---- tuning ----
	/** Wing beats: this fast toward where you look (slower carrying), easing in; gliding falls no faster than this. */
	public static final double FLAP_SPEED = 0.8, FLAP_CARRY = 0.55, FLAP_EASE = 0.2, FLAP_LIFT = 0.12;
	public static final double GLIDE_FALL = -0.12, GLIDE_DRIFT = 0.05;
	public static final double STOOP_SPEED = 1.4, STOOP_HIT = 1.0;
	public static final int STOOP_TICKS = 25, STOOP_COOLDOWN = 120;
	public static final float STOOP_DAMAGE = 4.0F;
	/** Snatched: only creatures this narrow and this short; held at most this long. */
	public static final float GRAB_WIDTH = 1.4F, GRAB_HEIGHT = 2.2F;
	public static final int CARRY_TICKS = 160;
	public static final double STRIKE_REACH = 3.0;
	public static final float STRIKE_DAMAGE = 5.0F;
	public static final int STRIKE_COOLDOWN = 30;
	public static final double SIGHT_RANGE = 96.0;
	public static final int SIGHT_TICKS = 200, SIGHT_COOLDOWN = 600;
	public static final int DARTS = 5, DARTS_COOLDOWN = 80;
	public static final float DART_FAN = 6.0F, DART_SPEED = 2.2F;
	public static final double DART_DAMAGE = 2.5;

	public Talon() {
		super("talon", 0.9F, 20.0F, 4.0, 0.05, 0.0, 0.0, Items.FEATHER, 0xA0703C);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Parrot;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.rush = 0;
		live.latched = -1;
		live.latchTicks = 0;
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
				if (ready(player, live, R) && live.latched < 0) {
					Vec3 look = player.getLookAngle();
					Vec3 go = look.scale(STOOP_SPEED);
					if (player.onGround()) {
						go = go.add(0.0, 0.6, 0.0);
					}
					player.setDeltaMovement(go);
					player.syncVelocity = true;
					live.rush = STOOP_TICKS;
					sound(player, SoundEvents.PHANTOM_SWOOP, 1.0F, 1.4F);
					cooldown(player, live, R, STOOP_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (live.latched >= 0) {
					release(live);
					sound(player, SoundEvents.PARROT_FLY, 1.0F, 0.8F);
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, STRIKE_REACH, e -> true);
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), STRIKE_DAMAGE);
					live.hurt = true;
					sound(player, SoundEvents.PARROT_HURT, 1.0F, 0.5F);
					cooldown(player, live, G, STRIKE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT_RANGE),
						e -> e instanceof Enemy && e.isAlive())) {
						e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SIGHT_TICKS, 0, false, false), player);
					}
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT_TICKS + 20, 0, false, false));
					sound(player, SoundEvents.PARROT_AMBIENT, 1.0F, 0.6F);
					cooldown(player, live, V, SIGHT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					darts(player, level);
					cooldown(player, live, B, DARTS_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A fan of quills, flat across where you look. */
	private static void darts(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getLookAngle();
		for (int i = 0; i < DARTS; i++) {
			float turn = (i - (DARTS - 1) / 2.0F) * DART_FAN * net.minecraft.util.Mth.DEG_TO_RAD;
			Vec3 dir = look.yRot(turn);
			Arrow dart = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			dart.pickup = AbstractArrow.Pickup.DISALLOWED;
			dart.setBaseDamage(DART_DAMAGE);
			dart.shoot(dir.x, dir.y, dir.z, DART_SPEED, 1.0F);
			level.addFreshEntity(dart);
		}
		sound(player, SoundEvents.ARROW_SHOOT, 1.0F, 1.6F);
		burst(level, ParticleTypes.CLOUD, player.getEyePosition(), 6, 0.2, 0.02);
	}

	private static void release(XenoformPower.Live live) {
		live.latched = -1;
		live.latchTicks = 0;
	}

	/** Whether it beats its wings this tick: in the air, jump held. */
	private static boolean flapping(ServerPlayer player) {
		return !player.onGround() && player.getLastClientInput().jump() && !player.isInWater();
	}

	private static boolean gliding(ServerPlayer player) {
		return !player.onGround() && !player.getLastClientInput().shift() && !player.isInWater() && player.getDeltaMovement().y < 0.0;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.resetFallDistance();
		boolean carrying = live.latched >= 0;
		if (live.rush > 0) {
			live.rush--;
			if (!carrying) {
				for (LivingEntity e : around(player, STOOP_HIT + 2.0)) {
					if (e.getBoundingBox().inflate(STOOP_HIT * 0.5).intersects(player.getBoundingBox())) {
						e.hurtServer(level, player.damageSources().playerAttack(player), STOOP_DAMAGE);
						live.hurt = true;
						live.rush = 0;
						if (e instanceof Mob && e.getBbWidth() <= GRAB_WIDTH && e.getBbHeight() <= GRAB_HEIGHT && !e.isPassenger() && !e.isVehicle()) {
							live.latched = e.getId();
							live.latchTicks = CARRY_TICKS;
							player.setDeltaMovement(player.getDeltaMovement().multiply(0.4, 0.0, 0.4).add(0.0, 0.5, 0.0));
							player.syncVelocity = true;
						}
						sound(player, SoundEvents.PARROT_HURT, 1.2F, 0.5F);
						break;
					}
				}
			}
			if (player.onGround() && live.rush < STOOP_TICKS - 3) {
				live.rush = 0;
			}
		} else if (flapping(player)) {
			double speed = carrying ? FLAP_CARRY : FLAP_SPEED;
			Vec3 want = player.getLookAngle().scale(speed).add(0.0, FLAP_LIFT, 0.0);
			player.setDeltaMovement(player.getDeltaMovement().lerp(want, FLAP_EASE));
			player.syncVelocity = true;
			if (player.tickCount % 8 == 0) {
				sound(player, SoundEvents.PARROT_FLY, 0.8F, 0.7F);
			}
		} else if (gliding(player)) {
			Vec3 m = player.getDeltaMovement();
			Vec3 drift = player.getLookAngle().horizontal().scale(GLIDE_DRIFT);
			player.setDeltaMovement(m.x + drift.x, Math.max(m.y, GLIDE_FALL), m.z + drift.z);
			player.syncVelocity = true;
		}
		if (live.latched >= 0) {
			Entity e = level.getEntity(live.latched);
			if (!(e instanceof LivingEntity held) || !held.isAlive() || --live.latchTicks <= 0 || held.distanceTo(player) > 6.0) {
				release(live);
			} else {
				// held in the talons, hanging under you
				Vec3 under = player.position().add(0.0, -held.getBbHeight() - 0.1, 0.0);
				held.setPos(under.x, under.y, under.z);
				held.setDeltaMovement(player.getDeltaMovement());
				held.resetFallDistance();
				held.syncVelocity = true;
				if (held instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return !player.onGround();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.rush > 0 || flapping(player);
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		if (flapping(player) || live.rush > 0 && !player.onGround()) {
			return XenoformState.FLY_WINGS;
		}
		return gliding(player) ? XenoformState.FLY_GLIDE : XenoformState.FLY_NONE;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), 30, 0.8, 0.05);
		sound(player, SoundEvents.PHANTOM_SWOOP, 1.6F, 0.6F);
		return true;
	}
}
