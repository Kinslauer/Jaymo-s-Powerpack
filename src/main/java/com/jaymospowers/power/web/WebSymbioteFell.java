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
 * blows by its size ({@link #BLOWS_ONE} and {@link #BLOWS_TWO} logs); the last one takes the log struck clean out and the
 * tree above it falls the way any felled tree does (Kingdoms' {@code Trees.fellAtOnce}: it topples away from the host and
 * breaks up where it lands). Without Kingdoms the logs above break where they stand, as if each were broken by hand. A
 * stack of logs that grew no leaves (a cabin's wall) is only the one log. The blows count for {@link #WINDOW_TICKS} after
 * the last one.
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
		fell(player, level, hit, logs);
		return true;
	}

	/** The log struck is taken out, and the tree above it falls (Kingdoms' toppling tree, or broken log by log without it). */
	private static void fell(ServerPlayer player, ServerLevel level, BlockPos hit, List<BlockPos> logs) {
		if (!level.mayInteract(player, hit)) {
			return;
		}
		level.destroyBlock(hit, true, player);
		if (logs.size() > 1 && topple(level, hit.above(), player) <= 0) {
			// no Kingdoms (or it wouldn't take it): what stood on the cut breaks where it stands
			List<BlockPos> order = new ArrayList<>(logs);
			order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
			for (BlockPos log : order) {
				if (log.getY() > hit.getY() && level.getBlockState(log).is(BlockTags.LOGS)) {
					player.gameMode.destroyBlock(log);
				}
			}
		}
		level.playSound(null, hit.getX() + 0.5, hit.getY() + 0.5, hit.getZ() + 0.5, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 2.0F, 0.5F);
		com.jaymospowers.core.Shakes.send(player, 0.8F, Vec3.atCenterOf(hit));
	}

	private static java.lang.reflect.@Nullable Method fellAtOnce;
	private static boolean looked;

	/** Kingdoms' {@code Trees.fellAtOnce(level, log, player)}: the tree on {@code log} topples like any felled one. Logs felled, 0 if it couldn't. */
	private static int topple(ServerLevel level, BlockPos log, ServerPlayer player) {
		if (!looked) {
			looked = true;
			try {
				fellAtOnce = Class.forName("com.jaymoskingdoms.Trees").getMethod("fellAtOnce", ServerLevel.class, BlockPos.class, ServerPlayer.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				fellAtOnce = null;
			}
		}
		if (fellAtOnce == null) {
			return 0;
		}
		try {
			return (int)fellAtOnce.invoke(null, level, log, player);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
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
		// only the trunk's own wood, so a cabin of another log leaning on a tree stays up
		net.minecraft.world.level.block.Block trunk = level.getBlockState(origin).getBlock();
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
							|| logs.size() >= MAX_LOGS || !seen.add(next.immutable()) || !level.getBlockState(next).is(BlockTags.LOGS)
							|| !level.getBlockState(next).is(trunk)) {
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
