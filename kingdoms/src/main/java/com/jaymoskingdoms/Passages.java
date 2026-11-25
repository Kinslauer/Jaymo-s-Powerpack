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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
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
import org.jspecify.annotations.Nullable;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): doors. A fighter (a humanoid
 * in a fight, {@link #fighter}) plans its path through a shut fence gate, and through a shut iron door with a button or a
 * lever within {@link #SWITCH_REACH} of it ({@link #type}, from {@code mixin.WalkNodeEvaluatorMixin}); wooden doors it already
 * plans through. On the way ({@link #tick}, from the end of {@code PathNavigation.tick}) {@link Doorways} opens the gates,
 * wooden doors and wooden trapdoors just ahead and shuts them once it's through, and at an iron door it presses the button or
 * flips the lever it can reach from where it stands ({@link #PRESS_REACH}, in sight); a lever is flipped back once the doorway
 * is clear ({@link #LEVER_BACK}).
 * <p>
 * Locks hold: a gate, door or trapdoor with a lock fitted ({@link Locks#barred}) stays shut to anyone without its key (a
 * player's lock bars every mob; a realm's, all but its own folk), and an iron door with one is never switched open.
 */
public final class Passages {
	/** A button or lever this near an iron door (blocks across, and from a block under it to two over it) is its switch. */
	static final int SWITCH_REACH = 2;
	/** A switch is pressed from this near (blocks, from the eyes). */
	static final double PRESS_REACH = 3.0;
	/** Path nodes looked at ahead for an iron door. */
	static final int AHEAD = 3;
	/** A door's switches are looked for again after this long (ticks); at most this many doors are remembered. */
	static final int SWITCH_KEPT = 100, DOORS_KEPT = 256;
	/** Not pressed again by the same mob for this long (ticks). */
	static final int PRESS_GAP = 30;
	/** A lever it flipped is flipped back this long after (ticks) once nobody is in the doorway, and after this long anyway. */
	static final int LEVER_BACK = 60, LEVER_LONGEST = 400;

	private record Switches(List<BlockPos> at, long until) {
	}

	private record Flipped(ServerLevel level, BlockPos door, long at) {
	}

	private static final Map<Level, Map<Long, Switches>> SWITCHES = new WeakHashMap<>();
	private static final Map<BlockPos, Flipped> LEVERS = new HashMap<>();
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

	/** A humanoid on its feet in a fight: whose paths climb ({@link Climbing}) and go through what it can open. */
	public static boolean fighter(Mob mob) {
		return Temperaments.humanoid(mob) && !Downed.isDowned(mob) && Comrades.target(mob) != null;
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
				if (Locks.barred(level, p, mob)) {
					return was;
				}
				opens = true;
			} else if (s.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand()) {
				if (door.isOpen(s)) {
					continue;
				}
				BlockPos lower = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
				if (Locks.barred(level, lower, mob) || switches(level, lower).isEmpty()) {
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

	/** The buttons and levers near the iron door whose lower half is at {@code door} (looked up again every {@link #SWITCH_KEPT}). */
	private static List<BlockPos> switches(Level level, BlockPos door) {
		Map<Long, Switches> known = SWITCHES.computeIfAbsent(level, l -> new HashMap<>());
		long now = level.getGameTime();
		Switches s = known.get(door.asLong());
		if (s != null && now < s.until()) {
			return s.at();
		}
		if (known.size() >= DOORS_KEPT) {
			known.values().removeIf(k -> now >= k.until());
			if (known.size() >= DOORS_KEPT) {
				known.clear();
			}
		}
		List<BlockPos> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(door.offset(-SWITCH_REACH, -1, -SWITCH_REACH), door.offset(SWITCH_REACH, 2, SWITCH_REACH))) {
			BlockState b = level.getBlockState(p);
			if (b.is(BlockTags.BUTTONS) && b.getBlock() instanceof ButtonBlock || b.getBlock() instanceof LeverBlock) {
				found.add(p.immutable());
			}
		}
		known.put(door.asLong(), new Switches(found, now + SWITCH_KEPT));
		return found;
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
						press(level, mob, lower, now);
					}
					return;
				}
			}
		}
	}

	/** Presses the nearest switch of {@code door} it can reach and see from where it stands; nothing if there's none. */
	private static void press(ServerLevel level, Mob mob, BlockPos door, long now) {
		Vec3 eye = mob.getEyePosition();
		BlockPos best = null;
		double bestD = PRESS_REACH * PRESS_REACH;
		for (BlockPos sw : switches(level, door)) {
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
				LEVERS.put(best, new Flipped(level, door, now));
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
		for (Iterator<Map.Entry<BlockPos, Flipped>> it = LEVERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<BlockPos, Flipped> e = it.next();
			Flipped f = e.getValue();
			long since = f.level().getGameTime() - f.at();
			if (since < LEVER_BACK) {
				continue;
			}
			boolean someone = !f.level().getEntitiesOfClass(LivingEntity.class, new AABB(f.door()).expandTowards(0.0, 1.0, 0.0).inflate(0.5), LivingEntity::isAlive).isEmpty();
			if (someone && since < LEVER_LONGEST) {
				continue;
			}
			BlockState s = f.level().getBlockState(e.getKey());
			if (s.getBlock() instanceof LeverBlock lever && s.getValue(LeverBlock.POWERED)) {
				lever.pull(s, f.level(), e.getKey(), null);
			}
			it.remove();
		}
	}
}
