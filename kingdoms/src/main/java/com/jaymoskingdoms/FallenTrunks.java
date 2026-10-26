package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.232) Fallen trunks, our own take on Tree Physics' (ARR, none of its code: it runs on the Sable physics library
 * and has no 26.3 build). A felled tree that comes to rest on its side ({@link FallingTree#settle}) is kept here as a
 * trunk: its logs, by where they lie.
 * <ul>
 * <li><b>Pushing and rolling</b>: right-click a fallen trunk with an empty hand and it shifts a block away from you (a
 * little hunger for every log). Pushed along its length it slides; pushed across it, it rolls, and keeps rolling down
 * a slope, striking whatever it rolls into. Leaves in its way break; anything solid stops it. A trunk of more than
 * {@link #PUSH_MOST} logs is too heavy to shift alone.</li>
 * <li><b>Built on</b>: a block set on top of a fallen trunk (not its own logs or leaves) pins it. It stays where it is
 * for good, and is forgotten here: just logs now, part of whatever was built.</li>
 * </ul>
 * Logs broken out of a trunk are dropped from it; a trunk split in two moves as two.
 */
public final class FallenTrunks {
	/** The most logs one person can shift, and the hunger each log costs a push. */
	static final int PUSH_MOST = 20;
	static final float PUSH_EXHAUSTION = 0.05F;
	/** Ticks between one player's pushes. */
	static final int PUSH_TICKS = 8;
	/** A rolling trunk moves a block every this many ticks, at most this far on its own. */
	static final int ROLL_TICKS = 4, ROLL_MOST = 16;
	/** What a rolling trunk does to what it rolls into, and how far it drops in one go. */
	static final float ROLL_HIT = 4.0F;
	static final int DROP_MOST = 6;
	/** The most fallen trunks kept per world (the oldest forgotten first). */
	static final int KEPT_MOST = 512;

	/** One fallen trunk: where its logs lie. */
	record Trunk(List<BlockPos> logs) {
		static final Codec<Trunk> CODEC = BlockPos.CODEC.listOf().xmap(Trunk::new, Trunk::logs);
	}

	static final AttachmentType<List<Trunk>> TRUNKS = AttachmentRegistry.create(JaymosKingdoms.id("fallen_trunks"),
		builder -> builder.persistent(Trunk.CODEC.listOf()));

	/** A trunk rolling on its own: its logs now, the way it rolls, how far it's come, when it next moves. */
	private static final class Rolling {
		final ServerLevel level;
		Set<BlockPos> logs;
		final Direction way;
		int rolled;
		long next;

		Rolling(ServerLevel level, Set<BlockPos> logs, Direction way, long next) {
			this.level = level;
			this.logs = logs;
			this.way = way;
			this.next = next;
		}
	}

	private static final List<Rolling> ROLLING = new ArrayList<>();
	private static final Map<UUID, Long> LAST_PUSH = new HashMap<>();

	private FallenTrunks() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(level instanceof ServerLevel server) || player.isSpectator()
				|| !player.getMainHandItem().isEmpty() || !level.getBlockState(hit.getBlockPos()).is(BlockTags.LOGS)) {
				return InteractionResult.PASS;
			}
			Direction way = hit.getDirection().getAxis().isHorizontal() ? hit.getDirection().getOpposite() : player.getDirection();
			return push(server, player, hit.getBlockPos(), way) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(FallenTrunks::tick);
	}

	/** A felled tree has come to rest on its side: its {@code logs} are a fallen trunk now. */
	static void add(ServerLevel level, List<BlockPos> logs) {
		if (logs.isEmpty()) {
			return;
		}
		List<Trunk> trunks = new ArrayList<>(level.getAttachedOrElse(TRUNKS, List.<Trunk>of()));
		trunks.add(new Trunk(List.copyOf(logs)));
		while (trunks.size() > KEPT_MOST) {
			trunks.removeFirst();
		}
		level.setAttached(TRUNKS, trunks);
	}

	/**
	 * {@code player} pushes the fallen trunk that {@code log} is part of toward {@code way}. False if it isn't one (the
	 * click goes on as ever); true if it was, moved or not.
	 */
	static boolean push(ServerLevel level, Player player, BlockPos log, Direction way) {
		Set<BlockPos> logs = trunkAt(level, log);
		if (logs == null) {
			return false;
		}
		long now = level.getGameTime();
		Long last = LAST_PUSH.get(player.getUUID());
		if (last != null && now - last < PUSH_TICKS) {
			return true;
		}
		LAST_PUSH.put(player.getUUID(), now);
		if (builtOn(level, logs)) {
			// (pinned for good: forgotten, so it's only logs now)
			forget(level, logs);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trunk_built_on").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (logs.size() > PUSH_MOST) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trunk_too_heavy").withStyle(ChatFormatting.GRAY));
			return true;
		}
		boolean across = lengthwise(level, logs) != way.getAxis();
		Set<BlockPos> moved = move(level, logs, way, across, player);
		if (moved == null) {
			level.playSound(null, log, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
			return true;
		}
		player.causeFoodExhaustion(PUSH_EXHAUSTION * logs.size());
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, log, across ? SoundEvents.WOOD_FALL : SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 1.0F, across ? 0.6F : 0.5F);
		int dropped = drop(level, moved);
		moved = relocated(level, logs, moved);
		if (moved != null && across && dropped > 0) {
			// (pushed over an edge: on down the slope)
			ROLLING.add(new Rolling(level, moved, way, now + ROLL_TICKS));
		}
		return true;
	}

	/** Rolling trunks roll on down their slopes. */
	private static void tick(MinecraftServer server) {
		if (ROLLING.isEmpty()) {
			return;
		}
		for (Iterator<Rolling> it = ROLLING.iterator(); it.hasNext(); ) {
			Rolling r = it.next();
			if (r.level.getServer() != server) {
				it.remove();
				continue;
			}
			if (r.level.getGameTime() < r.next) {
				continue;
			}
			Set<BlockPos> before = r.logs;
			Set<BlockPos> moved = still(r.level, before) ? move(r.level, before, r.way, true, null) : null;
			if (moved == null) {
				it.remove();
				continue;
			}
			int dropped = drop(r.level, moved);
			BlockPos any = moved.iterator().next();
			r.level.playSound(null, any, SoundEvents.WOOD_FALL, SoundSource.BLOCKS, 0.9F, 0.7F);
			Set<BlockPos> now = relocated(r.level, before, moved);
			if (now == null || dropped <= 0 || ++r.rolled >= ROLL_MOST) {
				// (on level ground again, it comes to rest with a thud)
				r.level.playSound(null, any, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.5F);
				it.remove();
				continue;
			}
			r.logs = now;
			r.next = r.level.getGameTime() + ROLL_TICKS;
		}
	}

	/**
	 * The fallen trunk {@code log} is part of: its logs still lying there and joined to {@code log} (null if it isn't
	 * one). The kept trunk loses any logs that are gone.
	 */
	private static @Nullable Set<BlockPos> trunkAt(ServerLevel level, BlockPos log) {
		List<Trunk> trunks = level.getAttachedOrElse(TRUNKS, List.<Trunk>of());
		for (int i = trunks.size() - 1; i >= 0; i--) {
			Trunk t = trunks.get(i);
			if (!t.logs().contains(log)) {
				continue;
			}
			List<BlockPos> left = new ArrayList<>();
			for (BlockPos p : t.logs()) {
				if (level.getBlockState(p).is(BlockTags.LOGS)) {
					left.add(p);
				}
			}
			if (left.size() != t.logs().size()) {
				List<Trunk> kept = new ArrayList<>(trunks);
				if (left.isEmpty()) {
					kept.remove(i);
				} else {
					kept.set(i, new Trunk(List.copyOf(left)));
				}
				level.setAttached(TRUNKS, kept);
			}
			if (!left.contains(log)) {
				return null;
			}
			return joined(log, new HashSet<>(left));
		}
		return null;
	}

	/** Those of {@code logs} joined to {@code from} (touching, corners too). */
	private static Set<BlockPos> joined(BlockPos from, Set<BlockPos> logs) {
		Set<BlockPos> found = new HashSet<>();
		Deque<BlockPos> open = new ArrayDeque<>();
		found.add(from);
		open.add(from);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				BlockPos q = n.immutable();
				if (logs.contains(q) && found.add(q)) {
					open.add(q);
				}
			}
		}
		return found;
	}

	/** Whether these logs are all still there (a rolling trunk broken up stops). */
	private static boolean still(ServerLevel level, Set<BlockPos> logs) {
		for (BlockPos p : logs) {
			if (!level.getBlockState(p).is(BlockTags.LOGS)) {
				return false;
			}
		}
		return true;
	}

	/** Something built on top of it: a block on one of its logs that isn't its own wood, a leaf, or nothing much. */
	private static boolean builtOn(ServerLevel level, Set<BlockPos> logs) {
		for (BlockPos p : logs) {
			BlockPos up = p.above();
			if (logs.contains(up)) {
				continue;
			}
			BlockState there = level.getBlockState(up);
			if (!there.canBeReplaced() && !(there.getBlock() instanceof LeavesBlock && !there.getValue(LeavesBlock.PERSISTENT))) {
				return true;
			}
		}
		return false;
	}

	/** The way it lies: the axis most of its logs lie along (X if it's even). */
	private static Direction.Axis lengthwise(ServerLevel level, Set<BlockPos> logs) {
		int x = 0, z = 0;
		for (BlockPos p : logs) {
			BlockState s = level.getBlockState(p);
			if (s.hasProperty(BlockStateProperties.AXIS)) {
				Direction.Axis a = s.getValue(BlockStateProperties.AXIS);
				x += a == Direction.Axis.X ? 1 : 0;
				z += a == Direction.Axis.Z ? 1 : 0;
			}
		}
		if (x == z) {
			// (a single log, or one standing on end: by its spread)
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos p : logs) {
				minX = Math.min(minX, p.getX());
				maxX = Math.max(maxX, p.getX());
				minZ = Math.min(minZ, p.getZ());
				maxZ = Math.max(maxZ, p.getZ());
			}
			return maxZ - minZ > maxX - minX ? Direction.Axis.Z : Direction.Axis.X;
		}
		return z > x ? Direction.Axis.Z : Direction.Axis.X;
	}

	/** Room for a moving log: nothing solid (water will do), or a fallen tree's own withering leaves (they break). */
	private static boolean room(ServerLevel level, BlockPos pos) {
		BlockState there = level.getBlockState(pos);
		if (there.getBlock() instanceof LeavesBlock) {
			return !there.getValue(LeavesBlock.PERSISTENT);
		}
		return there.canBeReplaced() && (there.getFluidState().isEmpty() || there.getFluidState().is(FluidTags.WATER));
	}

	/**
	 * Moves {@code logs} a block toward {@code way} if there's room for all of them: the new places (null if it wouldn't
	 * go). Leaves in the way break. Rolling or dropping ({@code hits}), it strikes what it comes into (not
	 * {@code pusher}).
	 */
	private static @Nullable Set<BlockPos> move(ServerLevel level, Set<BlockPos> logs, Direction way, boolean hits, @Nullable Player pusher) {
		for (BlockPos p : logs) {
			BlockPos to = p.relative(way);
			if (!logs.contains(to) && !room(level, to)) {
				return null;
			}
		}
		Map<BlockPos, BlockState> states = new HashMap<>();
		for (BlockPos p : logs) {
			states.put(p, level.getBlockState(p));
		}
		Set<BlockPos> moved = new HashSet<>();
		for (BlockPos p : logs) {
			BlockPos to = p.relative(way);
			moved.add(to);
			if (!logs.contains(to) && level.getBlockState(to).getBlock() instanceof LeavesBlock) {
				level.destroyBlock(to, true);
			}
		}
		for (BlockPos p : logs) {
			if (!moved.contains(p)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (Map.Entry<BlockPos, BlockState> e : states.entrySet()) {
			BlockState state = e.getValue();
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			level.setBlock(e.getKey().relative(way), state, Block.UPDATE_CLIENTS);
		}
		for (BlockPos p : logs) {
			level.updateNeighborsAt(p, Blocks.AIR);
		}
		for (BlockPos p : moved) {
			level.updateNeighborsAt(p, states.get(p.relative(way.getOpposite())).getBlock());
		}
		if (hits) {
			strike(level, moved, way, pusher);
		}
		return moved;
	}

	/** What stands where the trunk has rolled is struck and shoved on (not whoever pushed it). */
	private static void strike(ServerLevel level, Set<BlockPos> at, Direction way, @Nullable Player pusher) {
		Set<LivingEntity> hit = new HashSet<>();
		for (BlockPos p : at) {
			hit.addAll(level.getEntitiesOfClass(LivingEntity.class, new AABB(p), e -> e.isAlive() && !e.isSpectator() && e != pusher));
		}
		for (LivingEntity e : hit) {
			if (e.hurtServer(level, level.damageSources().generic(), ROLL_HIT)) {
				e.push(way.getStepX() * 0.5, 0.2, way.getStepZ() * 0.5);
				e.needsSync = true;
			}
		}
	}

	/** It drops while nothing holds it up ({@code logs} becomes where it lands): how far it fell. */
	private static int drop(ServerLevel level, Set<BlockPos> logs) {
		int fell = 0;
		Set<BlockPos> now = logs;
		while (fell < DROP_MOST) {
			boolean held = false;
			for (BlockPos p : now) {
				BlockPos below = p.below();
				if (!now.contains(below) && !room(level, below)) {
					held = true;
					break;
				}
			}
			if (held) {
				break;
			}
			Set<BlockPos> moved = move(level, now, Direction.DOWN, true, null);
			if (moved == null) {
				break;
			}
			now = moved;
			fell++;
		}
		if (fell > 0) {
			logs.clear();
			logs.addAll(now);
		}
		return fell;
	}

	/**
	 * The kept trunk that had {@code before} now has {@code after} instead (it moved). Returns {@code after}, or null if
	 * it's no longer kept.
	 */
	private static @Nullable Set<BlockPos> relocated(ServerLevel level, Set<BlockPos> before, Set<BlockPos> after) {
		List<Trunk> trunks = level.getAttachedOrElse(TRUNKS, List.<Trunk>of());
		BlockPos any = before.iterator().next();
		for (int i = trunks.size() - 1; i >= 0; i--) {
			Trunk t = trunks.get(i);
			if (!t.logs().contains(any)) {
				continue;
			}
			List<BlockPos> logs = new ArrayList<>();
			for (BlockPos p : t.logs()) {
				if (!before.contains(p)) {
					logs.add(p);
				}
			}
			List<Trunk> kept = new ArrayList<>(trunks);
			if (logs.isEmpty()) {
				// (all of it moved: one trunk still)
				kept.set(i, new Trunk(List.copyOf(after)));
			} else {
				// (a piece of a split trunk moved: two trunks now)
				kept.set(i, new Trunk(List.copyOf(logs)));
				kept.add(new Trunk(List.copyOf(after)));
			}
			level.setAttached(TRUNKS, kept);
			return after;
		}
		return null;
	}

	/** These logs are no longer a fallen trunk (built on): kept as they are, never moved again. */
	private static void forget(ServerLevel level, Set<BlockPos> logs) {
		List<Trunk> kept = new ArrayList<>();
		for (Trunk t : level.getAttachedOrElse(TRUNKS, List.<Trunk>of())) {
			List<BlockPos> left = new ArrayList<>();
			for (BlockPos p : t.logs()) {
				if (!logs.contains(p)) {
					left.add(p);
				}
			}
			if (!left.isEmpty()) {
				kept.add(left.size() == t.logs().size() ? t : new Trunk(List.copyOf(left)));
			}
		}
		level.setAttached(TRUNKS, kept);
	}
}
