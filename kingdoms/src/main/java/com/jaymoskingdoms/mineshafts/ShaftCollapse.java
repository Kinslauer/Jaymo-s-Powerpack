package com.jaymoskingdoms.mineshafts;

import com.jaymoskingdoms.disasters.BlockBudget;
import com.jaymoskingdoms.disasters.Calamity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, mm-collapse) Cave-ins: a quake ({@code disasters/Earthquakes}) brings a mineshaft's ceiling down
 * round whoever is in it, unless timber holds it up: a post or beam (logs, fences, planks) within {@link #SUPPORT_ALONG}
 * along the corridor (and {@link #SUPPORT_ACROSS} across it) of the stretch of ceiling that shakes only creaks and drops
 * dust, so the stretches between a shaft's frames can fall. Every block that falls comes out of the quakes' share of the
 * disasters' block budget ({@code BlockBudget}). So a shaft shored by its miners, or by a player, keeps
 * its roof. A shaft's miners ({@link ShaftMiners}) under unshored rock may be shut in by the fall: their settlement asks
 * for them to be dug out ({@code QuakeRescue}).
 */
public final class ShaftCollapse {
	// ---- tuning ----
	/** How often (ticks) a quake shakes the shafts; each time, by magnitude, the chance a ceiling near a player in one comes down. */
	static final int SHAKE_EVERY = 10;
	static final float[] CAVE_IN = {0.0F, 0.04F, 0.08F, 0.15F, 0.25F, 0.35F};
	/** Each time, by magnitude, the chance a shaft miner under unshored rock is shut in. */
	static final float[] TRAP = {0.0F, 0.0F, 0.01F, 0.03F, 0.05F, 0.08F};
	/** How far from the player the ceiling may give (blocks); how high over them it is looked for. */
	static final int CAVE_REACH = 6, CEILING_UP = 6;
	/**
	 * How far along a corridor, and across it, a timber frame holds a stretch of ceiling (blocks); in a crossing or a room,
	 * the across reach both ways. A vanilla shaft has a frame every fifth block, so one block either side of it is held and
	 * the two between frames are not.
	 */
	static final int SUPPORT_ALONG = 1, SUPPORT_ACROSS = 2;
	/** The share of each block in the fallen stretch that comes down; how hard the rock hurts. */
	static final float FALL_SHARE = 0.7F, FALL_HURT = 1.0F;
	static final int FALL_HURT_MOST = 6;
	/** The hardest rock that shakes loose. */
	static final float LOOSE_HARDEST = 3.0F;

	private ShaftCollapse() {
	}

	/** (from {@code Earthquakes.tick}) One tick of quake {@code c}, of {@code magnitude}, that does damage. */
	public static void quake(ServerLevel level, Calamity c, int magnitude, RandomSource random, long now) {
		if (now % SHAKE_EVERY != 0) {
			return;
		}
		int m = Math.max(0, Math.min(magnitude, CAVE_IN.length - 1));
		for (ServerPlayer p : level.players()) {
			float here = c.over(p.getX(), p.getZ());
			if (here <= 0.1F || p.isSpectator() || !Mineshafts.in(level, p.blockPosition())) {
				continue;
			}
			if (random.nextFloat() < CAVE_IN[m] * here) {
				BlockPos at = p.blockPosition().offset(random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH, 0, random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH);
				caveIn(level, at, random);
			}
		}
		for (Villager v : ShaftMiners.miners(level)) {
			float here = c.over(v.getX(), v.getZ());
			if (here <= 0.2F || random.nextFloat() >= TRAP[m] * here) {
				continue;
			}
			BlockPos ceiling = ceiling(level, v.blockPosition());
			if (ceiling != null && !supported(level, ceiling)) {
				dust(level, ceiling, level.getBlockState(ceiling));
				ShaftMiners.trapped(level, v);
			}
		}
	}

	/** The ceiling over {@code near}: the first solid block over open air, within {@link #CEILING_UP}. */
	static @Nullable BlockPos ceiling(ServerLevel level, BlockPos near) {
		BlockPos.MutableBlockPos at = near.mutable();
		for (int i = 0; i < CEILING_UP; i++) {
			at.move(0, 1, 0);
			BlockState s = level.getBlockState(at);
			if (!s.isAir() && level.getBlockState(at.below()).isAir()) {
				return at.immutable();
			}
		}
		return null;
	}

	/**
	 * Whether timber holds up the ceiling at {@code ceiling}: a log, fence or plank from the floor up to it, within
	 * {@link #SUPPORT_ALONG} along the corridor it's over (the longer side of its piece) and {@link #SUPPORT_ACROSS} across.
	 */
	static boolean supported(ServerLevel level, BlockPos ceiling) {
		BoundingBox piece = Mineshafts.piece(level, ceiling.below());
		int rx = SUPPORT_ACROSS, rz = SUPPORT_ACROSS;
		if (piece != null && piece.getXSpan() > piece.getZSpan()) {
			rx = SUPPORT_ALONG;
		} else if (piece != null && piece.getZSpan() > piece.getXSpan()) {
			rz = SUPPORT_ALONG;
		}
		for (BlockPos at : BlockPos.betweenClosed(ceiling.offset(-rx, -3, -rz), ceiling.offset(rx, 0, rz))) {
			BlockState s = level.getBlockState(at);
			if (s.is(BlockTags.LOGS) || s.is(BlockTags.FENCES) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_SLABS)) {
				return true;
			}
		}
		return false;
	}

	/** The ceiling over {@code near} comes down, or (shored) creaks. */
	static void caveIn(ServerLevel level, BlockPos near, RandomSource random) {
		BlockPos ceiling = ceiling(level, near);
		if (ceiling == null || !Mineshafts.inOrOver(level, ceiling)) {
			return;
		}
		if (supported(level, ceiling)) {
			level.playSound(null, ceiling, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.0F, 0.5F);
			dust(level, ceiling, level.getBlockState(ceiling));
			return;
		}
		level.playSound(null, ceiling, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.4F, 0.5F);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (random.nextFloat() >= FALL_SHARE) {
					continue;
				}
				BlockPos at = ceiling.offset(dx, 0, dz);
				BlockState s = level.getBlockState(at);
				if (!loose(level, at, s) || !level.getBlockState(at.below()).isAir() || !BlockBudget.spend(BlockBudget.Of.QUAKE)) {
					continue;
				}
				FallingBlockEntity falling = FallingBlockEntity.fall(level, at, s);
				falling.setHurtsEntities(FALL_HURT, FALL_HURT_MOST);
				dust(level, at, s);
			}
		}
	}

	private static boolean loose(ServerLevel level, BlockPos at, BlockState s) {
		if (s.isAir() || s.hasBlockEntity() || s.is(Blocks.BEDROCK) || !s.getFluidState().isEmpty() || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS)) {
			return false;
		}
		float hardness = s.getDestroySpeed(level, at);
		return hardness >= 0.0F && hardness <= LOOSE_HARDEST && s.isCollisionShapeFullBlock(level, at);
	}

	private static void dust(ServerLevel level, BlockPos at, BlockState s) {
		if (!s.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, s), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 10, 0.8, 0.1, 0.8, 0.0);
		}
	}
}
