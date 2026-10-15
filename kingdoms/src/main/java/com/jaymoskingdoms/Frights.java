package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.12) Villagers (MCA's too) are afraid of monsters, as they are of fire ({@link Firefighting}): one that sees an
 * enderman, a zombie, a skeleton, a creeper, a spider or any other monster nearby runs, home to its bed if that's
 * near and away from the monster, else simply away; some grown ones fetch the nearest child first and take it along.
 * They stay hidden a while after the monster's gone. A raider or a barbarian counts as a monster too.
 */
final class Frights {
	/** How often villagers look about them (ticks); how close a monster frightens them; how close to a seat they're watched. */
	static final int EVERY = 10;
	static final double FRIGHT = 12.0, WATCHED = 96.0;
	/** How far they run; how near home must be to run there; how long they keep hiding (ticks). */
	static final int RUN = 14, HOME_NEAR = 40;
	static final long HIDE_TICKS = 200L;
	/** The share of grown villagers who take a child along, and their pace. */
	static final float TAKE_CHILD = 0.35F, SPEED = 0.75F;

	private record Fright(BlockPos to, @Nullable UUID child, long until) {
	}

	private static final Map<UUID, Fright> FRIGHTS = new HashMap<>();

	private Frights() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Frights::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FRIGHTS.clear());
	}

	/** (0.5.21) It's running from a monster, or hiding from one. */
	static boolean afraid(UUID villager) {
		return FRIGHTS.containsKey(villager);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		FRIGHTS.values().removeIf(f -> f.until() < now);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && !Vampires.exposed(v))) {
				LivingEntity monster = monster(level, v, f);
				Fright fright = FRIGHTS.get(v.getUUID());
				if (monster != null) {
					if (fright == null) {
						// (0.6.11) a symbiote, a shade, an alien: they cry out what it is
						Oddities.cry(level, v, monster, f);
					}
					if (fright == null || fright.until() - now < HIDE_TICKS / 2) {
						fright = frighten(level, v, monster, now);
					}
				}
				if (fright != null) {
					flee(level, v, fright);
				}
			}
		}
	}

	/**
	 * The nearest monster it can see, close enough to frighten it; null if none. (0.6.4) A player changed into one of
	 * the Xeno-Band's aliens counts, unless {@code f}'s people saw them change ({@link Aliens}); (0.6.11) so does one in
	 * the symbiote's suit or walking as a shade, and a mob the symbiote has taken ({@link Oddities#monstrous}); (0.7.10) and a Z Fighter
	 * with a tail out, unless they have explained it to {@code v} ({@link Tails}).
	 */
	private static @Nullable LivingEntity monster(ServerLevel level, Villager v, Faction f) {
		LivingEntity best = null;
		double bestD = FRIGHT * FRIGHT;
		for (LivingEntity m : level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(FRIGHT),
			m -> m != v && m.isAlive() && (m instanceof Mob mob && (frightful(mob) || Oddities.monstrous(mob))
				|| m instanceof ServerPlayer p && (Aliens.feared(p, f.id()) || Tails.feared(p, v))))) {
			double d = m.distanceToSqr(v);
			if (d < bestD && v.hasLineOfSight(m)) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static boolean frightful(Mob m) {
		if (m instanceof Folk folk) {
			return Assault.attacker(folk) || folk.role() == Folk.BARBARIAN || folk.role() == Folk.CHIEF || Orcs.isOrc(folk);
		}
		return m instanceof Enemy || Vampires.exposed(m);
	}

	/** (0.6.11) Something came down at {@code at} (a meteor, a star): it runs from the spot and hides a while. */
	static void scare(ServerLevel level, Villager v, Vec3 at) {
		long now = level.getGameTime();
		if (!FRIGHTS.containsKey(v.getUUID())) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.6F, 1.3F);
		}
		Fright fright = new Fright(away(level, v, at), null, now + HIDE_TICKS);
		FRIGHTS.put(v.getUUID(), fright);
		flee(level, v, fright);
	}

	/** (0.7.65) It hides at {@code to} (its home) until {@code until}, as from a monster: a war party is coming ({@link WarAlert}). */
	static void shelter(ServerLevel level, Villager v, BlockPos to, long until) {
		Fright had = FRIGHTS.get(v.getUUID());
		if (had != null && had.until() >= until) {
			return;
		}
		Fright fright = new Fright(had != null ? had.to() : to, had != null ? had.child() : null, until);
		FRIGHTS.put(v.getUUID(), fright);
		flee(level, v, fright);
	}

	/** It takes fright: where it'll run (home if that's near and not toward the monster, else away), and with which child. */
	private static Fright frighten(ServerLevel level, Villager v, LivingEntity monster, long now) {
		BlockPos to = away(level, v, monster.position());
		Optional<GlobalPos> home = v.getBrain().getMemoryInternal(MemoryModuleType.HOME);
		if (home == null) {
			home = Optional.empty();
		}
		if (home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().closerThan(v.blockPosition(), HOME_NEAR)
			&& home.get().pos().distSqr(monster.blockPosition()) > v.blockPosition().distSqr(monster.blockPosition())) {
			to = home.get().pos();
		}
		UUID child = null;
		if (!v.isBaby() && level.getRandom().nextFloat() < TAKE_CHILD) {
			Villager c = level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(FRIGHT), x -> x.isAlive() && x.isBaby()).stream()
				.min((a, b) -> Double.compare(a.distanceToSqr(v), b.distanceToSqr(v))).orElse(null);
			child = c == null ? null : c.getUUID();
		}
		if (!FRIGHTS.containsKey(v.getUUID())) {
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.6F, 1.3F);
		}
		Fright fright = new Fright(to, child, now + HIDE_TICKS);
		FRIGHTS.put(v.getUUID(), fright);
		return fright;
	}

	private static void flee(ServerLevel level, Villager v, Fright fright) {
		if (fright.child() != null && level.getEntity(fright.child()) instanceof Villager c && c.isAlive()) {
			if (v.distanceToSqr(c) > 4.0) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(c, false), SPEED, 1);
				return;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(c, fright.to(), SPEED, 1);
		}
		BehaviorUtils.setWalkAndLookTargetMemories(v, fright.to(), SPEED, 1);
	}

	/** A spot on the ground {@link #RUN} blocks from {@code monster}, the way {@code v} already is from it. */
	private static BlockPos away(ServerLevel level, Villager v, Vec3 monster) {
		Vec3 off = v.position().subtract(monster).multiply(1.0, 0.0, 1.0);
		if (off.lengthSqr() < 1.0E-3) {
			off = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 to = v.position().add(off.normalize().scale(RUN));
		int x = (int)Math.floor(to.x), z = (int)Math.floor(to.z);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}
}
