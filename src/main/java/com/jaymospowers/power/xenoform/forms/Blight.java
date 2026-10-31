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
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.105, Scan B) Blight, the wither alien, boss tier: big, coal-black bone, a ribbed spine that ends in a tail, and
 * three skulls on its shoulders, the middle one largest. 60 health, fists of 7, heavy. Scanned from the Wither only;
 * hidden (not needed for a complete band). The Wither effect can't touch it.
 * <ul>
 * <li>R, Skull: a wither skull from the middle head.</li>
 * <li>G, Volley: a skull from each head, fanned; the middle one is the blue, block-breaking kind.</li>
 * <li>V, Rot Touch: the creature in reach withers, and grass, leaves and crops round it rot away.</li>
 * <li>B, Dark Pulse: a ring of decay that throws back and withers everything round you.</li>
 * </ul>
 * At half health or less its dark shield rises, as the Wither's armour does: shots can't touch it.
 */
public final class Blight extends XenoForm {
	// ---- tuning ----
	public static final double SKULL_SPEED = 1.2;
	public static final int SKULL_COOLDOWN = 20, VOLLEY_COOLDOWN = 80;
	public static final float VOLLEY_FAN = 12.0F;
	public static final double ROT_REACH = 4.0;
	public static final int ROT_RADIUS = 2, ROT_TICKS = 120, ROT_COOLDOWN = 60;
	public static final float ROT_DAMAGE = 6.0F;
	public static final double PULSE_RADIUS = 6.0;
	public static final float PULSE_DAMAGE = 4.0F;
	public static final int PULSE_COOLDOWN = 200;

	public Blight() {
		super("blight", 1.4F, 60.0F, 7.0, -0.05, 0.8, 0.5, Items.WITHER_SKELETON_SKULL, 0x3A3440);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof WitherBoss;
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.darkShield = false;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.WITHER_IMMUNE_TO) || source.getDirectEntity() instanceof WitherSkull skull && skull.getOwner() == player) {
			return false;
		}
		if (live.darkShield && source.is(DamageTypeTags.IS_PROJECTILE)) {
			sound(player, SoundEvents.WITHER_HURT, 0.6F, 1.6F);
			return false;
		}
		return true;
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
					skull(player, level, player.getLookAngle(), false, 0.0);
					sound(player, SoundEvents.WITHER_SHOOT, 0.8F, 1.0F);
					cooldown(player, live, R, SKULL_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					Vec3 look = player.getLookAngle();
					skull(player, level, look.yRot(VOLLEY_FAN * net.minecraft.util.Mth.DEG_TO_RAD), false, -0.6);
					skull(player, level, look, true, 0.0);
					skull(player, level, look.yRot(-VOLLEY_FAN * net.minecraft.util.Mth.DEG_TO_RAD), false, 0.6);
					sound(player, SoundEvents.WITHER_SHOOT, 1.0F, 0.7F);
					cooldown(player, live, G, VOLLEY_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, ROT_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), ROT_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, ROT_TICKS, 1), player);
				rot(level, target.blockPosition());
				live.hurt = true;
				sound(player, SoundEvents.WITHER_AMBIENT, 0.8F, 1.4F);
				cooldown(player, live, V, ROT_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					for (LivingEntity e : around(player, PULSE_RADIUS)) {
						e.hurtServer(level, player.damageSources().playerAttack(player), PULSE_DAMAGE);
						e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), player);
						shove(e, player.position(), 1.2, 0.4);
					}
					ring(level, ParticleTypes.SMOKE, player.position(), PULSE_RADIUS * 0.5, 20);
					ring(level, ParticleTypes.SOUL, player.position(), PULSE_RADIUS, 28);
					rot(level, player.blockPosition());
					sound(player, SoundEvents.WITHER_SPAWN, 0.6F, 1.4F);
					cooldown(player, live, B, PULSE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A wither skull from one of the heads ({@code side}: blocks left or right of the middle). */
	private static void skull(ServerPlayer player, ServerLevel level, Vec3 dir, boolean blue, double side) {
		WitherSkull skull = new WitherSkull(level, player, dir.normalize().scale(SKULL_SPEED));
		Vec3 right = new Vec3(-dir.z, 0.0, dir.x).normalize();
		Vec3 from = player.getEyePosition().add(dir.normalize().scale(1.0)).add(right.scale(side)).add(0.0, side == 0.0 ? 0.1 : -0.2, 0.0);
		skull.setPos(from.x, from.y, from.z);
		skull.setDangerous(blue);
		level.addFreshEntity(skull);
	}

	/** Grass, leaves, flowers and crops round {@code at} rot away (where the game lets mobs grief). */
	private static void rot(ServerLevel level, BlockPos at) {
		if (!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)) {
			return;
		}
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-ROT_RADIUS, -1, -ROT_RADIUS), at.offset(ROT_RADIUS, 2, ROT_RADIUS))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.GRASS_BLOCK)) {
				level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
			} else if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(Blocks.SHORT_GRASS)
				|| state.is(Blocks.TALL_GRASS)) {
				level.destroyBlock(pos, false);
			} else {
				continue;
			}
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0.01);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		player.removeEffect(MobEffects.WITHER);
		boolean shield = player.getHealth() <= player.getMaxHealth() * 0.5F;
		if (shield != live.darkShield) {
			live.darkShield = shield;
			sound(player, shield ? SoundEvents.WITHER_AMBIENT : SoundEvents.WITHER_HURT, 1.0F, 0.6F);
		}
		if (live.darkShield && player.tickCount % 3 == 0) {
			ring(player.level(), ParticleTypes.SOUL_FIRE_FLAME, player.position().add(0.0, 1.2, 0.0), 1.3, 6);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SOUL, player.position().add(0.0, 1.4, 0.0), 40, 1.2, 0.05);
		sound(player, SoundEvents.WITHER_SPAWN, 1.0F, 0.8F);
		return true;
	}
}
