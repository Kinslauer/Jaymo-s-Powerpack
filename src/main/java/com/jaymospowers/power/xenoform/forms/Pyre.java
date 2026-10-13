package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Flames;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Pyre, the living magma (scanned from a blaze): your size, 24 health, burning fists. Fire and lava can't hurt it; water
 * and rain do.
 * <ul>
 * <li>R, Fire Bolt: a small fireball where you look.</li>
 * <li>G (hold), Flame Jet: jets of fire from your hands lift you; let go and you drift down.</li>
 * <li>V, Fire Nova: everything within {@link #NOVA_RADIUS} is burnt and set alight.</li>
 * <li>B, Magma Trail: for {@link #TRAIL_TICKS} the ground burns where you step.</li>
 * </ul>
 */
public final class Pyre extends XenoForm {
	// ---- tuning ----
	public static final int BOLT_COOLDOWN = 20;
	public static final double BOLT_SPEED = 1.6;
	/** The jet: levitation this strong (amplifier), costing this much hunger a tick, needing this much food. */
	public static final int JET_LIFT = 3;
	public static final float JET_EXHAUSTION = 0.06F;
	public static final int JET_MIN_FOOD = 3;
	public static final int JET_SLOW_FALL_TICKS = 50;
	public static final int NOVA_COOLDOWN = 200;
	public static final double NOVA_RADIUS = 5.0;
	public static final float NOVA_DAMAGE = 6.0F;
	public static final int NOVA_BURN_SECONDS = 5;
	public static final int TRAIL_COOLDOWN = 300, TRAIL_TICKS = 120;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;

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
		player.removeEffect(MobEffects.LEVITATION);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			if (down != live.jet) {
				live.jet = down;
				if (!down) {
					player.removeEffect(MobEffects.LEVITATION);
					if (!player.onGround()) {
						player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
					}
				} else {
					cooldown(player, live, G, 1);
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
				if (ready(player, live, B)) {
					live.trailTicks = TRAIL_TICKS;
					sound(player, SoundEvents.BLAZE_BURN, 1.0F, 0.7F);
					cooldown(player, live, B, TRAIL_COOLDOWN);
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
			if (player.getFoodData().getFoodLevel() <= JET_MIN_FOOD && !player.isCreative()) {
				live.jet = false;
				player.removeEffect(MobEffects.LEVITATION);
			} else {
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, JET_LIFT, false, false));
				player.causeFoodExhaustion(JET_EXHAUSTION);
				player.resetFallDistance();
				Vec3 at = player.position();
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 6, 0.3, 0.1, 0.3, 0.04);
				level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.1, at.z, 4, 0.2, 0.1, 0.2, 0.08);
				if (player.tickCount % 8 == 0) {
					sound(player, SoundEvents.FIRE_AMBIENT, 0.8F, 1.3F);
				}
			}
		}
		// the trail
		if (live.trailTicks > 0) {
			live.trailTicks--;
			if (player.onGround() && player.tickCount % 2 == 0) {
				BlockPos at = player.blockPosition();
				if (level.getBlockState(at).isAir() && BaseFireBlock.canBePlacedAt(level, at, Direction.NORTH)) {
					level.setBlock(at, BaseFireBlock.getState(level, at), Block.UPDATE_ALL);
				}
				level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
			}
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

	/** Fire and lava roll off it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		}
		return true;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.jet || player.hasEffect(MobEffects.SLOW_FALLING);
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
	}
}
