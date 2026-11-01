package com.jaymospowers.core;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.38) Andy: "apes should also knock trees down with their strides, as well as towerback, all giants should get this
 * damaging effect". A giant's steps (the Great Ape's, Towerback's) knock over the trees round its feet, and walking into a
 * tree pushes it down. With Jaymo's Kingdoms the tree topples whole, away from the giant ({@code Trees.trample}, as the
 * Aether Giants'), and the falling wood crushes whatever it lands on; without it the trunk just breaks. A player giant
 * only fells where they may build; a mob giant only with mob griefing on.
 */
public final class GiantTrample {
	/** How far past its body a step reaches for trees (blocks, plus this per scale), and how high up its legs it looks for a trunk. */
	public static final double REACH = 1.0, REACH_PER_SCALE = 0.15;
	public static final int HEIGHT = 6;
	/** Walking into a tree: checked this often (ticks). */
	public static final int PUSH_EVERY = 5;
	/** Without Kingdoms: the most logs one broken trunk drops. */
	public static final int FALLBACK_LOGS = 24;

	private static @Nullable Method trample;
	private static boolean looked;

	private GiantTrample() {
	}

	/** Each tick of a giant: walking into a tree pushes it over. */
	public static void tick(LivingEntity giant, long now) {
		if (now % PUSH_EVERY == 0 && giant.horizontalCollision) {
			step(giant);
		}
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

	private static boolean mayFell(LivingEntity giant, ServerLevel level) {
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
			} catch (ReflectiveOperationException | LinkageError e) {
				trample = null;
			}
		}
		return trample;
	}
}
