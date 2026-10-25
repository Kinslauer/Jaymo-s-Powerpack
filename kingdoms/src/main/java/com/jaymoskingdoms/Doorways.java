package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.197) Andy: "Guard got stuck leading me to the queen." MCA plans a villager's paths through wooden doors and fence
 * gates and leaves the opening to its brain ({@code SmarterOpenDoorsTask}). While our work stands in for that brain
 * ({@link Workers#step}: a guard leading you to the ruler, the ruler's escort, a ruler on their round), nothing opened
 * them, so the villager walked up to a shut gate and stood there. Now the doors and gates on the path just ahead are
 * opened as they come to them, and shut again once they and whoever follows them ({@code follower}) are through.
 */
public final class Doorways {
	/** Path nodes looked at: the one they're leaving and this many ahead. */
	static final int AHEAD = 2;
	/** They shut what they opened once they (and whoever follows) are this far from it. */
	static final double CLOSE_RANGE = 2.5;
	/** Left open longer than this (ticks: they went another way), it's shut anyway when nobody's in it. */
	static final int FORGET_TICKS = 20 * 30;

	/** Mob → the doors and gates they opened, and when. */
	private static final Map<UUID, Map<BlockPos, Long>> OPENED = new HashMap<>();

	private Doorways() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OPENED.clear());
	}

	/**
	 * A villager's step (from {@code VillagerWorkMixin}): {@code driving} is true when our work walks them this tick (their
	 * brain, which opens its own doors, is waiting), so the doors and gates ahead on their path are opened. Either way,
	 * the ones they opened and have left behind are shut.
	 */
	public static void tick(ServerLevel level, PathfinderMob mob, boolean driving) {
		Map<BlockPos, Long> opened = OPENED.get(mob.getUUID());
		if (driving && mob.isAlive() && !mob.isSleeping() && !mob.isPassenger()) {
			Path path = mob.getNavigation().getPath();
			if (path != null && !path.isDone()) {
				int from = Math.max(0, path.getNextNodeIndex() - 1);
				int to = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + AHEAD);
				for (int i = from; i <= to; i++) {
					BlockPos at = path.getNodePos(i);
					if (open(level, mob, at) || open(level, mob, at.above())) {
						if (opened == null) {
							opened = new HashMap<>();
							OPENED.put(mob.getUUID(), opened);
						}
						opened.put(at.immutable(), level.getGameTime());
					}
				}
			}
		}
		if (opened == null || mob.tickCount % 5 != 0) {
			return;
		}
		Entity follower = Audiences.follower(level, mob);
		long now = level.getGameTime();
		for (Iterator<Map.Entry<BlockPos, Long>> it = opened.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, Long> e = it.next();
			BlockPos at = e.getKey();
			boolean stale = !mob.isAlive() || now - e.getValue() > FORGET_TICKS;
			boolean passed = away(mob, at) && (follower == null || away(follower, at));
			if ((passed || stale) && !occupied(level, at)) {
				if (!close(level, mob, at)) {
					close(level, mob, at.above());
				}
				it.remove();
			}
		}
		if (opened.isEmpty()) {
			OPENED.remove(mob.getUUID());
		}
	}

	private static boolean away(Entity e, BlockPos at) {
		return e.position().distanceTo(Vec3.atBottomCenterOf(at)) > CLOSE_RANGE;
	}

	/** Someone stands in the doorway: it isn't shut on them. */
	private static boolean occupied(ServerLevel level, BlockPos at) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(at).expandTowards(0.0, 1.0, 0.0), LivingEntity::isAlive).isEmpty();
	}

	/** Opens the shut wooden door or fence gate at {@code at}: true if there was one. */
	private static boolean open(ServerLevel level, PathfinderMob mob, BlockPos at) {
		BlockState s = level.getBlockState(at);
		if (s.is(BlockTags.MOB_INTERACTABLE_DOORS) && s.getBlock() instanceof DoorBlock door) {
			if (!door.isOpen(s)) {
				door.setOpen(mob, level, s, at, true);
			}
			return true;
		}
		if (s.is(BlockTags.FENCE_GATES) && s.hasProperty(FenceGateBlock.OPEN)) {
			if (!s.getValue(FenceGateBlock.OPEN)) {
				level.setBlock(at, s.setValue(FenceGateBlock.OPEN, true), 10);
				level.playSound(null, at, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
				level.gameEvent(mob, GameEvent.BLOCK_OPEN, at);
			}
			return true;
		}
		return false;
	}

	/** Shuts the door or gate at {@code at}: true if there was one. */
	private static boolean close(ServerLevel level, PathfinderMob mob, BlockPos at) {
		BlockState s = level.getBlockState(at);
		if (s.is(BlockTags.MOB_INTERACTABLE_DOORS) && s.getBlock() instanceof DoorBlock door) {
			if (door.isOpen(s)) {
				door.setOpen(mob, level, s, at, false);
			}
			return true;
		}
		if (s.is(BlockTags.FENCE_GATES) && s.hasProperty(FenceGateBlock.OPEN)) {
			if (s.getValue(FenceGateBlock.OPEN)) {
				level.setBlock(at, s.setValue(FenceGateBlock.OPEN, false), 10);
				level.playSound(null, at, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
				level.gameEvent(mob, GameEvent.BLOCK_CLOSE, at);
			}
			return true;
		}
		return false;
	}

}
