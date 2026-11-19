package com.jaymospowers.power.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The weapon arm against a tree: charged while looking at a log, the symbiote always forms the axe (any strain, the plain
 * black suit too: {@link WebMorph}), and each stroke that lands on the tree bites it. A tree comes down in one to three
 * blows by its size ({@link #BLOWS_ONE} and {@link #BLOWS_TWO} logs); the last one brings the whole tree down, every log
 * joined to the one struck (at most {@link #MAX_LOGS}, from it upward, the leaves left to fall by themselves) dropping
 * as it would if each were broken by hand, so claims and protection still have their say. A stack of logs that grew no
 * leaves (a cabin's wall) is only the one log. The blows count for {@link #WINDOW_TICKS} after the last one.
 */
public final class WebSymbioteFell {
	/** A tree of at most this many logs falls to one blow, of at most {@code BLOWS_TWO} to two, a bigger one takes three. */
	public static final int BLOWS_ONE = 12, BLOWS_TWO = 40;
	/** The most logs one tree brings down, and how far (blocks, each way) from the log struck they may lie. */
	public static final int MAX_LOGS = 200, RADIUS = 10;
	/** Blows are forgotten this long (ticks) after the last. */
	public static final int WINDOW_TICKS = 200;

	/** Blows so far on each tree (keyed by its lowest log): {count, game time of the last}. */
	private static final Map<GlobalPos, long[]> BLOWS = new HashMap<>();

	private WebSymbioteFell() {
	}

	/** The log under the crosshair within {@code reach} blocks of the eyes, or null. */
	static @Nullable BlockPos aimedLog(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK && player.level().getBlockState(hit.getBlockPos()).is(BlockTags.LOGS)) {
			return hit.getBlockPos();
		}
		return null;
	}

	/** Whether the weapon arm would go for a tree now (the axe is picked on this, whatever the strain). */
	static boolean lookingAtLog(ServerPlayer player) {
		return aimedLog(player, WebMorph.REACH) != null;
	}

	/** The axe lands: bites the log under the crosshair, and fells the tree on the last blow. False if there was no log to hit. */
	static boolean chop(ServerPlayer player) {
		BlockPos hit = aimedLog(player, WebMorph.REACH + WebMorph.REACH_SLACK);
		if (hit == null) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		// the tree is the logs joined to the foot of the trunk the blow landed on
		BlockPos base = hit;
		for (int i = 0; i < RADIUS * 8 && level.getBlockState(base.below()).is(BlockTags.LOGS); i++) {
			base = base.below();
		}
		List<BlockPos> logs = tree(level, base);
		int needed = logs.size() <= BLOWS_ONE ? 1 : logs.size() <= BLOWS_TWO ? 2 : 3;
		GlobalPos key = GlobalPos.of(level.dimension(), base.immutable());
		BLOWS.values().removeIf(blow -> now - blow[1] > WINDOW_TICKS);
		long[] blow = BLOWS.computeIfAbsent(key, k -> new long[] {0L, now});
		blow[0]++;
		blow[1] = now;
		BlockState state = level.getBlockState(hit);
		Vec3 at = Vec3.atCenterOf(hit);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 20, 0.35, 0.35, 0.35, 0.1);
		level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.2F, 0.7F);
		com.jaymospowers.core.Shakes.send(player, 0.35F, at);
		if (blow[0] < needed) {
			// the trunk shudders: it isn't through yet
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 1.2F, 0.6F);
			return true;
		}
		BLOWS.remove(key);
		fell(player, level, logs);
		return true;
	}

	/** Every log of the tree, broken from the top down (as a player would break each: claims and protection apply). */
	private static void fell(ServerPlayer player, ServerLevel level, List<BlockPos> logs) {
		List<BlockPos> order = new ArrayList<>(logs);
		order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos log : order) {
			if (level.getBlockState(log).is(BlockTags.LOGS)) {
				player.gameMode.destroyBlock(log);
			}
		}
		BlockPos first = logs.get(0);
		level.playSound(null, first.getX() + 0.5, first.getY() + 0.5, first.getZ() + 0.5, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, 0.8F, Vec3.atCenterOf(first));
	}

	/**
	 * The logs joined to {@code origin} (corners count), from it upward only, at most {@link #MAX_LOGS}, with {@code origin}
	 * among them. A tree only if one of them has leaves that grew rather than ones someone placed: otherwise just the one log.
	 */
	private static List<BlockPos> tree(ServerLevel level, BlockPos origin) {
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> logs = new ArrayList<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(origin);
		logs.add(origin);
		queue.add(origin);
		boolean leafy = false;
		while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
			BlockPos at = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockState near = level.getBlockState(at.relative(direction));
				if (near.is(BlockTags.LEAVES) && near.hasProperty(LeavesBlock.PERSISTENT) && !near.getValue(LeavesBlock.PERSISTENT)) {
					leafy = true;
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = at.offset(dx, dy, dz);
						if (Math.abs(next.getX() - origin.getX()) > RADIUS || Math.abs(next.getZ() - origin.getZ()) > RADIUS
							|| logs.size() >= MAX_LOGS || !seen.add(next.immutable()) || !level.getBlockState(next).is(BlockTags.LOGS)) {
							continue;
						}
						logs.add(next.immutable());
						queue.add(next.immutable());
					}
				}
			}
		}
		return leafy ? logs : List.of(origin);
	}
}
