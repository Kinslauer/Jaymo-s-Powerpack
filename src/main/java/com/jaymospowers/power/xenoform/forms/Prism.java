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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Prism, the crystal golem (scanned from a shulker): a little bigger than you, 30 health, hard to shift.
 * <ul>
 * <li>R, Crystal Shards: three shards fan out where you look, each cutting the first thing in its way.</li>
 * <li>G, Crystal Wall: a wall of amethyst rises ahead of you for {@link #WALL_TICKS}.</li>
 * <li>V (hold), Reflect: you root yourself in crystal; arrows that hit you fly back at whoever shot them, and other
 * projectiles shatter.</li>
 * <li>B, Spike Field: crystal spikes burst from the ground round you, cutting and slowing everything within
 * {@link #SPIKE_RADIUS}.</li>
 * <li>C (ascended), Geode: everything within {@link #GEODE_RADIUS} is cased in crystal for {@link #GEODE_TICKS}: held
 * fast, unable to strike, with amethyst grown round it; you're hard as the crystal meanwhile. Again every
 * {@link #GEODE_COOLDOWN}.</li>
 * </ul>
 * Corrupted: the reflect stance is flawed. One arrow in {@link #CORRUPT_FLAW} isn't turned at all and goes into you.
 */
public final class Prism extends XenoForm {
	// ---- tuning ----
	public static final int SHARD_COOLDOWN = 30, SHARDS = 3;
	public static final double SHARD_RANGE = 16.0, SHARD_SPREAD_DEGREES = 12.0;
	public static final float SHARD_DAMAGE = 4.0F;
	public static final int WALL_COOLDOWN = 240, WALL_TICKS = 200, WALL_AHEAD = 3, WALL_HALF_WIDTH = 2, WALL_HEIGHT = 3;
	/** A reflected arrow flies back this fast. */
	public static final float REFLECT_SPEED = 2.6F;
	public static final int SPIKE_COOLDOWN = 200, SPIKE_SLOW_TICKS = 80;
	public static final double SPIKE_RADIUS = 4.5;
	public static final float SPIKE_DAMAGE = 5.0F;
	/** Geode: its reach, how long it holds, C's cooldown, and the resistance you get while it holds (amplifier). */
	public static final double GEODE_RADIUS = 6.0;
	public static final int GEODE_TICKS = 80, GEODE_COOLDOWN = 400, GEODE_RESISTANCE = 2;
	/** Corrupted: one arrow in this many gets through the stance. */
	public static final int CORRUPT_FLAW = 4;

	private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockParticleOption CRYSTAL_DUST = new BlockParticleOption(ParticleTypes.BLOCK, CRYSTAL);

	public Prism() {
		super("prism", 1.1F, 30.0F, 5.0, -0.05, 0.4, 0.0, Items.AMETHYST_SHARD, 0xC48BF0);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Shulker;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.reflecting = false;
		clearWall(player.level(), live);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_3) {
			if (down != live.reflecting) {
				live.reflecting = down;
				if (down) {
					sound(player, SoundEvents.AMETHYST_BLOCK_PLACE, 1.0F, 0.8F);
					cooldown(player, live, V, 1);
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
					shards(player);
					cooldown(player, live, R, SHARD_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && wall(player, live)) {
					cooldown(player, live, G, WALL_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					spikes(player);
					cooldown(player, live, B, SPIKE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.reflecting) {
			// rooted: no walking while the stance holds
			Vec3 motion = player.getDeltaMovement();
			if (motion.horizontalDistanceSqr() > 1.0E-4) {
				player.setDeltaMovement(0.0, motion.y, 0.0);
				player.syncVelocity = true;
			}
			if (player.tickCount % 4 == 0) {
				level.sendParticles(CRYSTAL_DUST, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.5, 0.6, 0.5, 0.0);
			}
		}
		if (!live.wall.isEmpty() && level.getGameTime() >= live.wallUntil) {
			clearWall(level, live);
		}
	}

	/** In the stance, arrows go back where they came from and everything else shatters on the crystal. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (!live.reflecting || !source.is(DamageTypeTags.IS_PROJECTILE)) {
			return true;
		}
		ServerLevel level = player.level();
		Entity incoming = source.getDirectEntity();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
		if (live.corrupted && incoming instanceof AbstractArrow && player.getRandom().nextInt(CORRUPT_FLAW) == 0) {
			// the flaw: this one finds the crack
			sound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.5F);
			level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.0);
			return true;
		}
		if (incoming instanceof AbstractArrow old) {
			Vec3 back = old.getDeltaMovement().scale(-1.0);
			if (source.getEntity() != null && source.getEntity() != player) {
				back = source.getEntity().getEyePosition().subtract(at);
			}
			if (back.lengthSqr() < 1.0E-4) {
				back = player.getLookAngle();
			}
			Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			arrow.setPos(at.x, at.y, at.z);
			arrow.setBaseDamage(2.5);
			arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
			arrow.shoot(back.x, back.y, back.z, REFLECT_SPEED, 0.5F);
			level.addFreshEntity(arrow);
			old.discard();
			sound(player, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0F, 1.6F);
		} else {
			if (incoming != null) {
				incoming.discard();
			}
			sound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
		}
		level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
		return false;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return false;
	}

	// ---- Geode ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		return geode(player, live);
	}

	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (ultimateReady(player, live)) {
			geode(player, live);
		}
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.geodeUntil <= 0L) {
			return;
		}
		if (level.getGameTime() >= live.geodeUntil) {
			shatter(level, live);
			return;
		}
		// held fast
		for (int id : live.frozen) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				living.setDeltaMovement(0.0, Math.min(0.0, living.getDeltaMovement().y), 0.0);
				living.syncVelocity = true;
				if (living instanceof net.minecraft.world.entity.Mob mob) {
					mob.getNavigation().stop();
					mob.setTarget(null);
				}
				if (player.tickCount % 5 == 0) {
					level.sendParticles(CRYSTAL_DUST, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
				}
			}
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		shatter(player.level(), live);
	}

	/** Crystal grows over everything near: the creatures are held and cased, and projectiles shatter. */
	private static boolean geode(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		shatter(level, live);
		for (LivingEntity living : around(player, GEODE_RADIUS)) {
			live.frozen.add(living.getId());
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GEODE_TICKS, 9, false, false));
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GEODE_TICKS, 4, false, false));
			living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, GEODE_TICKS, 4, false, false));
			// amethyst round its feet and head
			BlockPos feet = living.blockPosition();
			int height = Math.max(1, (int)Math.ceil(living.getBbHeight()));
			for (int h = 0; h < height; h++) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos pos = feet.above(h).relative(side);
					if (level.getBlockState(pos).isAir() && level.getEntities(null, new AABB(pos)).isEmpty()) {
						level.setBlock(pos, CRYSTAL, Block.UPDATE_ALL);
						live.crystals.add(pos);
					}
				}
			}
		}
		for (Entity projectile : level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, player.getBoundingBox().inflate(GEODE_RADIUS))) {
			level.sendParticles(CRYSTAL_DUST, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1, 0.1, 0.0);
			projectile.discard();
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, GEODE_TICKS, GEODE_RESISTANCE, false, false));
		live.geodeUntil = level.getGameTime() + GEODE_TICKS;
		ultimateCooldown(player, live, GEODE_COOLDOWN);
		Vec3 at = player.position();
		ring(level, CRYSTAL_DUST, at, GEODE_RADIUS * 0.5, 16);
		ring(level, CRYSTAL_DUST, at, GEODE_RADIUS, 32);
		sound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.5F, 0.5F);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, at, GEODE_RADIUS * 2.0, 1.1F);
		return true;
	}

	private static void shatter(ServerLevel level, XenoformPower.Live live) {
		for (BlockPos pos : live.crystals) {
			if (level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(CRYSTAL_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!live.crystals.isEmpty()) {
			level.playSound(null, live.crystals.get(0), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		live.crystals.clear();
		live.frozen.clear();
		live.geodeUntil = 0L;
	}

	private static void shards(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (int i = 0; i < SHARDS; i++) {
			double spread = Math.toRadians((i - (SHARDS - 1) / 2.0) * SHARD_SPREAD_DEGREES);
			Vec3 dir = look.yRot((float)spread).normalize();
			Vec3 end = eye.add(dir.scale(SHARD_RANGE));
			AABB box = player.getBoundingBox().expandTowards(dir.scale(SHARD_RANGE)).inflate(1.0);
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
				e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand), 0.4F);
			Vec3 stop = hit != null ? hit.getLocation() : end;
			double length = stop.distanceTo(eye);
			for (double d = 1.0; d < length; d += 1.0) {
				Vec3 p = eye.add(dir.scale(d));
				level.sendParticles(CRYSTAL_DUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (hit != null && hit.getEntity() instanceof LivingEntity target) {
				target.hurtServer(level, player.damageSources().playerAttack(player), SHARD_DAMAGE);
				target.hurtTime = 0;
				level.sendParticles(CRYSTAL_DUST, stop.x, stop.y, stop.z, 8, 0.2, 0.2, 0.2, 0.0);
			}
		}
		sound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.5F);
		sound(player, SoundEvents.SHULKER_SHOOT, 0.6F, 1.4F);
	}

	/** A wall of amethyst {@link #WALL_AHEAD} blocks in front, across your facing; only air and grass make way for it. */
	private static boolean wall(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		clearWall(level, live);
		Direction facing = player.getDirection();
		Direction across = facing.getClockWise();
		BlockPos base = player.blockPosition().relative(facing, WALL_AHEAD);
		for (int w = -WALL_HALF_WIDTH; w <= WALL_HALF_WIDTH; w++) {
			for (int h = 0; h < WALL_HEIGHT; h++) {
				BlockPos pos = base.relative(across, w).above(h);
				BlockState state = level.getBlockState(pos);
				if ((state.isAir() || state.canBeReplaced()) && level.getEntities(player, new AABB(pos)).isEmpty()) {
					level.setBlock(pos, CRYSTAL, Block.UPDATE_ALL);
					live.wall.add(pos);
				}
			}
		}
		if (live.wall.isEmpty()) {
			return false;
		}
		live.wallUntil = level.getGameTime() + WALL_TICKS;
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, base, SoundEvents.AMETHYST_CLUSTER_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static void clearWall(ServerLevel level, XenoformPower.Live live) {
		for (BlockPos pos : live.wall) {
			if (level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(CRYSTAL_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!live.wall.isEmpty()) {
			level.playSound(null, live.wall.get(0), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.9F);
		}
		live.wall.clear();
	}

	private static void spikes(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SPIKE_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), SPIKE_DAMAGE);
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPIKE_SLOW_TICKS, 1));
			shove(living, player.position(), 0.2, 0.35);
		}
		Vec3 feet = player.position();
		ring(level, CRYSTAL_DUST, feet, SPIKE_RADIUS * 0.5, 12);
		ring(level, CRYSTAL_DUST, feet, SPIKE_RADIUS, 24);
		for (int i = 0; i < 16; i++) {
			double a = i / 16.0 * Math.PI * 2.0;
			double r = 1.0 + player.getRandom().nextDouble() * (SPIKE_RADIUS - 1.0);
			level.sendParticles(CRYSTAL_DUST, feet.x + Math.cos(a) * r, feet.y + 0.5, feet.z + Math.sin(a) * r, 4, 0.1, 0.5, 0.1, 0.0);
		}
		sound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2F, 0.6F);
		sound(player, SoundEvents.GLASS_BREAK, 0.8F, 0.7F);
		com.jaymospowers.core.Shakes.heavy(player, feet, SPIKE_RADIUS * 2.0, 0.8F);
	}
}
