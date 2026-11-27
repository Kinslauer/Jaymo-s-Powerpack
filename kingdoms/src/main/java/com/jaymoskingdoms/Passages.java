package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): doors. A fighter (a humanoid
 * in a fight, {@link #fighter}) plans its path through a shut fence gate, and through a shut iron door with a button or a
 * lever within {@link #SWITCH_REACH} of it that it can press from its own side of the door (within {@link #PRESS_REACH} of,
 * and in sight from, the spot before the door on that side: one only on the far side, or inside, doesn't count) ({@link #type},
 * from {@code mixin.WalkNodeEvaluatorMixin}); wooden doors it already
 * plans through. On the way ({@link #tick}, from the end of {@code PathNavigation.tick}) {@link Doorways} opens the gates,
 * wooden doors and wooden trapdoors just ahead and shuts them once it's through, and at an iron door it presses the button or
 * flips the lever on the side it comes from that it can reach from where it stands ({@link #PRESS_REACH}, in sight); a lever is
 * flipped back once the doorway is clear ({@link #LEVER_BACK}). Nothing here reads a block in a chunk that isn't loaded.
 * <p>
 * Locks hold: a gate, door or trapdoor with a lock fitted ({@link Locks#barred}) stays shut to anyone without its key (a
 * player's lock bars every mob; a realm's, all but its own folk), and an iron door with one is never switched open.
 */
public final class Passages {
	/** A button or lever this near an iron door (blocks across, and from a block under it to two over it) is its switch. */
	static final int SWITCH_REACH = 2;
	/** A switch is pressed from this near (blocks, from the eyes); for planning, eyes this high over the spot before the door. */
	static final double PRESS_REACH = 3.0, STAND_EYE = 1.6;
	/** Path nodes looked at ahead for an iron door. */
	static final int AHEAD = 3;
	/** A door's switches are looked for again after this long (ticks); at most this many doors are remembered. */
	static final int SWITCH_KEPT = 100, DOORS_KEPT = 256;
	/** Not pressed again by the same mob for this long (ticks). */
	static final int PRESS_GAP = 30;
	/** A lever it flipped is flipped back this long after (ticks) once nobody is in the doorway, and after this long anyway. */
	static final int LEVER_BACK = 60, LEVER_LONGEST = 400;

	/** A door's switches pressable from the side it faces ({@code ahead}) and from the other ({@code behind}). */
	private record Switches(Direction facing, List<BlockPos> ahead, List<BlockPos> behind, long until) {
	}

	private record Lever(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private record Flipped(ServerLevel level, BlockPos door, long at) {
	}

	private static final Map<Level, Map<Long, Switches>> SWITCHES = new WeakHashMap<>();
	private static final Map<Lever, Flipped> LEVERS = new HashMap<>();
	private static final Map<Mob, Long> NEXT_PRESS = new WeakHashMap<>();

	private Passages() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Passages::levers);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SWITCHES.clear();
			LEVERS.clear();
			NEXT_PRESS.clear();
		});
	}

	/**
	 * A grown humanoid with its wits, on its feet in a fight (not a witch or a wandering trader): whose paths climb
	 * ({@link Climbing}) and go through what it can open.
	 */
	public static boolean fighter(Mob mob) {
		return Temperaments.humanoid(mob) && !(mob instanceof Witch) && !(mob instanceof WanderingTrader) && !mob.isBaby() && !mob.isNoAi()
			&& !Downed.isDowned(mob) && Comrades.target(mob) != null;
	}

	// ---- planning --------------------------------------------------------------------------------------------------------

	/**
	 * From {@code WalkNodeEvaluator.getPathTypeOfMob}: a fighter's body at {@code x, y, z} in a shut fence gate, or a shut iron
	 * door with a switch, it may open (and nothing else in the way) is a door it walks through.
	 */
	public static PathType type(PathfindingContext ctx, int x, int y, int z, @Nullable Mob mob, PathType was) {
		if (was != PathType.FENCE && was != PathType.DOOR_IRON_CLOSED || mob == null || !fighter(mob)) {
			return was;
		}
		Level level = mob.level();
		boolean opens = false;
		for (int dy = 0; dy < 2; dy++) {
			BlockPos p = new BlockPos(x, y + dy, z);
			BlockState s = ctx.getBlockState(p);
			if (s.is(BlockTags.FENCE_GATES) && s.hasProperty(FenceGateBlock.OPEN)) {
				if (s.getValue(FenceGateBlock.OPEN)) {
					continue;
				}
				if (!level.isLoaded(p) || Locks.barred(level, p, mob)) {
					return was;
				}
				opens = true;
			} else if (s.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand()) {
				if (door.isOpen(s)) {
					continue;
				}
				BlockPos lower = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
				// (the side it comes from: where it stands as the path is worked out)
				Direction facing = s.getValue(DoorBlock.FACING);
				if (!level.isLoaded(lower) || Locks.barred(level, lower, mob) || switches(level, lower, facing, side(facing, lower, mob.position())).isEmpty()) {
					return was;
				}
				opens = true;
			} else {
				PathType t = ctx.getPathTypeFromState(x, y + dy, z);
				if (t == PathType.BLOCKED || t == PathType.FENCE || t == PathType.LAVA || t == PathType.DOOR_IRON_CLOSED || t == PathType.DOOR_WOOD_CLOSED) {
					return was;
				}
			}
		}
		return opens ? PathType.WALKABLE_DOOR : was;
	}

	/** Which side of the door facing {@code facing} at {@code door} {@code at} is on. */
	private static Direction side(Direction facing, BlockPos door, Vec3 at) {
		double along = (at.x - (door.getX() + 0.5)) * facing.getStepX() + (at.z - (door.getZ() + 0.5)) * facing.getStepZ();
		return along >= 0.0 ? facing : facing.getOpposite();
	}

	/**
	 * The buttons and levers near the iron door whose lower half is at {@code door} that can be pressed from its {@code side}
	 * (looked up again every {@link #SWITCH_KEPT}; none while the blocks round it aren't all loaded).
	 */
	private static List<BlockPos> switches(Level level, BlockPos door, Direction facing, Direction side) {
		Map<Long, Switches> known = SWITCHES.computeIfAbsent(level, l -> new HashMap<>());
		long now = level.getGameTime();
		Switches s = known.get(door.asLong());
		if (s != null && now < s.until() && s.facing() == facing) {
			return side == facing ? s.ahead() : s.behind();
		}
		if (!level.hasChunksAt(door.offset(-SWITCH_REACH - 1, -1, -SWITCH_REACH - 1), door.offset(SWITCH_REACH + 1, 2, SWITCH_REACH + 1))) {
			return List.of();
		}
		if (known.size() >= DOORS_KEPT) {
			known.values().removeIf(k -> now >= k.until());
			if (known.size() >= DOORS_KEPT) {
				known.clear();
			}
		}
		List<BlockPos> ahead = new ArrayList<>(), behind = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(door.offset(-SWITCH_REACH, -1, -SWITCH_REACH), door.offset(SWITCH_REACH, 2, SWITCH_REACH))) {
			BlockState b = level.getBlockState(p);
			if (b.is(BlockTags.BUTTONS) && b.getBlock() instanceof ButtonBlock || b.getBlock() instanceof LeverBlock) {
				BlockPos sw = p.immutable();
				if (pressable(level, door, facing, sw)) {
					ahead.add(sw);
				}
				if (pressable(level, door, facing.getOpposite(), sw)) {
					behind.add(sw);
				}
			}
		}
		known.put(door.asLong(), new Switches(facing, ahead, behind, now + SWITCH_KEPT));
		return side == facing ? ahead : behind;
	}

	/** The switch at {@code sw} is on the door's {@code side} (or in its wall), in reach of and in sight from the spot before it there. */
	private static boolean pressable(Level level, BlockPos door, Direction side, BlockPos sw) {
		int along = (sw.getX() - door.getX()) * side.getStepX() + (sw.getZ() - door.getZ()) * side.getStepZ();
		if (along < 0) {
			return false;
		}
		Vec3 eye = Vec3.atBottomCenterOf(door.relative(side)).add(0.0, STAND_EYE, 0.0);
		Vec3 at = Vec3.atCenterOf(sw);
		if (eye.distanceToSqr(at) > PRESS_REACH * PRESS_REACH) {
			return false;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, at, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(sw);
	}

	// ---- on the way ------------------------------------------------------------------------------------------------------

	/** From the end of {@link PathNavigation#tick}: the doors on a fighter's path opened (and shut behind it), an iron door's switch pressed. */
	public static void tick(Mob mob, PathNavigation nav) {
		if (!(mob.level() instanceof ServerLevel level) || !(mob instanceof PathfinderMob pm) || !Temperaments.humanoid(mob)) {
			return;
		}
		boolean fights = !nav.isDone() && fighter(mob);
		if (!(mob instanceof Villager) || fights) {
			// (a villager's own step runs Doorways for it already, every tick; out of a fight this only shuts what it opened)
			Doorways.tick(level, pm, fights);
		}
		if (fights) {
			ironDoor(level, mob, nav.getPath());
		}
	}

	private static void ironDoor(ServerLevel level, Mob mob, @Nullable Path path) {
		if (path == null || path.isDone()) {
			return;
		}
		long now = level.getGameTime();
		if (now < NEXT_PRESS.getOrDefault(mob, 0L)) {
			return;
		}
		int from = path.getNextNodeIndex(), to = Math.min(path.getNodeCount(), from + AHEAD);
		for (int i = from; i < to; i++) {
			BlockPos at = path.getNodePos(i);
			for (BlockPos q : new BlockPos[] {at, at.above()}) {
				BlockState s = level.getBlockState(q);
				if (s.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand() && !door.isOpen(s)) {
					BlockPos lower = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? q.below() : q;
					if (!Locks.barred(level, lower, mob)) {
						// (the side it comes from: its path's step before the door)
						Direction facing = s.getValue(DoorBlock.FACING);
						Vec3 before = i > 0 ? Vec3.atBottomCenterOf(path.getNodePos(i - 1)) : mob.position();
						press(level, mob, lower, facing, side(facing, lower, before), now);
					}
					return;
				}
			}
		}
	}

	/** Presses the nearest switch of {@code door} on its {@code side} it can reach and see from where it stands; nothing if there's none. */
	private static void press(ServerLevel level, Mob mob, BlockPos door, Direction facing, Direction side, long now) {
		Vec3 eye = mob.getEyePosition();
		BlockPos best = null;
		double bestD = PRESS_REACH * PRESS_REACH;
		for (BlockPos sw : switches(level, door, facing, side)) {
			double d = Vec3.atCenterOf(sw).distanceToSqr(eye);
			if (d < bestD && seen(level, mob, eye, sw)) {
				bestD = d;
				best = sw;
			}
		}
		if (best == null) {
			return;
		}
		BlockState s = level.getBlockState(best);
		if (s.getBlock() instanceof ButtonBlock button) {
			if (!s.getValue(ButtonBlock.POWERED)) {
				button.press(s, level, best, null);
			}
		} else if (s.getBlock() instanceof LeverBlock lever) {
			if (!s.getValue(LeverBlock.POWERED)) {
				lever.pull(s, level, best, null);
				LEVERS.put(new Lever(level.dimension(), best), new Flipped(level, door, now));
			}
		} else {
			return;
		}
		mob.getLookControl().setLookAt(Vec3.atCenterOf(best));
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		NEXT_PRESS.put(mob, now + PRESS_GAP);
	}

	private static boolean seen(ServerLevel level, Mob mob, Vec3 eye, BlockPos sw) {
		BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(sw), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mob));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(sw);
	}

	/** Every ten ticks: a lever flipped to get through is flipped back once nobody stands in its door (or after a long while). */
	private static void levers(MinecraftServer server) {
		if (LEVERS.isEmpty() || server.getTickCount() % 10 != 0) {
			return;
		}
		for (Iterator<Map.Entry<Lever, Flipped>> it = LEVERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Lever, Flipped> e = it.next();
			Flipped f = e.getValue();
			BlockPos lever = e.getKey().pos();
			long since = f.level().getGameTime() - f.at();
			if (since < LEVER_BACK) {
				continue;
			}
			if (!f.level().isLoaded(lever)) {
				// (not loaded: it waits, and is let be after a long while)
				if (since >= LEVER_LONGEST) {
					it.remove();
				}
				continue;
			}
			boolean someone = !f.level().getEntitiesOfClass(LivingEntity.class, new AABB(f.door()).expandTowards(0.0, 1.0, 0.0).inflate(0.5), LivingEntity::isAlive).isEmpty();
			if (someone && since < LEVER_LONGEST) {
				continue;
			}
			BlockState s = f.level().getBlockState(lever);
			if (s.getBlock() instanceof LeverBlock block && s.getValue(LeverBlock.POWERED)) {
				block.pull(s, f.level(), lever, null);
			}
			it.remove();
		}
	}
}
