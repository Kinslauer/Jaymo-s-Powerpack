package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.21) Whatever hunts villagers hunts a settlement's folk too: zombies (and husks, drowned, zombie villagers) go
 * for any of them, illagers and ravagers for a realm's (a camp's barbarians are no villagers). Builders, farmers,
 * miners and woodcutters run from them ({@link Folk}); guards fight. (With MCA a realm's workers are villagers
 * already, hunted as villagers are.)
 *
 * <p>(0.7.103) And livestock, after Better With Mods (MIT; its idea, our own code): zombies and husks go for cows,
 * sheep, pigs and llamas ({@link #ZOMBIE_PREY}), spiders for chickens and rabbits ({@link #SPIDER_PREY}, through
 * {@code wild.SpiderMinds}). Never an animal on a lead, one with a name, or one kept: inside a settlement's wall or
 * in a fenced pen ({@link #kept}).
 */
public final class Prey {
	/** The goal's place among their targets: with the villagers they hunt (vanilla's 3). */
	static final int PRIORITY = 3;
	/** Livestock comes after players and villagers (who take a zombie off a cow; never the other way round). */
	static final int LIVESTOCK_PRIORITY = 4;
	/** A pen: fences (or walls, or gates) this near on all four sides, at the animal's feet or a block up. */
	static final int PEN_REACH = 10;

	/** The livestock zombies and husks hunt; the small game spiders hunt. */
	public static final TagKey<EntityType<?>> ZOMBIE_PREY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("zombie_prey")),
		SPIDER_PREY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("spider_prey"));

	private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

	private Prey() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Zombie zombie && !(zombie instanceof ZombifiedPiglin)) {
			hunt(zombie, false, false);
			if (zombie.getType() == net.minecraft.world.entity.EntityTypes.ZOMBIE || zombie instanceof Husk) {
				livestock(zombie, ZOMBIE_PREY, LIVESTOCK_PRIORITY);
			}
		} else if (entity instanceof AbstractIllager || entity instanceof Ravager) {
			hunt((Mob)entity, true, true);
		}
	}

	private static void hunt(Mob hunter, boolean mustSee, boolean realmsOnly) {
		((com.jaymoskingdoms.mixin.MobAccessor)hunter).jaymoskingdoms$targets().addGoal(PRIORITY, new NearestAttackableTargetGoal<>(hunter, Folk.class, mustSee,
			(target, level) -> target instanceof Folk folk && !folk.isBaby() && (!realmsOnly || folk.style() != FactionType.BARBARIAN)));
	}

	/** Sets {@code hunter} on the animals in {@code prey} that nobody keeps, at {@code priority} among its targets. */
	public static void livestock(Mob hunter, TagKey<EntityType<?>> prey, int priority) {
		((com.jaymoskingdoms.mixin.MobAccessor)hunter).jaymoskingdoms$targets().addGoal(priority, new NearestAttackableTargetGoal<>(hunter, Animal.class, true,
			(target, level) -> target.getType().builtInRegistryHolder().is(prey) && fair(target, level)));
	}

	/** Whether {@code animal} is fair game: not on a lead, not named, not kept. */
	public static boolean fair(LivingEntity animal, ServerLevel level) {
		return !(animal instanceof Leashable l && l.isLeashed()) && !animal.hasCustomName() && !kept(animal, level);
	}

	/** Inside a settlement's wall, or penned in by fences on every side. */
	static boolean kept(LivingEntity animal, ServerLevel level) {
		BlockPos at = animal.blockPosition();
		Faction f = Factions.at(level.getServer(), level, at);
		if (f != null) {
			List<Integer> wall = Works.of(level.getServer(), f.id()).wall();
			if (wall.size() == 4 && at.getX() >= wall.get(0) && at.getZ() >= wall.get(1) && at.getX() <= wall.get(2) && at.getZ() <= wall.get(3)) {
				return true;
			}
		}
		for (Direction side : SIDES) {
			if (!fenced(level, at, side)) {
				return false;
			}
		}
		return true;
	}

	/** A fence, wall or gate along {@code side} within {@link #PEN_REACH}, at the feet or a block up, or a built wall (not ground or trees). */
	private static boolean fenced(ServerLevel level, BlockPos from, Direction side) {
		BlockPos.MutableBlockPos p = from.mutable();
		for (int i = 1; i <= PEN_REACH; i++) {
			p.move(side);
			for (int up = 0; up <= 1; up++) {
				BlockState state = level.getBlockState(p.above(up));
				if (state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)) {
					return true;
				}
			}
			BlockState feet = level.getBlockState(p);
			if (!feet.isAir() && !feet.getCollisionShape(level, p).isEmpty()) {
				// a barn's wall closes a pen as well as a fence; trees and the lie of the land don't
				return !(feet.is(BlockTags.LOGS) || feet.is(BlockTags.LEAVES) || feet.is(BlockTags.DIRT) || feet.is(BlockTags.SAND)
					|| feet.is(BlockTags.BASE_STONE_OVERWORLD) || feet.is(BlockTags.SNOW));
			}
		}
		return false;
	}
}
