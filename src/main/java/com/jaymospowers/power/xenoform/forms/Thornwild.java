package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.115, Scan F) Thornwild, the plant: a body of bark and twisted wood, mossy at the shoulders, thorns down its
 * arms, a face of knots with two glowing amber eyes. 28 health, fists of 5. It heals in sunlight. Scanned from a
 * creaking, or Enderscape's rustle ({@code XenoDna}).
 * <ul>
 * <li>R, Roots: the creature you aim at is held fast by roots, and they bite.</li>
 * <li>G, Thorn Volley: a fan of thorns.</li>
 * <li>V, Hedge: a wall of leaves grows in front of you for cover; it withers after {@link #HEDGE_TICKS} ticks.</li>
 * <li>B, Bloom: for {@link #BLOOM_TICKS} ticks, crops and saplings round your feet grow.</li>
 * </ul>
 */
public final class Thornwild extends XenoForm {
	// ---- tuning ----
	public static final int SUN_EVERY = 40;
	public static final float SUN_HEAL = 1.0F;
	public static final double ROOT_REACH = 16.0;
	public static final int ROOT_TICKS = 80, ROOT_EVERY = 20, ROOT_COOLDOWN = 200;
	public static final float ROOT_DAMAGE = 1.0F;
	public static final int THORNS = 5, THORN_COOLDOWN = 60;
	public static final float THORN_FAN = 7.0F, THORN_SPEED = 2.2F;
	public static final double THORN_DAMAGE = 2.5;
	public static final int HEDGE_TICKS = 600, HEDGE_COOLDOWN = 300, HEDGE_WIDTH = 2, HEDGE_HEIGHT = 2;
	public static final int BLOOM_TICKS = 200, BLOOM_EVERY = 10, BLOOM_TRIES = 3, BLOOM_COOLDOWN = 600;
	public static final int BLOOM_RADIUS = 3;

	public Thornwild() {
		super("thornwild", 1.1F, 28.0F, 5.0, -0.01, 0.4, 0.0, Items.OAK_SAPLING, 0x5A4630);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Creaking;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		wither(player.level(), live, Long.MAX_VALUE);
		live.latched = -1;
		live.latchTicks = live.brood = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, ROOT_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROOT_TICKS, 9), player);
				target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ROOT_TICKS, 250, false, false), player);
				target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
				target.syncVelocity = true;
				live.latched = target.getId();
				live.latchTicks = ROOT_TICKS;
				BlockState under = level.getBlockState(target.blockPosition().below());
				if (!under.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), target.getX(), target.getY() + 0.1, target.getZ(), 20, 0.4, 0.1, 0.4, 0.1);
				}
				sound(player, SoundEvents.ROOTED_DIRT_BREAK, 1.4F, 0.6F);
				cooldown(player, live, R, ROOT_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					Vec3 look = player.getLookAngle();
					for (int i = 0; i < THORNS; i++) {
						float turn = (i - (THORNS - 1) / 2.0F) * THORN_FAN * net.minecraft.util.Mth.DEG_TO_RAD;
						Vec3 dir = look.yRot(turn);
						Arrow thorn = new Arrow(level, player, new ItemStack(Items.ARROW), null);
						thorn.pickup = AbstractArrow.Pickup.DISALLOWED;
						thorn.setBaseDamage(THORN_DAMAGE);
						thorn.shoot(dir.x, dir.y, dir.z, THORN_SPEED, 1.5F);
						level.addFreshEntity(thorn);
					}
					burst(level, ParticleTypes.SPORE_BLOSSOM_AIR, player.getEyePosition(), 8, 0.3, 0.02);
					sound(player, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.2F, 0.7F);
					cooldown(player, live, G, THORN_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && hedge(player, live, level)) {
					cooldown(player, live, V, HEDGE_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.brood = BLOOM_TICKS;
					burst(level, ParticleTypes.HAPPY_VILLAGER, player.position().add(0.0, 0.3, 0.0), 20, 1.5, 0.02);
					sound(player, SoundEvents.AZALEA_LEAVES_PLACE, 1.4F, 0.8F);
					cooldown(player, live, B, BLOOM_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A wall of leaves two blocks ahead, across where it faces; each leaf withers in time. */
	private static boolean hedge(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		if (!player.mayBuild()) {
			return false;
		}
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos centre = player.blockPosition().relative(facing, 2);
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		long until = level.getGameTime() + HEDGE_TICKS;
		int grown = 0;
		for (int s = -HEDGE_WIDTH; s <= HEDGE_WIDTH; s++) {
			for (int h = 0; h < HEDGE_HEIGHT; h++) {
				BlockPos at = centre.relative(side, s).above(h);
				if (!level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty() || !level.mayInteract(player, at)) {
					continue;
				}
				level.setBlockAndUpdate(at, leaf);
				live.lights.put(at.asLong(), until);
				grown++;
			}
		}
		if (grown == 0) {
			return false;
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, centre.getX() + 0.5, centre.getY() + 1.0, centre.getZ() + 0.5, 20, 1.2, 0.6, 1.2, 0.0);
		sound(player, SoundEvents.AZALEA_LEAVES_PLACE, 1.4F, 0.8F);
		return true;
	}

	/** The hedge's leaves that have stood past {@code now} wither away. */
	private static void wither(ServerLevel level, XenoformPower.Live live, long now) {
		var it = live.lights.long2LongEntrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			if (entry.getLongValue() <= now) {
				BlockPos at = BlockPos.of(entry.getLongKey());
				if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.OAK_LEAVES)) {
					level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
					level.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
				}
				it.remove();
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// it heals in sunlight
		if (player.tickCount % SUN_EVERY == 0 && level.isBrightOutside() && !level.isRaining() && level.canSeeSky(player.blockPosition().above())) {
			player.heal(SUN_HEAL);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.6, player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		}
		if (!live.lights.isEmpty() && player.tickCount % 20 == 0) {
			wither(level, live, level.getGameTime());
		}
		if (live.latched >= 0) {
			Entity e = level.getEntity(live.latched);
			if (!(e instanceof LivingEntity held) || !held.isAlive() || --live.latchTicks <= 0) {
				live.latched = -1;
				live.latchTicks = 0;
			} else {
				held.setDeltaMovement(0.0, Math.min(0.0, held.getDeltaMovement().y), 0.0);
				held.syncVelocity = true;
				if (live.latchTicks % ROOT_EVERY == 0) {
					held.setInvulnerableTime(0);
					held.hurtServer(level, player.damageSources().playerAttack(player), ROOT_DAMAGE);
				}
				if (live.latchTicks % 4 == 0) {
					level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, held.getX(), held.getY() + 0.3, held.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
				}
			}
		}
		if (live.brood > 0) {
			live.brood--;
			if (live.brood % BLOOM_EVERY == 0) {
				bloom(player, level);
			}
		}
	}

	/** A few plants round its feet grow, as bone meal grows them. */
	private static void bloom(ServerPlayer player, ServerLevel level) {
		var random = player.getRandom();
		for (int i = 0; i < BLOOM_TRIES; i++) {
			BlockPos at = player.blockPosition().offset(random.nextInt(BLOOM_RADIUS * 2 + 1) - BLOOM_RADIUS, random.nextInt(3) - 1,
				random.nextInt(BLOOM_RADIUS * 2 + 1) - BLOOM_RADIUS);
			if (level.mayInteract(player, at) && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, at)) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SPORE_BLOSSOM_AIR, player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.05);
		sound(player, SoundEvents.CREAKING_ACTIVATE, 1.6F, 0.7F);
		return true;
	}
}
