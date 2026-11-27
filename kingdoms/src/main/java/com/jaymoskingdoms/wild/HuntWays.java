package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Hunter and hunted (Andy's nature picks, 2026-11-26; {@link Nature}).
 * <ul>
 * <li><b>Birds over a kill</b>: a body lying out (Jaymo's Kitchen's) draws the carrion birds within {@link #CIRCLE_DRAW}
 * to circle over it ({@link #CIRCLE_HIGH} up) and then come down to it; by day, where there are none, a few crows come
 * ({@link #CROWS_COME}). Hungry hunters within {@link #CIRCLE_SEEN} see the circling and come over.</li>
 * <li><b>The bigger one takes the kill</b>: two hunters at one body, the smaller is driven off and goes hungry.</li>
 * <li><b>Foxes pounce for mice</b> and crickets ({@code nature/fox_prey}), and <b>owls hunt at dusk</b>: barn owls drop
 * on mice and rats ({@code nature/owl_prey}) from dusk to dawn, so a farm with owls keeps fewer rats.</li>
 * <li><b>Small prey freeze, then bolt</b>: a rabbit or a mouse with a hunter (or a player who isn't sneaking) within
 * {@link #FREEZE_SEEN} goes still, and when it comes within {@link #BOLT_NEAR} it breaks away in sharp turns.</li>
 * <li><b>Pens raided at night</b>: on dark nights a hungry wolf, coyote or bear goes for livestock that isn't lit
 * ({@link #PEN_LIGHT}) or watched by a dog ({@link #DOG_WATCH}); a dog near goes for the raider.</li>
 * </ul>
 */
final class HuntWays {
	static final int STEP_EVERY = 20;
	static final double CIRCLE_DRAW = 64.0, CIRCLE_SEEN = 48.0, CIRCLE_HIGH = 12.0, CIRCLE_RADIUS = 6.0;
	static final long CIRCLE_TICKS = 20L * 25, KILL_KEPT = 20L * 60 * 4;
	static final int CROWS_COME = 3;
	static final double KILL_NEAR = 5.0;
	static final double FOX_LOOK = 10.0, OWL_LOOK = 16.0, STRIKE_REACH = 1.6;
	static final float OWL_STRIKE = 6.0F;
	static final double FREEZE_SEEN = 10.0, BOLT_NEAR = 4.0, BOLT_PACE = 1.8;
	static final double PEN_LOOK = 32.0, DOG_WATCH = 16.0;
	static final int PEN_LIGHT = 8;

	private static final Identifier CORPSE = Identifier.fromNamespaceAndPath("jaymoskitchen", "corpse");
	private static final TagKey<EntityType<?>> LIVESTOCK = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("herds_together"));

	/** Bodies the birds have found: when. */
	private static final Map<Entity, Long> FOUND = new WeakHashMap<>();
	/** Small prey bolting: which way it's turning. */
	private static final Map<Mob, Integer> ZIG = new WeakHashMap<>();

	private HuntWays() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HuntWays::tick);
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % STEP_EVERY != 0) {
			return;
		}
		// (every step, so a circling bird is always given its next point on the ring and never left hanging at the last one)
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.level() instanceof ServerLevel level) {
				kills(level, p);
			}
		}
		for (Mob prey : Nature.of(Nature.SMALL_PREY)) {
			if (prey instanceof PathfinderMob pm && Nature.wild(prey) && prey.level() instanceof ServerLevel level) {
				freeze(level, pm);
			}
		}
		if (t % 40 == 0) {
			for (Mob fox : Nature.of(Nature.FOX_PREY).isEmpty() ? List.<Mob>of() : foxes(server)) {
				if (fox instanceof Fox f && Nature.free(f) && !f.isSleeping() && f.level() instanceof ServerLevel level) {
					LivingEntity mouse = nearest(level, f, Nature.FOX_PREY, FOX_LOOK);
					if (mouse != null) {
						f.setTarget(mouse);
					}
				}
			}
			for (Mob owl : Nature.of(Nature.OWLS)) {
				if (Nature.free(owl) && owl.level() instanceof ServerLevel level && dusk(level)) {
					owl(level, owl);
				}
			}
		}
		if (t % 200 == 0) {
			for (Mob r : Nature.of(Nature.PEN_RAIDERS)) {
				if (Nature.free(r) && !FedPredators.fed(r) && r.level() instanceof ServerLevel level && level.isDarkOutside()) {
					raid(level, r);
				}
			}
		}
	}

	private static List<Mob> foxes(MinecraftServer server) {
		List<Mob> all = new java.util.ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			all.addAll(p.level().getEntitiesOfClass(Fox.class, p.getBoundingBox().inflate(Nature.NEAR)));
		}
		return all;
	}

	private static boolean dusk(ServerLevel level) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		return day >= 12000L || day < 500L;
	}

	// ---- bodies: the birds, the hunters, the bigger one ------------------------------------------------------------

	private static void kills(ServerLevel level, ServerPlayer p) {
		EntityType<?> corpse = BuiltInRegistries.ENTITY_TYPE.getOptional(CORPSE).orElse(null);
		if (corpse == null) {
			return;
		}
		long now = level.getGameTime();
		for (Entity body : level.getEntities(corpse, p.getBoundingBox().inflate(Nature.NEAR), e -> e.isAlive() && level.canSeeSky(e.blockPosition().above()))) {
			Long found = FOUND.get(body);
			if (found == null) {
				FOUND.put(body, now);
				found = now;
				birdsFind(level, body);
			}
			if (now - found > KILL_KEPT) {
				continue;
			}
			boolean circling = now - found < CIRCLE_TICKS;
			for (Mob bird : Nature.of(Nature.CARRION_BIRDS)) {
				if (bird.level() == level && Nature.free(bird) && !gone(bird) && bird.distanceToSqr(body) < CIRCLE_DRAW * CIRCLE_DRAW) {
					if (circling) {
						double a = (now / 20.0 + bird.getId()) * 0.4;
						Nature.go(bird, body.position().add(Math.cos(a) * CIRCLE_RADIUS, CIRCLE_HIGH, Math.sin(a) * CIRCLE_RADIUS), 1.2);
					} else if (bird.distanceToSqr(body) > 4.0) {
						Nature.go(bird, body.position(), 1.0);
					}
				}
			}
			for (Mob h : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(CIRCLE_SEEN),
				m -> (Nature.is(m, Nature.HERD_HUNTERS) || Nature.is(m, Nature.BEARS)) && Nature.free(m) && !FedPredators.fed(m))) {
				if (h.distanceToSqr(body) > KILL_NEAR * KILL_NEAR) {
					Nature.go(h, body.position(), 1.0);
				}
			}
			bigger(level, body);
		}
	}

	/** A crow that has had its fill and is flying off for good: not called back to the body. */
	private static boolean gone(Mob bird) {
		return bird instanceof com.jaymoskingdoms.Crow crow && crow.isLeaving();
	}

	/** A body with no carrion birds about: by day, a few crows come down. */
	private static void birdsFind(ServerLevel level, Entity body) {
		for (Mob bird : Nature.of(Nature.CARRION_BIRDS)) {
			if (bird.level() == level && bird.distanceToSqr(body) < CIRCLE_DRAW * CIRCLE_DRAW) {
				return;
			}
		}
		if (level.isDarkOutside() || level.isRaining()) {
			return;
		}
		for (int i = 0; i < CROWS_COME; i++) {
			Entity crow = com.jaymoskingdoms.Crow.TYPE == null ? null : com.jaymoskingdoms.Crow.TYPE.create(level, EntitySpawnReason.EVENT);
			if (crow != null) {
				crow.snapTo(body.getX() + level.getRandom().nextGaussian() * 6.0, body.getY() + CIRCLE_HIGH + 6.0, body.getZ() + level.getRandom().nextGaussian() * 6.0,
					level.getRandom().nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(crow);
			}
		}
	}

	/** Two hunters at one body: the smaller is driven off and goes hungry. */
	private static void bigger(ServerLevel level, Entity body) {
		List<Mob> at = level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(KILL_NEAR),
			m -> (Nature.is(m, Nature.HERD_HUNTERS) || Nature.is(m, Nature.BEARS)) && m.isAlive() && Nature.wild(m) && !m.isBaby());
		if (at.size() < 2) {
			return;
		}
		Mob big = at.get(0);
		for (Mob m : at) {
			if (DeathRoll.bulk(m) > DeathRoll.bulk(big)) {
				big = m;
			}
		}
		for (Mob m : at) {
			if (m.getType() != big.getType() && m instanceof PathfinderMob pm) {
				Vec3 to = Nature.away(pm, big.position(), 16);
				if (to != null) {
					Nature.go(pm, to, 1.4);
				}
				FedPredators.starve(m);
			}
		}
		big.getLookControl().setLookAt(body);
	}

	// ---- small prey, foxes and owls ---------------------------------------------------------------------------------

	private static LivingEntity nearest(ServerLevel level, Mob by, TagKey<EntityType<?>> kind, double look) {
		LivingEntity best = null;
		double bestD = look * look;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, by.getBoundingBox().inflate(look), e -> e.isAlive() && Nature.is(e, kind))) {
			double d = e.distanceToSqr(by);
			if (d < bestD && by.hasLineOfSight(e)) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void owl(ServerLevel level, Mob owl) {
		LivingEntity prey = nearest(level, owl, Nature.OWL_PREY, OWL_LOOK);
		if (prey == null) {
			return;
		}
		if (owl.distanceTo(prey) > STRIKE_REACH) {
			Nature.go(owl, prey.position(), 1.3);
			return;
		}
		Nature.strike(level, owl, prey, OWL_STRIKE);
	}

	/** A small beast with a hunter near goes still; too near, it breaks away in sharp turns. */
	private static void freeze(ServerLevel level, PathfinderMob prey) {
		Entity threat = null;
		double best = FREEZE_SEEN * FREEZE_SEEN;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, prey.getBoundingBox().inflate(FREEZE_SEEN), e -> e.isAlive() && e != prey)) {
			boolean hunter = e instanceof Player p ? !p.isShiftKeyDown() && !p.isCreative() && !p.isSpectator()
				: e instanceof Mob m && (Nature.is(m, Nature.HERD_HUNTERS) || Nature.is(m, Nature.OWLS) || m instanceof Fox) && Nature.wild(m);
			double d = e.distanceToSqr(prey);
			if (hunter && d < best) {
				best = d;
				threat = e;
			}
		}
		if (threat == null) {
			ZIG.remove(prey);
			return;
		}
		if (best > BOLT_NEAR * BOLT_NEAR && !ZIG.containsKey(prey)) {
			prey.getNavigation().stop();
			prey.setDeltaMovement(0.0, prey.getDeltaMovement().y, 0.0);
			return;
		}
		int side = -ZIG.getOrDefault(prey, 1);
		ZIG.put(prey, side);
		Vec3 off = prey.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
		off = off.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : off.normalize();
		Vec3 sideways = new Vec3(-off.z, 0.0, off.x).scale(side * 3.0);
		Nature.go(prey, prey.position().add(off.scale(6.0)).add(sideways), BOLT_PACE);
	}

	// ---- pens at night ----------------------------------------------------------------------------------------------

	private static void raid(ServerLevel level, Mob r) {
		Mob best = null;
		double bestD = PEN_LOOK * PEN_LOOK;
		for (Mob stock : level.getEntitiesOfClass(Mob.class, r.getBoundingBox().inflate(PEN_LOOK), m -> m.isAlive() && Nature.is(m, LIVESTOCK))) {
			double d = stock.distanceToSqr(r);
			if (d >= bestD) {
				continue;
			}
			BlockPos at = stock.blockPosition();
			if (level.getBrightness(LightLayer.BLOCK, at) >= PEN_LIGHT) {
				continue;
			}
			bestD = d;
			best = stock;
		}
		if (best == null) {
			return;
		}
		List<Wolf> dogs = level.getEntitiesOfClass(Wolf.class, best.getBoundingBox().inflate(DOG_WATCH), w -> w.isAlive() && w.isTame() && !w.isOrderedToSit());
		if (!dogs.isEmpty()) {
			for (Wolf dog : dogs) {
				if (dog.getTarget() == null) {
					dog.setTarget(r);
					dog.playAmbientSound();
				}
			}
			return;
		}
		r.setTarget(best);
	}
}
