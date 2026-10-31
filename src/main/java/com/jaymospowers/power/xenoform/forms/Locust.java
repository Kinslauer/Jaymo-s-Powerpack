package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.102, Xeno-Band patch 5) Locust, the crashing hopper: lean and green, long folded hind legs, feelers, wing cases
 * on its back. 22 health, fists of 4, quick. Scanned from a goat. It never takes fall damage.
 * <ul>
 * <li>R (hold, let go), Crash Leap: the longer you hold (up to {@link #CHARGE_MAX} ticks) the farther you go; where you
 * come down, a shock ring hits everything round, harder for a fuller charge.</li>
 * <li>G, Kick: both hind legs into the creature in front of you; a big shove, and you spring back.</li>
 * <li>V, Wall Bounce: against a wall, kick off it, up and away; chain them up a shaft or across an alley.</li>
 * <li>B, Swarm Hop: three short quick hops where you look.</li>
 * </ul>
 */
public final class Locust extends XenoForm {
	// ---- tuning ----
	public static final int CHARGE_MAX = 30, LEAP_COOLDOWN = 60;
	public static final double LEAP_MIN = 0.9, LEAP_MAX = 2.4, LEAP_UP = 0.7;
	public static final double RING_MIN = 3.0, RING_MAX = 6.0;
	public static final float RING_MIN_DAMAGE = 4.0F, RING_MAX_DAMAGE = 9.0F;
	public static final double KICK_REACH = 3.5, KICK_SHOVE = 1.8, KICK_RECOIL = 0.6;
	public static final float KICK_DAMAGE = 6.0F;
	public static final int KICK_COOLDOWN = 30;
	public static final double BOUNCE_OUT = 0.9, BOUNCE_UP = 0.85;
	public static final int BOUNCE_COOLDOWN = 8;
	public static final double HOP_SPEED = 0.7, HOP_UP = 0.45;
	public static final int HOPS = 3, HOP_EVERY = 6, HOP_COOLDOWN = 60;

	public Locust() {
		super("locust", 0.95F, 22.0F, 4.0, 0.12, 0.2, 0.5, Items.GOAT_HORN, 0x8DB33A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Goat;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.leapCharge = 0;
		live.crashing = false;
		live.horn = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_1) {
			if (down) {
				if (ready(player, live, R) && live.leapCharge == 0) {
					live.leapCharge = 1;
				}
			} else if (live.leapCharge > 0) {
				double t = Math.min(1.0, live.leapCharge / (double)CHARGE_MAX);
				live.leapCharge = 0;
				Vec3 look = player.getLookAngle();
				double speed = LEAP_MIN + (LEAP_MAX - LEAP_MIN) * t;
				player.setDeltaMovement(look.x * speed, Math.max(LEAP_UP, look.y * speed + LEAP_UP * t), look.z * speed);
				player.syncVelocity = true;
				live.crashing = true;
				live.hornFrom = t;
				sound(player, SoundEvents.GOAT_SCREAMING_LONG_JUMP, 1.0F, 1.3F);
				cooldown(player, live, R, LEAP_COOLDOWN);
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, KICK_REACH, e -> true);
				if (target != null) {
					target.hurtServer(level, player.damageSources().playerAttack(player), KICK_DAMAGE);
					shove(target, player.position(), KICK_SHOVE, 0.4);
					Vec3 back = player.getLookAngle().horizontal().normalize().scale(-KICK_RECOIL);
					player.setDeltaMovement(player.getDeltaMovement().add(back).add(0.0, 0.25, 0.0));
					player.syncVelocity = true;
					live.hurt = true;
				}
				sound(player, SoundEvents.GOAT_RAM_IMPACT, 1.0F, 1.3F);
				cooldown(player, live, G, KICK_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				Direction wall = wallBeside(player);
				if (wall == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.locust.no_wall").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				Vec3 away = Vec3.atLowerCornerOf(wall.getOpposite().getUnitVec3i());
				Vec3 look = player.getLookAngle().horizontal();
				Vec3 dir = away.add(look.scale(0.6)).normalize();
				player.setDeltaMovement(dir.x * BOUNCE_OUT, BOUNCE_UP, dir.z * BOUNCE_OUT);
				player.syncVelocity = true;
				player.resetFallDistance();
				BlockState hit = level.getBlockState(player.blockPosition().relative(wall));
				if (!hit.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, hit), player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
				}
				sound(player, SoundEvents.GOAT_LONG_JUMP, 1.0F, 1.5F);
				cooldown(player, live, V, BOUNCE_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && live.horn == 0) {
					live.horn = HOPS * HOP_EVERY;
					cooldown(player, live, B, HOP_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** The side a wall is on (beside you, at foot or head height), or null. */
	private static Direction wallBeside(ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		Direction best = null;
		double bestDot = -2.0;
		Vec3 look = player.getLookAngle().horizontal();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos at = feet.relative(d);
			boolean solid = !player.level().getBlockState(at).getCollisionShape(player.level(), at).isEmpty()
				|| !player.level().getBlockState(at.above()).getCollisionShape(player.level(), at.above()).isEmpty();
			if (!solid) {
				continue;
			}
			// close enough to touch
			double gap = d.getAxis() == Direction.Axis.X ? Math.abs(player.getX() - (feet.getX() + 0.5 + d.getStepX() * 0.5)) : Math.abs(player.getZ() - (feet.getZ() + 0.5 + d.getStepZ() * 0.5));
			if (gap > player.getBbWidth() * 0.5 + 0.45) {
				continue;
			}
			// prefer the wall you face
			double dot = look.dot(Vec3.atLowerCornerOf(d.getUnitVec3i()));
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		return best;
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.leapCharge > 0) {
			live.leapCharge = Math.min(CHARGE_MAX, live.leapCharge + 1);
			if (live.leapCharge % 5 == 0) {
				player.sendOverlayMessage(Component.literal("|".repeat(live.leapCharge / 3) + ".".repeat((CHARGE_MAX - live.leapCharge) / 3)).withStyle(ChatFormatting.GREEN));
			}
		}
		if (live.horn > 0) {
			if (live.horn % HOP_EVERY == 0) {
				Vec3 look = player.getLookAngle().horizontal().normalize();
				player.setDeltaMovement(look.x * HOP_SPEED, HOP_UP, look.z * HOP_SPEED);
				player.syncVelocity = true;
				sound(player, SoundEvents.SLIME_JUMP_SMALL, 0.6F, 1.6F);
			}
			live.horn--;
		}
		if (live.crashing && player.onGround() && player.getDeltaMovement().y <= 0.0) {
			live.crashing = false;
			crash(player, level, live.hornFrom);
		}
	}

	/** The landing: a shock ring, wider and harder for a fuller charge {@code t} (0-1). */
	private static void crash(ServerPlayer player, ServerLevel level, double t) {
		double radius = RING_MIN + (RING_MAX - RING_MIN) * t;
		float damage = (float)(RING_MIN_DAMAGE + (RING_MAX_DAMAGE - RING_MIN_DAMAGE) * t);
		for (LivingEntity e : around(player, radius)) {
			if (!e.onGround()) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			shove(e, player.position(), 0.8, 0.4);
		}
		BlockState under = level.getBlockState(player.blockPosition().below());
		for (double r = 1.0; r <= radius; r += 1.0) {
			if (!under.isAir()) {
				ring(level, new BlockParticleOption(ParticleTypes.BLOCK, under), player.position(), r, (int)(r * 8));
			}
		}
		ring(level, ParticleTypes.CLOUD, player.position(), radius, 24);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.6F);
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CLOUD, player.position(), 30, 1.0, 0.1);
		sound(player, SoundEvents.GOAT_SCREAMING_LONG_JUMP, 1.4F, 0.7F);
		return true;
	}
}
