package com.jaymospowers.core;

import com.jaymospowers.core.GiantTrees;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.39) Andy: "apes should also knock trees down with their strides, as well as towerback, all giants should get this
 * damaging effect". A giant's steps (the Great Ape's, Towerback's) knock over the trees round its feet, and walking into a
 * tree pushes it down. With Jaymo's Kingdoms the tree topples whole, away from the giant ({@code Trees.trample}, as the
 * Aether Giants'), and the falling wood crushes whatever it lands on; without it the trunk just breaks. A player giant
 * only fells where they may build; a mob giant only with mob griefing on.
 * <p>
 * When a giant sprints into a tree, the tree is torn up and thrown outward, sending logs flying.
 */
public final class GiantTrample {
	/** How far past its body a step reaches for trees (blocks, plus this per scale), and how high up its legs it looks for a trunk. */
	public static final double REACH = 1.0, REACH_PER_SCALE = 0.15;
	public static final int HEIGHT = 6;
	/** Walking into a tree: checked this often (ticks). */
	public static final int PUSH_EVERY = 5;
	/** Sprinting into a tree: checked every tick while sprinting. */
	public static final int SPRINT_EVERY = 1;
	/** How far ahead a sprint reaches for trees (blocks, plus this per scale). */
	public static final double SPRINT_REACH = 2.0, SPRINT_REACH_PER_SCALE = 0.2;
	/** How high up a sprint checks for trunks (blocks). */
	public static final int SPRINT_HEIGHT = 8;
	/** Without Kingdoms: the most logs one broken trunk drops. */
	public static final int FALLBACK_LOGS = 24;
	/** Sprint hit throw speed multiplier (blocks per tick per log). */
	public static final double SPRINT_THROW_SPEED = 0.15;
	/** Sprint hit upward lift. */
	public static final double SPRINT_THROW_LIFT = 0.3;

	private static @Nullable Method trample;
	private static @Nullable Method hurl;
	private static boolean looked;

	private GiantTrample() {
	}

	/** Each tick of a giant: walking into a tree pushes it over; sprinting into a tree tears it up and throws it. */
		public static void tick(LivingEntity giant, long now) {
			if (now % PUSH_EVERY == 0 && giant.horizontalCollision) {
				step(giant);
			}
			if (giant.isSprinting() && now % SPRINT_EVERY == 0) {
				sprintHit(giant);
			}
		}

		/** Sprinting into a tree: the tree is torn up and thrown outward, sending logs flying. */
		public static void sprintHit(LivingEntity giant) {
			if (!(giant.level() instanceof ServerLevel level) || !mayFell(giant, level)) {
				return;
			}
			Vec3 look = giant.getLookAngle();
			Vec3 start = giant.getEyePosition();
			double reach = SPRINT_REACH + SPRINT_REACH_PER_SCALE * giant.getScale();
			Vec3 end = start.add(look.scale(reach));
			// Check for logs in a box ahead
			AABB box = new AABB(start, end).inflate(giant.getBbWidth() * 0.5, 0.0, giant.getBbWidth() * 0.5);
			int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
			int x1 = Mth.floor(box.maxX), y1 = Mth.floor(box.maxY), z1 = Mth.floor(box.maxZ);
			y1 = Math.min(y1, y0 + SPRINT_HEIGHT);
			for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
				BlockState state = level.getBlockState(pos);
				if (!state.is(BlockTags.LOGS) || !standing(state)) {
					continue;
				}
				// Found a tree: tear it up and throw logs
				sprintFell(level, pos.immutable(), giant, look);
				break; // one tree per sprint hit
			}
		}

		/** Tears up a tree and throws its logs outward in the look direction. */
		private static void sprintFell(ServerLevel level, BlockPos log, LivingEntity giant, Vec3 look) {
			Method method = kingdomsHurl();
			if (method != null) {
				try {
					// Kingdoms' FallingTree.hurl(tree, velocity, tumble, thrower)
					// We need to create a FallingTree entity first - use take to get it
					Method take = Class.forName("com.jaymoskingdoms.FallingTree").getMethod("take", ServerLevel.class, Vec3.class, double.class, Entity.class, int.class);
					Entity tree = (Entity) take.invoke(null, level, Vec3.atCenterOf(log), 0.0, giant, GiantTrees.mostLogs(giant));
					if (tree != null) {
						double speed = SPRINT_THROW_SPEED * Math.max(1, GiantTrees.mostLogs(giant) / 20.0);
						Vec3 v = look.scale(speed).add(0.0, SPRINT_THROW_LIFT, 0.0);
						method.invoke(null, tree, v, GiantTrees.TUMBLE, giant);
						level.playSound(null, log.getX(), log.getY(), log.getZ(), SoundEvents.RAVAGER_ROAR, giant instanceof ServerPlayer ? SoundSource.PLAYERS : SoundSource.HOSTILE, 2.0F, 0.6F);
						return;
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					hurl = null;
				}
			}
			// No Kingdoms: break logs and scatter them
			BlockPos.MutableBlockPos at = log.mutable();
			Vec3 center = Vec3.atCenterOf(log);
			for (int i = 0; i < FALLBACK_LOGS && level.getBlockState(at).is(BlockTags.LOGS); i++, at.move(Direction.UP)) {
				BlockState state = level.getBlockState(at);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.2);
				level.destroyBlock(at, true, giant);
				// Throw the dropped items outward
							if (!level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(0.5)).isEmpty()) {
								for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(0.5))) {
									Vec3 away = look.normalize().scale(0.3 + giant.getRandom().nextDouble() * 0.2).add(0.0, 0.2 + giant.getRandom().nextDouble() * 0.2, 0.0);
									item.setDeltaMovement(away);
									item.setDefaultPickUpDelay();
								}
							}
			}
			level.playSound(null, log, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 2.0F, 0.5F);
		}

	/** One of its steps: the trees round its feet go down. */
	public static void step(LivingEntity giant) {
		if (!(giant.level() instanceof ServerLevel level) || !mayFell(giant, level)) {
			return;
		}
		double reach = REACH + REACH_PER_SCALE * giant.getScale();
		AABB box = giant.getBoundingBox().inflate(reach, 0.0, reach);
		int x0 = Mth.floor(box.minX), y0 = Mth.floor(box.minY), z0 = Mth.floor(box.minZ);
		int x1 = Mth.floor(box.maxX), z1 = Mth.floor(box.maxZ);
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y0 + HEIGHT, z1)) {
			BlockState state = level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) || !standing(state) || giant instanceof ServerPlayer player && !level.mayInteract(player, pos)) {
				continue;
			}
			fell(level, pos.immutable(), giant);
		}
	}

	static boolean mayFell(LivingEntity giant, ServerLevel level) {
		if (giant instanceof ServerPlayer player) {
			return player.mayBuild() && !player.isSpectator();
		}
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** An upright log (a fallen trunk lies on its side and stays where it is). */
	private static boolean standing(BlockState state) {
		return !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
	}

	/** The tree standing on {@code log} comes down: Kingdoms topples it whole; otherwise its trunk breaks. */
	private static void fell(ServerLevel level, BlockPos log, LivingEntity giant) {
		Method method = kingdoms();
		if (method != null) {
			try {
				method.invoke(null, level, log, giant);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				trample = null;
			}
		}
		// no Kingdoms: the trunk snaps where it stood, log by log up
		BlockPos.MutableBlockPos at = log.mutable();
		for (int i = 0; i < FALLBACK_LOGS && level.getBlockState(at).is(BlockTags.LOGS); i++, at.move(Direction.UP)) {
			BlockState state = level.getBlockState(at);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.4, 0.4, 0.4,
				0.1);
			level.destroyBlock(at, true, giant);
		}
		level.playSound(null, log, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.5F, 0.6F);
	}

	/** Kingdoms' {@code Trees.trample(level, log, giant)}, if Kingdoms is loaded. */
		private static @Nullable Method kingdoms() {
			if (!looked) {
				looked = true;
				try {
					trample = Class.forName("com.jaymoskingdoms.Trees").getMethod("trample", ServerLevel.class, BlockPos.class,
							net.minecraft.world.entity.Entity.class);
					hurl = Class.forName("com.jaymoskingdoms.FallingTree").getMethod("hurl", Entity.class, Vec3.class, float.class, Entity.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					trample = null;
					hurl = null;
				}
			}
			return trample;
		}

		/** Kingdoms' {@code FallingTree.hurl(tree, velocity, tumble, thrower)}, if Kingdoms is loaded. */
		private static @Nullable Method kingdomsHurl() {
			kingdoms(); // ensures looked and hurl are set
			return hurl;
		}
	}
