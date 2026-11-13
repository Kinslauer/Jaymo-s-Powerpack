package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Flames;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
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
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Pyre, the living magma (scanned from a blaze): your size, 24 health, burning fists. Fire and lava can't hurt it; water
 * and rain do.
 * <ul>
 * <li>R, Fire Bolt: a small fireball where you look.</li>
 * <li>G, Flame Jet (0.18.89, a toggle, steered like a Z Fighter's flight on the client: {@code PyreFlightClient}): hover
 * with WASD, Jump and Sneak, Sprint to streak along your look; G again (or Sneak on the ground) ends it, and you drift
 * down. Free. It leaves a comet's tail of flame ({@code PyreComet}).</li>
 * <li>V, Fire Nova: everything within {@link #NOVA_RADIUS} is burnt and set alight.</li>
 * <li>B, Fire Dive: from the ground you rise first, then you dive the way you look as a bird of fire, steering a
 * little, burning whatever you pass through; where you come down everything within {@link #DIVE_RADIUS} is burnt and
 * thrown back. From the air (the jet) the dive starts at once and ends the jet. It took Magma Trail's place.</li>
 * <li>C (ascended), Sunheart: (0.20.21) its Ultimate is its own evolved form, {@link com.jaymospowers.power.xenoform.forms.ultimate.Sunheart}.</li>
 * </ul>
 */
public final class Pyre extends XenoForm {
	// ---- tuning ----
	public static final int BOLT_COOLDOWN = 20;
	public static final double BOLT_SPEED = 1.6;
	/** The jet: slow fall for this long after it ends in the air. Free; its speeds are the client's ({@code PyreFlightClient}). */
	public static final int JET_SLOW_FALL_TICKS = 50;
	public static final int NOVA_COOLDOWN = 200;
	public static final double NOVA_RADIUS = 5.0;
	/** Screen shake from the Fire Nova (degrees), and how far it's felt. */
	public static final float NOVA_SHAKE = 0.9F;
	public static final double NOVA_SHAKE_RANGE = 14.0;
	public static final float NOVA_DAMAGE = 6.0F;
	public static final int NOVA_BURN_SECONDS = 5;
	/** The dive: its wait, how long it can last, the rise before it from the ground (ticks), its speeds and how much the look steers it each tick. */
	public static final int DIVE_COOLDOWN = 300, DIVE_TICKS = 40, DIVE_RISE = 7;
	public static final double DIVE_SPEED = 1.5, DIVE_RISE_SPEED = 0.95, DIVE_STEER = 0.1;
	/** The dive never goes flatter than this (the look's y is held under it), so it always comes down. */
	public static final double DIVE_DOWN = -0.3;
	/** What it does on the way (within {@link #DIVE_TOUCH}) and where it lands (within {@link #DIVE_RADIUS}). */
	public static final double DIVE_TOUCH = 1.6, DIVE_RADIUS = 5.0;
	public static final float DIVE_PASS_DAMAGE = 4.0F, DIVE_DAMAGE = 10.0F, DIVE_SHAKE = 1.1F;
	public static final int DIVE_BURN_SECONDS = 6;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;

	/** (0.20.21) Sunheart is its own evolved form now: the Ultimate move, health bar, model and kit are in {@code Sunheart}. */
	private final com.jaymospowers.power.xenoform.XenoUltimate sunheart = new com.jaymospowers.power.xenoform.forms.ultimate.Sunheart(this);

	public Pyre() {
		super("pyre", 1.0F, 24.0F, 4.0, 0.0, 0.0, 0.0, Items.MAGMA_BLOCK, 0xFF8A2A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Blaze;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.jet = false;
		live.diveTicks = 0;
		player.removeEffect(MobEffects.LEVITATION);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		// sneak + R / V / B: rays, the flame ring, a fire wall (the fire kit's gaps)
		if (down && player.isShiftKeyDown() && (key == PowerKey.ABILITY_1 || key == PowerKey.ABILITY_3 || key == PowerKey.ABILITY_4)) {
			if (key == PowerKey.ABILITY_1) {
				com.jaymospowers.power.ember.FireKit.rays(player);
			} else if (key == PowerKey.ABILITY_3) {
				com.jaymospowers.power.ember.FireKit.ring(player, false);
			} else {
				com.jaymospowers.power.ember.FireKit.wall(player);
			}
			return;
		}
		if (key == PowerKey.ABILITY_2) {
			// a toggle: G takes off, G again ends it (the client flies it, as a Z Fighter's flight)
			if (down) {
				if (live.jet) {
					endJet(player, live);
				} else {
					live.jet = true;
					cooldown(player, live, G, 1);
					sound(player, SoundEvents.BLAZE_SHOOT, 0.8F, 0.7F);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					bolt(player);
					cooldown(player, live, R, BOLT_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					nova(player);
					cooldown(player, live, V, NOVA_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && live.diveTicks <= 0 && !player.isInWater() && !player.isPassenger()) {
					startDive(player, live);
					cooldown(player, live, B, DIVE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the jet
		if (live.jet) {
			// the client flies it (PyreFlightClient); everyone's client draws the comet (PyreComet)
			player.resetFallDistance();
			if (player.onGround() && player.isShiftKeyDown()) {
				// sneaking on the ground: landed
				endJet(player, live);
			} else if (player.tickCount % 8 == 0) {
				sound(player, SoundEvents.FIRE_AMBIENT, 0.8F, 1.3F);
			}
		}
		// the dive
		if (live.diveTicks > 0) {
			dive(player, live);
		}
		// always smouldering; wet, it hisses and hurts
		if (player.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.3, 0.5, 0.3, 0.01);
		}
		if (player.isInWaterOrRain() && !player.isCreative()) {
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
			if (player.tickCount % WET_EVERY == 0) {
				player.hurtServer(level, player.damageSources().drown(), WET_DAMAGE);
				sound(player, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
			}
		}
	}

	private static void endJet(ServerPlayer player, XenoformPower.Live live) {
		live.jet = false;
		player.removeEffect(MobEffects.LEVITATION);
		if (!player.onGround()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
		}
	}

	/** Fire and lava roll off it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		}
		return true;
	}

	/** Z tap: a column of fire from the sky onto the spot looked at. */
	@Override
	public boolean onModeTap(ServerPlayer player, XenoformPower.Live live) {
		return com.jaymospowers.power.ember.FireKit.column(player);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.jet || live.diveTicks > 0 || player.hasEffect(MobEffects.SLOW_FALLING);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.jet || live.diveTicks > 0;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return !live.jet || player.onGround() ? XenoformState.FLY_NONE : XenoformState.FLY_JET;
	}

	// ---- Sunheart ----

	/** (0.20.21) Sunheart is its own evolved form: see {@link com.jaymospowers.power.xenoform.forms.ultimate.Sunheart}. */
	@Override
	public com.jaymospowers.power.xenoform.XenoUltimate evolved() {
		return sunheart;
	}

	private static void bolt(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		SmallFireball bolt = new SmallFireball(level, player, look.scale(BOLT_SPEED));
		Vec3 from = player.getEyePosition().add(look.scale(0.8));
		bolt.setPos(from.x, from.y - 0.2, from.z);
		level.addFreshEntity(bolt);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 1.2F);
	}

	// ---- B: Fire Dive ----

	private static void startDive(ServerPlayer player, XenoformPower.Live live) {
		boolean fromGround = player.onGround();
		if (live.jet) {
			// the dive takes over from the jet
			live.jet = false;
			player.removeEffect(MobEffects.LEVITATION);
		}
		live.diveTicks = DIVE_TICKS + (fromGround ? DIVE_RISE : 0);
		Vec3 heading = diveWay(player.getLookAngle());
		live.diveX = heading.x;
		live.diveY = heading.y;
		live.diveZ = heading.z;
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
		sound(player, SoundEvents.FIRECHARGE_USE, 1.0F, 0.7F);
	}

	/** The way a dive goes for a look: the same way, but always downward. */
	private static Vec3 diveWay(Vec3 look) {
		return new Vec3(look.x, Math.min(look.y, DIVE_DOWN), look.z).normalize();
	}

	private static void drive(ServerPlayer player, Vec3 velocity) {
		player.setDeltaMovement(velocity);
		player.needsSync = true;
		player.syncVelocity = true;
	}

	private static void dive(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.diveTicks--;
		player.resetFallDistance();
		if (player.isInWater() || player.isPassenger()) {
			live.diveTicks = 0;
			return;
		}
		Vec3 look = player.getLookAngle();
		if (live.diveTicks >= DIVE_TICKS) {
			// the rise before it: up, drifting the way you look
			drive(player, new Vec3(look.x * 0.25, DIVE_RISE_SPEED, look.z * 0.25));
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 6, 0.3, 0.2, 0.3, 0.03);
			return;
		}
		Vec3 heading = new Vec3(live.diveX, live.diveY, live.diveZ).scale(1.0 - DIVE_STEER).add(diveWay(look).scale(DIVE_STEER)).normalize();
		live.diveX = heading.x;
		live.diveY = heading.y;
		live.diveZ = heading.z;
		boolean diving = live.diveTicks < DIVE_TICKS - 2;
		if (diving && (player.onGround() || player.horizontalCollision || player.verticalCollision)) {
			live.diveTicks = 0;
			land(player);
			return;
		}
		if (live.diveTicks <= 0) {
			// it ran out in the air: no burst, a soft way down
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
			return;
		}
		drive(player, heading.scale(DIVE_SPEED));
		// the bird: two swept wings of fire and the flame it trails
		Vec3 chest = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		Vec3 side = heading.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		if (live.diveTicks % 2 == 0) {
			Vec3 back = chest.subtract(heading.scale(1.3)).add(0.0, 0.45, 0.0);
			com.jaymospowers.core.fx.MagicFx.beam(level, chest, back.add(side.scale(1.7)), 0.18F, 0xFF8A2A, 4);
			com.jaymospowers.core.fx.MagicFx.beam(level, chest, back.subtract(side.scale(1.7)), 0.18F, 0xFF8A2A, 4);
		}
		level.sendParticles(ParticleTypes.FLAME, chest.x, chest.y, chest.z, 8, 0.35, 0.35, 0.35, 0.02);
		for (LivingEntity living : around(player, DIVE_TOUCH)) {
			if (living.hurtServer(level, player.damageSources().playerAttack(player), DIVE_PASS_DAMAGE)) {
				living.igniteForSeconds(DIVE_BURN_SECONDS);
			}
		}
	}

	private static void land(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		player.resetFallDistance();
		drive(player, Vec3.ZERO);
		for (LivingEntity living : around(player, DIVE_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), DIVE_DAMAGE);
			shove(living, player.position(), 0.9, 0.45);
		}
		Flames.area(level, at, DIVE_RADIUS, DIVE_BURN_SECONDS, player, e -> e != player);
		com.jaymospowers.core.fx.MagicFx.blast(level, at, (float) DIVE_RADIUS, 0xFF8A2A);
		ring(level, ParticleTypes.FLAME, at, DIVE_RADIUS, 40);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 24, 1.2, 0.5, 1.2, 0.0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.1F);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.5F);
		com.jaymospowers.core.Shakes.heavy(player, at, NOVA_SHAKE_RANGE, DIVE_SHAKE);
	}

	private static void nova(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		for (LivingEntity living : around(player, NOVA_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), NOVA_DAMAGE);
			shove(living, player.position(), 0.6, 0.3);
		}
		Flames.area(level, at, NOVA_RADIUS, NOVA_BURN_SECONDS, player, e -> e != player);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS * 0.5, 24);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS, 40);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 20, 1.0, 0.5, 1.0, 0.0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, at, NOVA_SHAKE_RANGE, NOVA_SHAKE);
	}
}
