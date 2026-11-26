package com.jaymoskingdoms;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariants;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariants;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Animals get out of the weather (Subtly Wild's idea, from its CC0 first release). A beast in
 * {@code jaymoskingdoms:shelters_from_rain} out in the rain goes for the nearest cover (under a tree, an overhang, a cave
 * mouth, an open barn); one in {@code seeks_warmth} caught in falling snow, or a warm-land coat in a cold land, goes for
 * a fire's warmth or a roof; one in {@code seeks_shade} out under a hot land's midday sun goes for shade. Cold-land coats
 * (a cold pig, cow or chicken, a snow fox, a white rabbit) don't mind the cold, warm-land coats don't mind the heat. It
 * waits it out there, and goes back to grazing once it's dry, warm or cool where it stands.
 *
 * <p>Not while it's someone's pet near its owner, fighting, leashed or ridden, and never into a settlement's houses
 * (a built floor under a roof, {@link Strays#indoors}), where the townsfolk would only shoo it out again.
 */
public final class WeatherShelter {
	static final TagKey<EntityType<?>> RAIN = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("shelters_from_rain"));
	static final TagKey<EntityType<?>> COLD = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("seeks_warmth"));
	static final TagKey<EntityType<?>> HEAT = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("seeks_shade"));
	/** The goal's place among the animal's goals (after panic, breeding, temptation and following its mother). */
	static final int PRIORITY = 5;
	/** It looks about one tick in this many (on average) while it's out in it. */
	static final int LOOK_ODDS = 40;
	/** Where it looks for cover: rings this far out, the step between them, tries per block of a ring's radius, how far up and down (blocks). */
	static final int NEAREST = 4, FARTHEST = 16, RING_STEP = 4, TRIES_PER = 6, UP_DOWN = 4;
	/** Its pace there. */
	static final double SPEED = 1.1;
	/** A fire's warmth: block light at least this bright. */
	static final int WARM_LIGHT = 10;
	/** A land this hot (its biome's temperature) has a sun to shelter from. */
	static final float HOT_LAND = 2.0F;
	/** A pet stays by its owner within this far (blocks). */
	static final double OWNER_NEAR = 20.0;

	private enum Need {
		NONE, RAIN, COLD, HEAT
	}

	private static final Set<PathfinderMob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private WeatherShelter() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && (is(mob, RAIN) || is(mob, COLD) || is(mob, HEAT)) && GIVEN.add(mob)) {
				((com.jaymoskingdoms.mixin.MobAccessor) mob).jaymoskingdoms$goals().addGoal(PRIORITY, new SeekCover(mob));
			}
		});
	}

	private static boolean is(PathfinderMob mob, TagKey<EntityType<?>> tag) {
		return mob.getType().builtInRegistryHolder().is(tag);
	}

	/** What {@code mob} wants to get out of where it stands, if anything. */
	private static Need need(PathfinderMob mob) {
		Level level = mob.level();
		if (mob.getTarget() != null || mob.isLeashed() || mob.isVehicle() || mob.isPassenger() || mob.isInWater() || withOwner(mob)
			|| level.dimensionType().hasFixedTime()) {
			return Need.NONE;
		}
		BlockPos at = mob.blockPosition();
		if (is(mob, RAIN) && level.isRainingAt(at)) {
			return Need.RAIN;
		}
		if (is(mob, COLD) && !coldCoat(mob) && level.getBrightness(LightLayer.BLOCK, at) < WARM_LIGHT && level.canSeeSky(at)) {
			Biome biome = level.getBiome(at).value();
			boolean snowing = level.precipitationAt(at) == Biome.Precipitation.SNOW;
			if (snowing || warmCoat(mob) && biome.coldEnoughToSnow(at, level.getSeaLevel())) {
				return Need.COLD;
			}
		}
		if (is(mob, HEAT) && !warmCoat(mob) && level.isBrightOutside() && level.canSeeSky(at)
			&& level.getBiome(at).value().getBaseTemperature() >= HOT_LAND) {
			return Need.HEAT;
		}
		return Need.NONE;
	}

	/** A tame pet near its owner keeps with them. */
	private static boolean withOwner(PathfinderMob mob) {
		return mob instanceof TamableAnimal pet && pet.isTame() && !pet.isInSittingPose() && pet.getOwner() != null
			&& pet.distanceToSqr(pet.getOwner()) < OWNER_NEAR * OWNER_NEAR;
	}

	/** A cold land's coat: a cold pig, cow or chicken, a snow fox, a white rabbit. */
	static boolean coldCoat(PathfinderMob mob) {
		return mob instanceof Pig p && p.getVariant().is(PigVariants.COLD) || mob instanceof Cow c && c.getVariant().is(CowVariants.COLD)
			|| mob instanceof Chicken h && h.getVariant().is(ChickenVariants.COLD) || mob instanceof Fox f && f.getVariant() == Fox.Variant.SNOW
			|| mob instanceof Rabbit r && (r.getVariant() == Rabbit.Variant.WHITE || r.getVariant() == Rabbit.Variant.WHITE_SPLOTCHED);
	}

	/** A warm land's coat: a warm pig, cow or chicken, a gold rabbit. */
	static boolean warmCoat(PathfinderMob mob) {
		return mob instanceof Pig p && p.getVariant().is(PigVariants.WARM) || mob instanceof Cow c && c.getVariant().is(CowVariants.WARM)
			|| mob instanceof Chicken h && h.getVariant().is(ChickenVariants.WARM) || mob instanceof Rabbit r && r.getVariant() == Rabbit.Variant.GOLD;
	}

	/** Somewhere near to stand out of {@code need}, or null. */
	private static @Nullable Vec3 cover(PathfinderMob mob, Need need) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return null;
		}
		RandomSource random = mob.getRandom();
		BlockPos at = mob.blockPosition();
		for (int r = NEAREST; r <= FARTHEST; r += RING_STEP) {
			for (int i = 0; i < r * TRIES_PER; i++) {
				BlockPos p = at.offset(random.nextInt(r * 2 + 1) - r, random.nextInt(UP_DOWN * 2 + 1) - UP_DOWN, random.nextInt(r * 2 + 1) - r);
				if (level.isLoaded(p) && standable(level, p) && covered(level, p, need) && !Strays.indoors(level, p)) {
					return Vec3.atBottomCenterOf(p);
				}
			}
		}
		return null;
	}

	private static boolean standable(ServerLevel level, BlockPos p) {
		BlockPos below = p.below();
		return level.getBlockState(p).isPathfindable(PathComputationType.LAND) && level.getFluidState(p).isEmpty()
			&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	private static boolean covered(ServerLevel level, BlockPos p, Need need) {
		return switch (need) {
			case RAIN, HEAT -> !level.canSeeSky(p);
			case COLD -> level.getBrightness(LightLayer.BLOCK, p) >= WARM_LIGHT || !level.canSeeSky(p);
			case NONE -> false;
		};
	}

	/** Out in the weather: off to the nearest cover, and it waits there. */
	static final class SeekCover extends Goal {
		private final PathfinderMob mob;
		private @Nullable Vec3 to;

		SeekCover(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (this.mob.getRandom().nextInt(reducedTickDelay(LOOK_ODDS)) != 0) {
				return false;
			}
			Need need = need(this.mob);
			this.to = need == Need.NONE ? null : cover(this.mob, need);
			return this.to != null;
		}

		@Override
		public boolean canContinueToUse() {
			return !this.mob.getNavigation().isDone() && need(this.mob) != Need.NONE;
		}

		@Override
		public void start() {
			if (this.to != null) {
				this.mob.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, SPEED);
			}
		}

		@Override
		public void stop() {
			this.to = null;
		}
	}
}
