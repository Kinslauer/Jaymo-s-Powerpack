package com.jaymoskingdoms;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

/**
 * The villager fixes of Liberty's Villagers (gitsh01, CC0), ported: the ones that mend real troubles, for vanilla (and
 * MCA) villagers.
 * <ul>
 * <li>They look for beds, workplaces and bells {@link #POI_RANGE} out, not 48 (a big or tall town's far houses), and
 * their paths reach that far; they give up on a place they can't reach after {@link #GIVE_UP_TICKS}, not a minute.</li>
 * <li>They lie down in a bed and take a workstation from {@link #SLEEP_REACH} / {@link #CLAIM_REACH} away, so one set
 * behind a stair or a counter still counts.</li>
 * <li>They don't hunt for a workplace or a bell at bedtime ({@link Homes#bedtime}), wandering from their beds.</li>
 * <li>They plan no drop deeper than {@link #SAFE_DROP} (no hurting themselves jumping off stairs and ledges), and steer
 * clear of cactus and berry bushes, water, rails and powder snow where there's a way round.</li>
 * <li>On stairs they only take a path step as reached once truly over it ({@link #reached}), so one turning on a slab
 * doesn't try to hop to a stair too high.</li>
 * <li>Farmers look for ripe crops and bare farmland {@link #FIELD_ACROSS} blocks about them (and {@link #FIELD_UPDOWN}
 * up and down), not only at their feet, and plant back the crop they just cut when they have its seed.</li>
 * <li>Villagers and golems don't trample farmland.</li>
 * </ul>
 * Golems get the same path costs ({@link GolemPathing#improve}).
 */
public final class VillagerFixes {
	/** How far they look for a bed, workplace or bell, and how long a path they plan (blocks). */
	public static final int POI_RANGE = 80;
	/** How long a place may be out of reach before they forget it (ticks). */
	public static final int GIVE_UP_TICKS = 2400;
	/** How near a bed they lie down, and a workstation they take (blocks). */
	public static final double SLEEP_REACH = 3.0, CLAIM_REACH = 3.0;
	/** The deepest drop they plan to take (blocks). */
	static final int SAFE_DROP = 2;
	/** Path costs: beside cactus or a berry bush, water and its edge, a rail; on powder snow they never step. */
	static final float NEAR_HARM_COST = 16.0F, WATER_COST = 16.0F, RAIL_COST = 16.0F;
	/** A farmer's field: this far about it, this far up and down (blocks); it looks that wide this often (ticks). */
	public static final int FIELD_ACROSS = 10, FIELD_UPDOWN = 3, FIELD_LOOK_EVERY = 40;

	private static final Set<Villager> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private VillagerFixes() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && GIVEN.add(villager)) {
				avoid(villager);
				villager.getNavigation().setRequiredPathLength(POI_RANGE);
			}
		});
	}

	/** The path costs: what hurts, water, rails, powder snow. */
	static void avoid(Mob mob) {
		mob.setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, NEAR_HARM_COST);
		mob.setPathfindingMalus(PathType.RAIL, RAIL_COST);
		mob.setPathfindingMalus(PathType.ON_TOP_OF_POWDER_SNOW, -1.0F);
		if (mob instanceof Villager) {
			mob.setPathfindingMalus(PathType.WATER, WATER_COST);
			mob.setPathfindingMalus(PathType.WATER_BORDER, WATER_COST);
		}
	}

	/** A villager's deepest planned drop. */
	public static int maxFall(Mob mob, int was) {
		return mob instanceof Villager && mob.getTarget() == null ? Math.min(was, SAFE_DROP) : was;
	}

	/** At bedtime a workplace or a bell isn't worth setting out for. */
	public static BiPredicate<ServerLevel, BlockPos> byDay(MemoryModuleType<GlobalPos> acquire, BiPredicate<ServerLevel, BlockPos> valid) {
		if (acquire != MemoryModuleType.POTENTIAL_JOB_SITE && acquire != MemoryModuleType.MEETING_POINT) {
			return valid;
		}
		return (level, pos) -> !Homes.bedtime(level) && valid.test(level, pos);
	}

	/** Whether a villager or golem has reached its path's next step: within its reach across, as a circle, not a square. */
	public static boolean reached(Mob mob, Path path, float reach) {
		if (!(mob instanceof Villager) && !(mob instanceof AbstractGolem) || path.isDone()) {
			return true;
		}
		BlockPos node = path.getNextNodePos();
		double dx = mob.getX() - (node.getX() + 0.5), dz = mob.getZ() - (node.getZ() + 0.5);
		return dx * dx + dz * dz < reach * reach;
	}

	/** The nearest spot of a farmer's field it has work at (a ripe crop, or bare farmland with seed in hand), or null. */
	public static @Nullable BlockPos field(Villager farmer, Predicate<BlockPos> work) {
		BlockPos at = farmer.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FIELD_ACROSS, -FIELD_UPDOWN, -FIELD_ACROSS), at.offset(FIELD_ACROSS, FIELD_UPDOWN, FIELD_ACROSS))) {
			double d = p.distSqr(at);
			if (d < bestD && work.test(p) && (!farmer.level().getBlockState(p).isAir() || farmer.hasFarmSeeds())) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Just after a farmer cuts {@code cut} at {@code pos}: it plants that crop back, if it carries the seed. */
	public static void replant(ServerLevel level, Villager farmer, BlockPos pos, BlockState cut) {
		if (!(cut.getBlock() instanceof CropBlock) || !level.getBlockState(pos).isAir()) {
			return;
		}
		SimpleContainer inventory = farmer.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) && stack.getItem() instanceof BlockItem item && item.getBlock() == cut.getBlock()) {
				BlockState place = item.getBlock().defaultBlockState();
				level.setBlockAndUpdate(pos, place);
				level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(farmer, place));
				level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
				stack.shrink(1);
				return;
			}
		}
	}

	/** Villagers and golems step on farmland without trampling it. */
	public static boolean light(@Nullable Entity entity) {
		return entity instanceof Villager || entity instanceof AbstractGolem;
	}
}
