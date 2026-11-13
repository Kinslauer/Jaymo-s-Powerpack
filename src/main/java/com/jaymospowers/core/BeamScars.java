package com.jaymospowers.core;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Beams scar the land. Where a held beam of ki or fire ends on soft ground (earth, sand, gravel, snow, clay, mud), it
 * leaves a shallow scorched groove: the block it touched is burnt away and the earth under it is baked hard. Sweep the
 * beam and the groove follows it. Stone, wood and anything a player built of harder stuff are never touched.
 * <p>Only where the mob griefing rule allows and the beam's owner may build. The blocks go a few each tick
 * ({@link #PER_TICK}), nearest the queue's front first, so a long sweep never changes the world all in one tick, and
 * one beam can queue only so many ({@link #QUEUE_MOST}).
 */
public final class BeamScars {
	/** How many blocks are burnt away each tick over the whole server, and the most that can be waiting. */
	static final int PER_TICK = 4, QUEUE_MOST = 256;
	/** The groove is this many blocks wide for a beam at least this thick (blocks): otherwise one. */
	static final double WIDE_BEAM = 0.9;
	/** A beam passing this close over the ground, past its own thickness, grooves it (blocks). */
	static final double SKIM = 1.0;

	private record Mark(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final ArrayDeque<Mark> DUE = new ArrayDeque<>();
	private static final Set<Mark> QUEUED = new HashSet<>();

	private BeamScars() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BeamScars::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			DUE.clear();
			QUEUED.clear();
		});
	}

	/** {@code owner}'s beam of thickness {@code radius} ended on a block this tick: the spot is marked to be scarred. */
	public static void touch(ServerPlayer owner, BlockHitResult hit, double radius) {
		if (hit.getType() != HitResult.Type.BLOCK || !(owner.level() instanceof ServerLevel level) || !owner.mayBuild()
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		mark(level, pos);
		if (radius >= WIDE_BEAM) {
			// a thick beam takes the blocks beside it too, across the way it came
			Vec3 look = owner.getLookAngle();
			boolean alongX = Math.abs(look.x) > Math.abs(look.z);
			mark(level, alongX ? pos.north() : pos.east());
			mark(level, alongX ? pos.south() : pos.west());
		}
	}

	/** {@code owner}'s beam of thickness {@code radius} passes {@code at} this tick: soft ground close under it is marked. */
	public static void under(ServerPlayer owner, Vec3 at, double radius) {
		if (!(owner.level() instanceof ServerLevel level) || !owner.mayBuild() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		BlockHitResult hit = level.clip(new ClipContext(at, at.add(0.0, -(radius + SKIM), 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, owner));
		if (hit.getType() == HitResult.Type.BLOCK) {
			touch(owner, hit, radius);
		}
	}

	private static void mark(ServerLevel level, BlockPos pos) {
		if (DUE.size() >= QUEUE_MOST || !soft(level.getBlockState(pos))) {
			return;
		}
		Mark mark = new Mark(level.dimension(), pos.immutable());
		if (QUEUED.add(mark)) {
			DUE.add(mark);
		}
	}

	/** Ground a beam can groove: loose earth and the like, never stone, wood or worked blocks. */
	static boolean soft(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.SNOW) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
			|| state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(Blocks.SNOW) || state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
	}

	private static void tick(MinecraftServer server) {
		for (int i = 0; i < PER_TICK && !DUE.isEmpty(); i++) {
			Mark mark = DUE.poll();
			QUEUED.remove(mark);
			ServerLevel level = server.getLevel(mark.dimension());
			if (level == null || !level.isLoaded(mark.pos())) {
				continue;
			}
			BlockPos pos = mark.pos();
			BlockState state = level.getBlockState(pos);
			if (!soft(state) || level.getBlockEntity(pos) != null) {
				continue;
			}
			// burnt away, nothing dropped; what was growing on it goes with it
			BlockPos above = pos.above();
			if (!level.getBlockState(above).isAir() && level.getBlockState(above).getCollisionShape(level, above).isEmpty()
				&& level.getFluidState(above).isEmpty()) {
				level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			BlockPos below = pos.below();
			BlockState under = level.getBlockState(below);
			if (under.is(BlockTags.DIRT) || under.is(Blocks.FARMLAND) || under.is(Blocks.DIRT_PATH)) {
				level.setBlock(below, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.01);
			level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 1, 0.2, 0.0, 0.2, 0.0);
			if (i == 0) {
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 0.7F);
			}
		}
	}
}
