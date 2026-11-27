package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wild's ways with each other, the year and the land (Andy's nature picks, 2026-11-26: every row picked). What
 * they share: the animals each part looks after, by tag ({@code jaymoskingdoms:nature/*}, other mods' animals listed
 * as not required), kept track of as they load, and the season (Kitchen's). The parts:
 * {@link FishEaters} (bears at the run, birds diving for fish, crocodiles at the shoals), {@link HerdWays} (lookouts,
 * water holes, crossings, the year's moves, grazing, hunters following), {@link HuntWays} (birds over a kill, the bigger
 * one taking it, foxes and owls at their small prey, prey freezing, pens raided), {@link WildYear} (the rut, spring young
 * and their mothers, bears' dens, the summer birds, frogs, seeds carried) and {@link WildSigns} (a storm felt coming,
 * the dawn chorus and night calls, the woods gone quiet round a hunter). Everything only happens near a player
 * ({@link #NEAR}), so nothing far off costs anything. Nothing about any animal's own body or art is changed.
 */
public final class Nature {
	/** How near a player something must be for the wild's ways to run on it (blocks). */
	static final double NEAR = 64.0;

	static final TagKey<EntityType<?>> BEARS = tag("bears"), DENNING = tag("denning"), FISH_DIVERS = tag("fish_divers"),
		CARRION_BIRDS = tag("carrion_birds"), GRAZERS = tag("grazers"), HERD_HUNTERS = tag("herd_hunters"), PEN_RAIDERS = tag("pen_raiders"),
		SMALL_PREY = tag("small_prey"), FOX_PREY = tag("fox_prey"), OWLS = tag("owls"), OWL_PREY = tag("owl_prey"), RUTTING = tag("rutting"),
		FIERCE_MOTHERS = tag("fierce_mothers"), SHY_MOTHERS = tag("shy_mothers"), SEASONAL_MOVERS = tag("seasonal_movers"),
		SUMMER_BIRDS = tag("summer_birds"), BERRY_EATERS = tag("berry_eaters"), CHORUS = tag("chorus"), NIGHT_CALLERS = tag("night_callers");
	private static final List<TagKey<EntityType<?>>> ALL = List.of(BEARS, DENNING, FISH_DIVERS, CARRION_BIRDS, GRAZERS, HERD_HUNTERS,
		PEN_RAIDERS, SMALL_PREY, FOX_PREY, OWLS, OWL_PREY, RUTTING, FIERCE_MOTHERS, SHY_MOTHERS, SEASONAL_MOVERS, SUMMER_BIRDS, BERRY_EATERS,
		CHORUS, NIGHT_CALLERS);
	private static final Map<TagKey<EntityType<?>>, Set<Mob>> TRACKED = new HashMap<>();

	private Nature() {
	}

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("nature/" + name));
	}

	static void register() {
		for (TagKey<EntityType<?>> t : ALL) {
			TRACKED.put(t, Collections.newSetFromMap(new WeakHashMap<>()));
		}
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				for (TagKey<EntityType<?>> t : ALL) {
					if (is(mob, t)) {
						TRACKED.get(t).add(mob);
					}
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				for (Set<Mob> s : TRACKED.values()) {
					s.remove(mob);
				}
			}
		});
		FishEaters.register();
		HerdWays.register();
		HuntWays.register();
		WildYear.register();
		WildSigns.register();
	}

	static boolean is(Entity e, TagKey<EntityType<?>> t) {
		return e.getType().builtInRegistryHolder().is(t);
	}

	/** The loaded animals of {@code t} that are alive, in a server level, with a player near. */
	static List<Mob> of(TagKey<EntityType<?>> t) {
		Set<Mob> s = TRACKED.get(t);
		if (s == null || s.isEmpty()) {
			return List.of();
		}
		return s.stream().filter(m -> m.isAlive() && m.level() instanceof ServerLevel l && l.getNearestPlayer(m, NEAR) != null).toList();
	}

	/** "spring", "summer", "autumn", "winter", or null without Kitchen's seasons. */
	static @Nullable String season(Level level) {
		return KitchenCompat.season(level);
	}

	static boolean season(Level level, String name) {
		return name.equals(season(level));
	}

	/** One of the wild's own: not someone's pet, not leashed, ridden, carried or named. */
	static boolean wild(Mob m) {
		return !(m instanceof TamableAnimal t && t.isTame()) && !m.isLeashed() && !m.isPassenger() && !m.isVehicle() && !m.hasCustomName()
			&& !m.isNoAi();
	}

	/** Grown, wild and with nothing else on its mind. */
	static boolean free(Mob m) {
		return m.isAlive() && wild(m) && !m.isBaby() && m.getTarget() == null && !DeathRoll.held(m);
	}

	/** {@code by} strikes {@code on} itself (for animals with no attack of their own). */
	static void strike(ServerLevel level, Mob by, LivingEntity on, float damage) {
		by.getLookControl().setLookAt(on, 30.0F, 30.0F);
		on.hurtServer(level, level.damageSources().mobAttack(by), damage);
	}

	/** Somewhere {@code far} blocks away from {@code from} for {@code m} to go, or null. */
	static @Nullable Vec3 away(PathfinderMob m, Vec3 from, int far) {
		return DefaultRandomPos.getPosAway(m, far, 7, from);
	}

	/** {@code m} runs for {@code to} at {@code pace}. */
	static void go(Mob m, Vec3 to, double pace) {
		m.getNavigation().moveTo(to.x, to.y, to.z, pace);
	}

	/** The top block at {@code x, z} (the first open one above the ground). */
	static BlockPos top(ServerLevel level, int x, int z) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Days since it last rained on the overworld (Kingdoms' towns keep it), 0 if it's raining. */
	static long dryDays(ServerLevel level) {
		return com.jaymoskingdoms.TownTroubles.dryDays(level);
	}
}
