package com.jaymospowers.fishing;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * Spring spawning in the shallows (Andy's nature picks, 2026-11-26). In spring the pond and river fish that spawn then
 * (our freshwater kinds keener in spring than in winter: carp, tench, bream and the rest, {@link Species#seasons}) leave
 * the deep water for the warm shallows ({@link #SHALLOW} blocks or less), where they crowd and splash and are easy to see.
 * Living things' rules: sympathy (they spawn in the weedy margins, as carp do); feedback and stewardship: where
 * {@link #SPAWNERS} or more of one kind gather in the shallows, now and then a young one of that kind comes of it
 * ({@link #YOUNG_EVERY}), so a pond whose spawners are left alone that spring has more fish by summer, and one fished
 * out has none to come. Never past {@link #CROWD} fish of a kind round there. Needs Kitchen's seasons.
 */
public final class SpringShallows {
	static final int STEP_EVERY = 40, SHALLOW = 2, LOOK = 12, TRIES = 8;
	static final int SPAWNERS = 3, CROWD = 12;
	static final long YOUNG_EVERY = 20L * 120;
	static final double MOVE_PACE = 1.0;

	private static final Set<WildFish> FISH = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<WildFish, Long> BRED = new WeakHashMap<>();

	private SpringShallows() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof WildFish f && spawnsInSpring(f.species())) {
				FISH.add(f);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> FISH.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(SpringShallows::tick);
	}

	/** A freshwater kind that spawns in spring: keener then than in winter. */
	static boolean spawnsInSpring(Species sp) {
		return sp != null && sp.water == Species.Water.FRESH && sp.seasons != null && sp.seasons.length >= 4 && sp.seasons[0] > sp.seasons[3];
	}

	private static void tick(MinecraftServer server) {
		if (FISH.isEmpty() || server.getTickCount() % STEP_EVERY != 0 || FishSeasons.season(server.overworld()) != SalmonRun.SPRING) {
			return;
		}
		for (WildFish f : FISH.toArray(WildFish[]::new)) {
			if (f.isAlive() && f.isInWater() && !f.legend() && f.level() instanceof ServerLevel level) {
				step(level, f);
			}
		}
	}

	private static void step(ServerLevel level, WildFish f) {
		BlockPos at = f.blockPosition();
		if (depth(level, at) > SHALLOW) {
			BlockPos to = shallowNear(level, at, level.getRandom());
			if (to != null) {
				f.getNavigation().moveTo(to.getX() + 0.5, to.getY() + 0.2, to.getZ() + 0.5, MOVE_PACE);
			}
			return;
		}
		RandomSource random = level.getRandom();
		if (random.nextInt(6) == 0) {
			level.sendParticles(ParticleTypes.SPLASH, f.getX(), f.getY() + 0.5, f.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
		}
		long now = level.getGameTime();
		if (now - BRED.getOrDefault(f, Long.MIN_VALUE / 2) < YOUNG_EVERY) {
			return;
		}
		Species sp = f.species();
		List<WildFish> kin = level.getEntitiesOfClass(WildFish.class, new AABB(at).inflate(8.0), o -> o.isAlive() && o.species() == sp);
		if (kin.size() < SPAWNERS || level.getEntitiesOfClass(WildFish.class, new AABB(at).inflate(32.0), o -> o.species() == sp).size() >= CROWD) {
			return;
		}
		for (WildFish k : kin) {
			BRED.put(k, now);
		}
		EntityType<?> type = FishSpecies.type(sp);
		Entity young = type == null ? null : type.create(level, EntitySpawnReason.BREEDING);
		if (young instanceof WildFish y) {
			y.snapTo(f.getX(), f.getY(), f.getZ(), random.nextFloat() * 360.0F, 0.0F);
			y.setWeight(sp.kgMin);
			level.addFreshEntity(y);
			level.sendParticles(ParticleTypes.BUBBLE, f.getX(), f.getY(), f.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
		}
	}

	/** Water blocks from {@code at} down to the floor. */
	private static int depth(ServerLevel level, BlockPos at) {
		BlockPos.MutableBlockPos p = at.mutable();
		int n = 0;
		while (n < 8 && !level.getBlockState(p).getFluidState().isEmpty()) {
			n++;
			p.move(0, -1, 0);
		}
		// (and the water above it)
		p.set(at).move(0, 1, 0);
		while (n < 8 && !level.getBlockState(p).getFluidState().isEmpty()) {
			n++;
			p.move(0, 1, 0);
		}
		return n;
	}

	private static BlockPos shallowNear(ServerLevel level, BlockPos at, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			BlockPos p = at.offset(random.nextInt(LOOK * 2 + 1) - LOOK, random.nextInt(5) - 2, random.nextInt(LOOK * 2 + 1) - LOOK);
			if (level.isLoaded(p) && !level.getBlockState(p).getFluidState().isEmpty() && depth(level, p) <= SHALLOW) {
				return p;
			}
		}
		return null;
	}
}
